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
package com.mastfrog.giulius;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.ProvisionListener.ProvisionInvocation;
import com.google.inject.util.Providers;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.guicy.annotations.Value;
import com.mastfrog.giulius.annotations.processors.NamespaceAnnotationProcessor;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.settings.MutableSettings;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import com.mastfrog.util.thread.ProtectedThreadLocal;
import com.mastfrog.util.thread.QuietAutoCloseable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A wrapper around Guice's injector which enforces a few things such as how
 * configuration information is loaded, and binds &#064;Named injections to
 * values from system properties, environment variables, any default values
 * specified using the &#064;Defaults annotation, any
 * com/mastfrom/defaults.properties files on the classpath,
 * <p/>
 * Typically you create a Dependencies once on startup, and then get whatever
 * bootsrap objects you need to start the application. It is possible to
 * completely isolate things by using multiple Dependencies, but this is usually
 * an indication of doing something wrong.
 *
 * @author Tim Boudreau
 */
public final class Dependencies {

    /**
     * System property which determines Guice stage & result of
     * isProductionMode(). System property overrides same value in the default
     * namespace settings (string value is &quot;productionMode&quot;).
     */
    public static final String SYSTEM_PROP_PRODUCTION_MODE = "productionMode";
    private final Map<String, Settings> settings = new HashMap<>();
    private final Set<SettingsBindings> settingsBindings;
    private final List<Module> modules = new LinkedList<>();
    private volatile Injector injector;

