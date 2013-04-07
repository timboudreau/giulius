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
package com.mastfrog.giulius.tests;

import com.google.inject.Module;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies a list of Guice modules which a test class or method needs.
 * When used on methods, can be used in place of the standard JUnit
 * <code>{@literal @}{@link org.junit.Test}</code> annotation.  
 * <p/>
 * This annotation can
 * be used on test classes, together with the standard JUnit <code>{@literal @}{@link org.junit.Test}</code>
 * on test methods;  or it can be used directly on test methods.  To specify
 * modules both in the test class and test method, annotate both (with different modules).
 * <p/>
 * See <code>{@link GuiceRunner}</code> for usage details.
 *
 * @author Tim Boudreau
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestWith {

    /**
     * A list of 0 or more Guice module classes which should be instantiated
     * and used to construct object for this test.
     * @return An array of Guice
     * <code>{@link com.google.inject.Module}</code> classes
     */
    Class<? extends Module>[] value() default {};

    /**
     * A list of 0 or more Guice
     * <code>{@link com.google.inject.Module}</code> classes which provide
     * the same objects.  A test or test class which has a &gt; 1 number of
     * <code>iterate</code> modules will be run once with each module specified.
     * <p/>
     * This is used to have one test class or method test multiple implementations
     * of objects provided by different modules.
     * @return An array of 0 or more modules to iterate.
     */
    Class<? extends Module>[] iterate() default {};
    String[] iterateSettings() default {};

    @SuppressWarnings("MissingStaticMethodInNonInstantiatableClass")
    static final class None extends Throwable {
        private static final long serialVersionUID = 1L;
        private None() {}
    }
    Class<? extends Throwable> expected() default None.class;
}
