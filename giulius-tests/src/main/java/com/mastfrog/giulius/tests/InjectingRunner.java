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
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
/**
 * Subclass of BlockJUnit4ClassRunner which leverages some of its internal
 * functionality, but delegates to a TestMethodRunner to actually run the
 * test, passing in the Statement that would run the test in the default
 * JUnit way.
 *
 * @author Tim Boudreau
 */
final class InjectingRunner extends BlockJUnit4ClassRunner {

    private final TestMethodRunner injectionRunner;

    InjectingRunner(Class<?> testClass, TestMethodRunner injectionRunner) throws InitializationError {
        super(testClass);
        this.injectionRunner = injectionRunner;
    }

    @Override
    protected List<MethodRule> rules(Object test) {
        List<MethodRule> result = super.rules(test);
        result.add(injectionRunner);
        return result;
    }

    @Override
    public void runChild(FrameworkMethod method, RunNotifier notifier) {
        super.runChild(method, notifier);
    }

    @Override
    protected void validateTestMethods(List<Throwable> errors) {
        validatePublicVoidNoArgMethods(Test.class, false, errors);
    }

    @Override
    protected Description describeChild(FrameworkMethod method) {
        return injectionRunner.describeChild(super.describeChild(method));
    }

    @Override
    protected void validateInstanceMethods(List<Throwable> errors) {
        //this is already done by the GuiceRunner
    }
}
