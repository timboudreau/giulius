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

import com.mastfrog.settings.Settings;
import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DependenciesBuilder;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * JUnit test runner which embeds Guice/Settings/Dependencies, so that the
 * test class takes care of instantiating Guice modules.  The purpose is
 * to eliminate repetitive Guice configuration in
 * <code>setUp()</code> / <code>{@literal @}{@link org.junit.Before}</code>
 * methods.  Instead, you simply annotate test classes or methods with what
 * modules should be used, and either have method parameters on test methods
 * or injected test class members.
 * <p/>
 * The test runner takes care of initializing
 * Guice with the right modules and using Guice to create the objects the test
 * needs, so tests can concentrate on being tests.
 * <p/>
 * To use, simply annotate your test class with <code>{@literal @}{@link org.junit.runner.RunWith}(GuiceRunner.class)</code>
 * Then annotate either the test methods or the class itself with
 * <code>{@literal @}{@link TestWith}(ModuleA.class, ModuleB.class)</code>
 * to indicate the list of modules that should be used.
 * <p/>
 * Modules in such a test must either have a no-argument constructor or one
 * which takes a Settings object.  These constructors need not be public, but
 * one or the other or both must exist.  If both a constructor which does and
 * does not take a Settings exist, the one which takes a Settings will be
 * preferred.
 * <p/>
 * A convenience test base class is provided, <code>{@link GuiceTest}</code> which takes care of the
 * <code>{@literal @}{@link org.junit.runner.RunWith}(GuiceRunner.class)</code> part, and provides
 * protected methods for getting the <code>{@link Dependencies}</code> and
 * <code>{@link com.google.inject.Injector}</code>.
 * <p/>
 * If you want to specify modules for a specific test method, simply apply the
 * annotation to just the test method, i.e.:
 * <pre>
 *  {@literal @}TestWith({ModuleA.class, ModuleC.class})
 *  public void testGenericsA(List&lt;Integer&gt; ints, List&lt;String&gt; strings) {...}
 * </pre>
 * or you can apply the annotation to the entire test class, and simply use
 * the ordinary JUnit <code>{@literal @}{@link org.junit.Test}</code> annotation, i.e.
 * <pre>
 * {@literal @}TestWith({ModuleA.class, ModuleC.class})
 * public class MyTest extends GuiceTest {
 *   {@literal @}Inject
 *   SomeObject createdByGuice;
 *
 *   {@literal @}Test
 *   public void myTest (List&lt;String&gt; strings) { ... }
 * }
 * </pre>
 * As you can see, while you are using the standard JUnit <code>{@literal @}{@link org.junit.Test}</code>
 * annotation, unlike in standard JUnit, your test method can have method parameters -
 * if Guice can create them, they will be passed in;  if they cannot, the test
 * will fail before it runs.
 * <p/>
 * Also, as shown above, you can use the standard Guice <code>{@literal @}{@link com.google.inject.Inject}</code>
 * annotation to inject class members into your test.
 * <p/>
 * <h3>Combining class and method annotations</h3>
 * You can also combine method and class annotations, if some modules are common
 * to all test methods, and others are needed only by specific test methods:
 * <pre>
 * {@literal @}TestWith({CommonModule.class})
 * public class MyTest extends GuiceTest {
 *   {@literal @}Inject
 *   SomeObject createdByGuice;
 *
 *   {@literal @}TestWith({AnotherModule.class})
 *   public void myTest (List&lt;String&gt; strings) { ... }
 * }
 * </pre>
 * The list of modules belonging to the class and to the test method
 * may not overlap.
 * 
 * <h3>Specifying configuration</code>
 * The <code>{@literal @}{@link SettingsLocations}</code> can be used to
 * annotate a test class or method (or both) with a list of paths on the
 * classpath to properties files which are used to initialize the
 * <code>{@link Settings}</code> settings used to initialize modules and
 * provide <code>{@literal @}{@link com.google.inject.name.Named}</code>
 * values.
 * <p/>
 * GuiceRunner will automatically look for a properties file with the same
 * name as the source file in the same package, and include it if present.
 *
 * <h3>Post-injection set-up</h3>
 * To run code after injection, but before a test is run, simply annotate
 * a method with <code>{@literal @}{@link OnInjection}</code>.  I.e:
 * <pre>
 * {@literal @}TestWith(DatabaseModule.class)
 * public class MyTest extends GuiceTest {
 *   Connection connection;
 *   {@literal @}OnInjection
 *   void setupConnection(DataSource dataSource) {
 *     connection = dataSource.getConnection();
 *   }
 *
 *   {@literal @}TestWith(AnotherModule.class)
 *   public void myTest () { ... }
 * }
 * </pre>
 * These methods can also accept parameters created by Guice. <i>Note that
 * the standard JUnit <code>{@literal @}{@link org.junit.Before}</code> or
 * <code>setUp()</code> methods will run <u>before</u> injection happens, and
 * should <u>not</u> expect injected parameters to be non-null.</i>
 *
 * <h2>Advanced Use</h2>
 * One of the purposes of this class is to be able to have a single test class or
 * method which is run with multiple different modules (for example, to run a
 * test against both a development database configuration, and also a production
 * database).  So a second value is possible on the
 * <code>{@literal @}{@link TestWith}</code> annotation:
 * <pre>
 * <code>{@literal @}{@link TestWith}(value=CommonModule.class, iterate={Database1.class, Database2.class})</code>
 * public void thisTestWillRunTwice(SomeFixture fixture, DataSource src) { ... }
 * </pre>
 * This way a single test or test class can be used with multiple modules which
 * provide the same thing, without needing to duplicate code.
 * <p/>
 * While not commonly useful, you <i>can</i> also combine <code>iterate</code>
 * values in both method and class annotations, and the test will be run with
 * each possible combination of modules:
 * <pre>
 * {@literal @}TestWith(value=CommonModule.class, iterate={DirectConnectionModule.class, ConnectionPoolModule.class})
 * public class MyTest extends GuiceTest {
 *
 *   {@literal @}TestWith(iterate={DevDatabaseModule.class, ProductionDatabaseModule.class})
 *   public void myTest (DataSource src) { ... }
 * }
 * </pre>
 * <h2>Extending GuiceRunner</h2>
 * <code>GuiceRunner</code> is designed to be extensible, so that subclasses which
 * handle additional annotations can be created, which still use Guice for injection and
 * handle the test annotations defined in this package.  This is done by
 * implementing <code>{@link TestMethodRunner}</code> and a custom
 * <code>{@link RunnerFactory}</code> which provides runners for additional
 * test methods on a test class.
 * 
 * @author Tim Boudreau
 */
