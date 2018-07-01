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
package com.mastfrog.settings;

import com.mastfrog.util.Checks;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.Streams;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimerTask;

/**
 * Builder for Settings. Allows multiple sources of settings to be layered
 * together. File and URL types of settings allow for periodic reloading.
 * <p/>
 * Sources for settings are added to the SettingsBuilder in LIFO order - the
 * last one to be added will be the first one to be queried in the resulting
 * Settings. <h2>Namespaces</h2> A "namespace" may be provided, which changes
 * the default files SettingsBuilder looks in, in the case you call
 * <code>addDefaultLocations()</code> or similar.
 *
 * @author Tim Boudreau
 */
public final class SettingsBuilder {

    /**
     * Path within JAR files to look for settings information
     */
    public static final String DEFAULT_PATH = "META-INF/settings/";
    /**
     * The default namespace, "defaults", which is used for settings which do
     * not explicitly have a namespace
     */
    public static final String DEFAULT_NAMESPACE = "defaults";
    /**
     * File extension for settings files <code>.properties</code>
     */
    public static final String DEFAULT_EXTENSION = ".properties";
    /**
     * Prefix used for settings files generated from annotations,
     * <code>generated-</code>
     */
    public static final String GENERATED_PREFIX = "generated-";
    private final List<PropertiesSource> all = new ArrayList<>(7);
    private final Set<String> environmentKeys = new HashSet<>(12);
    private final String namespace;

    public SettingsBuilder() {
        this.namespace = DEFAULT_NAMESPACE;
    }

    /**
     * Create a new settings builder which will read files which match the
     * passed namespace name - i.e. if you pass "foo" and then call
     * <code>addDefaultLocations</code>, you get a settings builder which will
     * look for <code>META-INF/settings/foo.properties</code> and
     * <code>META-INF/settings/generated-foo.properties</code> in all JARs on
     * the classpath.
     *
     * @param namespace The namespace. May not contain whitespace, colons,
     * commas, or path or file delimiter characters
     */
    public SettingsBuilder(String namespace) {
        Checks.notNull("namespace", namespace);
        Checks.mayNotContain("namespace", namespace, ',', '/', '\\', ':', ';');
        this.namespace = namespace;
    }

    /**
     * Create a new settings builder which will read files which match the
     * passed namespace name - i.e. if you pass "foo" and then call
     * <code>addDefaultLocations</code>, you get a settings builder which will
     * look for <code>META-INF/settings/foo.properties</code> and
     * <code>META-INF/settings/generated-foo.properties</code> in all JARs on
     * the classpath.
     *
     * @param namespace The namespace. May not contain whitespace, colons,
     * commas, or path or file delimiter characters
     */
    public static SettingsBuilder forNamespace(String namespace) {
        Checks.notNull("namespace", namespace);
        Checks.notEmpty("namespace", namespace);
        return new SettingsBuilder(namespace);
    }

    public String getNamespace() {
        return namespace;
    }

    private String getGeneratedFilesLocation() {
        return DEFAULT_PATH + GENERATED_PREFIX + namespace + DEFAULT_EXTENSION;
    }

    private String getDefaultLocation() {
        return DEFAULT_PATH + namespace + DEFAULT_EXTENSION;
    }

    public SettingsBuilder add(SettingsBuilder sb) {
        this.all.addAll(sb.all);
        return this;
    }

    public SettingsBuilder add(String key, boolean value) {
        add(key, Boolean.toString(value));
        return this;
    }

    public SettingsBuilder add(String key, int value) {
        add(key, Integer.toString(value));
        return this;
    }

    public SettingsBuilder add(String key, long value) {
        add(key, Long.toString(value));
        return this;
    }

    public SettingsBuilder add(String key, double value) {
        add(key, Double.toString(value));
        return this;
    }

    public SettingsBuilder add(String key, float value) {
        add(key, Float.toString(value));
        return this;
    }

    /**
     * Add any properties file generated from the &#064;Defaults annotation.
     *
     * @return this
     */
    public SettingsBuilder addGeneratedDefaultsFromClasspath() {
        return add(getGeneratedFilesLocation());
    }

