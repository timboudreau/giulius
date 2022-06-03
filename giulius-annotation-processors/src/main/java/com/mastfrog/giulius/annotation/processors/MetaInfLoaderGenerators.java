/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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

import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 *
 * @author Tim Boudreau
 */
final class MetaInfLoaderGenerators {

    private static Map<ProcessingEnvironment, GenWriter> items = new WeakHashMap<>();

    static void requiredForElement(Element el, ProcessingEnvironment env) {
        GenWriter gw = items.computeIfAbsent(env, pe -> new GenWriter());
        gw.add(el);
    }

    static void onDone(ProcessingEnvironment e) throws IOException {
        GenWriter gw = items.remove(e);
        if (gw != null) {
            gw.write(e);
        }
    }

    static final class GenWriter {

        private Set<Element> allElements = new HashSet<>();
        private boolean written;

        void add(Element el) {
            allElements.add(el);
        }

        private String packageOf(Element el) {
            while (el != null && !(el instanceof PackageElement)) {
                el = el.getEnclosingElement();
            }
            if (el instanceof PackageElement) {
                PackageElement pl = (PackageElement) el;
                return pl.getQualifiedName().toString();
            }
            return "";
        }

        private String leastPackage() {
            Set<List<String>> all = new HashSet<>();
            for (Element e : allElements) {
                String a = packageOf(e);
                all.add(Arrays.asList(a.split("\\.")));
            }
            List<List<String>> sorted = new ArrayList<>(all);
            Collections.sort(sorted, (a, b) -> {
                return 0;
            });
            StringBuilder sb = new StringBuilder();
            List<String> target = sorted.get(0);
            for (int i = 0; i < target.size(); i++) {
                sb.append(target);
                if (i < target.size() - 1) {
                    sb.append('.');
                }
            }
            return sb.toString();
        }

        void write(ProcessingEnvironment env) throws IOException {
            if (written) {
                return;
            }
            Filer filer = env.getFiler();
            String pkg = leastPackage();
            System.out.println("CREATING " + pkg + ".GeneratedMetaInfLoader");
            FileObject fo = filer.createResource(StandardLocation.CLASS_OUTPUT, pkg,
                    "GeneratedMetaInfLoader", allElements.toArray(new Element[0]));
            try ( OutputStream out = fo.openOutputStream()) {
                StringBuilder sb = new StringBuilder("package ").append(pkg).append(";\n");
                sb.append("import ").append("com.mastfrog.metainf.MetaInfLoader;\n");
                sb.append("public final class GeneratedMetaInfLoader extends MetaInfLoader{}");
            }
            FileObject fo2 = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/services/com.mastfrog.metainf.MetaInfLoader",
                    allElements.toArray(new Element[0]));
            try ( OutputStream out = fo2.openOutputStream()) {
                out.write((pkg + ".GeneratedMetaInfLoader\n").getBytes(UTF_8));
            }
        }
    }
}
