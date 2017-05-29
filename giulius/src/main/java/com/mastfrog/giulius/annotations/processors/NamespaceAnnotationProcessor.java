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

import com.mastfrog.giulius.annotations.Defaults;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.giulius.annotations.Value;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(service = Processor.class)
@SupportedAnnotationTypes({"com.mastfrog.guicy.annotations.Namespace", "com.mastfrog.guicy.annotations.Value",
"com.mastfrog.giulius.annotations.Namespace", "com.mastfrog.giulius.annotations.Value"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class NamespaceAnnotationProcessor extends IndexGeneratingProcessor {

    private static final char[] illegalChars = "/\\/:[],'\"<>?%+".toCharArray();

    private void addNamespaceForElement(String ns, Element e) {
        if (Namespace.DEFAULT.equals(ns)) {
            return;
        }
        addLine(Defaults.DEFAULT_PATH + "namespaces.list", ns, e);
    }

    @Override
    public boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element e : roundEnv.getElementsAnnotatedWith(Namespace.class)) {
            Namespace ns = e.getAnnotation(Namespace.class);
            testNamespace(ns, e);
            addNamespaceForElement(ns.value(), e);
        }

        for (Element e : roundEnv.getElementsAnnotatedWith(Value.class)) {
            Value v = e.getAnnotation(Value.class);
            if (!Namespace.DEFAULT.equals(v.namespace().value())) {
                testNamespace(v.namespace(), e);
            }
            if ("".equals(v.value().trim())) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Empty name",
                        e, findMirror(Value.class, e));
            }
            addNamespaceForElement(v.namespace().value(), e);
        }
        return false;
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
        super.init(processingEnv);
        this.processingEnv = processingEnv;
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
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Illegal character '" + c + "' in namespace definition",
                        on, findMirror(Namespace.class, on));
            }
        }
        return null;
    }

    private void testNamespace(Namespace ns, Element e) {
        if (ns.value() != null) {
            if (ns.value().length() == 0) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Empty string not allowed as namespace", e, findMirror(Namespace.class, e));
            }
            Character c = firstIllegalCharacter(ns.value(), e);
            if (c != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Illegal character '" + c + "' in namespace definition",
                        e, findMirror(Namespace.class, e));
            }
        } else {
            //should not be possible, but could be with badly broken sources
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Null annotation value", e, findMirror(Namespace.class, e));
        }
    }
}
