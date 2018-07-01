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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mastfrog.graal.injection.processor.GraalEntryIndexFactory.GraalEntry;
import com.mastfrog.util.service.AnnotationIndexFactory;
import com.mastfrog.util.service.IndexEntry;
import java.io.IOException;
import java.io.OutputStream;
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
            for (GraalEntry g : this) {
                List<GraalEntry> l = m1.get(g.className);
                if (l == null) {
                    l = new ArrayList<>(2);
                    m1.put(g.className, l);
                }
                l.add(g);
            }
            List<Map<String, Object>> all = new ArrayList<>(this.size());
            Set<String> alls = new HashSet<>();
            for (Map.Entry<String, List<GraalEntry>> e : m1.entrySet()) {
                Map<String, Object> curr = new LinkedHashMap<>();
                curr.put("name", e.getKey());
                List<Map<String, Object>> methods = new ArrayList<>(2);
                List<Map<String, Object>> fields = new ArrayList<>(2);
                for (GraalEntry en : e.getValue()) {
                    if (alls.contains(en.className)) {
                        continue;
                    }
                    if (en.isAll()) {
                        alls.add(en.className);
                        curr.put("allDeclaredConstructors", "true");
                        curr.put("allDeclaredMethods", "true");
                        curr.put("allPublicConstructors", "true");
                        curr.put("allPublicMethods", "true");
                        continue;
                    }
                    Map<String, Object> currMember = new LinkedHashMap<>();
                    currMember.put("name", en.memberName);
                    if (en.isField()) {
                        fields.add(currMember);
                    } else {
                        currMember.put("parameterTypes", en.parameterTypes);
                        methods.add(currMember);
                    }
                }
                if (!methods.isEmpty()) {
                    curr.put("methods", methods);
                }
                if (!fields.isEmpty()) {
                    curr.put("fields", fields);
                }
                all.add(curr);
            }
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(out, all);
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
            this.parameterTypes = parameterTypes == null || parameterTypes.isEmpty() ? Collections.emptyList() : new ArrayList<>(parameterTypes);
            this.els = els == null ? new Element[0] : els;
        }

        public String toString() {
            return className + "." + memberName + (parameterTypes == null ? "" : " (" + parameterTypes + ")");
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
            int result = className.compareTo(other.className);
            if (result == 0) {
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
            return className.equals(other.className)
                    && memberName.equals(other.memberName)
                    && Objects.equals(parameterTypes, other.parameterTypes);
        }
    }
}
