/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.graal.injection.processor;

import com.mastfrog.graal.injection.processor.GraalEntryIndexFactory.GraalEntry;
import com.mastfrog.util.service.AnnotationIndexFactory;
import com.mastfrog.util.service.AnnotationUtils;
import com.mastfrog.util.service.IndexEntry;
import com.mastfrog.util.fileformat.SimpleJSON;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

/**
 *
 * @author Tim Boudreau
 */
final class GraalEntryIndexFactory extends AnnotationIndexFactory<GraalEntry> {

    @Override
    protected AnnotationIndex<GraalEntry> newIndex(String path) {
        return new GraalIndex();
    }

    static final class GraalIndex extends AnnotationIndex<GraalEntry> {

        @Override
        public void write(OutputStream out, ProcessingEnvironment processingEnv) throws IOException {
            Map<String, List<GraalEntry>> m1 = new TreeMap<>();
            Set<String> alls = new HashSet<>();
            // Group by class name
            for (GraalEntry g : this) {
                if (g.isAll()) {
                    alls.add(g.className);
                }
                List<GraalEntry> l = m1.get(g.className);
                if (l == null) {
                    l = new ArrayList<>(2);
                    m1.put(g.className, l);
                }
                l.add(g);
            }
            // Build entries for all items where we are exposing all
            // methods and constructors;  consolidate any fields
            // into such records, since there is no allDeclaredFields
            // option
            List<Map<String, Object>> all = new ArrayList<>(this.size());
            for (String a : alls) {
                List<GraalEntry> listFor = m1.get(a);
                Map<String, Object> curr = new LinkedHashMap<>();
                curr.put("name", a);
                curr.put("allDeclaredConstructors", true);
                curr.put("allDeclaredMethods", true);
                curr.put("allPublicConstructors", true);
                curr.put("allPublicMethods", true);
                List<Map<String, Object>> fields = new ArrayList<>();
                Set<String> seenFieldNames = new HashSet<>();
                // Find any records that expose fields and merge those
                // in here
                for (GraalEntry e : listFor) {
                    if (e.isField()) {
                        // Ensure no duplicates
                        if (seenFieldNames.contains(e.memberName)) {
                            continue;
                        }
                        Map<String, Object> fieldInfo = new LinkedHashMap<>();
                        seenFieldNames.add(e.memberName);
                        fieldInfo.put("name", e.memberName);
                        fields.add(fieldInfo);
                    }
                }
                if (!fields.isEmpty()) {
                    curr.put("fields", fields);
                }
            }

            for (Map.Entry<String, List<GraalEntry>> e : m1.entrySet()) {
                if (alls.contains(e.getKey())) {
                    // already added
                    continue;
                }
                // New record
                Map<String, Object> curr = new LinkedHashMap<>();
                curr.put("name", e.getKey());
                List<Map<String, Object>> methods = new ArrayList<>(5);
                List<Map<String, Object>> fields = new ArrayList<>(5);
                // Duplicate checking
                Set<String> seenMethodSignatures = new HashSet<>(10);
                Set<String> seenFieldNames = new HashSet<>(10);
                for (GraalEntry en : e.getValue()) {
                    Map<String, Object> currMember = new LinkedHashMap<>();
                    currMember.put("name", en.memberName);
                    if (en.isField()) {
                        if (!seenFieldNames.contains(en.memberName)) {
                            fields.add(currMember);
                            seenFieldNames.add(en.memberName);
                        }
                    } else {
                        String signature = en.memberName + en.parameterTypes;
                        if (!seenMethodSignatures.contains(signature)) {
                            currMember.put("parameterTypes", en.parameterTypes);
                            methods.add(currMember);
                            seenMethodSignatures.add(signature);
                        }
                    }
                }
                if (!methods.isEmpty()) {
                    curr.put("methods", methods);
                }
                if (!fields.isEmpty()) {
                    curr.put("fields", fields);
                }
                if (!methods.isEmpty() || !fields.isEmpty()) {
                    all.add(curr);
                }
            }
            // Sort for human readability and repeatable builds
            Collections.sort(all, (a, b) -> {
                return ((String) a.get("name")).compareTo((String) b.get("name"));
            });
            out.write(SimpleJSON.stringify(all, SimpleJSON.Style.MINIFIED).getBytes(UTF_8));
        }
    }

    static final class GraalEntry implements IndexEntry {

        private final String className;
        private final String memberName;
        private final List<String> parameterTypes;
        private Element[] els;

        GraalEntry(String className, Element... els) {
            this.className = className;
            this.memberName = null;
            this.parameterTypes = null;
            this.els = els;
        }

        GraalEntry(String className, String fieldName, Element... els) {
            this.className = className;
            this.memberName = fieldName;
            this.els = els;
            this.parameterTypes = null;
        }

        GraalEntry(String className, String methodName, List<String> parameterTypes, Element... els) {
            this.className = className;
            this.memberName = methodName == null ? "<init>" : methodName;
            this.parameterTypes = parameterTypes == null
                    || parameterTypes.isEmpty() ? Collections.emptyList() : new ArrayList<>(parameterTypes);
            this.els = els == null ? new Element[0] : els;
        }

        public String toString() {
            return className + "." + (memberName != null ? memberName : "")
                    + (parameterTypes == null ? "" : " (" + AnnotationUtils.join(',', parameterTypes) + ")");
        }

        public boolean isField() {
            return parameterTypes == null;
        }

        public boolean isAll() {
            return memberName == null && parameterTypes == null;
        }

        @Override
        public Element[] elements() {
            return els;
        }

        @Override
        public void addElements(Element... els) {
            Set<Element> all = new HashSet<>(Arrays.asList(this.els));
            all.addAll(Arrays.asList(els));
            this.els = all.toArray(new Element[all.size()]);
        }

        @Override
        public int compareTo(IndexEntry o) {
            GraalEntry other = (GraalEntry) o;
            if (this.parameterTypes == null && other.parameterTypes != null) {
                return 1;
            } else if (this.parameterTypes != null && other.parameterTypes == null) {
                return -1;
            }
            if (this.memberName == null && other.memberName != null) {
                return -1;
            } else if (this.memberName != null && other.memberName == null) {
                return 1;
            }
            if (this.className == null && other.className != null) {
                return -1;
            } else if (this.className != null && other.className == null) {
                return 1;
            }
            int result = className.compareTo(other.className);
            if (result == 0 && this.memberName != null) {
                result = memberName.compareTo(other.memberName);
            }
            if (result == 0 && parameterTypes != null) {
                result = parameterTypes.size() > other.parameterTypes.size() ? 1
                        : parameterTypes.size() == other.parameterTypes.size() ? 0
                        : -1;
            }
            return result;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 41 * hash + Objects.hashCode(this.className);
            hash = 41 * hash + Objects.hashCode(this.memberName);
            hash = 41 * hash + Objects.hashCode(this.parameterTypes);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final GraalEntry other = (GraalEntry) obj;
            if (className.equals(other.className)) {
                if ((memberName == null) != (other.memberName == null)) {
                    return false;
                }
                if (memberName != null && !memberName.equals(other.memberName)) {
                    return false;
                }
                if (!Objects.equals(parameterTypes, other.parameterTypes)) {
                    return false;
                }
                return true;
            }
            return false;
        }
    }
}
