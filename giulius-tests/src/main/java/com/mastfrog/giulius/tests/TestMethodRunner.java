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

import com.google.inject.Key;
import com.google.inject.Module;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.settings.Settings;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.settings.MutableSettings;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import com.mastfrog.util.Strings;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.rules.MethodRule;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

/**
 * Test runner which is capable of configuring and instantiating the modules
 * required by one test method and then invoking the test method in whatever way
 * is needed. GuiceRunner creates one of these for each test method, and it is
 * what actually runs the test.
 * <p/>
 *
 * @author Tim Boudreau
 */
public abstract class TestMethodRunner extends Runner implements MethodRule {

    protected final TestClass testClass;
    protected final FrameworkMethod method;
    private final InjectingRunner actualRunner;
    RunWrapper wrap;
    protected static final NetworkCheck NETWORK_CHECK = new NetworkCheck("google.com", true);
    protected final RuleWrapperProvider ruleWrapper;
    private final AbstractRunner guiceRunner;

    protected TestMethodRunner(TestClass testClass, FrameworkMethod method, RuleWrapperProvider p, AbstractRunner guiceRunner) throws InitializationError {
        this.testClass = testClass;
        this.method = method;
        this.actualRunner = new InjectingRunner(testClass.getJavaClass(), this);
        this.ruleWrapper = p;
        this.guiceRunner = guiceRunner;
    }

    @Override
    public final void run(RunNotifier notifier) {
        actualRunner.runChild(method, notifier);
    }

    @Override
    public Description getDescription() {
        return actualRunner.getDescription();
    }

    protected boolean skip() {
        boolean inIDE = Dependencies.isIDEMode();
        boolean result = inIDE && (testClass.getJavaClass().getAnnotation(SkipWhenRunInIDE.class) != null
                || method.getAnnotation(SkipWhenRunInIDE.class) != null);
        if (!result) {
            if (testClass.getJavaClass().getAnnotation(SkipWhenNetworkUnavailable.class) != null
                    || method.getAnnotation(SkipWhenNetworkUnavailable.class) != null) {
                result = !NETWORK_CHECK.isNetworkAvailable();
                if (!result) {
                    System.out.println("Skip " + testClass.getName() + "."
                            + method.getName() + " due to network unavailability");
                }
            }
        }
        return result;
    }

    /**
     * Collect classes from a particular annotation.
     *
     * @param annotation
     * @param classes
     */
    protected void collectModuleClasses(TestWith annotation, List<Class<? extends Module>> classes) {
        if (annotation != null) {
            classes.addAll(Arrays.asList(annotation.value()));
        }
    }

    /**
     * Collect a list of all module classes which need to be instantiated to run
     * this test.
     * <p/>
     * Subclasses which wish to support the
     * <code>{@literal @}{@link TestWith}</code> annotation should call
     * <code>super.findModuleClasses()</code> to find any Module classes
     * declared in class or method annotations.
     *
     * @return
     */
    protected List<Class<? extends Module>> findModuleClasses() {
        List<Class<? extends Module>> result = new LinkedList<Class<? extends Module>>();
        collectModuleClasses(testClass.getJavaClass().getAnnotation(TestWith.class), result);
        collectModuleClasses(method.getAnnotation(TestWith.class), result);
        return result;
    }
    public static final String TESTS_HOME_SETTINGS_OVERRIDE_DIR_SYSTEM_PROPERTY = "guice.test.settings.overrides.dir";
    public static final String TESTS_DEFAULT_HOME_SETTINGS_OVERRIDE_DIR = "tests";
    public static final String TESTS_HOME_SETTINGS_FILE_NAME_SYSTEM_PROPERTY = "guice.test.properties.file";
    public static final String TESTS_DEFAULT_HOME_SETTINGS_FILE_NAME = "tests.properties";
    private static boolean skipHomeDir = Boolean.getBoolean("guice.test.dont.read.from.user.home");

    /**
     * Override to add any locations for settings files to the <i>beginning</i>
     * of the list of places to load settings from.
     * <p/>
     * Note: The default implementation looks for
     * <code>tests.properties</code> (this file name can be overridden by
     * setting the system property
     * <code>guice.test.properties.file</code>). If overriding, call
     * <code>super.onBeforeComputeSettingsLocations()</code> if you want to
     * maintain this behavior.
     *
     * @param locations
     */
    protected void onBeforeComputeSettingsLocations(List<? super String> locations) {
        if (skipHomeDir) {
            return;
        }
        String propsFileName = System.getProperty(TESTS_HOME_SETTINGS_FILE_NAME_SYSTEM_PROPERTY);
        if (propsFileName == null) {
            propsFileName = TESTS_DEFAULT_HOME_SETTINGS_FILE_NAME;
        }
        String home = System.getProperty("user.home");
        if (home != null) {
            File homeDir = new File(home);
            if (homeDir.exists()) {
                File testSettings = new File(homeDir, propsFileName);
                if (testSettings.exists()) {
                    try {
                        String url = testSettings.getAbsoluteFile().toURI().toURL().toExternalForm();
                        locations.add(url);
                    } catch (MalformedURLException ex) {
                        throw new AssertionError(ex);
                    }
                }
            }
        }
    }