    /**
     * Add any properties files on the classpath in the default location
     * (com/mastfrog/defaults.properties or the value of the system property
     * settings.location)
     *
     * @return this
     */
    public SettingsBuilder addDefaultsFromClasspath() {
        return add(getDefaultLocation());
    }

    /**
     * Add system properties
     *
     * @return this
     */
    public SettingsBuilder addSystemProperties() {
        return add(new SystemPropertiesSource());
    }

    /**
     * Look up settings files matching the pattern $NAMESPACE.properties in the
     * process working directory.
     *
     * @return this
     */
    public SettingsBuilder addDefaultsFromProcessWorkingDir() {
        File f = new File(namespace.replace('/', '_').replace('\\', '_') + SettingsBuilder.DEFAULT_EXTENSION);
        if (f.exists()) {
            return add(f);
        } else {
            log("Not adding " + f.getAbsolutePath() + " to settings for "
                    + "namespace " + namespace + " because it does not exist");
            return this;
        }
    }

    /**
     * Add a file with the name of this namespace from the user home. E.g. for
     * namespace foo this will look for a file named
     * <code>~/foo.properties</code>. Note that if the namespace contains / or \
     * these are replaced with _.
     *
     * @return
     */
    public SettingsBuilder addDefaultsFromUserHome() {
        File home = new File(System.getProperty("user.home"));
        File file = new File(home, namespace.replace('/', '_').replace('\\', '_') + DEFAULT_EXTENSION);
        if (file.exists()) {
            return add(file);
        } else {
            log("Not adding " + file + " to settings for "
                    + "namespace " + namespace + " because it does not exist");
            return this;
        }
    }

    public SettingsBuilder addDefaultsFromEtc() {
        File home = new File("/opt/local/etc");
        if (!home.exists()) {
            home = new File("/etc");
        }
        if (home.exists()) {
            File file = new File(home, namespace.replace('/', '_').replace('\\', '_') + DEFAULT_EXTENSION);
            if (file.exists()) {
                return add(file);
            } else {
                log("Not adding " + file + " to settings for "
                        + "namespace " + namespace + " because it does not exist");
            }
        }
        return this;
    }

    /**
     * Add environment variables. If you wish to restrict the set of environment
     * variables used, make and calls to
     * <code>restrictEnvironmentProperties()</code> before calling this method.
     *
     * @return this
     */
    public SettingsBuilder addEnv() {
        return add(new FixedPropertiesSource(new EnvironmentProperties(environmentKeys)));
    }

    int sourceCount() {
        return all.size();
    }

    /**
     * Add a single key and value
     *
     * @param key
     * @param value
     * @return
     */
    public SettingsBuilder add(String key, String value) {
        // Avoid adding lots of single-property properties instances
        if (!all.isEmpty()) {
            PropertiesSource last = all.get(all.size() - 1);
            if (last instanceof FixedPropertiesSource) {
                FixedPropertiesSource fp = (FixedPropertiesSource) last;
                if (fp.properties.getClass() == Properties.class) {
                    if (!fp.properties.containsKey(key)) {
                        fp.properties.setProperty(key, value);
                        return this;
                    }
                }
            }
        }
        Properties props = new Properties();
        props.setProperty(key, value);
        return add(props);
    }

    /**
     * Add a properties file at a remote URL, for supplying remote configuration
     *
     * @param url The url
     * @param timeout The timeout for reloading
     * @return this
     */
    public SettingsBuilder add(URL url, RefreshInterval timeout) {
        all.add(new UrlPropertiesSource(url, timeout));
        return this;
    }

    /**
     * Add a properties file at a remote URL, for supplying remote configuration
     *
     * @param url The url
     * @return this
     */
    public SettingsBuilder add(URL url) {
        all.add(new UrlPropertiesSource(url));
        return this;
    }

