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

import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.graal.injection.processor.GraalEntryIndexFactory.GraalEntry;
import static com.mastfrog.graal.injection.processor.GraalInjectionProcessor.EXPOSE_MANY_ANNOTATION;
import static com.mastfrog.graal.injection.processor.GraalInjectionProcessor.EXPOSE_TYPES_ANNOTATION;
import com.mastfrog.annotation.registries.AbstractRegistrationAnnotationProcessor;
import com.mastfrog.util.service.ServiceProvider;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import static com.mastfrog.graal.injection.processor.GraalInjectionProcessor.REFLECTION_INFO_ANNOTATION;
import static com.mastfrog.graal.injection.processor.GraalInjectionProcessor.JAVAX_INJECT_ANNOTATION;
import static com.mastfrog.graal.injection.processor.GraalInjectionProcessor.GUICE_INJECT_ANNOTATION;
import static com.mastfrog.graal.injection.processor.GraalInjectionProcessor.JSON_CREATOR_ANNOTATION;
import static com.mastfrog.graal.injection.processor.GraalInjectionProcessor.JSON_PROPERTY_ANNOTATION;
import java.util.Arrays;

/**
 *
 * @author Tim Boudreau
 */
@SupportedAnnotationTypes({GUICE_INJECT_ANNOTATION, JAVAX_INJECT_ANNOTATION, REFLECTION_INFO_ANNOTATION,
    JSON_CREATOR_ANNOTATION, JSON_PROPERTY_ANNOTATION, EXPOSE_TYPES_ANNOTATION, EXPOSE_MANY_ANNOTATION})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@ServiceProvider(Processor.class)
public final class GraalInjectionProcessor extends AbstractRegistrationAnnotationProcessor<GraalEntryIndexFactory.GraalEntry> {

    static final String GUICE_INJECT_ANNOTATION = "com.google.inject.Inject";
    static final String JAVAX_INJECT_ANNOTATION = "javax.inject.Inject";
    static final String REFLECTION_INFO_ANNOTATION = "com.mastfrog.graal.annotation.Expose";
    static final String EXPOSE_MANY_ANNOTATION = "com.mastfrog.graal.annotation.ExposeMany";
    static final String EXPOSE_TYPES_ANNOTATION = "com.mastfrog.graal.annotation.ExposeAllMethods";
    static final String JSON_CREATOR_ANNOTATION = "com.fasterxml.jackson.annotation.JsonCreator";
    static final String JSON_PROPERTY_ANNOTATION = "com.fasterxml.jackson.annotation.JsonProperty";
    static final String GUICE_MODULE_ANNOTATION = "com.mastfrog.acteur.annotations.GuiceModule";

    public static final String JAR_PATH_JSON_FILE = "META-INF/injection/reflective.json";

    public GraalInjectionProcessor() {
        super(new GraalEntryIndexFactory());
    }

    private Boolean verbose;