    /**
     * Override to add any locations for settings files to the <i>end</i> of the
     * list of places to load settings from.
     * <p/>
     * The default behavior is to look for a directory under user.home with the
     * name
     * <code>tests</code> (or whatever value the system property
     * <code>guice.test.settings.overrides.dir</code> is), and, if it exists,
     * iterate all the already established settings locations (simple
     * com/foo/whatever.properties paths) and, if a file with the same relative
     * path exists under that directory, include that file in the settings
     * locations as well.
     *
     * @param locations The locations
     */
    protected void onAfterComputeSettingsLocations(List<String> locations) {
        if (skipHomeDir) {
            return;
        }
        String propsDirName = System.getProperty(TESTS_HOME_SETTINGS_OVERRIDE_DIR_SYSTEM_PROPERTY);
        if (propsDirName == null) {
            propsDirName = TESTS_DEFAULT_HOME_SETTINGS_OVERRIDE_DIR;
        }
        String home = System.getProperty("user.home");
        if (home != null) {
            File homeDir = new File(home);
            if (homeDir.exists()) {
                File dir = new File(homeDir, propsDirName);
                if (dir.exists() && dir.isDirectory()) {
                    String[] locs = locations.toArray(new String[locations.size()]);
                    for (String path : locs) {
                        File f = new File(dir, path.replace('/', File.separatorChar));
                        if (f.exists()) {
                            try {
                                locations.add(f.toURI().toURL().toExternalForm());
                            } catch (MalformedURLException ex) {
                                Logger.getLogger(TestMethodRunner.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                }
            }
        }
    }

    private String[] settingsLocations() {
        List<String> locations = new LinkedList<String>();
        //First look for one next to the test:
        Class<?> tc = testClass.getJavaClass();
        //just in case
        Class<?> outer = tc;
        while (outer.getEnclosingClass() != null) {
            outer = outer.getEnclosingClass();
        }
        String siblingPropertiesName = outer.getSimpleName() + ".properties";
        if (outer.getResource(siblingPropertiesName) != null) {
            String qname = outer.getName().replace('.', '/') + ".properties";
            locations.add(qname);
        }
        onBeforeComputeSettingsLocations(locations);
        //Now look for any in annotations, class first, then method
        Configurations locs = testClass.getJavaClass().getAnnotation(Configurations.class);
        if (locs != null) {
            locations.addAll(Arrays.asList(locs.value()));
        }
        locs = method.getAnnotation(Configurations.class);
        if (locs != null) {
            locations.addAll(Arrays.asList(locs.value()));
        }
        onAfterComputeSettingsLocations(locations);
        return locations.toArray(new String[0]);
    }

    private Module[] createModules(Settings settings) throws Throwable {
        List<Module> result = new LinkedList<Module>();
        for (Class<? extends Module> type : findModuleClasses()) {
            result.add(instantiateModule(type, settings));
        }
        return result.toArray(new Module[0]);
    }

    protected final Settings loadSettings() throws Throwable {
        SettingsBuilder sb = SettingsBuilder.createDefault();
        String[] settingsLocations = settingsLocations();
        System.out.println("Loading Settings from the following locations for " + getDescription() + " " + method.getName());
        System.out.println(Strings.toString(settingsLocations));
        for (String loc : settingsLocations) {
            if (Streams.locate(loc) == null) {
                throw GuiceRunner.fakeException("No such settings file: " + loc, testClass.getJavaClass(), 0);
            }
            sb.add(loc);
        }

        Settings settings = sb.build();

        if (Boolean.getBoolean("guice.tests.debug")) {
            System.out.println("SETTINGS ARE " + settings);
        }
        if (guiceRunner != null) {
            settings = guiceRunner.onSettingsCreated(settings);
        }

        return settings;
    }

    protected final Dependencies createDependencies() throws Throwable {
        Settings settings = onSettingsCreated(loadSettings());
        Module[] modules = createModules(settings);
        if (modules.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (Module m : modules) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(m.getClass().getName());
            }
            System.out.println("Instantiated the following modules for :" + testClass.getName() + "." + method.getName());
            System.out.println(sb);
        } else {
            System.out.println("No modules requred for " + testClass.getName() + "." + method.getName());
        }
        DependenciesBuilder builder = Dependencies.builder().useMutableSettings();
        builder.addDefaultSettings();
        System.out.println("NAMESPACES: " + builder.namespaces());
        for (String ns : builder.namespaces()) {
            builder.add(settings, ns);
        }
        builder.add(modules);
        onBeforeCreateDependencies(settings, builder);
        Dependencies dependencies = builder.build();
        onAfterCreateDependencies(settings, dependencies);
        return dependencies;
    }

    protected Settings onSettingsCreated(Settings orig) throws IOException {
        // Allows system properties prefixed with "override." to take precedence
        // over defaults
        MutableSettings mutable = new SettingsBuilder().buildMutableSettings();
        String prefix = "override.";
        boolean hasOverrides = false;
        for (String prop : System.getProperties().stringPropertyNames()) {
            if (prop.startsWith(prefix) && prop.length() > prefix.length()) {
                String name = prop.substring(prefix.length());
                String val = System.getProperty(prop);
                System.out.println("Setting override: " + name + "=" + val);
                hasOverrides = true;
                mutable.setString(name, val);
            }
        }
        if (hasOverrides) {
            SettingsBuilder b = new SettingsBuilder();
            b.add(orig);
            b.add(mutable);
            orig = b.build();
        }
        return orig;
    }

    protected void onBeforeCreateDependencies(Settings settings, DependenciesBuilder builder) {
        if (this.guiceRunner != null) {
            this.guiceRunner.onBeforeCreateDependencies(testClass, method, settings, builder);
        }
    }

    protected void onAfterCreateDependencies(Settings settings, Dependencies dependencies) {
        if (guiceRunner != null) {
            this.guiceRunner.onAfterCreateDependencies(testClass, method, settings, dependencies);
        }
    }

    private Module instantiateModule(Class<? extends Module> moduleClass, Settings settings) throws Throwable {
        Module module;
        Constructor c = GuiceRunner.findUsableModuleConstructor(moduleClass);
        System.out.println("Will construct module " + moduleClass.getName() + " using " + c);
        c.setAccessible(true);
        if (c.getParameterTypes().length == 1) {
            module = (Module) c.newInstance(settings);
        } else if (c.getParameterTypes().length == 0) {
            module = (Module) c.newInstance();
        } else {
            throw new AssertionError("Should have rejected module class "
                    + moduleClass.getName()
                    + "with constructor arguments which are not empty or a "
                    + "single Settings object");
        }
        return module;
    }
    private boolean first;
    private boolean last;

    void setFirst(boolean first) {
        this.first = first;
    }

    void setLast(boolean last) {
        this.last = last;
    }

    protected final boolean isFirst() {
        return first;
    }

    protected final boolean isLast() {
        return last;
    }

    @Override
    public final Statement apply(Statement base, final FrameworkMethod method, Object target) {
        if (skip()) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    System.out.println("Skipping " + method.getName() + " in IDE mode");
                }
            };
        }
        Statement doIt = createStatement(base, target);
        return doIt;
    }

