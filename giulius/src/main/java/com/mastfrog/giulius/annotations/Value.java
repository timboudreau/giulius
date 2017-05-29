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

import com.google.inject.BindingAnnotation;
import static java.lang.annotation.ElementType.*;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.*;
import java.lang.annotation.Target;

/**
 * Annotation similar to Guice's &#064;Named annotation, to indicate
 * the name of what to inject, but allows a namespace to be specified on
 * a per-property basis.
 * <p/>
 * Note that this annotation does nothing in particular unless you are using
 * a framework such as Guicy which feeds those values to Guice.  It is 
 * defined here so that code can declare these annotations without dragging
 * in a dependency on Guice and all its libraries.
 *
 * @author Tim Boudreau
 */
@Retention(RUNTIME)
@Target({FIELD, PARAMETER})
@BindingAnnotation
public @interface Value {
    /**
     * The name of the property to inject
     * 
     * @return 
     */
    String value();
    /**
     * The namespace can be used by frameworks to decide where Guice should
     * get the values.
     */
    Namespace namespace() default @Namespace(Namespace.DEFAULT);
}
