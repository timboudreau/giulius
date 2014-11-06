/*
 * The MIT License
 *
 * Copyright 2014 tim.
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
package com.mastfrog.giulius.annotations.processors;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * An annotation processor that generates an index correctly, waiting until all
 * rounds of annotation processing have been completed before writing the output
 * so as to avoid a FilerException on opening a file for writing twice in one
 * compile sequence.
 *
 * @author Tim Boudreau
 */
public abstract class IndexGeneratingProcessor extends AbstractProcessor {

    private int count;
    private final Map<Filer, Map<String, SortedSet<Line>>> outputFilesByProcessor
            = new HashMap<>();

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.processingEnv = processingEnv;
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            if (roundEnv.errorRaised()) {
                return false;
            }
            if (roundEnv.processingOver()) {
                write();
                outputFilesByProcessor.clear();
                return true;
            } else {
                return handleProcess(annotations, roundEnv);
            }
        } catch (Exception e) {
            if (processingEnv != null) {
                processingEnv.getMessager().printMessage(Kind.ERROR, e.toString());
            }
            e.printStackTrace(System.err);
            return false;
        }
    }

    private void write() {
        for (Map.Entry<Filer, Map<String, SortedSet<Line>>> outputFiles : outputFilesByProcessor.entrySet()) {
            Filer filer = outputFiles.getKey();
            for (Map.Entry<String, SortedSet<Line>> entry : outputFiles.getValue().entrySet()) {
                try {
                    List<Element> elements = new ArrayList<>();
                    for (Line line : entry.getValue()) {
                        elements.addAll(Arrays.asList(line.el));
                    }
                    FileObject out = filer.createResource(StandardLocation.CLASS_OUTPUT, "", entry.getKey(),
                            elements.toArray(new Element[0]));
                    OutputStream os = out.openOutputStream();
                    try {
                        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(os, "UTF-8"))) {
                            for (Line line : entry.getValue()) {
                                line.write(w);
                            }
                            w.flush();
                        }
                    } finally {
                        os.close();
                    }
                } catch (IOException x) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Failed to write to " + entry.getKey() + ": " + x.toString());
                }
            }
        }
    }

    protected abstract boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv);

    protected void addLine(String path, String line, Element... el) {
        Line l = new Line(count++, el, line);
        Filer filer = processingEnv.getFiler();
        Map<String, SortedSet<Line>> linesForPath = outputFilesByProcessor.get(filer);
        if (linesForPath == null) {
            linesForPath = new HashMap<>();
            outputFilesByProcessor.put(filer, linesForPath);
        }
        SortedSet<Line> set = linesForPath.get(path);
        if (set == null) {
            set = new TreeSet<>();
        } else if (set.contains(l)) {
            List<Element> els = new LinkedList<>();
            for (Line ll : set) {
                if (line.equals(ll)) {
                    els.addAll(Arrays.asList(ll.el));
                }
            }
            l = new Line(l.index, els.toArray(new Element[els.size()]), line);
        }
        set.add(l);
        linesForPath.put(path, set);
    }

    protected String comment(String line) {
        return "# " + line;
    }

    private final class Line implements Comparable<Line> {

        private final int index;
        private final Element[] el;
        private final String line;

        private Line(int index, Element[] el, String line) {
            this.index = index;
            this.el = el;
            this.line = line;
        }

        @Override
        public int compareTo(Line other) {
            Integer mine = index;
            return mine.compareTo(other.index);
        }

        private void write(PrintWriter w) {
            String origin = origin();
            if (origin != null) {
                w.println(comment(origin));
            }
            w.println(line);
        }

        String origin() {
            if (el.length == 1) {
                if (el[0] instanceof TypeElement) {
                    return ((TypeElement) el[0]).getQualifiedName().toString();
                } else if (el[0] instanceof PackageElement) {
                    return ((PackageElement) el[0]).getQualifiedName().toString();
                }
            } else {
                StringBuilder sb = new StringBuilder();
                for (Element e : el) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    if (e instanceof PackageElement) {
                        PackageElement pe = (PackageElement) e;
                        sb.append(pe.getQualifiedName().toString());
                    } else if (e instanceof TypeElement) {
                        TypeElement te = (TypeElement) e;
                        sb.append(te.getQualifiedName().toString());
                    }
                }
                return sb.length() == 0 ? null : sb.toString();
            }
            return null;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            String origin = origin();
            if (origin != null) {
                sb.append(comment(origin));
            }
            sb.append(line).append("\n");
            return sb.toString();
        }

        public boolean equals(Object o) {
            return o instanceof Line && ((Line) o).line.equals(line);
        }

        public int hashCode() {
            return line.hashCode();
        }
    }
}
