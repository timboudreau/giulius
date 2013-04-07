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

import java.util.List;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

/**
 * Factory which GuiceRunner subclasses can use to create additional runners
 * which invoke test methods in other ways.
 * <p/>
 * Used when subclassing GuiceRunner to create a runner which can run both
 * Guice tests and standard tests, and also tests with some other annotation.
 *
 * @author Tim Boudreau
 */
public interface RunnerFactory {
    /**
     * Create runners for specific test methods.  The typical pattern is to
     * look up all methods on the passed <code>{@link org.junit.runners.model.TestClass}</code>
     * which have a particular annotation, and create custom subclasses of
     * <code>{@link TestMethodRunner}</code> which do whatever is needed.
     *
     * @param testClass A JUnit test class
     * @param runners A list of runners this method can add runners to.
     * @param errors A list of errors this method can add errors to
     *
     * @throws InitializationError Generally should not be thrown, but is
     * provided as a convenience since <code>{@link TestMethodRunner}</code> throws
     * it because it delegates to a subclass of
     * <code>{@link org.junit.runners.BlockJUnit4ClassRunner}</code> and runs
     * some of that class's test validation.
     */
    void createChildren(TestClass testClass, List<? super TestMethodRunner> runners, List<Throwable> errors, RuleWrapperProvider wrapperProvider) throws InitializationError;
    void setRunner(AbstractRunner runner);
}
