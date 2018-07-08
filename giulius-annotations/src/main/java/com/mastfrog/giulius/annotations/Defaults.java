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
package com.mastfrog.giulius.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows default settings to be specified via an annotation. For example:
 * <pre>
 * &#063;Defaults({"foo=bar", "port=8080"})
 * </pre>
 * You can use the location() property to actually generate a properties file
 * at compile-time anywhere on the classpath.
 * <p/>
 * If two properties specify different values for the same setting, one will
 * be used, but it is unspecified which;  but a warning should be logged.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
public @interface Defaults {

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

    public static final String DEFAULT_FILE = GENERATED_PREFIX + Namespace.DEFAULT + DEFAULT_EXTENSION;
    public static final String DEFAULT_LOCATION = DEFAULT_PATH + DEFAULT_FILE;

    /**
     * Each string element should be parseable as a Properties file (or one line
     * of one). The same syntax as specified in java.util.Properties is used.
     * So, you can either specify multiple properties like this:
     * <pre>
     * &#064;Defaults ("a=b\nc=d")
     * </pre> or more readably as
     * <pre>
     * &#064;Defaults ({"a=b", "c=d"})
     * </pre>
     *
     * @return
     */
    String[] value();

    /**
     * Location where the properties file should be generated
     *
     * @return A qualified, /-delimited path on the classpath. The default is
     * <code>com/mastfrog/generated-defaults.properties</code>
     */
    String location() default DEFAULT_LOCATION;
    
    /**
     * The namespace, if any, for this element.  Setting the namespace changes
     * where settings are written to by the annotation processor.  Using the
     * <code>location</code> attribute overrides this value.
     * @return 
     */
    Namespace namespace() default @Namespace(Namespace.DEFAULT);
}
