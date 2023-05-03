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

import com.mastfrog.annotation.AnnotationUtils;
import static com.mastfrog.giulius.annotation.processors.DefaultsAnnotationProcessor.NEW_DEFAULTS_ANNOTATION_TYPE;
import static com.mastfrog.giulius.annotation.processors.DefaultsAnnotationProcessor.OLD_DEFAULTS_ANNOTATION_TYPE;
import static com.mastfrog.giulius.annotation.processors.DefaultsAnnotationProcessor.REPLACEMENT_DEFAULTS_ANNOTATION_TYPE;
import com.mastfrog.giulius.annotation.processors.PropertiesIndexFactory.PropertiesIndexEntry;
import com.mastfrog.annotation.registries.AbstractRegistrationAnnotationProcessor;
import static com.mastfrog.giulius.annotation.processors.DefaultsAnnotationProcessor.SETTING_ANNOTATION_TYPE;
import com.mastfrog.util.service.ServiceProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.Diagnostic;

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
@SupportedAnnotationTypes({NEW_DEFAULTS_ANNOTATION_TYPE, OLD_DEFAULTS_ANNOTATION_TYPE,
    REPLACEMENT_DEFAULTS_ANNOTATION_TYPE, SETTING_ANNOTATION_TYPE})
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class DefaultsAnnotationProcessor extends AbstractRegistrationAnnotationProcessor<PropertiesIndexEntry> {

    /**
     * Path within JAR files to look for settings information
     */
    public static final String DEFAULT_PATH = "META-INF/settings/";
    /**
     * The default namespace, "defaults", which is used for settings which do
     * not explicitly have a namespace
     */
    public static final String DEFAULT_NAMESPACE = "defaults";
    /**
     * File extension for settings files <code>.properties</code>
     */
    public static final String DEFAULT_EXTENSION = ".properties";
    /**
     * Prefix used for settings files generated from annotations,
     * <code>generated-</code>
     */
    public static final String GENERATED_PREFIX = "generated-";

    public static final String DEFAULT_FILE = GENERATED_PREFIX + DEFAULT_NAMESPACE + DEFAULT_EXTENSION;
    public static final String DEFAULT_LOCATION = DEFAULT_PATH + DEFAULT_FILE;

    public static final String DESCRIPTIONS_PATH = DEFAULT_PATH + "_generated-descriptions.properties";
    public static final String ORIGINAL_DEFAULTS_PATH = DEFAULT_PATH + "_generated-original.properties";

    static final String OLD_DEFAULTS_ANNOTATION_TYPE = "com.mastfrog.guicy.annotations.Defaults";
    static final String NEW_DEFAULTS_ANNOTATION_TYPE = "com.mastfrog.giulius.annotations.Defaults";
    static final String REPLACEMENT_DEFAULTS_ANNOTATION_TYPE = "com.mastfrog.giulius.annotations.SettingsDefaults";
    static final String SETTING_ANNOTATION_TYPE = "com.mastfrog.giulius.annotations.Setting";

    public DefaultsAnnotationProcessor() {
        super(new PropertiesIndexFactory());
    }

    @SuppressWarnings("StringEquality")
    private boolean checkValidNamespace(String ns) {
        if (ns == DEFAULT_NAMESPACE) {
            return true;
        }
        if (ns != null) {
            for (char c : ns.toCharArray()) {
                if (Character.isWhitespace(c)) {
                    return false;
                }
            }
            if (ns.contains("..")) {
                return false;
            }
        }
        return true;
    }

    private AnnotationMirror findNamespaceAnnotationOn(Element el, AnnotationUtils utils) {
        AnnotationMirror result = utils.findMirror(el, NamespaceAnnotationProcessor.NEW_NAMESPACE_ANNOTATION_TYPE);
        if (result == null) {
            result = utils.findMirror(el, NamespaceAnnotationProcessor.OLD_NAMESPACE_ANNOTATION_TYPE);
        }
        return result;
    }

    private String findNamespaceOn(Element el, AnnotationUtils utils) {
        AnnotationMirror mirror = findNamespaceAnnotationOn(el, utils);
        if (mirror != null) {
            return utils.annotationValue(mirror, "value", String.class);
        }
        return null;
    }

    private String findNamespace(Element element, AnnotationUtils utils) {
        String result;
        //get the package before we change the value of element
        PackageElement pe = processingEnv.getElementUtils().getPackageOf(element);
        do {
            //search up enclosing class/method/whatever
            result = findNamespaceOn(element, utils);
            element = element.getEnclosingElement();
            if (result != null) {
                break;
            }
        } while (result == null && element != null);
        //element.getEnclosingElement() doens't actually make the leap from
        //class -> package, so now search the package name hierarchy
        //(javac doesn't see packages as having hierarchy - they might
        //be in different jars)
        while (pe != null && result == null && !pe.isUnnamed()) {
            result = findNamespaceOn(pe, utils);
            if (result == null) {
                CharSequence name = pe.getQualifiedName();
                int ix = name.toString().lastIndexOf('.');
                if (ix > 0) {
                    name = name.subSequence(0, ix - 1);
                    pe = processingEnv.getElementUtils().getPackageElement(name);
//                    result = pe == null ? null : findNamespaceOn(pe);
                } else {
                    break;
                }
            }
        }
        return result == null ? DEFAULT_NAMESPACE : result;
    }

    private String findNamespaceForDefaultsAnnotationMirror(Element on, AnnotationMirror defaults, AnnotationUtils utils) {
        String result;
        if (REPLACEMENT_DEFAULTS_ANNOTATION_TYPE.equals(defaults.getAnnotationType().toString())) {
            result = utils.annotationValue(defaults, "namespace", String.class);
            if (result == null || DEFAULT_NAMESPACE.equals(result)) {
                String pkgNamespace = findNamespace(on, utils);
                if (pkgNamespace != null && !DEFAULT_NAMESPACE.equals(pkgNamespace) && !pkgNamespace.equals(DEFAULT_NAMESPACE)) {
                    result = pkgNamespace;
                }
            }
        } else {
            AnnotationMirror ns = utils.annotationValue(defaults, "namespace", AnnotationMirror.class);
            if (ns == null) {
                String packageNamespace = findNamespace(on, utils);
                if (packageNamespace != null) {
                    return packageNamespace;
                }
            }
            if (ns == null) {
                return DEFAULT_NAMESPACE;
            }
            result = utils.annotationValue(ns, "value", String.class);
        }
        if (!checkValidNamespace(result)) {
            fail("Namespace may not be the empty string or contain whitespace or the string ..", on, defaults);
            return null;
        }
        return result == null || result.isEmpty() ? DEFAULT_NAMESPACE : result;
    }

    private String findPath(AnnotationMirror defaults, Element e, AnnotationUtils utils) {

        if (SETTING_ANNOTATION_TYPE.equals(defaults.getAnnotationType().toString())) {
            return ORIGINAL_DEFAULTS_PATH;
        }

        boolean specifiesNamespace;
        String namespace = findNamespaceForDefaultsAnnotationMirror(e, defaults, utils);

        specifiesNamespace = !DEFAULT_NAMESPACE.equals(namespace);

        String path = null;
        if (!REPLACEMENT_DEFAULTS_ANNOTATION_TYPE.equals(defaults.getAnnotationType().toString())) {
            path = utils.annotationValue(defaults, "location", String.class);
        }

        if ("null".equals(path) || path == null) {
            path = DEFAULT_LOCATION;
        }

        String generatedDefaults = GENERATED_PREFIX + DEFAULT_NAMESPACE;
        if (DEFAULT_LOCATION.equals(path) && specifiesNamespace) {
            path = namespace;
            if (!path.startsWith(GENERATED_PREFIX)) {
                path = GENERATED_PREFIX + path;
            }
            if (path.length() > 0 && path.charAt(0) != '/') {
                path = DEFAULT_PATH + path;
            }
            path += DEFAULT_EXTENSION;
        } else if (!generatedDefaults.equals(path) && specifiesNamespace) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, e.asType()
                    + " or its package specifies the namespace \""
                    + namespace + "\", but location=\"" + path + "\" "
                    + "overrides it.", e);
        }
        return path;
    }

    private final Map<String, Map<String, Element>> elementsForPropertyForPath = new HashMap<>();

    @Override
    protected void handleOne(Element e, AnnotationMirror anno, int order, AnnotationUtils utils) {
        String path = findPath(anno, e, utils);
        if (path == null) {
            // fail already called
            return;
        }
        Properties pp = new Properties();
        if (REPLACEMENT_DEFAULTS_ANNOTATION_TYPE.equals(anno.getAnnotationType().toString())) {
            List<AnnotationMirror> pairs = utils.annotationValues(anno, "value", AnnotationMirror.class);
            if (pairs.isEmpty()) {
                fail("@SettingsDefaults present, but no pairs specified", e, anno);
                return;
            }
            for (AnnotationMirror pair : pairs) {
                String key = utils.annotationValue(pair, "name", String.class);
                if (key == null) {
                    fail("Missing property name", e, pair);
                    continue;
                }
                String val = utils.annotationValue(pair, "value", String.class);
                if (val == null) {
                    fail("Missing property value", e, pair);
                    continue;
                }
                if (key.isEmpty() || key.trim().isEmpty()) {
                    fail("Empty or whitespace-only key not allowed", e, pair);
                    continue;
                }
                if (pp.contains(key)) {
                    fail("Key/value pair specified twice: " + key + "=" + val, e, pair);
                    continue;
                }
                pp.setProperty(key, val);
            }
        } else if (SETTING_ANNOTATION_TYPE.equals(anno.getAnnotationType().toString())) {
            SettingRecord.fromAnnotationMirror(utils, anno, e).ifPresent(rec -> {
                rec.defaultValue().ifPresent(def -> {
                    pp.put(rec.key, def);
                });
                Properties props = new Properties();
                props.setProperty(rec.key, rec.toString());
                indexer.add(DESCRIPTIONS_PATH, new PropertiesIndexEntry(props, e), processingEnv, e);
            });
        } else {
            List<String> pairs = utils.annotationValues(anno, "value", String.class);
            StringBuilder sb = new StringBuilder();
            for (String s : pairs) {
                sb.append(s).append('\r').append('\n');
            }
            try {
                pp.load(new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.ISO_8859_1)));
            } catch (IOException ex) {
                fail("Invalid properties syntax in '" + sb, e, anno);
                return;
            }
        }
        if (pp.isEmpty() && !SETTING_ANNOTATION_TYPE.equals(anno.getAnnotationType().toString())) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, e.asType()
                    + " annotated with @Defaults yet defines no properties.  Check syntax.", e, anno);
            return;
        }
        Map<String, Element> alreadySet = elementsForPropertyForPath.get(path);
        if (alreadySet == null) {
            alreadySet = new HashMap<>();
            elementsForPropertyForPath.put(path, alreadySet);
            for (String p : pp.stringPropertyNames()) {
                alreadySet.put(p, e);
            }
        } else {
            for (String p : pp.stringPropertyNames()) {
                if (alreadySet.containsKey(p)) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, e.asType() + " redefines value for " + p
                            + " previously defined on " + alreadySet.get(p).asType(), e, anno);
                }
            }
        }
        indexer.add(path, new PropertiesIndexEntry(pp, e), processingEnv, e);
    }
}
