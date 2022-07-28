/*
 * The MIT License
 *
 * Copyright 2021 Mastfrog Technologies.
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
package com.mastfrog.jarmerge;

import com.mastfrog.function.TriFunction;
import com.mastfrog.function.threadlocal.ThreadLocalValue;
import com.mastfrog.jarmerge.spi.JarFilter;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.jarmerge.PhaseRunner.PhaseOutput;
import com.mastfrog.jarmerge.spi.ClassNameRewriter;
import com.mastfrog.settings.Settings;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.strings.AlignedText;
import com.mastfrog.util.strings.Strings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Factored out of the original maven-merge-dependencies plugin, this is a
 * standalone application/utility for merging JAR files with an extensible set
 * of filters (installable via META-INF/services) for building fat-JARs that
 * intelligently combine various types of files that may be duplicated across
 * JARs.
 *
 * @author Tim Boudreau
 */
public class JarMerge implements ThrowingRunnable {

    private static final ThreadLocalValue<JarMerge> MERGE = ThreadLocalValue.create();
    static final String DEFAULT_JAR_NAME = "combined.jar";
    public final String jarName;
    public final Set<String> excludePathPrefixes;
    public final Set<JarFilter<?>> filters;
    public final boolean generateIndex;
    final boolean exitOnError;
    final boolean dryRun;
    private final Set<Path> jars;
    final int compressionLevel;
    public final String mainClass;
    public final Set<String> excludePatterns;
    public final boolean zerodates;
    public final boolean verbose;
    public final Map<String, String> manifestEntries;
    public final Map<String, String> extensionProperties;
    private final TriFunction<String, Phase, JarMerge, MergeLog> logFactory;
    private ClassNameRewriter rewriter;

    private JarMerge(String jarName, String excludePaths, String exclude,
            Set<JarFilter<?>> filters,
            boolean generateIndex, Set<Path> jars, boolean exitOnError,
            boolean dryRun, int compressionLevel, String mainClass,
            String excludePatterns, boolean zerodates, boolean verbose,
            TriFunction<String, Phase, JarMerge, MergeLog> logFactory) {
        this(jarName, excludePatterns, exclude, filters, generateIndex, jars,
                exitOnError, dryRun, compressionLevel, mainClass, excludePatterns,
                zerodates, verbose, logFactory, emptyMap(), emptyMap());
    }

    private JarMerge(String jarName, String excludePaths, String exclude,
            Set<JarFilter<?>> filters, boolean generateIndex, Set<Path> jars,
            boolean exitOnError, boolean dryRun, int compressionLevel,
            String mainClass, String excludePatterns, boolean zerodates,
            boolean verbose, TriFunction<String, Phase, JarMerge, MergeLog> logFactory,
            Map<String, String> extensionProperties, Map<String, String> manifestEntries) {
        this.jarName = outputJarName(jarName, jars);
        this.logFactory = logFactory;
        Set<String> prefixen = new HashSet<>();
        prefixen.addAll(toSet(excludePaths, false));
        prefixen.addAll(toSet(exclude, true));
        this.excludePathPrefixes = Collections.unmodifiableSet(prefixen);
        this.excludePatterns = Collections.unmodifiableSet(toSet(excludePatterns, false));
        this.generateIndex = generateIndex;
        this.exitOnError = exitOnError;
        this.dryRun = dryRun;
        this.filters = Collections.unmodifiableSet(filters);
        this.jars = jars;
        this.compressionLevel = compressionLevel;
        this.mainClass = mainClass;
        this.zerodates = zerodates;
        this.verbose = verbose;
        this.manifestEntries = unmodifiableMap(manifestEntries);
        this.extensionProperties = unmodifiableMap(extensionProperties);
    }

    public ClassNameRewriter rewriter() {
        if (rewriter == null) {
            rewriter = ClassNameRewriter.coalesce(filters);
        }
        return rewriter;
    }

