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
package com.mastfrog.giulius.annotation.processors;

import static com.mastfrog.giulius.annotation.processors.NamespaceAnnotationProcessor.NEW_NAMESPACE_ANNOTATION_TYPE;
import static com.mastfrog.giulius.annotation.processors.NamespaceAnnotationProcessor.OLD_NAMESPACE_ANNOTATION_TYPE;
import com.mastfrog.util.service.AbstractLineOrientedRegistrationAnnotationProcessor;
import com.mastfrog.util.service.ServiceProvider;
import java.util.Arrays;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(Processor.class)
@SupportedAnnotationTypes({OLD_NAMESPACE_ANNOTATION_TYPE, "com.mastfrog.guicy.annotations.Value",
    NEW_NAMESPACE_ANNOTATION_TYPE, "com.mastfrog.giulius.annotations.Value"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class NamespaceAnnotationProcessor extends AbstractLineOrientedRegistrationAnnotationProcessor {

    private static final char[] illegalChars = "/\\/:[],'\"<>?%+".toCharArray();
    public static final String DEFAULT_PATH = "META-INF/settings/";
    /**
     * The default namespace, "defaults", which is used for settings which do
     * not explicitly have a namespace
     */
    public static final String DEFAULT_NAMESPACE = "defaults";

    static final String OLD_NAMESPACE_ANNOTATION_TYPE = "com.mastfrog.guicy.annotations.Namespace";
    static final String NEW_NAMESPACE_ANNOTATION_TYPE = "com.mastfrog.giulius.annotations.Namespace";

    public NamespaceAnnotationProcessor() {
        super(true);
    }

    private void addNamespaceForElement(String ns, Element e) {
        if (DEFAULT_NAMESPACE.equals(ns)) {
            return;
        }
        addLine(DEFAULT_PATH + "namespaces.list", ns, e);
    }

    int lineCount = 0;

    boolean isNamespace(AnnotationMirror mir) {
        return mir.getAnnotationType().toString().endsWith(".Namespace");
    }

    private String namespaceFor(AnnotationMirror anno) {
        String result = DEFAULT_NAMESPACE;
        if (isNamespace(anno)) {
            result = utils.annotationValue(anno, "value", String.class);
        } else {
            AnnotationMirror nsAnno = utils.annotationValue(anno, "namespace", AnnotationMirror.class);
            if (nsAnno != null) {
                result = utils.annotationValue(nsAnno, "value", String.class);
            }
        }
        return result;
    }

    @Override
    protected void handleOne(Element e, AnnotationMirror anno, int order) {
        String ns = namespaceFor(anno);
        if (!DEFAULT_NAMESPACE.equals(ns)) {
            if (testNamespace(ns, e, anno)) {
                addNamespaceForElement(ns, e);
            }
        }
    }

    private Character firstIllegalCharacter(String s, Element on, AnnotationMirror anno) {
        char[] test = s.toCharArray();
        Arrays.sort(test);
        for (char c : illegalChars) {
            if (Arrays.binarySearch(test, c) >= 0) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Illegal character '" + c + "' in namespace definition",
                        on, anno);
            }
        }
        return null;
    }

    private boolean testNamespace(String ns, Element e, AnnotationMirror anno) {
        if (ns != null) {
            if (ns.isEmpty()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Empty string not allowed as namespace", e, anno);
                return false;
            }
            Character c = firstIllegalCharacter(ns, e, anno);
            if (c != null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Illegal character '" + c + "' in namespace definition",
                        e, anno);
                return false;
            }
        } else {
            //should not be possible, but could be with badly broken sources
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Null value for namespace", e, anno);
        }
        return true;
    }
}