    protected static Object[] argumentsForMethod(Method m, Dependencies dependencies) {
        Type[] types = m.getGenericParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            args[i] = dependencies.getInstance(Key.get(type));
        }
        return args;
    }

    Statement createStatement(final Statement base, final Object target) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final Dependencies dependencies = createDependencies();
                try {
                    dependencies.injectMembers(target);
                    for (FrameworkMethod m : testClass.getAnnotatedMethods(OnInjection.class)) {
                        m.getMethod().setAccessible(true);
                        if (m.getMethod().getParameterTypes().length == 1 && m.getMethod().getParameterTypes()[0] == Dependencies.class) {
                            m.invokeExplosively(target, dependencies);
                        } else if (m.getMethod().getParameterTypes().length == 0) {
                            m.invokeExplosively(target);
                        } else {
                            m.invokeExplosively(target, argumentsForMethod(m.getMethod(), dependencies));
                        }
                    }
                    if (wrap != null) {
                        Statement doIt = new Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                invokeTest(base, target, dependencies);
                            }
                        };
                        wrap.invokeTest(doIt, target, method, dependencies);
                    } else {
                        Statement b = base;
                        invokeTest(b, target, dependencies);
                    }
                } finally {
                    dependencies.shutdown();
                }
            }
        };
    }

    /**
     * Called to actually invoke a unit test. At the time this is called, the
     * test class instance is already created and has been injected.
     * <p/>
     * This is where you actually assemble any method parameters necessary or do
     * anything else necessary to the test class, and then invoke the test
     * method as desired.
     *
     * @param base The standard JUnit statement which would simply run the test
     * as a normal JUnit test method. Can be used or not.
     * @param target The test class
     * @param dependencies The dependencies object which provides the injector
     * @throws Throwable Any error thrown by the test method
     */
    protected abstract void invokeTest(Statement base, Object target, Dependencies dependencies) throws Throwable;

    /**
     * Get a description for this test method. Any TestMethodRunner which
     * expects to run a test method multiple times should override this method
     * to return a different description that includes information about the
     * context of each run. Otherwise, IDE integrations will show multiple runs
     * of a test method as if there were only one.
     * <p/>
     * Note that many IDE integrations will elide any text in parentheses or
     * separated by whitespace from the method name. So it is important that the
     * returned value provide a contiguous string, or it will still appear that
     * a test method was run only once, even though actually multiple runners
     * ran the method.
     * <p/>
     * Example implementation: return
     * Description.createTestDescription(testClass.getJavaClass(),
     * origDescription.getMethodName() + "-[" + mySpecialValue +']');
     *
     * @return A description of whatever this runner does and what method it
     * does it to, which provides enough information for somebody reading a test
     * report to know what context the test was run in.
     */
    protected Description describeChild(Description origDescription) {
        return origDescription;
    }
}
