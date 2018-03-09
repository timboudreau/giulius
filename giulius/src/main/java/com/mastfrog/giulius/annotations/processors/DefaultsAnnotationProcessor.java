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

import com.mastfrog.util.service.IndexGeneratingProcessor;
import com.mastfrog.giulius.annotations.Defaults;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.service.ServiceProvider;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.processing.Completion;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Processes the &#064;Defaults annotation, generating properties files in the
 * location specified by the annotation (the default is
 * com/mastfrog/defaults.properties).
 * <p/>
 * Keep this in a separate package so it can be detached from this JAR
 *
 * @author Tim Boudreau
 */
@ServiceProvider(Processor.class)
@SupportedAnnotationTypes({"com.mastfrog.giulius.annotations.Defaults", "com.mastfrog.guicy.annotations.Defaults"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DefaultsAnnotationProcessor extends IndexGeneratingProcessor {

    private String findPath(Defaults anno, Element e) {
        boolean specifiesNamespace;
        Namespace namespaceAnno = anno.namespace();
        if (Namespace.DEFAULT.equals(namespaceAnno.value())) {
            namespaceAnno = findNamespace(e);
        }
        String namespace = namespaceAnno.value();
        specifiesNamespace = !Namespace.DEFAULT.equals(namespace);
        String path = anno.location();
        String generatedDefaults = SettingsBuilder.GENERATED_PREFIX + SettingsBuilder.DEFAULT_NAMESPACE;
        if (Defaults.DEFAULT_LOCATION.equals(path) && specifiesNamespace) {
            path = namespace;
            if (!path.startsWith(SettingsBuilder.GENERATED_PREFIX)) {
                path = SettingsBuilder.GENERATED_PREFIX + path;
            }
            if (path.length() > 0 && path.charAt(0) != '/') {
                path = Defaults.DEFAULT_PATH + path;
            }
            path += SettingsBuilder.DEFAULT_EXTENSION;
        } else if (!generatedDefaults.equals(path) && specifiesNamespace) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, e.asType()
                    + " or its package specifies the namespace \""
                    + namespace + "\", but location=\"" + path + "\" "
                    + "overrides it.", e);
        }
        return path;
    }

    private Properties findProperties(Map<String, Properties> propertiesForPath, String path) throws IOException {
        Properties props = propertiesForPath.get(path);
        if (props == null) {
            props = new Properties();
            try {
                FileObject fo = processingEnv.getFiler().getResource(
                        StandardLocation.CLASS_OUTPUT,
                        "", path);
                if (fo != null) {
                    try (InputStream in = fo.openInputStream()) {
                        props.load(in);
                    }
                }
            } catch (FileNotFoundException ex) {
                //OK
            }
            propertiesForPath.put(path, props);
        }
        return props;
    }

    @SuppressWarnings("AnnotationAsSuperInterface")
    private static final class DefaultNamespace implements Namespace {

        @Override
        public String value() {
            return SettingsBuilder.GENERATED_PREFIX + Namespace.DEFAULT;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Namespace.class;
        }
    }

    private Namespace findNamespace(Element element) {
        Namespace result;
        //get the package before we change the value of element
        PackageElement pe = processingEnv.getElementUtils().getPackageOf(element);
        do {
            //search up enclosing class/method/whatever
            result = element.getAnnotation(Namespace.class);
            element = element.getEnclosingElement();
        } while (result == null && element != null);
        //element.getEnclosingElement() doens't actually make the leap from
        //class -> package, so now search the package name hierarchy
        //(javac doesn't see packages as having hierarchy - they might
        //be in different jars)
        while (pe != null && result == null && !pe.isUnnamed()) {
            result = pe.getAnnotation(Namespace.class);
            if (result == null) {
                CharSequence name = pe.getQualifiedName();
                int ix = name.toString().lastIndexOf('.');
                if (ix > 0) {
                    name = name.subSequence(0, ix - 1);
                    pe = processingEnv.getElementUtils().getPackageElement(name);
                    result = pe == null ? null : pe.getAnnotation(Namespace.class);
                } else {
                    break;
                }
            }
        }
        return result == null ? new DefaultNamespace() : result;
    }
    Set<Element> elements = new HashSet<>();
    Map<String, Properties> propertiesForPath = new HashMap<>();
    Map<String, List<Element>> elementForPath = new HashMap<>();

    @Override
    public boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        boolean result = false;
        try {
            Set<? extends Element> all = roundEnv.getElementsAnnotatedWith(Defaults.class);
            for (Element e : all) {
                Defaults anno = e.getAnnotation(Defaults.class);
                if (anno == null) {
                    continue;
                }

                String path = findPath(anno, e);
                Properties props = findProperties(propertiesForPath, path);
                List<Element> els = elementForPath.get(path);
                if (els == null) {
                    els = new ArrayList<>();
                    elementForPath.put(path, els);
                }
                els.add(e);

                for (String pair : anno.value()) {
                    Properties pp = new Properties();
                    try (ByteArrayInputStream in = new ByteArrayInputStream(pair.getBytes("UTF-8"))) {
                        pp.load(in);
                    }
                    if (!pp.isEmpty()) {
                        elements.add(e);
                    }
                    for (String s : pp.stringPropertyNames()) {
                        if (props.containsKey(s)) {
                            String old = props.getProperty(s);
                            String nue = pp.getProperty(s);
                            if (!Objects.equals(old, nue)) {
                                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, e.asType() + " redefines " + s + " from " + old + " to " + nue, e);
                            }
                        }
                    }
                    props.putAll(pp);
                    addLine(path, pair, e);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(DefaultsAnnotationProcessor.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return result;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return Collections.<Completion>emptySet();
    }
}
