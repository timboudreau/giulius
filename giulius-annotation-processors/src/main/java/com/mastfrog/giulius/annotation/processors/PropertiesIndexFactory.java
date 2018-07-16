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
package com.mastfrog.giulius.annotation.processors;

import com.mastfrog.giulius.annotation.processors.PropertiesIndexFactory.PropertiesIndexEntry;
import com.mastfrog.util.service.AnnotationIndexFactory;
import com.mastfrog.util.service.IndexEntry;
import com.mastfrog.util.fileformat.PropertiesFileUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/**
 *
 * @author Tim Boudreau
 */
public class PropertiesIndexFactory extends AnnotationIndexFactory<PropertiesIndexEntry> {

    @Override
    protected AnnotationIndex<PropertiesIndexEntry> newIndex(String path) {
        return new PropertiesIndex();
    }

    private static final class PropertiesIndex extends AnnotationIndex<PropertiesIndexEntry> {

        private StringBuilder qualifiedNameOf(Element el) {
            LinkedList<String> names = new LinkedList<>();
            while (el != null && !(el instanceof TypeElement)) {
                if (el instanceof TypeElement) {
                    names.push(((TypeElement) el).getQualifiedName().toString());
                } else {
                    names.push(el.getSimpleName().toString());
                }
                el = el.getEnclosingElement();
            }
            StringBuilder sb = new StringBuilder();
            for (String name : names) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(name);
            }
            return sb;
        }

        @Override
        public void write(OutputStream out, ProcessingEnvironment processingEnv) throws IOException {
            Properties props = new Properties();
            Set<Element> els = new HashSet<>();
            for (PropertiesIndexEntry e : this) {
                props.putAll(e.properties);
                els.addAll(e.els);
            }
            List<String> names = new ArrayList<>(els.size());
            for (Element e : els) {
                names.add(e.toString());
            }
            Collections.sort(names);
            StringBuilder cmt = new StringBuilder();
            for (Iterator<String> it = names.iterator(); it.hasNext();) {
                String name = it.next();
                cmt.append(name);
                if (it.hasNext()) {
                    cmt.append(", ");
                }
            }
            PropertiesFileUtils.savePropertiesFile(props, out, cmt.toString(), true);
        }
    }

    static final class PropertiesIndexEntry implements IndexEntry {

        private final Properties properties;
        private final Set<Element> els = new HashSet<>(2);

        PropertiesIndexEntry(Properties properties, Element el) {
            this.properties = properties;
            if (el != null) {
                this.els.add(el);
            }
        }

        @Override
        public Element[] elements() {
            return this.els.toArray(new Element[els.size()]);
        }

        @Override
        public void addElements(Element... els) {
            this.els.addAll(Arrays.asList(els));
        }

        @Override
        public int compareTo(IndexEntry o) {
            return 0;
        }

        public int hashCode() {
            return properties.hashCode();
        }

        public boolean equals(Object o) {
            return o == this ? true : o == null ? false
                    : o instanceof PropertiesIndexEntry
                            ? properties.equals(((PropertiesIndexEntry) o).properties)
                            : false;
        }
    }
}