    /**
     * Create a settings builder which will provide settings from the following
     * locations, last-first ($NAMESPACE is whatever namespace name was passed
     * to the constructor): <ol> <li>Environment variables</li> <li>System
     * properties</li> <li>All files named
     * /com/mastfrog/$NAMESPACE-defaults.properties on the classpath
     * (annotation-generated files in JARs); precedence is determined by
     * classpath order</li> <li>All files named
     * /com/mastfrog/$NAMESPACE.properties (hand-written files in JARs);
     * precedence is determined by classpath order</li> <li>Any file named
     * $NAMESPACE.properties in the user home dir</li> <li>Any file named
     * $NAMESPACE.properties in the process's working directory</li> </ol>
     *
     * @return A settings builder
     */
    public SettingsBuilder addDefaultLocations() {
        return addEnv()
                .addSystemProperties()
                .addFilesystemAndClasspathLocations();
    }

    public SettingsBuilder addFilesystemAndClasspathLocations() {
        return addGeneratedDefaultsFromClasspath()
                .addDefaultsFromClasspath()
                .addDefaultsFromEtc()
                .addDefaultsFromUserHome()
                .addDefaultsFromProcessWorkingDir();
    }

    public SettingsBuilder restrictEnvironmentProperties(String... props) {
        this.environmentKeys.addAll(Arrays.asList(props));
        return this;
    }

    /**
     * Add system properties, the default locations for this SettingsBuilder's
     * namespace (e.g.
     * <code>/etc/$NAMESPACE.properties, $HOME/$NAMESPACE.properties, $PWD/$NAMESPACE.properties</code>
     * and so forth), then layer the passed command-line arguments on top of
     * that, further overridden by environment variables.
     *
     * @param args The command line arguments
     * @return This SettingsBuilder
     */
    public SettingsBuilder addDefaultLocationsAndParseArgs(String... args) {
        return addDefaultLocationsAndParseArgs(Collections.<Character, String>emptyMap(), args);
    }

    /**
     * Add system properties, the default locations for this SettingsBuilder's
     * namespace (e.g.
     * <code>/etc/$NAMESPACE.properties, $HOME/$NAMESPACE.properties, $PWD/$NAMESPACE.properties</code>
     * and so forth), then layer the passed command-line arguments on top of
     * that, further overridden by environment variables.
     *
     * @param expansions Optional mapping of character to settings-string
     * extensions, e.g. translating "-f" to "--foo" before procssing command
     * line arguments.
     *
     * @param args The command line arguments
     * @return This SettingsBuilder
     */
    public SettingsBuilder addDefaultLocationsAndParseArgs(Map<Character, String> expansions, String... args) {
        expansions = expansions == null ? Collections.<Character, String>emptyMap() : expansions;
        return addSystemProperties().addFilesystemAndClasspathLocations()
                .parseCommandLineArguments(expansions, args).addEnv();
    }

