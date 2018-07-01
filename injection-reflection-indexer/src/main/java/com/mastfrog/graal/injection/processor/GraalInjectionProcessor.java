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
import com.mastfrog.graal.injection.processor.ReflectionInfo.MemberInfo;
import com.mastfrog.util.service.AbstractRegistrationAnnotationProcessor;
import com.mastfrog.util.service.ServiceProvider;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

/**
 *
 * @author Tim Boudreau
 */
@SupportedAnnotationTypes({"com.google.inject.Inject", "javax.inject.Inject", "com.mastfrog.graal.injection.processor.ReflectionInfo",
    "com.fasterxml.jackson.annotation.JsonCreator", "com.fasterxml.jackson.annotation.JsonProperty"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@ServiceProvider(Processor.class)
public final class GraalInjectionProcessor extends AbstractRegistrationAnnotationProcessor<GraalEntryIndexFactory.GraalEntry> {

    public static final String JAR_PATH_JSON_FILE = "META-INF/injection/reflective.json";

    public GraalInjectionProcessor() {
        super(new GraalEntryIndexFactory());
        System.out.println("Created a " + GraalInjectionProcessor.class.getSimpleName());
    }

    protected Set<Element> findAnnotatedElements(RoundEnvironment roundEnv) {
        Set<Element> result = new HashSet<>();

        result.addAll(roundEnv.getElementsAnnotatedWith(com.google.inject.Inject.class));
        result.addAll(roundEnv.getElementsAnnotatedWith(javax.inject.Inject.class));
        result.addAll(roundEnv.getElementsAnnotatedWith(ReflectionInfo.class));
        result.addAll(roundEnv.getElementsAnnotatedWith(com.fasterxml.jackson.annotation.JsonCreator.class));
        result.addAll(roundEnv.getElementsAnnotatedWith(com.fasterxml.jackson.annotation.JsonProperty.class));

//        TypeElement te = processingEnv.getElementUtils().getTypeElement("com.google.inject.Inject");
//        if (te != null) {
//            result.add(te);
//        }
//        te = processingEnv.getElementUtils().getTypeElement("javax.inject.Inject");
//        if (te != null) {
//            result.add(te);
//        }
//        te = processingEnv.getElementUtils().getTypeElement(ReflectionInfo.class.getName());
//        if (te != null) {
//            result.add(te);
//        }
//
        System.out.println("Have elements: " + result);

        // JDK 9

//        for (TypeElement type : processingEnv.getElementUtils().getAllTypeElements("com.google.inject.Inject")) {
//            result.addAll(roundEnv.getElementsAnnotatedWith(type));
//        }
//
//        for (TypeElement type : processingEnv.getElementUtils().getAllTypeElements("javax.inject.Inject")) {
//            result.addAll(roundEnv.getElementsAnnotatedWith(type));
//        }
//
//        for (TypeElement type : processingEnv.getElementUtils().getAllTypeElements(ReflectionInfo.class.getName())) {
//            result.addAll(roundEnv.getElementsAnnotatedWith(type));
//        }
        return result;
    }

    @Override
    protected int getOrder(Annotation anno) {
        return 0;
    }

    private TypeMirror enclosingType(Element el) {
        while (!(el instanceof TypeElement) && el != null) {
            el = el.getEnclosingElement();
        }
        return el == null ? null : el.asType();
    }

    private ExecutableElement enclosingMethod(Element el) {
        while (!(el instanceof ExecutableElement) && el != null) {
            el = el.getEnclosingElement();
        }
        return el instanceof ExecutableElement ? ((ExecutableElement) el) : null;
    }


    private void note(String msg, Element e) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg, e);
    }

    @Override
    protected void handleOne(Element e, Annotation anno, int order) {
        try {
            if (e.getKind() == ElementKind.PARAMETER) {
                Element orig = e;
                e = enclosingMethod(e);
                if (e == null) {
                    super.fail("Could not find an enclosing method for " + orig);
                    return;
                }
            }
            TypeMirror type = enclosingType(e);
            if (type == null) {
                super.fail("Could not find enclosing type for " + e);
                return;
            }
            String typeName = canonicalize(type, processingEnv.getTypeUtils());
            if (anno instanceof ReflectionInfo) {
                ReflectionInfo refInfo = (ReflectionInfo) anno;
                String name = refInfo.type().isEmpty() ? typeName : refInfo.type();
                for (MemberInfo me : refInfo.members()) {
                    boolean isField = me.parameters().length == 1 && ".+.".equals(me.parameters()[0]);
                    if (isField && "<init>".equals(me.name())) {
                        fail("Specify the field name, or specify just the parameters if you are referencing a constructor.");
                        return;
                    }
                    GraalEntry infoEntry;
                    if (isField) {
                        infoEntry = new GraalEntry(name, me.name(), e);
                    } else {
                        infoEntry = new GraalEntry(name, me.name(), Arrays.asList(me.parameters()), e);
                    }
                    note("[reflective-access] " + infoEntry, e);
                    indexer.add(JAR_PATH_JSON_FILE, infoEntry, processingEnv, e);
                }
                return;
            }
            if (e.getKind() != ElementKind.FIELD && !(e instanceof ExecutableElement)) {
                super.fail("Not a field or ExecutableElement (method, constructor): " + e, e);
                return;
            }
            switch (e.getKind()) {
                case CONSTRUCTOR:
                case METHOD:
                    if (!(e instanceof ExecutableElement)) { // broken sources can do strange things
                        fail("Not an executable element (method, constructor): " + e, e);
                        return;
                    }
                    ExecutableElement ex = (ExecutableElement) e;
                    List<String> params = new ArrayList<>();
                    for (VariableElement v : ex.getParameters()) {
                        TypeMirror ptype = v.asType();
                        String paramTypeName = canonicalize(ptype, processingEnv.getTypeUtils());
                        params.add(paramTypeName);
                    }
                    GraalEntry methodOrConstructorEntry = new GraalEntry(typeName, ex.getSimpleName().toString(), params, e);
                    indexer.add(JAR_PATH_JSON_FILE, methodOrConstructorEntry, processingEnv, e);
                    note("[reflective-access] " + methodOrConstructorEntry, e);
                    System.out.println("GRAAL-ENTRY: " + methodOrConstructorEntry);
                    break;
                case FIELD:
                    if (!(e instanceof VariableElement)) {
                        fail("Not a variable element (field): " + e, e);
                        return;
                    }
                    VariableElement ve = (VariableElement) e;
                    GraalEntry fieldEntry = new GraalEntry(typeName, ve.getSimpleName().toString(), e);
                    note("[reflective-access] " + fieldEntry, e);
                    System.out.println("FIELD ENTRY: " + fieldEntry);
                    indexer.add(JAR_PATH_JSON_FILE, fieldEntry, processingEnv, ve);
                    break;
                default:
                    throw new IllegalArgumentException("Unprocessable element kind " + e.getKind() + " on " + type);
            }
        } catch (java.lang.annotation.IncompleteAnnotationException inc) {
            fail(inc.getMessage(), e);
        }
    }

    @Override
    protected Annotation findAnnotation(Element e) {
        Annotation result = e.getAnnotation(com.google.inject.Inject.class);
        if (result == null) {
            result = e.getAnnotation(javax.inject.Inject.class);
        }
        if (result == null) {
            result = e.getAnnotation(ReflectionInfo.class);
        }
        return result;
    }
}