public class GuiceRunner extends AbstractRunner {

    public GuiceRunner(Class<?> testClass) throws InitializationError {
        this(testClass, new DefaultRunnerFactory());
    }
    
    protected RunnerFactory createDefaultRunnerFactory() {
        return new DefaultRunnerFactory();
    }

    @Override
    protected void onBeforeCreateDependencies(TestClass testClass, FrameworkMethod method, Settings settings, DependenciesBuilder builder) {
        super.onBeforeCreateDependencies(testClass, method, settings, builder); //To change body of generated methods, choose Tools | Templates.
        System.setProperty("testMethodQname", testClass.getJavaClass().getSimpleName() + '.' + method.getName());
        System.setProperty("testMethod", method.getName());
        System.setProperty("testClass", testClass.getName());
    }

    /**
     * Create a new GuiceRunner which will create test runners from the passed
     * factories.  For use by subclasses which want to add additional test
     * method types.  Typically you will want to pass an instance of
     * <code>{@link GuiceRunner#defaultRunnerFactory()} so that regular
     * JUnit and TestWith tests work.
     *
     * @param testClass The test class, passed in by JUnit's infrastructure
     * @param runnerFactories An array of &gt;=1 runner factories.
     * @throws InitializationError An error if the test class is somehow invalid
     */
    protected GuiceRunner(Class<?> testClass, RunnerFactory... runnerFactories) throws InitializationError {
        super (testClass, runnerFactories);
    }

