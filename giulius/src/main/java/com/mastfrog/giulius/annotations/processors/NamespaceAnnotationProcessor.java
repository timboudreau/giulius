/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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

import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.guicy.annotations.Value;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({"com.mastfrog.guicy.annotations.Namespace", "com.mastfrog.guicy.annotations.Value"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class NamespaceAnnotationProcessor extends AbstractProcessor {

    private ProcessingEnvironment env;
    private static final char[] illegalChars = "/\\/:[],'\"<>?%+".toCharArray();

    private void addNamespaceForElement(String ns, Element e, Map<String, Set<Element>> elementsForNamespace) {
        Set<Element> s = elementsForNamespace.get(ns);
        if (s == null) {
            s = new HashSet<>();
            elementsForNamespace.put(ns, s);
        }
        s.add(e);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        Set<String> knownNamespaces = new HashSet<>();
        Map<String, Set<Element>> elementsForNamespace = new HashMap<>();
        Set<Element> allElements = new HashSet<>();

        for (Element e : roundEnv.getElementsAnnotatedWith(Namespace.class)) {
            Namespace ns = e.getAnnotation(Namespace.class);
            testNamespace(ns, e);
            knownNamespaces.add(ns.value());
            addNamespaceForElement(ns.value(), e, elementsForNamespace);
            allElements.add(e);
        }

        for (Element e : roundEnv.getElementsAnnotatedWith(Value.class)) {
            Value v = e.getAnnotation(Value.class);
            if (!Namespace.DEFAULT.equals(v.namespace().value())) {
                testNamespace(v.namespace(), e);
            }

            if ("".equals(v.value().trim())) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Empty name",
                        e, findMirror(Value.class, e));
            }
            knownNamespaces.add(v.namespace().value());
            addNamespaceForElement(v.namespace().value(), e, elementsForNamespace);
            allElements.add(e);
        }
        knownNamespaces.remove(Namespace.DEFAULT);
        if (!knownNamespaces.isEmpty()) {
            try {
                Set<String> found = readNamespaces();
                if (!found.equals(knownNamespaces)) {
                    List<String> all = new ArrayList<>(knownNamespaces);
                    all.addAll(found);
                    Collections.sort(all); //so if they're accidentally checked in to version control, we minimize diffs
                    FileObject fo = env.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", Defaults.DEFAULT_PATH + "namespaces.list", allElements.toArray(new Element[0]));
                    System.out.println("Write namespace list to " + fo.toUri() + ": " + all);
                    try (Writer w = fo.openWriter()) {
                        w.write("# ");
                        w.write(new Date().toString());
                        w.write("\n\n");
                        for (String namespace : all) {
                            Set<Element> elements = new HashSet<>();
                            if (elements != null && !elements.isEmpty()) {
                                w.write("# ");
                                w.write(setToString(elementsForNamespace.get(namespace)));
                                w.write('\n');
                            }
                            w.write(namespace);
                            w.write('\n');
                        }
                    }

                }
            } catch (IOException ex) {
                Logger.getLogger(NamespaceAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return false;
    }

    private List<String> toStrings(Set<Element> elements) {
        List<String> result = new ArrayList<String>();
        for (Element el : elements) {
            StringBuilder sb = new StringBuilder();
            do {
                sb.insert(0, el.getSimpleName());
                el = el.getEnclosingElement();
                if (el != null) {
                    sb.insert(0, '.');
                }
            } while (el != null);
            result.add(sb.toString());
        }
        return result;
    }

    private String setToString(Set<Element> elements) {
        List<String> l = toStrings(elements);
        Collections.sort(l);
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = l.iterator(); it.hasNext();) {
            String string = it.next();
            sb.append(string);
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private Set<String> readNamespaces() throws IOException {
        FileObject fo = env.getFiler().getResource(
                StandardLocation.CLASS_OUTPUT,
                "", Defaults.DEFAULT_PATH + "namespaces.list");
        Set<String> all = new HashSet<>();
        if (fo != null) {
            try (Reader rdr = fo.openReader(true)) {
                readNamepaces(rdr, all);
            } catch (FileNotFoundException fnfe) {
                System.err.println(fnfe.getMessage());
            }
        }
        return all;
    }

    public static void readNamepaces(Reader reader, Set<? super String> into) throws IOException {
        //XXX preserve comments
        String line = "";
        for (LineNumberReader r = new LineNumberReader(reader); line != null; line = r.readLine()) {
            line = line.trim();
            if (line.length() > 0 && line.charAt(0) != '#') {
                into.add(line);
            }
        }
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        this.env = processingEnv;
    }

    private final AnnotationMirror findMirror(Class<? extends Annotation> annotationType, Element e) {
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
            if (am.getAnnotationType().asElement().getSimpleName().contentEquals(annotationType.getSimpleName())) {
                //ignore the corner case of two annotations with the same name on the same class
                //there should be a way to easily get the qname from an AnnotationMirror - but there isn't :-(
                return am;
            }
        }
        return null;
    }

    private Character firstIllegalCharacter(String s, Element on) {
        char[] test = s.toCharArray();
        Arrays.sort(test);
        for (char c : illegalChars) {
            if (Arrays.binarySearch(test, c) >= 0) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Illegal character '" + c + "' in namespace definition",
                        on, findMirror(Namespace.class, on));
            }
        }
        return null;
    }

    private void testNamespace(Namespace ns, Element e) {
        if (ns.value() != null) {
            if (ns.value().length() == 0) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Empty string not allowed as namespace", e, findMirror(Namespace.class, e));
            }
            Character c = firstIllegalCharacter(ns.value(), e);
            if (c != null) {
                env.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Illegal character '" + c + "' in namespace definition",
                        e, findMirror(Namespace.class, e));
            }
        } else {
            //should not be possible, but could be with badly broken sources
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Null annotation value", e, findMirror(Namespace.class, e));
        }
    }
}
