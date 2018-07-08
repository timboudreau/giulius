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
package com.mastfrog.graal.annotation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows additional information about reflectively accessed classes in foreign
 * JARs to be included. If the value of type() is unspecified, then it
 * references the type the annotation is declared on. If no fields or methods
 * are specified, all declared methods and fields should be available via
 * reflection.
 * <p>
 * <b>Note:</b> This annotation exists to publish information about foreign
 * classes - make sure fully qualified names are correct, or it will fail at
 * image-building time.
 * </p>
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.SOURCE)
@Target({METHOD, FIELD, TYPE, ANNOTATION_TYPE, CONSTRUCTOR})
public @interface Expose {

    /**
     * The fully qualified name of a Java class (use $ notation for nested
     * classes).
     *
     * @return A type
     */
    String type() default "";

    /**
     * The set of methods which should be accessible at runtime via reflection.
     *
     * @return A set of methods
     */
    MethodInfo[] methods() default {};

    /**
     * The set of field names which should be accessible at runtime via
     * reflection.  <p>If this returns <i>exactly one element</i> and that element
     * is the string "*", the annotation processor will <i>attempt</i> to look
     * up that class and include entries for all of its fields.  That means the
     * class in question <b>must</b> be on the compilation classpath or the JDK
     * boot classpath.</p>
     *
     * @return A set of field names.
     */
    String[] fields() default {};

    /**
     * Information about a method which should be made available via reflection
     * at runtime.
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({})
    @interface MethodInfo {

        /**
         * The method name. If unspecified, you are referencing a constructor.
         *
         * @return The name (will be <code>&lt;init&gt;</code> by default, for
         * constructors.
         */
        String name() default "<init>";

        /**
         * A list of fully qualified Java class names, in the order they occur
         * in this method's signature.
         *
         * @return A list of class names.
         */
        String[] parameterTypes() default {};

    }
}