    @Override
    @SuppressWarnings("AvoidCatchingThrowable")
    public void run(RunNotifier notifier) {
        Statement st = classBlock(notifier);
        try {
            //        super.run(notifier);
                    st.evaluate();
        } catch (Throwable ex) {
            notifier.fireTestFailure(new Failure(getDescription(), ex));
        }
    }
    
     protected static void validateModuleClassesCanBeConstructed(List<Class<?>> types, List<Throwable> errors) {
        for (Class<?> c : types) {
            Constructor con = findUsableModuleConstructor(c);
            if (con == null) {
                StringBuilder sb = new StringBuilder();
                sb.append(c.getName()).append("must have a constructor with the signature '").append(c.getSimpleName()).append("()' or " + "'").append(c.getSimpleName()).append("(Settings settings)'.  "
                        + "The constructor need not be public, but must exist.");
                errors.add(fakeException(sb.toString(), c, 0));
            }
        }
    }    
     
    protected static void checkOverlap(FrameworkMethod m, TestWith anno, List<Throwable> errors) {
        List<Class<?>> a = collectClasses(anno, errors);
        List<Class<?>> b = collectClasses(m.getAnnotation(TestWith.class), errors);
        if (a == null || b == null) {
            return;
        }
        Set<Class<?>> set = new HashSet<Class<?>>();
        set.addAll(a);
        set.addAll(b);
        if (set.size() != a.size() + b.size()) {
            a.addAll(b);
            a.removeAll(set);
            errors.add(new IllegalArgumentException("Class and method both want to " + 
                    "create an instance of the same module(s): " + a));
        }
    }

    protected static List<Class<?>> collectClasses(TestWith anno, List<Throwable> errors) {
        if (anno != null) {
            List<Class<?>> result = new LinkedList<Class<?>>();
            result.addAll(Arrays.asList(anno.value()));
            result.addAll(Arrays.asList(anno.iterate()));
            validateModuleClassesCanBeConstructed(result, errors);
            return result;
        }
        return null;
    }
    
    static class DefaultRunnerFactory implements RunnerFactory {
        private AbstractRunner runner;