    boolean verbose() {
        if (verbose == null) {
            String val = processingEnv.getOptions().get("verbose");
            if (val == null) {
                val = System.getenv("GRAAL_PROCESSOR_VERBOSE");
            }
            if (val == null) {
                val = System.getProperty("graal.processor.verbose");
            }
            verbose = "1".equals(val) || "true".equals(val);
        }
        return verbose;
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

    private void note(String msg, Element e, AnnotationMirror me) {
        if (!verbose()) {
            return;
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.OTHER, msg, e, me);
    }

    private void handleReflectionInfo(Element e, String typeName, AnnotationMirror anno, AnnotationUtils utils) {
        String name = utils.annotationValue(anno, "type", String.class);
        name = name == null || name.isEmpty() ? typeName : name;
        int memberCount = 0;
        for (AnnotationMirror me : utils.annotationValues(anno, "methods", AnnotationMirror.class)) {
            List<String> params = utils.annotationValues(me, "parameterTypes", String.class);
            String methodName = utils.annotationValue(me, "name", String.class);
            GraalEntry infoEntry = new GraalEntry(name, methodName, params, e);

            if (indexer.add(JAR_PATH_JSON_FILE, infoEntry, processingEnv, e)) {
                note("[reflective-method-access] " + infoEntry, e, me);
                memberCount++;
            }
        }

        List<String> fieldDefs = utils.annotationValues(anno, "fields", String.class);
        if (fieldDefs.size() == 1 && Arrays.asList("*").equals(fieldDefs)) {
            TypeElement el = processingEnv.getElementUtils().getTypeElement(name);
            if (el == null) {
                fail("Cannot resolve " + name + " on compiler classpath to fill in all fields to expose '*'", e, anno);
            } else {
                for (Element member : el.getEnclosedElements()) {
                    switch (member.getKind()) {
                        case FIELD:
                            String nm = member.getSimpleName().toString();
                            GraalEntry fieldEntry = new GraalEntry(name, nm, e);
                            if (indexer.add(JAR_PATH_JSON_FILE, fieldEntry, processingEnv, e)) {
                                memberCount++;
                                note("[reflective-field-access] " + fieldEntry, e, anno);
                            }
                    }
                }
            }
        } else {
            for (String fieldName : fieldDefs) {
                GraalEntry infoEntry = new GraalEntry(name, fieldName, e);

                if (indexer.add(JAR_PATH_JSON_FILE, infoEntry, processingEnv, e)) {
                    note("[reflective-field-access] " + infoEntry, e, anno);
                    memberCount++;
                }
            }
        }

        if (memberCount == 0) {
            GraalEntry allEntry = new GraalEntry(name, e);
            if (indexer.add(JAR_PATH_JSON_FILE, allEntry, processingEnv, e)) {
                note("[reflective-all-access] " + allEntry, e, anno);
                memberCount++;
            }
        }
    }

    private void handleExposeTypesAnnotation(Element e, AnnotationMirror anno, AnnotationUtils utils) {
        List<String> typesToExpose = utils.typeList(anno, "value");
        utils.warn("Handle expose types: " + anno);
        if (typesToExpose != null) {
            for (String type : typesToExpose) {
                utils.warn("Expose type " + type, e, anno);
                GraalEntry allEntry = new GraalEntry(type, e);
                indexer.add(JAR_PATH_JSON_FILE, allEntry, processingEnv, e);
            }
        }
    }

    @Override
    protected void handleOne(Element e, AnnotationMirror anno, int order, AnnotationUtils utils) {
        try {
            if (EXPOSE_TYPES_ANNOTATION.equals(anno.getAnnotationType().toString())) {
                handleExposeTypesAnnotation(e, anno, utils);
                return;
            }

            if (EXPOSE_MANY_ANNOTATION.equals(anno.getAnnotationType().toString())) {
                List<AnnotationMirror> exposeAnnotations = utils.annotationValues(anno, "value", AnnotationMirror.class);
                for (AnnotationMirror expose : exposeAnnotations) {
                    handleOne(e, expose, order, utils);
                }
                return;
            }

            if (e.getKind() == ElementKind.PARAMETER) {
                Element orig = e;
                e = enclosingMethod(e);
                if (e == null) {
                    super.fail("Could not find an enclosing method for " + orig, e, anno);
                    return;
                }
            }
            TypeMirror type = enclosingType(e);
            if (type == null) {
                super.fail("Could not find enclosing type for " + e, e, anno);
                return;
            }
            String typeName = utils.canonicalize(type);

            if (anno.getAnnotationType().toString().equals(REFLECTION_INFO_ANNOTATION)) {
                handleReflectionInfo(e, typeName, anno, utils);
                return;
            }
            if (GUICE_MODULE_ANNOTATION.equals(anno.getAnnotationType().toString())) {
                GraalEntry allEntry = new GraalEntry(utils.canonicalize(e.asType()), e);
                indexer.add(JAR_PATH_JSON_FILE, allEntry, processingEnv, e);
                return;
            }
            if (e.getKind() != ElementKind.FIELD && !(e instanceof ExecutableElement)) {
                utils.fail("Not a field or ExecutableElement (method, constructor): " + e, e, anno);
                return;
            }
            switch (e.getKind()) {
                case CONSTRUCTOR:
                case METHOD:
                    if (!(e instanceof ExecutableElement)) { // broken sources can do strange things
                        fail("Not an executable element (method, constructor): " + e, e, anno);
                        return;
                    }
                    ExecutableElement ex = (ExecutableElement) e;
                    List<String> params = new ArrayList<>();
                    for (VariableElement v : ex.getParameters()) {
                        TypeMirror ptype = v.asType();
                        String paramTypeName = utils.canonicalize(ptype);
                        if (paramTypeName == null) {
                            fail("Could not canonicalize " + v, v);
                            break;
                        }
                        params.add(paramTypeName);
                    }
                    GraalEntry methodOrConstructorEntry = new GraalEntry(typeName, ex.getSimpleName().toString(), params, e);
                    indexer.add(JAR_PATH_JSON_FILE, methodOrConstructorEntry, processingEnv, e);
                    String noteKey = e.getKind() == ElementKind.CONSTRUCTOR
                            ? "[reflective-constructor-access] " : "[reflective-method-access] ";
                    note(noteKey + methodOrConstructorEntry, e, anno);
                    break;
                case FIELD:
                    if (!(e instanceof VariableElement)) {
                        fail("Not a variable element (field): " + e, e, anno);
                        return;
                    }
//                    if (e.getModifiers().contains(Modifier.PUBLIC)) {
//                        // No need to record reflection access for a field which is already
//                        // public
//                        return;
//                    }
                    VariableElement ve = (VariableElement) e;
                    GraalEntry fieldEntry = new GraalEntry(typeName, ve.getSimpleName().toString(), e);
                    note("[reflective-field-access] " + fieldEntry, e, anno);
                    indexer.add(JAR_PATH_JSON_FILE, fieldEntry, processingEnv, ve);
                    break;
                default:
                    throw new IllegalArgumentException("Unprocessable element kind " + e.getKind() + " on " + type);
            }
        } catch (java.lang.annotation.IncompleteAnnotationException inc) {
            fail(inc.getMessage(), e, anno);
        }
    }
}
