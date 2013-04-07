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
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.settings.MutableSettings;
import com.mastfrog.settings.Settings;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractRunner extends ParentRunner<TestMethodRunner> implements RuleWrapperProvider {
    private final List<TestMethodRunner> runners;
    private final RunnerFactory runnerFactory;
    private boolean runnersInitialized;
    private List<RunWrapper> runWrappers = new ArrayList<RunWrapper>();

    protected AbstractRunner(Class<?> testClass, RunnerFactory... runnerFactories) throws InitializationError {
        super(testClass);
        if (runnerFactories == null || runnerFactories.length == 0) {
            throw new IllegalArgumentException("No runner factories");
        }
        this.runnerFactory = new MetaRunnerFactory(runnerFactories);
        runnerFactory.setRunner(this);
        runners = createChildren();
    }

    protected void onBeforeCreateDependencies(TestClass testClass, FrameworkMethod method, Settings settings, DependenciesBuilder builder) {
        //Subclasses can add additional Guice modules here
    }

    protected void onAfterCreateDependencies(TestClass testClass, FrameworkMethod method, Settings settings, Dependencies dependencies) {
        //Subclasses can create test fixtures or whatever they need to here
    }
    
    protected Settings onSettingsCreated(Settings settings) {
        return settings;
    }

    private static class MetaRunnerFactory implements RunnerFactory {

        private final RunnerFactory[] delegateTo;

        MetaRunnerFactory(RunnerFactory... delegateTo) {
            this.delegateTo = delegateTo;
        }

        @Override
        public void createChildren(TestClass testClass, List<? super TestMethodRunner> runners, List<Throwable> errors, RuleWrapperProvider p) throws InitializationError {
            for (RunnerFactory factory : delegateTo) {
                factory.createChildren(testClass, runners, errors, p);
            }
        }

        @Override
        public void setRunner(AbstractRunner runner) {
            for (RunnerFactory factory : delegateTo) {
                factory.setRunner(runner);
            }
        }
    }
    
    protected static TestMethodRunner createJUnitTestMethodRunner(TestClass clazz, FrameworkMethod method, RuleWrapperProvider p, AbstractRunner runner) throws InitializationError {
        return new StandardTestRunner(clazz, method, p, runner);
    }
    
    private static class StandardTestRunner extends TestMethodRunner {

        StandardTestRunner(TestClass testClass, FrameworkMethod method, RuleWrapperProvider provider, AbstractRunner runner) throws InitializationError {
            super(testClass, method, provider, runner);
        }

        @Override
        @SuppressWarnings("AvoidCatchingThrowable")
        protected void invokeTest(Statement base, Object target, Dependencies dependencies) throws Throwable {
            try {
                base.evaluate();
            } catch (Throwable t) {
                Test anno = method.getAnnotation(Test.class);
                if (anno.expected() != null && anno.expected().isInstance(t)) {
                    return;
                }
                throw t;
            }
        }
    }    

    public static Exception fakeException(String msg, Class<?> testClass, int positedLineNumber) {
        return new GuiceTestException(msg, testClass, positedLineNumber);
    }

    private final List<TestMethodRunner> createChildren() throws InitializationError {
        TestClass testClass = getTestClass();
        List<Throwable> errors = new LinkedList<Throwable>();
        List<TestMethodRunner> runners = new LinkedList<TestMethodRunner>();
        runnerFactory.createChildren(testClass, runners, errors, this);
        if (runners.isEmpty()) {
            errors.add(fakeException("No runnable methods", testClass.getJavaClass(), 0));
        }
        if (!errors.isEmpty()) {
            throw new InitializationError(errors);
        }
        if (!runners.isEmpty()) {
            runners.get(0).setFirst(true);
            runners.get(runners.size() - 1).setLast(true);
        }
        return runners;
    }

    @Override
    public Statement withAfterClasses(Statement statement) {
        return super.withAfterClasses(statement);
    }

    @Override
    public Statement withBeforeClasses(Statement statement) {
        return super.withBeforeClasses(statement);
    }

    @Override
    public Statement classBlock(RunNotifier notifier) {
        return super.classBlock(notifier);
    }
    
    @Override
    protected Description describeChild(TestMethodRunner child) {
        return child.getDescription();
    }

    @Override
    protected List<TestMethodRunner> getChildren() {
        if (!runnersInitialized) {
            runnersInitialized = true;
            if (!this.runWrappers.isEmpty()) {
                for (int i = 0; i < runners.size(); i++) {
                    TestMethodRunner r = runners.get(i);
                    for (RunWrapper rw : runWrappers) {
                        if (rw.match(r.method)) {
                            if (r.wrap != null) {
                                throw new AssertionError("Multiple wrapper runners not supported yet");
                            }
                            r.wrap = rw;
                        }
                    }
                    if (r != runners.get(i)) {
                        runners.set(i, r);
                    }
                }
            }
        }
        return runners;
    }

    protected List<RunWrapper> getWrappersFor(FrameworkMethod meth) {
        List<RunWrapper> result = new ArrayList<RunWrapper>();
        for (RunWrapper w : runWrappers) {
            if (w.match(meth)) {
                result.add(w);
            }
        }
        return result;
    }

    protected void registerRunWrapper(RunWrapper wrapper) {
        runWrappers.add(wrapper);
    }

    @Override
    protected void runChild(TestMethodRunner child, RunNotifier notifier) {
        child.run(notifier);
    }

}