        @Override
        public void createChildren(TestClass testClass, List<? super TestMethodRunner> runners, List<Throwable> errors, RuleWrapperProvider p) throws InitializationError {
            TestWith classAnnotation = testClass.getJavaClass().getAnnotation(TestWith.class);
            if (classAnnotation != null && !"None".equals(classAnnotation.expected().getSimpleName())) {
                errors.add(new AssertionError("expected= is meaningless on class annotation"));
            }
            List<FrameworkMethod> standardTestMethods = new ArrayList<FrameworkMethod>();
            List<FrameworkMethod> guiceTestMethods = new ArrayList<FrameworkMethod>();
            guiceTestMethods.addAll(testClass.getAnnotatedMethods(TestWith.class));
            standardTestMethods.addAll(testClass.getAnnotatedMethods(Test.class));
            for (FrameworkMethod m : standardTestMethods) {
                if (m.getAnnotation(TestWith.class) != null) {
                    throw new IllegalArgumentException(m.getName() + " has both @Test and "
                            + "@TestWith annotations.  This is illegal.");
                }
                checkOverlap(m, classAnnotation, errors);
                if (classAnnotation == null || classAnnotation.iterate().length == 0) {
                    if (m.getMethod().getGenericParameterTypes().length == 0) {
                        runners.add(createJUnitTestMethodRunner(testClass, m, p, runner));
                    } else {
                        runners.add(new GuiceTestRunner(testClass, m, p, runner));
                    }
                } else {
                    for (int i = 0; i < classAnnotation.iterate().length; i++) {
                        runners.add(new IterativeGuiceTestRunner(i, -1, testClass, m, p, runner));
                    }
                }
            }
            for (FrameworkMethod m : guiceTestMethods) {
                checkOverlap(m, classAnnotation, errors);
                TestWith methodAnnotation = m.getAnnotation(TestWith.class);
                if (classAnnotation == null && methodAnnotation != null) {
                    if (methodAnnotation.iterate().length == 0) {
                        runners.add (new GuiceTestRunner(testClass, m, p, runner));
                    } else {
                        for (int i=0; i < methodAnnotation.iterate().length; i++) {
                            runners.add (new IterativeGuiceTestRunner(-1, i, testClass, m, p, runner));
                        }
                    }
                } else if (classAnnotation != null && methodAnnotation == null) {
                    if (classAnnotation.iterate().length == 0) {
                        runners.add (new GuiceTestRunner(testClass, m, p, runner));
                    } else {
                        for (int i=0; i < classAnnotation.iterate().length; i++) {
                            runners.add (new IterativeGuiceTestRunner(i, -1, testClass, m, p, runner));
                        }
                    }
                } else if (classAnnotation != null && methodAnnotation != null) {
                    if (classAnnotation.iterate().length == 0 && methodAnnotation.iterate().length == 0) {
                        runners.add (new GuiceTestRunner(testClass, m, p, runner));
                    } else if (classAnnotation.iterate().length > 0 && methodAnnotation.iterate().length == 0) {
                        for (int i=0; i < classAnnotation.iterate().length; i++) {
                            runners.add (new IterativeGuiceTestRunner(i, -1, testClass, m, p, runner));
                        }
                    } else if (classAnnotation.iterate().length == 0 && methodAnnotation.iterate().length > 0) {
                        for (int i=0; i < methodAnnotation.iterate().length; i++) {
                            runners.add (new IterativeGuiceTestRunner(-1, i, testClass, m, p, runner));
                        }
                    } else if (classAnnotation.iterate().length > 0 && methodAnnotation.iterate().length > 0) {
                        for (int i = 0; i < classAnnotation.iterate().length; i++) {
                            for (int j = 0; j < methodAnnotation.iterate().length; j++) {
                                runners.add(new IterativeGuiceTestRunner(i, j, testClass, m, p, runner));
                            }
                        }
                    } else {
                        throw new AssertionError("Should not get here: " + classAnnotation + " : " + methodAnnotation);
                    }
                } else {
                    throw new AssertionError ("Processing a method for Guice "
                            + "but there is not such annotation. ??");
                }
            }
        }

        @Override
        public void setRunner(AbstractRunner runner) {
            this.runner = runner;
        }
    }

    static Constructor<?> findUsableModuleConstructor(Class<?> type) {
        Constructor<?> con = null;
        try {
            con = type.getDeclaredConstructor(Settings.class);
        } catch (NoSuchMethodException ex) {
            try {
                con = type.getDeclaredConstructor();
            } catch (NoSuchMethodException ex1) {
                //caller will handle null return value
            }
        }
        return con;
    }

    static class GuiceTestRunner extends TestMethodRunner {

        public GuiceTestRunner(TestClass testClass, FrameworkMethod method, RuleWrapperProvider prov, AbstractRunner runner) throws InitializationError {
            super(testClass, method, prov, runner);
        }