    public SettingsBuilder addLocation(File directory) {
        if (directory.exists() && !directory.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        File gen = new File(directory, GENERATED_PREFIX + namespace + DEFAULT_EXTENSION);
        add(gen);
        File user = new File(directory, namespace + DEFAULT_EXTENSION);
        return add(user);
    }

    public static SettingsBuilder create() {
        return createWithDefaults(DEFAULT_NAMESPACE);
    }

    public static SettingsBuilder createWithDefaults(String namespace) {
        Checks.notNull("namespace", namespace);
        return new SettingsBuilder(namespace).addDefaultLocations();
    }

    /**
     * Create a settings builder which will provide settings from the following
     * locations, last-first: <ol> <li>System properties</li> <li>All files
     * named /com/mastfrog/generated-defaults.properties on the classpath
     * (annotation-generated files in JARs); precedence is determined by
     * classpath order</li> <li>All files named
     * /com/mastfrog/defaults.properties (hand-written files in JARs);
     * precedence is determined by classpath order</li> <li>Any file named
     * defaults.properties in the user home dir</li> <li>Any file named
     * defaults.properties in the process's working directory</li> </ol>
     *
     * @return A settings builder
     */
    public static SettingsBuilder createDefault() {
        SettingsBuilder b = new SettingsBuilder()
                //                .addEnv()
                .addSystemProperties()
                .addGeneratedDefaultsFromClasspath()
                .addDefaultsFromClasspath()
                .addDefaultsFromEtc()
                .addDefaultsFromUserHome()
                .addDefaultsFromProcessWorkingDir();
        return b;
    }

    /**
     * Add a location on the classpath to load from
     *
     * @param location
     * @return
     */
    public SettingsBuilder add(String location) {
        if (isLog()) {
            log("Add cp " + location);
        }
        InputStream[] streams = Streams.locate(location);
        if (streams != null) {
            for (InputStream in : streams) {
                add(in);
            }
        }
        return this;
    }

    /**
     * Add a static properties object
     *
     * @param properties
     * @return
     */
    public SettingsBuilder add(Properties properties) {
        all.add(new FixedPropertiesSource(properties));
        return this;
    }

    /**
     * Add a properties source which may or may not refresh on a timeout. If its
     * timeout is non-zero, it will automatically have its properties re-gotten
     * every interval.
     *
     * @param src
     * @return
     */
    public SettingsBuilder add(PropertiesSource src) {
        all.add(src);
        return this;
    }

    /**
     * Add an properties to be loaded from an input stream
     *
     * @param in
     * @return
     */
    public SettingsBuilder add(InputStream in) {
        if (in != null) {
            add(new InputStreamSource(in));
        }
        return this;
    }

    /**
     * Add a properties file. The file need not exist, but it helps if it does.
     *
     * @param file
     * @return
     */
    public SettingsBuilder add(File file) {
        if (isLog()) {
            log("Add " + file);
        }
        add(new FileSource(file));
        return this;
    }

    /**
     * Add a properties file. The file need not exist, but it helps if it does.
     *
     * @param file
     * @param reloadInterval A timeout. If the file did not exist at the
     * previous load, it will still be checked for again subsequently
     * @return
     */
    public SettingsBuilder add(File file, RefreshInterval reloadInterval) {
        add(new FileSource(file, reloadInterval));
        return this;
    }

    public SettingsBuilder add(Settings settings) {
        add(new SettingsSource(settings));
        return this;
    }

    private boolean isLog() {
        return Boolean.getBoolean(SettingsBuilder.class.getName() + ".log");
    }

    @SuppressWarnings("NP_ALWAYS_NULL") //WTF! Findbugs thinks System.out might be null
    private void log(String s) {
        if (isLog()) {
            System.out.println(s);
        }
    }

    private static class ShutdownRefreshTasks implements Runnable {

        Set<Bridge> bridges = new HashSet<>();

        @Override
        public void run() {
            for (Iterator<Bridge> it = bridges.iterator(); it.hasNext();) {
                Bridge b = it.next();
                try {
                    b.cancel();
                } finally {
                    it.remove();
                }
            }
        }
    }

    private final ShutdownRefreshTasks shutdownRunnable = new ShutdownRefreshTasks();

    public final Runnable onShutdownRunnable() {
        return shutdownRunnable;
    }

    public Settings build() throws IOException {
        List<Settings> settings = new LinkedList<>();
        List<PropertiesSource> all = new LinkedList<>(this.all);
        Collections.reverse(all);
        Set<Bridge> bridges = new HashSet<>();
        log("BUILDING SETTINGS FOR NAMESPACE " + this.namespace + " FROM:");
        for (Iterator<PropertiesSource> it = all.iterator(); it.hasNext();) {
            PropertiesSource src = it.next();
            it.remove();
            if (isLog()) {
                log("  " + src);
            }
            if (src instanceof SettingsSource) {
                settings.add(((SettingsSource) src).settings);
            } else {
                PropertiesSettings s = new PropertiesSettings(src + "");
                Bridge bridge = new Bridge(src, s);
                shutdownRunnable.bridges.add(bridge);
                bridge.go();
                bridges.add(bridge);
                src.interval.add(bridge);
                settings.add(s);
            }
        }
        LayeredSettings result = new LayeredSettings(namespace, Collections.unmodifiableList(settings));
        //use a weak reference to ensure refresh stops when
        //all references to the settings have been garbage collected
        Reference<LayeredSettings> ref = new WeakReference<>(result);
        for (Bridge b : bridges) {
            b.ref = ref;
        }
        return result;
    }

    /**
     * Parse command line arguments ala <code>--foo</code> translating to a
     * setting of foo=true or <code>--foo 23</code> translating to a setting of
     * foo=23.
     *
     * @param args
     * @return
     */
    public SettingsBuilder parseCommandLineArguments(String... args) {
        return parseCommandLineArguments(Collections.<Character, String>emptyMap(), args);
    }

    /**
     * Parse command line arguments ala <code>--foo</code> translating to a
     * setting of foo=true or <code>--foo 23</code> translating to a setting of
     * foo=23.
     * <p/>
     * The passed map can map individual characters - i.e. short arguments such
     * as making -f equivalent to --foo, or -fb equivalent to --foo --bar
     *
     * @param args a mapping of short to long args
     * @return this
     */
    public SettingsBuilder parseCommandLineArguments(Map<Character, String> mapping, String... args) {
        if (args == null || args.length == 0) {
            return this;
        }
        String last = null;
        Properties p = new Properties();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.length() > 2 && arg.charAt(2) != '-') {
                if (last != null) {
                    p.setProperty(last, "true");
                }
                last = arg.substring(2);
            } else if (arg.length() >= 2 && arg.charAt(0) == '-' && arg.charAt(1) != '-') {
                String sub = arg.substring(1);
                for (char c : sub.toCharArray()) {
                    String val = mapping.get(c);
                    if (val != null) {
                        if (last != null) {
                            p.setProperty(last, "true");
                        }
                        last = val;
                    } else {
                        throw new ConfigurationError("Unknown short arg " + c + " - known args are " + mapping.keySet());
                    }
                }
            } else {
                if (last != null) {
                    p.setProperty(last, arg);
                    last = null;
                } else {
                    throw new ConfigurationError("Dangling argument " + arg);
                }
            }
        }
        if (last != null) {
            p.setProperty(last, "true");
        }
        return p.isEmpty() ? this : add(p);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("SettingsBuilder").append('[');
        for (PropertiesSource src : all) {
            sb.append('\n').append(src).append('\n');
        }
        return sb.toString();
    }

    /**
     * Create a Settings which has a mutable, ephemeral layer which overrides
     * the rest
     *
     * @return A mutable settings object
     * @throws IOException If an error occurs loading any of the settings
     */
    public MutableSettings buildMutableSettings() throws IOException {
        return new WritableSettings(namespace, build());
    }

    /**
     * Assign the passed key to the path to a file or folder relative to the JAR
     * the passed class lives in, optionally not setting it if another value is
     * already set for it.
     * <p/>
     * This is useful to, for example, automatically locate a folder of html
     * files relative to the project during development. If it discovers that
     * the folder is named "target" or "build" it will go up a level.
     * <p/>
     * Note: If called from inside an application server, all bets are off as to
     * where this looks - that varies wildly by vendor.
     *
     * @param key The settings key that should map to the file path
     * @param jarClass A class whose JAR the path is relative to
     * @param relPath A relative path
     * @param onlyIfNotPresent If true and if a setting for <code>key</code>
     * exists, do nothing
     * @return this
     * @throws URISyntaxException
     * @throws IOException
     */
    public SettingsBuilder addFolderRelativeToJAR(String key, Class<?> jarClass, String relPath, boolean onlyIfNotPresent) throws URISyntaxException, IOException {
        // Get the location this JAR is in on disk, to set up paths relative to it
        ProtectionDomain protectionDomain = jarClass.getProtectionDomain();
        URL location = protectionDomain.getCodeSource().getLocation();

        if (onlyIfNotPresent) {
            String explicitDir = build().getString(key);
            if (explicitDir != null) {
                return this;
            }
        }
        // See if an explicitly provided assets folder was passed to us;  if
        // not we will look for an "assets" folder belonging to this application
        File codebaseDir = new File(location.toURI()).getParentFile();
        if ("target".equals(codebaseDir.getName()) || "build".equals(codebaseDir.getName())) {
            codebaseDir = codebaseDir.getParentFile();
        }
        File f = new File(codebaseDir, relPath);
        return add(key, f.getPath());
    }

    private static class Bridge extends TimerTask implements Runnable {

        private final PropertiesSource src;
        private final PropertiesContainer container;
        private Reference<LayeredSettings> ref;

        Bridge(PropertiesSource src, PropertiesContainer container) {
            this.src = src;
            this.container = container;
        }

        void go() throws IOException {
            container.setDelegate(src.getProperties());
        }

        @Override
        public void run() {
            try {
                if (ref != null && ref.get() == null) {
                    cancel();
                    return;
                }
                go();
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
        }
    }

    /**
     * Source for properties which may be reread on a timer
     */
    public static abstract class PropertiesSource {

        private final com.mastfrog.settings.RefreshInterval interval;

        protected PropertiesSource() {
            this(RefreshInterval.NONE);
        }

        protected PropertiesSource(RefreshInterval interval) {
            this.interval = interval;
        }

        public abstract Properties getProperties() throws IOException;

        public final RefreshInterval getPollInterval() {
            return interval;
        }
    }

    private static final class FixedPropertiesSource extends PropertiesSource {

        final Properties properties;

        FixedPropertiesSource(Properties properties) {
            this.properties = properties;
        }

        @Override
        public Properties getProperties() throws IOException {
            return properties;
        }

        @Override
        public String toString() {
            return "FIXED: " + properties;
        }
    }

    private static final class InputStreamSource extends PropertiesSource {

        private final InputStream in;
        private volatile boolean done;
        private Properties result;

        InputStreamSource(InputStream in) {
            this.in = in;
        }

        @Override
        public Properties getProperties() throws IOException {
            if (done) {
                return this.result;
            }
            Properties result = new Properties();
            try {
                result.load(in);
            } finally {
                done = true;
                in.close();
            }
            return this.result = result;
        }

        @Override
        public String toString() {
            return in + "";
        }
    }

    private static final class FileSource extends PropertiesSource {

        private final File file;

        FileSource(File file) {
            this(file, SettingsRefreshInterval.FILES);
        }

        FileSource(File file, RefreshInterval interval) {
            super(interval);
            this.file = file;
        }

        @Override
        public Properties getProperties() throws IOException {
            Properties props = new Properties();
            if (file.exists()) {
                try (InputStream in = new FileInputStream(file)) {
                    props.load(in);
                }
            }
            return props;
        }

        @Override
        public String toString() {
            return "File: " + file.getAbsolutePath();
        }
    }

    private static final class SettingsSource extends PropertiesSource {

        private final Settings settings;

        SettingsSource(Settings settings) {
            this.settings = settings;
        }

        @Override
        public Properties getProperties() throws IOException {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public String toString() {
            return "Settings " + settings;
        }
    }

    private static final class SystemPropertiesSource extends PropertiesSource {

        SystemPropertiesSource() {
            super(SettingsRefreshInterval.SYSTEM_PROPERTIES);
        }

        @Override
        public Properties getProperties() throws IOException {
            return System.getProperties();
        }

        @Override
        public String toString() {
            return "System Properties";
        }
    }

    private static final class UrlPropertiesSource extends PropertiesSource {

        private final URL url;
        private long last = 0;
        private String etag;
        private final Properties lastProperties = new Properties();

        UrlPropertiesSource(URL url) {
            this(url, SettingsRefreshInterval.URLS);
        }

        UrlPropertiesSource(URL url, RefreshInterval timeout) {
            super(timeout);
            this.url = url;
        }

        @Override
        public Properties getProperties() throws IOException {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setIfModifiedSince(last);
            connection.setConnectTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            if (etag != null) {
                connection.setRequestProperty("If-None-Match", etag);
            }
            connection.connect();
            int code = connection.getResponseCode();
            if (code == 200) {
                last = connection.getLastModified();
                try (InputStream in = connection.getInputStream()) {
                    Properties p = new Properties();
                    p.load(in);
                    synchronized (lastProperties) {
                        lastProperties.clear();
                        lastProperties.putAll(p);
                    }
                }
            }
            return lastProperties;
        }

        @Override
        public String toString() {
            return "URL: " + url;
        }
    }
}
