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

import com.mastfrog.giulius.Dependencies;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

/**
 * Class which allows GuiceRunner subclasses to execute some code (such as
 * entering write access on a database) before running the original
 * test code.
 * @see GuiceRunner.registerRunWrapper()
 *
 * @author Tim Boudreau
 */
public abstract class RunWrapper {
    private final Class<? extends Annotation> annotationClass;
    protected RunWrapper (Class<? extends Annotation> annotationClass) {
        this.annotationClass = annotationClass;
        Retention r = annotationClass.getAnnotation(Retention.class);
        if (r == null) {
            throw new AssertionError (annotationClass + " has no retention and will not be found at runtime");
        } else {
            RetentionPolicy p = r.value();
            if (p != RetentionPolicy.RUNTIME) {
                throw new AssertionError (annotationClass + " does not have RetentionPolicy=RUNTIME, so "
                        + "the test harness will never see it");
            }
            Target tgt = annotationClass.getAnnotation(Target.class);
            if (tgt != null) {
                if (!Arrays.asList(tgt.value()).contains(ElementType.METHOD)) {
                    throw new AssertionError (annotationClass + " does not have a target of METHOD");
                }
            }
        }
    }

    boolean match (FrameworkMethod method) {
        boolean result = method.getAnnotation(annotationClass) != null;
        return result;
    }

    protected abstract void invokeTest(Statement base, Object target, FrameworkMethod method, Dependencies dependencies) throws Throwable;
}