        @Override
        protected void invokeTest(final Statement base, final Object target, final Dependencies dependencies) throws Throwable {
            final Type[] paramTypes = method.getMethod().getGenericParameterTypes();
            Test test = method.getAnnotation(Test.class);
            TestWith testWith = method.getAnnotation(TestWith.class);
            final boolean shouldThrowException = (test != null && !"None".equals(test.expected().getSimpleName())) || 
                    (testWith != null && !"None".equals(testWith.expected().getSimpleName()));
            try {
                if (paramTypes.length == 0) {
                    base.evaluate();
                    if (shouldThrowException) {
                        throw new AssertionError(method.getMethod().getName() + " should have thrown an exception but did not");
                    }
                } else {
                    final Statement st = new Statement(){
                        @Override
                        public void evaluate() throws Throwable {
                            Object[] parameters = new Object[paramTypes.length];
                            Annotation[][] a = method.getMethod().getParameterAnnotations();
                            for (int i = 0; i < paramTypes.length; i++) {
                                try {
                                    Key key;
                                    if (a[i].length > 0) {
                                        if (a[i].length > 1) {
                                            throw new AssertionError("Multiple annotations on test method parameter " + paramTypes[i] + ": " + Arrays.asList(a[i]) + " but Guice's Key.get() only allows one");
                                        }
                                        key = Key.get(paramTypes[i], a[i][0]);
                                    } else {
                                        key = Key.get(paramTypes[i]);
                                    }
                                    System.out.println("Get instance of " + key + " for " + method.getMethod().getDeclaringClass() + "." + method.getName());
                                    parameters[i] = dependencies.getInstance(key);
                                } catch (ConfigurationException e) {
                                    throw new IllegalStateException ("Guice configuration exception creating parameter of type " + paramTypes[i] + " for " + method.getName() + " on " + target.getClass().getName(), e);
                                }
                            }
                            method.invokeExplosively(target, parameters);
                            if (shouldThrowException) {
                                throw fakeException(method.getMethod().getName() + " should have thrown an exception but did not", testClass.getJavaClass(), 0);
                            }
                        }
                    };
                    Statement beforeAfter = new Statement() {

                        @Override
                        public void evaluate() throws Throwable {
                            List<FrameworkMethod> befores = testClass.getAnnotatedMethods(Before.class);
                            List<FrameworkMethod> afters = testClass.getAnnotatedMethods(After.class);
                            for (FrameworkMethod m : befores) {
                                m.invokeExplosively(target);
                            }
                            try {
                            st.evaluate();
                            } finally {
                                for (FrameworkMethod m : afters) {
                                    m.invokeExplosively(target);
                                }
                            }
                        }
                    };
//                    if (isFirst()) {
//                        beforeAfter = ruleWrapper.withBeforeClasses(beforeAfter);
//                    }
//                    if (isLast()) {
//                        beforeAfter = ruleWrapper.withAfterClasses(beforeAfter);
//                    }
                    beforeAfter.evaluate();
                }
                
            } catch (Throwable t) {
                if (test != null && (test.expected().isInstance(t) || t.getCause() != null && test.expected().isInstance(t.getCause()))) {
                    return;
                }
                if (testWith != null && (testWith.expected().isInstance(t) || t.getCause() != null && testWith.expected().isInstance(t.getCause()))) {
                    return;
                }
                throw t;
            }
        }
    }

    static class IterativeGuiceTestRunner extends GuiceTestRunner {

        private final int indexInClassAnnotation;
        private final int indexInMethodAnnotation;

        public IterativeGuiceTestRunner(int indexInClassAnnotation, int indexInMethodAnnotation, TestClass testClass, FrameworkMethod method, RuleWrapperProvider p, AbstractRunner runner) throws InitializationError {
            super(testClass, method, p, runner);
            this.indexInClassAnnotation = indexInClassAnnotation;
            this.indexInMethodAnnotation = indexInMethodAnnotation;
            TestWith classAnnotation = testClass.getJavaClass().getAnnotation(TestWith.class);
            TestWith methodAnnotation = method.getAnnotation(TestWith.class);
            checkAnnotationSettingsArguments(classAnnotation);
            checkAnnotationSettingsArguments(methodAnnotation);
        }
        
        private void checkAnnotationSettingsArguments(TestWith anno) {
            if (anno == null) {
                return;
            }
            int iterCount = anno.iterate().length;
            int settingsCount = anno.iterateSettings().length;
            if (iterCount == 0 && settingsCount != 0) {
                throw new AssertionError("iterateSettings= is meaningless if iterate= not set");
            }
            if (iterCount > 0 && settingsCount != 0 && settingsCount != iterCount) {
                throw new AssertionError("If both iterate= and iterateSettings= are set, they must have the same number of arguments");
            }
        }