    public boolean isOverwrite() {
        return jars.contains(Paths.get(outputJarName(jarName, jars)));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path outputJar() {
        return Paths.get(jarName);
    }

    private static String outputJarName(String specified, Set<Path> jars) {
        if (!jars.isEmpty() && (specified == null || DEFAULT_JAR_NAME.equals(specified))) {
            String firstJar = jars.iterator().next().getFileName().toString();
            int ix = firstJar.lastIndexOf('.');
            if (ix > 0) {
                String sub = firstJar.substring(0, ix);
                String result = sub + "-standalone.jar";
                return jars.iterator().next().getParent().resolve(result).toString();
            }
        }
        if (specified == null || specified.isEmpty()) {
            specified = "combined";
        }
        if (!specified.endsWith(".jar")) {
            specified += ".jar";
        }
        return specified;
    }

    private Set<String> toSet(String commaDelimOrLines, boolean swapDot) {
        if (commaDelimOrLines == null || commaDelimOrLines.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> all = new HashSet<>();
        for (String s : commaDelimOrLines.split("[,\n]")) {
            s = s.trim();
            if (s.endsWith(",")) {
                s = s.substring(0, s.length() - 1).trim();
            }
            if (swapDot) {
                s = s.replace('.', '/');
            }
            if (!s.isEmpty()) {
                all.add(s);
            }
        }
        return all;
    }
    
    /**
     * For use by jar filters which need access to the current JarMerge
     * <i>and run on the same thread they were invoked on</i>.
     * 
     * @return A JarMerge or null
     */
    public static JarMerge get() {
        return MERGE.get();
    }

    @Override
    public void run() throws Exception {
        MERGE.withValueThrowing(this, () -> {
            Path output = Paths.get(jarName);
            if (!dryRun && isOverwrite() && Files.exists(output)) {
                Path copy;
                if (output.getParent() == null) {
                    copy = Paths.get("original-" + output.getFileName());
                } else {
                    copy = output.getParent().resolve("original-" + output.getFileName());
                }
                Files.copy(output, copy, StandardCopyOption.REPLACE_EXISTING);
                jars.remove(output);
                jars.add(copy);
            }
            List<Phase> phases = new ArrayList<>();
            phases.add(Phase.DRY_RUN);
            if (!dryRun) {
                phases.add(Phase.WRITE);
            }
            PhaseOutput prev = null;
            for (Phase mode : phases) {
                prev = run(mode, prev);
            }
        });
    }

    private PhaseRunner.PhaseOutput run(Phase phase, PhaseOutput prevPhaseOut) throws Exception {
        return new PhaseRunner(phase, this, prevPhaseOut, logFactory).run();
    }

    void eachJar(ThrowingBiConsumer<Path, Boolean> c) throws Exception {
        for (Iterator<Path> iterator = jars.iterator(); iterator.hasNext();) {
            c.accept(iterator.next(), !iterator.hasNext());
        }
    }
    
    /**
     * Get the set of installed filters, in the order they will be applied.
     * @return A list of unique filter instances.
     */
    public List<JarFilter> filters() {
        List<JarFilter> result = new ArrayList<>(this.filters);
        Collections.sort(result);
        return result;
    }

    public static void main(String[] args) throws Exception {
        int lastFileIndex = args.length;
        while (lastFileIndex >= 0) {
            // Scan backwards to find the start of the portion of the
            // command-line which is a list of JAR file names -
            // this is necessarily a little fuzzy
            String arg = args[lastFileIndex - 1];
            if (arg.charAt(0) == '-') {
                break;
            }
            if (arg.contains(".") && !arg.contains("/")) {
                break;
            }
            Path pt = Paths.get(arg);
            if (!Files.exists(pt)) {
                break;
            }
            lastFileIndex--;
        }
        String[] subArgs;
        if (lastFileIndex > 0) {
            subArgs = Arrays.copyOf(args, lastFileIndex);
        } else {
            subArgs = new String[0];
        }
        Settings s = Settings.builder()
                .add(ARG_OUTPUT, DEFAULT_JAR_NAME)
                .add(ARG_EXCLUDE, "")
                .add(ARG_EXCLUDEPATHS, "")
                .add(ARG_EXCLUDE_PATTERNS, "")
                .add(ARG_MAIN_CLASS, "")
                .add(ARG_GENERATE_INDEX, false)
                .add(ARG_DISABLED_FILTERS, "")
                .add(ARG_DONT_EXIT, false)
                .add(ARG_DRY_RUN_ONLY, false)
                .add(ARG_COMPRESSION_LEVEL, 9)
                .add(ARG_LIST_FILTERS, false)
                .add(ARG_ZERO_OUT_DATES, false)
                .add(ARG_VERBOSE_LOGGING, false)
                .add(ARG_ENABLED_FILTERS, false)
                .add(ARG_HELP, false)
                .parseCommandLineArguments(subArgs).build();

        boolean reallyExit = !s.getBoolean(ARG_DONT_EXIT);
        if (s.getBoolean(ARG_HELP)) {
            printHelpAndMaybeExit(reallyExit);
        }

        Set<Path> jars = new LinkedHashSet<>();

        boolean failed = false;
        for (int i = lastFileIndex; i < args.length; i++) {
            Path path = Paths.get(args[i]);
            path = path.toAbsolutePath();
            if (!Files.exists(path)) {
                errorExit("Does not exist: " + path, reallyExit);
                failed = true;
            }
            if (Files.isDirectory(path)) {
                errorExit("Is a directory: " + path, reallyExit);
                failed = true;
            }
            if (!Files.isReadable(path)) {
                errorExit("Cannot read: " + path, reallyExit);
            }
            jars.add(path);
        }

        if (failed) {
            return;
        }
        Set<CharSequence> omit = Strings.splitUniqueNoEmpty(',', s.getString(ARG_DISABLED_FILTERS));
        Set<JarFilter<?>> filters = new LinkedHashSet<>();
        Set<CharSequence> include = Strings.splitUniqueNoEmpty(',', s.getString(ARG_ENABLED_FILTERS));

        JarMerge jm = new JarMerge(s.getString(ARG_OUTPUT), s.getString(ARG_EXCLUDEPATHS),
                s.getString(ARG_EXCLUDE),
                filters, s.getBoolean(ARG_GENERATE_INDEX), jars, !s.getBoolean(ARG_DONT_EXIT),
                s.getBoolean(ARG_DRY_RUN_ONLY), s.getInt(ARG_COMPRESSION_LEVEL), s.getString(ARG_MAIN_CLASS),
                s.getString(ARG_EXCLUDE_PATTERNS), s.getBoolean(ARG_ZERO_OUT_DATES),
                s.getBoolean(ARG_VERBOSE_LOGGING), MergeLog::stdout);

        if (s.getBoolean(ARG_LIST_FILTERS)) {
            listFiltersAndMaybeExit("Available Filters", installedJarFilters(), reallyExit);
        }
        if (jars.isEmpty()) {
            errorExit("No jars passed in " + Arrays.toString(args), reallyExit);
            return;
        }
        filters = loadFromClasspath(jm, include, filters, omit);
        if (s.getBoolean(ARG_VERBOSE_LOGGING)) {
            listFiltersAndMaybeExit("Using Filters", filters, false);
        }
        jm.run();
    }
    public static final String ARG_ENABLED_FILTERS = "enable";
    public static final String ARG_VERBOSE_LOGGING = "verbose";
    public static final String ARG_ZERO_OUT_DATES = "zerodates";
    public static final String ARG_LIST_FILTERS = "list";
    public static final String ARG_COMPRESSION_LEVEL = "compress";
    public static final String ARG_DRY_RUN_ONLY = "dryrun";
    public static final String ARG_DONT_EXIT = "noexit";
    public static final String ARG_DISABLED_FILTERS = "disable";
    public static final String ARG_GENERATE_INDEX = "index";
    public static final String ARG_MAIN_CLASS = "mainclass";
    public static final String ARG_EXCLUDE_PATTERNS = "excludepatterns";
    public static final String ARG_EXCLUDEPATHS = "excludepaths";
    public static final String ARG_EXCLUDE = "exclude";
    public static final String ARG_OUTPUT = "output";
    public static final String ARG_HELP = "help";
    private static final String HELP_HEAD
            = "Smart JAR Merge\n"
            + "===============\n\n"
            + "Merge multiple JAR files into one, intelligently combining their contents, and"
            + "\naltering files where necessary (properties files with generated date contents,\n"
            + "archive file timestamps) to assist in creating repeatable builds, where the same\n"
            + "class and resource files in result in *exactly* the same bytes out.\n\n"
            + "In particular, correctly merges the following:\n"
            + " * META-INF/services service registration files\n"
            + " * Maven plexus components XML files\n"
            + " * Acteur META-INF/http/pages.list files\n"
            + " * All .properties files under META-INF are rewritten to remove the timestamp"
            + "\n    comment written by Properties.store()\n"
            + " * Giulius META-INF/settings and namespaces.list files\n"
            + " * License files of various common naming conventions\n"
            + " * Jar signature files, which would be incorrect in a merged JAR, are removed\n"
            + " * Optionally write the archive with all files using the Unix epoch (1/1/1970 00:00:00) as the timestamp\n"
            + " * Remove module-info.class files in the default package which would be incorrect in a merged JAR\n\n"
            + "Nearly all of the above are optional, and can be enabled or disabled using --enable or --disable - see "
            + "\nthe filters list below for possible arguments to pass for those."
            + "\n\nUsage:\n======\n\n  java -jar simple-jar-merge.jar --index --enable omit-module-info,merge-license-files \\"
            + "\n    --disable merge-plexus-components --zerodates --compress 5 --mainclass com.foo.Bar jar1 jar2 jar3"
            + "\n\nCommand-line Switches:\n"
            + "======================\n";

    private static final String HELP_TAIL = "\n"
            + "Filters can be added by implementing com.mastfrog.jarmerge.spi.JarFilter and registering them\n"
            + "in a file named META-INF/services/com.mastfrog.jarmerge.spi.JarFilter that lists the fully-qualified\n"
            + "names of the added classes, one per line, and including those classes on the classpath when running.\n\n"
            + "For programmatic use, e.g. in a Maven or Ant plugin, call JarMerge.builder() and call\nrun() on the "
            + "resulting JarMerge instance.\n\n";
    private static final String[] ALL_ARGS = new String[]{
        ARG_MAIN_CLASS,
        ARG_OUTPUT,
        ARG_ENABLED_FILTERS, ARG_DISABLED_FILTERS,
        ARG_GENERATE_INDEX, ARG_COMPRESSION_LEVEL,
        ARG_ZERO_OUT_DATES,
        ARG_EXCLUDE_PATTERNS, ARG_EXCLUDEPATHS, ARG_EXCLUDE,
        ARG_DRY_RUN_ONLY,
        ARG_LIST_FILTERS, ARG_DONT_EXIT
    };

    private static void printHelpAndMaybeExit(boolean reallyExit) {
        System.err.println(helpText());
        if (reallyExit) {
            System.exit(3);
        }
    }

    public static String helpText() {
        StringBuilder sb = new StringBuilder();
        for (String arg : ALL_ARGS) {
            sb.append("--")
                    .append(arg)
                    .append("\t")
                    .append(handleLongDescriptions(description(arg), 1, 70)).append('\n');
        }
        return HELP_HEAD + "\n" + AlignedText.formatTabbed(sb) + "\n\n"
                + "Built-In Filters (use with --enable or --disable)\n"
                + "=================================================\n\n" + filtersHelp() + "\n"
                + HELP_TAIL;
    }

    private static String description(String arg) {
        switch (arg) {
            case ARG_EXCLUDEPATHS:
                return "Simple prefix-based exclusion - exclude any path names that start with "
                        + "one of the passed (comma or newline-delimited) list of strings";
            case ARG_EXCLUDE:
                return "Simple prefix-based exclusion using Java package semantics - . is replaced"
                        + " with / - excludes any path names that start with "
                        + "one of the passed (comma or newline-delimited) list of strings";
            case ARG_ENABLED_FILTERS:
                return "Enable some filters that are disabled by default - comma-delimited list";
            case ARG_DISABLED_FILTERS:
                return "Disable some filters that are disabled by default - comma-delimited list";
            case ARG_VERBOSE_LOGGING:
                return "Turn on verbose logging of what is being done";
            case ARG_LIST_FILTERS:
                return "List built in filters (and any added to the classpath) and exit unless --noexit is passed";
            case ARG_ZERO_OUT_DATES:
                return "Set all file dates in the resulting archive to zero, to assist in creating reproducible builds.";
            case ARG_COMPRESSION_LEVEL:
                return "Set the JAR compression level 0-9 (0 = no compression)";
            case ARG_DRY_RUN_ONLY:
                return "Don't actually write anything, just simulate what would happen";
            case ARG_DONT_EXIT:
                return "Don't exit (if --help or --list was passed, run normally after printing the requested output)";
            case ARG_GENERATE_INDEX:
                return "Generate a standard JAR index in META-INF/INDEX.LIST";
            case ARG_MAIN_CLASS:
                return "The resulting JAR manifest should list the passed class as the main class (if not,"
                        + " the main class will be that of the first JAR passed on the command line, if any)";
            case ARG_EXCLUDE_PATTERNS:
                return "Exclude files from the resulting JAR file using glob-style paths, e.g. foo/**bar/Whatever*.class";
            case ARG_OUTPUT:
                return "Specify the path to the output JAR (if unspecified, it will be the name of the first JAR"
                        + " passed on the command-line with -standalone interpolated before the file extension)";
            default:
                return "";
        }
    }

    public static void listFiltersAndMaybeExit(String heading, Collection<? extends JarFilter<?>> all, boolean reallyExit) {
        System.err.println(heading);
        System.err.println(filtersHelp(all));
        if (reallyExit) {
            System.exit(2);
        }
    }

    private static String filtersHelp() {
        return filtersHelp(installedJarFilters());
    }

    private static String filtersHelp(Collection<? extends JarFilter<?>> all) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name\tPrecedence\tEnabled\tDescription\n");
        sb.append("----\t----------\t-------\t-----------\n");
        for (JarFilter jf : all) {
            sb.append(jf.name()).append("\t")
                    .append(jf.precedence()).append("\t")
                    .append(jf.isCritical() ? "always" : jf.enabledByDefault() ? "yes" : "no")
                    .append("\t")
                    .append(handleLongDescriptions(jf.description())).append('\n');
        }
        return AlignedText.formatTabbed(sb);
    }

    private static String handleLongDescriptions(String s) {
        return handleLongDescriptions(s, 3);
    }

    private static String handleLongDescriptions(String s, int tabDepth) {
        return handleLongDescriptions(s, tabDepth, 50);
    }

    private static String handleLongDescriptions(String s, int tabDepth, int max) {
        if (s.length() < max) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        String pfx = "\n";
        for (int i = 0; i < tabDepth; i++) {
            pfx += "\t";
        }
        int lineLength = 0;
        boolean any = false;
        for (String word : s.split("\\s+")) {
            if (sb.length() > 0) {
                if (lineLength + word.length() + 1 > max) {
                    sb.append(pfx);
                    any = true;
                    lineLength = 0;
                } else {
                    sb.append(" ");
                    lineLength++;
                }
            }
            sb.append(word);
            lineLength += word.length();
        }
        if (any) {
            sb.append("\n");
        }
        return sb.toString();
    }

    private static void errorExit(String msg, boolean reallyExit) {
        System.err.println(msg);
        if (reallyExit) {
            System.exit(1);
        }
    }

    public static List<JarFilter<?>> installedJarFilters() {
        List<JarFilter<?>> result = new ArrayList<>();
        for (JarFilter<?> f : ServiceLoader.load(JarFilter.class)) {
            result.add(f);
        }
        Collections.sort(result);
        return result;
    }

    public static Set<JarFilter<?>> loadFromClasspath(JarMerge jm,
            Set<? extends CharSequence> includedFilters, Set<JarFilter<?>> finalFilters,
            Set<? extends CharSequence> omittedFilters) {
        Set<String> omitted = new LinkedHashSet<>();
        omittedFilters.forEach(nm -> omitted.add(nm.toString()));
        Set<String> included = new LinkedHashSet<>();
        includedFilters.forEach(nm -> included.add(nm.toString()));
        Set<JarFilter<?>> tempFilters = new LinkedHashSet<>();
        for (JarFilter<?> f : installedJarFilters()) {
            if (!f.isCritical()) {
                if (omitted.contains(f.name())) {
                    continue;
                }
                if (!f.enabledByDefault() && !included.contains(f.name())) {
                    continue;
                }
            }
            JarFilter<?> ff = f.configureInstance(jm);
            tempFilters.add(ff);
        }
        for (Iterator<JarFilter<?>> it = tempFilters.iterator(); it.hasNext();) {
            JarFilter<?> filter = it.next();
            boolean remove = false;
            for (JarFilter<?> other : tempFilters) {
                if (other != filter && other.supersedes(filter)) {
                    remove = true;
                    break;
                }
            }
            if (remove) {
                it.remove();
            }
        }
        finalFilters.addAll(tempFilters);
        return finalFilters;
    }

    static Set<String> knownFilterNames() {
        Set<String> result = new HashSet<>();
        installedJarFilters().forEach(filter -> result.add(filter.name()));
        return result;
    }

    @Override
    public String toString() {
        return "JarMerge{" + "jarName=" + jarName + ", excludePathPrefixes="
                + excludePathPrefixes + ", filters=" + filters + ", generateIndex="
                + generateIndex + ", exitOnError=" + exitOnError + ", dryRun=" + dryRun
                + ", jars=" + jars + ", compressionLevel=" + compressionLevel
                + ", mainClass=" + mainClass + ", excludePatterns=" + excludePatterns
                + ", zerodates=" + zerodates + ", verbose=" + verbose + ", logFactory="
                + logFactory + '}';
    }

    public static class Builder {

        private String mainClass;
        private String exclude = "";
        private String excludePaths = "";
        private String excludePatterns = "";

        private boolean loadFromClasspath = true;
        private boolean index = false;
        private boolean exitOnError = true;
        private boolean dryRun = false;
        private int compressionLevel = 9;
        private boolean zeroDates;
        private boolean verbose;
        private TriFunction<String, Phase, JarMerge, MergeLog> logFactory
                = MergeLog::stdout;

        private final Set<JarFilter<?>> filters = new LinkedHashSet<>();
        private final Set<String> omittedFilters = new HashSet<>();
        private final Set<String> includedFilters = new HashSet<>();
        private final Set<Path> jars = new LinkedHashSet<>();
        private final Map<String, String> extensionProperties = new LinkedHashMap<>();
        private final Map<String, String> manifestEntries = new LinkedHashMap<>();

        private static final Set<String> KNOWN = knownFilterNames();

        public JarMerge build() {
            return finalJarName(null);
        }

        public JarMerge finalJarName(String jarName) {
            if ("".equals(jarName)) {
                jarName = null;
            }
            Set<JarFilter<?>> finalFilters = new HashSet<>(filters);
            JarMerge result = new JarMerge(jarName, excludePaths, exclude, finalFilters,
                    index, jars, exitOnError, dryRun, compressionLevel, mainClass,
                    excludePatterns, zeroDates, verbose, logFactory, extensionProperties, manifestEntries);

            if (loadFromClasspath) {
                loadFromClasspath(result, includedFilters, finalFilters, omittedFilters);
            }
            return result;
        }

        public Builder withLoggerFactory(TriFunction<String, Phase, JarMerge, MergeLog> factory) {
            this.logFactory = notNull("factory", factory);
            return this;
        }

        public Builder enable(String filterName) {
            if (!KNOWN.contains(filterName)) {
                System.err.println("Not a known filter to disable: '" + filterName + "'."
                        + " Known filters: " + KNOWN);
                return this;
            }
            includedFilters.add(filterName);
            return this;
        }

        public Builder verbose() {
            verbose = true;
            return this;
        }

        /**
         * Set all the creation/modification/access dates of JAR entries to the
         * unix epoch, to assist in having repeatable builds where, if the class
         * files are the same, so is the resulting JAR file.
         *
         * @return this
         */
        public Builder zeroDates() {
            zeroDates = true;
            return this;
        }

        public Builder excludePattern(String globPattern) {
            if (excludePatterns.length() == 0) {
                excludePatterns = globPattern;
            } else {
                excludePatterns += "\n" + globPattern;
            }
            return this;
        }

        public Builder compressionLevel(int level) {
            if (level < 0 || level > 9) {
                throw new IllegalArgumentException("Compressionn level should be "
                        + "0-9 but got " + compressionLevel);
            }
            this.compressionLevel = level;
            return this;
        }

        public Builder dryRun() {
            dryRun = true;
            return this;
        }

        public Builder noSystemExitOnError() {
            exitOnError = false;
            return this;
        }

        public Builder addJar(Path jar) {
            jars.add(jar);
            return this;
        }

        public Builder generateJarIndex() {
            index = true;
            return this;
        }

        public Builder disable(String filterName) {
            if (!KNOWN.contains(filterName)) {
                System.err.println("Not a known filter to disable: '" + filterName + "'."
                        + " Known filters: " + KNOWN);
                return this;
            }
            omittedFilters.add(filterName);
            return this;
        }

        public Builder withFilter(JarFilter filter) {
            filters.add(filter);
            return this;
        }

        public Builder dontLoadFromClasspath() {
            loadFromClasspath = false;
            return this;
        }

        public Builder omitMavenMetadata() {
            enable("omit-maven-metadata");
            return this;
        }

        public Builder omitLicenseFiles() {
            enable("omit-license-files");
            return this;
        }

        public Builder dontNormalizeMetaInfPropertiesFiles() {
            disable("merge-meta-inf-settings-properties");
            return this;
        }

        public Builder withMainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        public Builder exclude(String prefix) {
            if (exclude.isEmpty()) {
                exclude = prefix;
            } else {
                exclude = exclude + "\n" + prefix;
            }
            return this;
        }

        public Builder excludePaths(String prefix) {
            if (excludePaths.isEmpty()) {
                excludePaths = prefix;
            } else {
                excludePaths = excludePaths + "\n" + prefix;
            }
            return this;
        }

        public Builder withExtensionProperties(Map<String, String> toStringMap) {
            this.extensionProperties.putAll(toStringMap);
            return this;
        }

        public Builder withManifestEntries(Map<String, String> toStringMap) {
            this.manifestEntries.putAll(toStringMap);
            return this;
        }

        public Builder withExtensionProperty(String k, String v) {
            this.extensionProperties.put(k, v);
            return this;
        }

        public Builder withManifestEntry(String k, String v) {
            this.manifestEntries.put(k, v);
            return this;
        }

    }
}