    public Dependencies(Module... modules) throws IOException {
        this(SettingsBuilder.createDefault().build(), modules);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(" {\n");
        for (Iterator<Module> it = modules.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append("\n");
        for (Map.Entry<String, Settings> e : settings.entrySet()) {
            sb.append("  ").append(e.getKey()).append("=").append(e.getValue()).append('\n');
        }
        return sb.append("\n}").toString();
    }

    /**
     * Create a Dependencies with the classpath contents used to look up
     * properties files and named properties, and the default settings.
     *
     * @param modules
     * @return A dependencies
     * @throws Error if an IOException occurred
     */
    public static Dependencies create(Module... modules) {
        try {
            return new Dependencies(modules);
        } catch (IOException ioe) {
            //we're early enough in startup to bail
            throw new ConfigurationError(ioe);
        }
    }

    /**
     * Create a dependency using the passed Configuration, bypassing any loading
     * from the classpath or elsewhere.
     *
     * @param configuration The configuration which is the source for Named
     * injections
     * @param modules A set of modules
     */
    public Dependencies(Settings configuration, Module... modules) {
        this(Collections.singletonMap(Namespace.DEFAULT, configuration), EnumSet.allOf(SettingsBindings.class), modules);
    }

    Dependencies(Map<String, Settings> settings, Set<SettingsBindings> settingsBindings, Module... modules) {
        this.settings.putAll(settings);
        if (!this.settings.containsKey(Namespace.DEFAULT)) {
            try {
                //need at least an empty one for defaults
                this.settings.put(Namespace.DEFAULT, new SettingsBuilder().build());
            } catch (IOException ex) {
                throw new ConfigurationError(ex);
            }
        }
        this.modules.add(createBindings());
        this.modules.addAll(Arrays.asList(modules));
        this.settingsBindings = settingsBindings;
    }

    public static DependenciesBuilder builder() {
        return new DependenciesBuilder();
    }

    /**
     * Get the injector, creating it if necessary. This is the typical
     * entry-point for starting an application, e.g.:
     * <pre>
     * Dependencies deps = new Dependencies(new Module1(), new Module2());
     * Server server = deps.getInjector().getInstance(Server.class);
     * server.start();
     * </pre>
     *
     *
     * @return
     */
    public Injector getInjector() {
        if (injector == null) {
            synchronized (this) {
                if (injector == null) {
                    injector = Guice.createInjector(getStage(), modules);
                }
            }
        }
        return injector;
    }

    /**
     * Get an instance of the passed type; throws an exception if nothing is
     * bound.
     *
     * @param <T> A type
     * @param type The type
     * @return An instance of that class
     */
    public <T> T getInstance(Class<T> type) {
        return getInjector().getInstance(type);
    }

    /**
     * Get an instance of the passed key; throws an exception if nothing is
     * bound.
     *
     * @param <T> A type
     * @param type The type
     * @return An instance of that class
     */
    public <T> T getInstance(Key<T> key) {
        return getInjector().getInstance(key);
    }

    /**
     * Triggers running shutdown hooks; for use with unit tests, servlet
     * unloading, etc.
     */
    public void shutdown() {
        try {
            reg.runShutdownHooks();
        } finally {
            for (Dependencies d : others) {
                d.shutdown();
            }
        }
    }

    /**
     * For use with frameworks that insist on creating the injector
     *
     * @param config
     * @return
     */
    public static Module createBindings(Settings config) throws IOException {
        return new Dependencies(config).createBindings();
    }

    /**
     * Create a module which binds &#064;Named properties appropriately.
     * <p/>
     * This method is only necessary when using frameworks such as
     * GuiceRestEasy, which insist on creating the injector.
     *
     * @return
     */
    protected Module createBindings() {
        return new DependenciesModule();
    }

    /**
     * whether the productionMode system property (-DproductionMode=true) or
     * settings (productionMode=true) is set. If it is, will be configured for
     * production mode, meaning that singletons will be initialized eagerly, and
     * that modules might configure themselves to configure e.g. for using a
     * real mail server rather than a mock as you would probably do in
     * development mode. See {@link Stage#PRODUCTION}. If not provided, this
     * class will be bootstrapped in {@link Stage#DEVELOPMENT} mode.
     *
     * @return true if the system should start up in production mode, false
     * otherwise
     */
    public boolean isProductionMode() {
        return isProductionMode(settings.get(Namespace.DEFAULT));
    }

    /**
     * Whether the productionMode system property (-DproductionMode=true) or
     * settings (productionMode=true) is set. If it is, will be configured for
     * production mode, meaning that singletons will be initialized eagerly, and
     * that modules might configure themselves to configure e.g. for using a
     * real mail server rather than a mock as you would probably do in
     * development mode. See {@link Stage#PRODUCTION}. If not provided, this
     * class will be bootstrapped in {@link Stage#DEVELOPMENT} mode.
     *
     * @param settings settings (may contain productionMode)
     * @return true if the system should start up in production mode, false
     * otherwise
     */
    public static boolean isProductionMode(Settings settings) {
        try {
            return settings.getBoolean(SYSTEM_PROP_PRODUCTION_MODE, false)
                    || Boolean.getBoolean(SYSTEM_PROP_PRODUCTION_MODE);
        } catch (NoSuchElementException e) {
            //UGH!
            return false;
        }
    }

    public Settings getSettings() {
        Settings result = settings.get(Namespace.DEFAULT);
        if (result == null) {
            //May want to just get is production mode out of here.
            result = settings.get(settings.keySet().iterator().next());
        }
        return result;
    }

    public Settings getSettings(String namespace) {
        Checks.notNull("namespace", namespace);
        return settings.get(namespace);
    }

    /**
     * Get the Guice stage, as determined by isProductionMode()
     *
     * @return
     */
    public Stage getStage() {
        isIDEMode();
        return isProductionMode() ? Stage.PRODUCTION : Stage.DEVELOPMENT;
    }
    public static final String IDE_MODE_SYSTEM_PROPERTY = "in.ide";

    /**
     * Used by the tests.guice framework to bypass execution of long running
     * tests if this system property is set. To use, configure your IDE to pass
     * -Din.ide=true to Maven
     *
     * @return True if the system property is set
     */
    public static boolean isIDEMode() {
        return Boolean.getBoolean(IDE_MODE_SYSTEM_PROPERTY);
    }
    private final ShutdownHookRegistry reg = ShutdownHookRegistry.get();

    public void autoShutdownRefresh(SettingsBuilder sb) {
        reg.add(sb.onShutdownRunnable());
    }

    /**
     * Same as getInjector().injectMembers(arg)
     *
     * @param arg
     */
    public void injectMembers(Object arg) {
        getInjector().injectMembers(arg);
    }

    private static class NamespaceImpl implements Namespace {

        private final String name;

        public NamespaceImpl(String name) {
            this.name = name;
        }

        @Override
        public String value() {
            return name;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Namespace.class;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Namespace && name.equals(((Namespace) o).value());
        }

        @Override
        public int hashCode() {
            // This is specified in java.lang.Annotation.
            int result = (127 * "value".hashCode()) ^ name.hashCode();
            return result;
        }
    }

    private static final class ValueImpl implements Value {

        private final Namespace ns;
        private final String key;

        ValueImpl(String key, String ns) {
            this.ns = new NamespaceImpl(ns);
            this.key = key;
        }

        @Override
        public String toString() {
            return ns + "::" + key;
        }

        @Override
        public String value() {
            return key;
        }

        @Override
        public Namespace namespace() {
            return ns;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Value.class;
        }

        public int hashCode() {
            // This is specified in java.lang.Annotation.
            int result = (127 * "value".hashCode()) ^ key.hashCode();
            result += (127 * "namespace".hashCode()) ^ ns.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ValueImpl other = (ValueImpl) obj;
            if (!Objects.equals(this.ns, other.ns)) {
                return false;
            }
            if (!Objects.equals(this.key, other.key)) {
                return false;
            }
            return true;
        }
    }
    private final ProtectedThreadLocal<TypeLiteral<?>> currentType = new ProtectedThreadLocal<>();
    private final ProtectedThreadLocal<TypeLiteral<?>> prevType = new ProtectedThreadLocal<>();

    private final Set<Dependencies> others = Collections.<Dependencies>synchronizedSet(new HashSet<Dependencies>());

    public final Dependencies alsoShutdown(Dependencies other) {
        if (other == this) {
            throw new IllegalArgumentException("add to self");
        }
        others.add(other);
        return this;
    }

    private final class DependenciesModule extends AbstractModule {

        @Override
        protected void configure() {
            try {
                Binder binder = binder();
                bind(Dependencies.class).toInstance(Dependencies.this);
                bind(ShutdownHookRegistry.class).toInstance(reg);
                Set<String> knownNamespaces = loadNamespaceListsFromClasspath();
                log("Loaded namespaces " + knownNamespaces);
                knownNamespaces.addAll(settings.keySet());
                knownNamespaces.add(Namespace.DEFAULT);

                Stage stage = getStage();
                DeploymentMode mode;
                switch (stage) {
                    case PRODUCTION:
                        mode = DeploymentMode.PRODUCTION;
                        break;
                    default:
                        mode = DeploymentMode.DEVELOPMENT;
                }
                bind(DeploymentMode.class).toInstance(mode);

                Set<String> allKeys = new HashSet<>();
                for (String namespace : knownNamespaces) {
                    Settings s = settings.get(namespace);
                    if (s == null) {
                        s = SettingsBuilder.forNamespace(namespace).addGeneratedDefaultsFromClasspath().addDefaultsFromClasspath().build();
                        settings.put(namespace, s);
                    }
                    allKeys.addAll(s.allKeys());
                }

                Provider<Settings> namespacedSettings = new NamespacedSettingsProvider(Dependencies.this);
                for (String k : allKeys) {
                    Named n = Names.named(k);
                    PropertyProvider p = new PropertyProvider(k, namespacedSettings);
                    for (SettingsBindings type : settingsBindings) {
                        switch(type) {
                            case INT :
                                binder.bind(Key.get(Integer.class, n)).toProvider(new IntProvider(p));
                                break;
                            case STRING :
                                binder.bind(Key.get(String.class, n)).toProvider(p);
                                break;
                            case LONG :
                                binder.bind(Key.get(Long.class, n)).toProvider(new LongProvider(p));
                                break;
                            case BOOLEAN :
                                binder.bind(Key.get(Boolean.class, n)).toProvider(new BooleanProvider(p));
                                break;
                            case BYTE :
                                binder.bind(Key.get(Byte.class, n)).toProvider(new ByteProvider(p));
                                break;
                            case CHARACTER :
                                binder.bind(Key.get(Character.class, n)).toProvider(new CharacterProvider(p));
                                break;
                            case DOUBLE :
                                binder.bind(Key.get(Double.class, n)).toProvider(new DoubleProvider(p));
                                break;
                            case FLOAT :
                                binder.bind(Key.get(Float.class, n)).toProvider(new FloatProvider(p));
                                break;
                            case SHORT :
                                binder.bind(Key.get(Short.class, n)).toProvider(new ShortProvider(p));
                                break;
                            case BIG_DECIMAL :
                                binder.bind(Key.get(BigDecimal.class, n)).toProvider(new BigDecimalProvider(p));
                                break;
                            case BIG_INTEGER :
                                binder.bind(Key.get(BigInteger.class, n)).toProvider(new BigIntegerProvider(p));
                                break;
                        }
                    }
                }
                for (String namespace : knownNamespaces) {
                    Settings s = settings.get(namespace);
                    bind(Settings.class).annotatedWith(new NamespaceImpl(namespace)).toInstance(s);
                    for (String key : s) {
                        Provider<String> p = new PropertyProvider(key, Providers.of(s));
                        Value n = new ValueImpl(key, namespace);
                        for (SettingsBindings type : settingsBindings) {
                            switch(type) {
                            case INT :
                                binder.bind(Key.get(Integer.class, n)).toProvider(new IntProvider(p));
                                break;
                            case STRING :
                                binder.bind(Key.get(String.class, n)).toProvider(p);
                                break;
                            case LONG :
                                binder.bind(Key.get(Long.class, n)).toProvider(new LongProvider(p));
                                break;
                            case BOOLEAN :
                                binder.bind(Key.get(Boolean.class, n)).toProvider(new BooleanProvider(p));
                                break;
                            case BYTE :
                                binder.bind(Key.get(Byte.class, n)).toProvider(new ByteProvider(p));
                                break;
                            case CHARACTER :
                                binder.bind(Key.get(Character.class, n)).toProvider(new CharacterProvider(p));
                                break;
                            case DOUBLE :
                                binder.bind(Key.get(Double.class, n)).toProvider(new DoubleProvider(p));
                                break;
                            case FLOAT :
                                binder.bind(Key.get(Float.class, n)).toProvider(new FloatProvider(p));
                                break;
                            case SHORT :
                                binder.bind(Key.get(Short.class, n)).toProvider(new ShortProvider(p));
                                break;
                            case BIG_DECIMAL :
                                binder.bind(Key.get(BigDecimal.class, n)).toProvider(new BigDecimalProvider(p));
                                break;
                            case BIG_INTEGER :
                                binder.bind(Key.get(BigInteger.class, n)).toProvider(new BigIntegerProvider(p));
                                break;                                
                            }   
                        }
                    }
                }
                bind(Settings.class).toProvider(namespacedSettings);
                //Provide a binding to 
                bind(MutableSettings.class).toProvider(new MutableSettingsProvider(namespacedSettings, currentType));
                //A hack, but it works

                boolean isUsingNamespaces = knownNamespaces.size() > 1 ||
                        (knownNamespaces.size() == 1 && !Namespace.DEFAULT.equals(knownNamespaces.iterator().next()));

                if (isUsingNamespaces) {
                    binder.bindListener(Matchers.any(), new ProvisionListenerImpl());
                }
            } catch (Exception ioe) {
                throw new ConfigurationError(ioe);
            }
        }

        private class ProvisionListenerImpl implements ProvisionListener {

            public ProvisionListenerImpl() {
            }

            @Override
            public <T> void onProvision(ProvisionInvocation<T> provision) {

                TypeLiteral<?> old = currentType.get();
                prevType.set(old);
                try (QuietAutoCloseable pc = prevType.set(currentType.get())) {
                    try (QuietAutoCloseable ac = currentType.set(provision.getBinding().getKey().getTypeLiteral())) {
                        T obj = provision.provision();
                    }
                }
            }
        }
    }

    static void log(String s) {
        if (Boolean.getBoolean(Dependencies.class.getName() + ".log")) {
            System.out.println(s);
        }
    }

    private static class MutableSettingsProvider implements Provider<MutableSettings> {

        private final Provider<Settings> namespaced;
        private final ProtectedThreadLocal<?> injectingInto;

        public MutableSettingsProvider(Provider<Settings> namespaced, ProtectedThreadLocal<?> injectingInto) {
            this.namespaced = namespaced;
            this.injectingInto = injectingInto;
        }

        @Override
        public MutableSettings get() {
            Settings result = namespaced.get();
            if (result instanceof MutableSettings) {
                return (MutableSettings) result;
            } else {
                try {
                    new Exception(injectingInto.get() + " is requesting MutableSettings, "
                            + "but none was bound.  Creating ephemeral settings, "
                            + "but probably nothing but this object will see "
                            + "the contents.").printStackTrace();
                    return new SettingsBuilder().add(result).buildMutableSettings();
                } catch (IOException ex) {
                    return Exceptions.chuck(ex);
                }
            }
        }
    }
    private static final Pattern pat = Pattern.compile("(.*)\\..*?");

    private static class NamespacedSettingsProvider implements Provider<Settings> {

        private final Dependencies deps;

        @Inject
        public NamespacedSettingsProvider(Dependencies deps) {
            this.deps = deps;
        }

        @Override
        public Settings get() {
            TypeLiteral<?> t = deps.prevType.get();
            String namespace = Namespace.DEFAULT;
            if (t != null) {
                Class<?> type = t.getRawType();
                Namespace ns = type.getAnnotation(Namespace.class);
                if (ns == null) {
                    Package pkg = type.getPackage();
                    if (pkg != null) {
                        do {
                            ns = pkg.getAnnotation(Namespace.class);
                            if (ns == null) {
                                String nm = pkg.getName();
                                java.util.regex.Matcher m = pat.matcher(nm);
                                if (!m.find()) {
                                    break;
                                } else {
                                    pkg = Package.getPackage(m.group(1));
                                    if (pkg == null || pkg.getName().equals("")) {
                                        break;
                                    }
                                }
                            }
                        } while (ns == null);
                    }
                }
                if (ns != null) {
                    namespace = ns.value();
                }
            }
            log("INJECTING INTO " + t + " WITH NAMESPACE " + namespace);
            Settings s = deps.settings.get(namespace);
            return s;
        }
    }

    public static Set<String> loadNamespaceListsFromClasspath() throws IOException {
        Set<String> all = new HashSet<>();
        String listPathOnClasspath = Defaults.DEFAULT_PATH + "namespaces.list";
        InputStream[] streams = Streams.locate(listPathOnClasspath);
        if (streams != null) {
            for (InputStream in : streams) {
                try {
                    Reader reader = new InputStreamReader(in);
                    NamespaceAnnotationProcessor.readNamepaces(reader, all);
                } finally {
                    in.close();
                }
            }
            log("Loaded namespace files: " + all);
        } else {
            log("No input streams for namespaces " + all + " - no classpath files " + listPathOnClasspath);
        }
        return all;
    }

    private static class ByteProvider implements Provider<Byte> {

        private final Provider<String> p;

        ByteProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Byte get() {
            String s = p.get();
            return s == null ? null : Byte.parseByte(s);
        }
    }

    private static class FloatProvider implements Provider<Float> {

        private final Provider<String> p;

        FloatProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Float get() {
            String s = p.get();
            return s == null ? null : Float.parseFloat(s);
        }
    }

    private static class DoubleProvider implements Provider<Double> {

        private final Provider<String> p;

        DoubleProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Double get() {
            String s = p.get();
            return s == null ? null : Double.parseDouble(s);
        }
    }

    private static class ShortProvider implements Provider<Short> {

        private final Provider<String> p;

        ShortProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Short get() {
            String s = p.get();
            return s == null ? null : Short.parseShort(s);
        }
    }

    private static class CharacterProvider implements Provider<Character> {

        private final Provider<String> p;

        CharacterProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Character get() {
            String s = p.get();
            return s == null ? null : s.length() == 0 ? 0 : s.charAt(0);
        }
    }

    private static class LongProvider implements Provider<Long> {

        private final Provider<String> p;

        LongProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Long get() {
            String s = p.get();
            return s == null ? null : Long.parseLong(s);
        }
    }

    private static class IntProvider implements Provider<Integer> {

        private final Provider<String> p;

        IntProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Integer get() {
            String s = p.get();
            return s == null ? null : Integer.parseInt(s);
        }
    }

    private static class BooleanProvider implements Provider<Boolean> {

        private final Provider<String> p;

        BooleanProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Boolean get() {
            String s = p.get();
            return s == null ? false : Boolean.valueOf(s);
        }
    }

    private static class PropertyProvider implements Provider<String> {

        private final String key;
        private final Provider<Settings> props;

        PropertyProvider(String key, Provider<Settings> props) {
            this.key = key;
            this.props = props;
        }

        @Override
        public String get() {
            return props.get().getString(key);
        }
    }

    private static class BigDecimalProvider implements Provider<BigDecimal> {

        private final Provider<String> p;

        BigDecimalProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public BigDecimal get() {
            String s = p.get();
            return s == null ? null : new BigDecimal(s);
        }
    }

    private static class BigIntegerProvider implements Provider<BigInteger> {

        private final Provider<String> p;

        BigIntegerProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public BigInteger get() {
            String s = p.get();
            return s == null ? null : new BigInteger(s);
        }
    }
}