        private static String nameOf(Class<? extends Module> moduleClass) {
            ModuleName mn = moduleClass.getAnnotation(ModuleName.class);
            return mn == null ? moduleClass.getSimpleName() : mn.value();
        }

        private List<String> parseSettingsValue(String value) {
            List<String> result = new ArrayList<String>();
            for (String s : value.split(",")) {
                result.add(s.trim());
            }
            return result;
        }

        @Override
        protected void onAfterComputeSettingsLocations(List<String> locations) {
            TestWith classAnnotation = testClass.getJavaClass().getAnnotation(TestWith.class);
            TestWith methodAnnotation = method.getAnnotation(TestWith.class);
            String[] cSettings = classAnnotation == null ? new String[0] : classAnnotation.iterateSettings();
            String[] mSettings = methodAnnotation == null ? new String[0] : methodAnnotation.iterateSettings();
            if (cSettings.length + mSettings.length > 0) {
                if (cSettings.length != 0) {
                    locations.addAll(parseSettingsValue(cSettings[indexInClassAnnotation]));
                }
                if (mSettings.length != 0) {
                    locations.addAll(parseSettingsValue(mSettings[indexInMethodAnnotation]));
                }
            }
            super.onAfterComputeSettingsLocations(locations);
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            if (indexInClassAnnotation >= 0) {
                TestWith classAnnotation = testClass.getJavaClass().getAnnotation(TestWith.class);
                if (classAnnotation != null) {
                    Class<? extends Module> moduleClass = classAnnotation.iterate()[indexInClassAnnotation];
                    result.append(nameOf(moduleClass));
                }
            }
            if (indexInMethodAnnotation >= 0) {
                TestWith methodAnnotation = method.getAnnotation(TestWith.class);
                if (methodAnnotation != null) {
                    Class<? extends Module> moduleClass = methodAnnotation.iterate()[indexInMethodAnnotation];
                    if (result.length() > 1) {
                        result.append(",");
                    }
                    result.append(nameOf(moduleClass));
                }
            }
            return result.toString();
        }

        @Override
        protected Description describeChild(Description origDescription) {
            return Description.createTestDescription(testClass.getJavaClass(),
                   toString() + "__" + origDescription.getMethodName());
        }

        @Override
        protected boolean skip() {
            boolean result = super.skip();
            if (!result && Dependencies.isIDEMode()) {
                TestWith classAnnotation = testClass.getJavaClass().getAnnotation(TestWith.class);
                if (classAnnotation != null && indexInClassAnnotation != -1) {
                    Class<? extends Module> iteration = classAnnotation.iterate()[indexInClassAnnotation];
                    if (iteration.getAnnotation(SkipWhenRunInIDE.class) != null) {
                        result = true;
                    }
                }
                TestWith methodAnnotation = method.getAnnotation(TestWith.class);
                if (methodAnnotation != null && indexInMethodAnnotation != -1) {
                    Class<? extends Module> iteration = methodAnnotation.iterate()[indexInMethodAnnotation];
                    if (iteration.getAnnotation(SkipWhenRunInIDE.class) != null) {
                        result |= true;
                    }
                }
            }
            return result;
        }

        @Override
        protected List<Class<? extends Module>> findModuleClasses() {
            List<Class<? extends Module>> result = super.findModuleClasses();
            if (indexInClassAnnotation >= 0) {
                TestWith classAnnotation = testClass.getJavaClass().getAnnotation(TestWith.class);
                if (classAnnotation != null) {
                    Class<? extends Module> iteration = classAnnotation.iterate()[indexInClassAnnotation];
                    result.add(iteration);
                }
            }
            if (indexInMethodAnnotation >= 0) {
                TestWith methodAnnotation = method.getAnnotation(TestWith.class);
                if (methodAnnotation != null) {
                    Class<? extends Module> iteration = methodAnnotation.iterate()[indexInMethodAnnotation];
                    result.add(iteration);
                }
            }
            return result;
        }
    }
}
