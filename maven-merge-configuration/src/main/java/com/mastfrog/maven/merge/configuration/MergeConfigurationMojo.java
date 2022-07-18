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
package com.mastfrog.maven.merge.configuration;

import com.mastfrog.jarmerge.JarMerge;
import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.Phase;
import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import static java.util.Collections.emptyMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;

/**
 * A Maven plugin which, on good days, merges together all properties files on
 * the classpath whicha live in target/classes/META-INF/settings into a single
 * file in the classes output dir.
 * <p/>
 * Use this when you want to merge multiple JARs using the default namespace for
 * settings into one big JAR without files randomly clobbering each other.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        name = "merge-configuration", threadSafe = true)
public class MergeConfigurationMojo extends AbstractMojo {

    // FOR ANYONE UNDER THE ILLUSION THAT WHAT WE DO IS COMPUTER SCIENCE:
    //
    // The following two unused fields are magical.  Remove them and you get
    // a plugin which contains no mojo.
    @Parameter(defaultValue = "${localRepository}", readonly = true)
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    private java.util.List remoteRepositories;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    // These are magically injected by Maven:
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    /**
     * The main class - if empty or unspecified, any main class from the
     * project's output-jar's manifest will be used, if any.
     */
    @Parameter(property = "mainClass", defaultValue = "")
    private String mainClass;
    /**
     * The main class - if empty or unspecified, the name of the project JAR
     * with <code>-standalone</code> interpolated into it will be used.
     */
    @Parameter(property = "jarName", defaultValue = "")
    private String jarName;
    /**
     * List of comma= or newline-delimited Java-package (dot-delimited)
     * <i>prefixes</i>, which cause any file with that prefix in the JAR to be
     * excluded - for example, com.foo excludes any files from the jar whose
     * path starts with com/foo, com/foozle, com/foo/whatever, and so forth.
     */
    @Parameter(property = "exclude", defaultValue = "")
    private String exclude = "";
    /**
     * List of comma= or newline-delimited folder paths (/-delimited)
     * <i>prefixes</i>, which cause any file with that prefix in the JAR to be
     * excluded - for example, com.foo excludes any files from the jar whose
     * path starts with com/foo, com/foozle, com/foo/whatever, and so forth.
     */
    @Parameter(property = "excludePaths", defaultValue = "")
    private String excludePaths = "";
    /**
     * &0x64; List of comma= or newline-delimited Ant-style file glob-patterns
     * which cause any file with that match the prefix in one of the source JARs
     * to be excluded.
     */
    @Parameter(property = "excludePatterns", defaultValue = "")
    private String excludePatterns = "";

    /**
     * If set to true, do not include POM and related files under
     * META-INF/maven, to reduce resulting JAR size.
     */
    @Parameter(property = "skipMavenMetadata", defaultValue = "true")
    private boolean skipMavenMetadata;
    /**
     * If true, merge properties files under META-INF/, ensuring no date-bearing
     * comments are present (these are frequently generated by build tasks that
     * use Properties.save()).
     */
    @Parameter(property = "normalizeMetaInfPropertiesFiles", defaultValue = "true")
    private boolean normalizeMetaInfPropertiesFiles;
    /**
     * Coalesce all license notices into a single META-INF/LICENSE.txt file..
     */
    @Parameter(property = "skipLicenseFiles", defaultValue = "false")
    private boolean skipLicenseFiles;
    /**
     * Rewrite all properties files in the resulting build, sorting keys and
     * omitting comments, to assit in creating repeatable builds.
     */
    @Parameter(property = "rewritePropertiesFiles", defaultValue = "false")
    @SuppressWarnings("FieldMayBeFinal")
    private boolean rewritePropertiesFiles = false;
    /**
     * Omit `module-info.class` if present in the default package - in a merged
     * JAR, this information is likely inaccurate, and currently there is no way
     * to merge such information.
     */
    @Parameter(property = "omitModuleInfo", defaultValue = "false")
    @SuppressWarnings("FieldMayBeFinal")
    private boolean omitModuleInfo = true;
    /**
     * Set the compression level for the resulting JAR file.
     */
    @Parameter(property = "compression", defaultValue = "9")
    @SuppressWarnings("FieldMayBeFinal")
    private int compressionLevel = 9;
    /**
     * Generate a standard JAR-index file, META-INF/INDEX.LIST - this is off by
     * default, since it is only significantly advantageous for classloading
     * when there are many JARs on the classpath, which is likely not the case
     * if you're building a merged JAR.
     */
    @Parameter(property = "index", defaultValue = "false")
    @SuppressWarnings("FieldMayBeFinal")
    private boolean index = false;
    /**
     * Set all file creation/last-modified/last-accessed dates of all entries in
     * the JAR file to the Unix epoch data, 1/1/1970 00:00:00 GMT to assist in
     * repeatable builds.
     */
    @Parameter(property = "zerodates", defaultValue = "true")
    @SuppressWarnings("FieldMayBeFinal")
    private boolean zerodates = true;

    /**
     * This plugin uses smart-jar-merge under the hood, which can be extended by
     * JarFilter instances registered in META-INF/settings - if you are running
     * with additional filters installed on the classpath, they can be enabled
     * (if disabled by default) here.
     */
    @Parameter(property = "enableFilters", defaultValue = "")
    @SuppressWarnings("FieldMayBeFinal")
    private String enableFilters = "";
    /**
     * This plugin uses smart-jar-merge under the hood, which can be extended by
     * JarFilter instances registered in META-INF/settings - if you are running
     * with additional filters installed on the classpath, they can be disabled
     * (if enabled by default) here.
     */
    @Parameter(property = "disableFilters", defaultValue = "")
    @SuppressWarnings("FieldMayBeFinal")
    private String disableFilters = "";

    @Parameter(property = "omitOptionalDependencies", defaultValue = "false")
    private boolean omitOptionalDependencies;

    @Parameter(property = "extensionProperties")
    private Properties extensionProperties;

    @Parameter(property = "manifestEntries")
    private Properties manifestEntries;

    @Parameter(property = "merge.configuration.verbose", defaultValue = "false")
    private boolean verbose;

    @Parameter(property = "artifactClassifier")
    private String classifier;

    @Parameter(property = "replacePom")
    private boolean replacePom;

    /**
     * @component @required @readonly
     */
    @Component
    private ArtifactFactory artifactFactory;

    // Pending: Bother with manifest *sections*?
    private static final Pattern SIG1 = Pattern.compile("META-INF\\/[^\\/]*\\.SF");
    private static final Pattern SIG2 = Pattern.compile("META-INF\\/[^\\/]*\\.DSA");
    private static final Pattern SIG3 = Pattern.compile("META-INF\\/[^\\/]*\\.RSA");

    private static final String PLEXUS_COMPONENTS_FILE = "META-INF/plexus/components.xml";

    @Component
    private ProjectDependenciesResolver resolver;

    private static final boolean notSigFile(String name) {
        return !SIG1.matcher(name).find() && !SIG2.matcher(name).find() && !SIG3.matcher(name).find();
    }

    private String jarName() {
        if (jarName != null && !jarName.trim().isEmpty()) {
            return jarName;
        }
        Artifact artifact = project.getArtifact();
        File file = artifact.getFile();
        if (file == null) {
            return "combined";
        }
        String nm = file.getName();
        int ix = nm.lastIndexOf('.');
        if (ix > 0 && ix < nm.length() - 1) {
            nm = nm.substring(0, ix);
        }
        if (classifier != null && !classifier.trim().isBlank()) {
            nm += "-" + classifier.trim();
        }
        return nm;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = super.getLog();
        log.info("Merging JAR contents");
        if (repoSession == null) {
            throw new MojoFailureException("RepositorySystemSession is null");
        }
        JarMerge.Builder bldr = JarMerge.builder();
        File theProjectsJar = project.getArtifact().getFile();
        if (theProjectsJar == null || !theProjectsJar.exists()) {
            throw new MojoFailureException("Project JAR artifact does not exist or was not "
                    + "yet created.  maven-merge-configuration needs to run in a phase "
                    + "such as package, *after* the JAR has been created.");
        }
        bldr.addJar(theProjectsJar.toPath());
        Set<Dependency> removed = new HashSet<>();
        try {
            DependencyResolutionResult result
                    = resolver.resolve(new DefaultDependencyResolutionRequest(project, repoSession));
            if (verbose) {
                log.info("FOUND " + result.getDependencies().size() + " dependencies");
            }
            for (Dependency d : result.getDependencies()) {
                if (d.isOptional()) {
                    continue;
                }
                switch (d.getScope()) {
                    case "test":
                    case "provided":
                    case "import":
                        break;
                    case "optional":
                        if (omitOptionalDependencies) {
                            break;
                        }
                    default:
                        if (verbose) {
                            log.info("Include " + d.getArtifact().getGroupId()
                                    + ":" + d.getArtifact().getArtifactId()
                                    + ":" + d.getArtifact().getVersion());
                        }
                        File f = d.getArtifact().getFile();
                        if (f.getName().endsWith(".jar") && f.isFile() && f.canRead()) {
                            removed.add(d);
                            bldr.addJar(f.toPath());
                        }
                }
            }
        } catch (DependencyResolutionException ex) {
            throw new MojoExecutionException("Collecting dependencies failed", ex);
        }

        if (!"none".equals(mainClass)) {
            bldr.withMainClass(mainClass);
        }
        if (excludePaths != null) {
            bldr.excludePaths(excludePaths);
        }
        if (!exclude.isEmpty()) {
            bldr.exclude(exclude);
        }
        if (!excludePatterns.isEmpty()) {
            bldr.excludePattern(excludePatterns);
        }
        if (skipMavenMetadata) {
            bldr.enable("omit-maven-metadata");
        } else {
            bldr.disable("omit-maven-metadata");
        }
        if (normalizeMetaInfPropertiesFiles) {
            bldr.enable("merge-meta-inf-settings");
        } else {
            bldr.disable("merge-meta-inf-settings");
        }
        if (skipLicenseFiles) {
            bldr.enable("merge-license-files");
        } else {
            bldr.disable("merge-license-files");
        }
        if (rewritePropertiesFiles) {
            bldr.enable("rewrite-properties-files");
        } else {
            bldr.disable("rewrite-properties-files");
        }
        if (omitModuleInfo) {
            bldr.enable("omit-module-info");
        } else {
            bldr.disable("omit-module-info");
        }
        if (index) {
            bldr.generateJarIndex();
        }
        if (zerodates) {
            bldr.zeroDates();
        }
        if (!enableFilters.isEmpty()) {
            bldr.enable(enableFilters);
        }
        if (!disableFilters.isEmpty()) {
            bldr.disable(disableFilters);
        }
        bldr.compressionLevel(compressionLevel);
        bldr.noSystemExitOnError();
        bldr.withLoggerFactory(this::newLog);
        bldr.withExtensionProperties(toStringMap(this.extensionProperties));
        bldr.withManifestEntries(toStringMap(this.manifestEntries));

        String jn = jarName();
        if (!jn.isEmpty()) {
            if (!jn.contains("/")) {
                jn = project.getBasedir().toPath().resolve("target").resolve(jn).toString();
            }
        }
        JarMerge jm = bldr.finalJarName(jn);

        // Return value changes after we run it
        boolean overwrite = jm.isOverwrite();

        try {
            jm.run();
            String cl = classifier();

            if (!cl.isEmpty()) {
                Artifact artie = artifactFactory.createArtifactWithClassifier(project.getGroupId(), project.getArtifactId(), project.getVersion(), "jar", cl);
                artie.setFile(jm.outputJar().toFile());
                artie.setResolved(true);
                project.addAttachedArtifact(artie);
            }
            if (overwrite && "".equals(cl) && replacePom && !removed.isEmpty()) {
                PomRewriter rewriter = new PomRewriter(project, removed, artifactFactory, jm.outputJar());
                Path rewritten = rewriter.rewrite();
                if (rewritten != null) {
                    Artifact oldPom = project.getArtifactMap().get("pom");
                    if (oldPom != null) {
                        project.getArtifactMap().remove("pom");
                        project.getArtifacts().remove(oldPom);
                    }
                    Artifact pom = artifactFactory.createBuildArtifact(project.getGroupId(),
                            project.getArtifactId(), project.getVersion(), "pom");
                    pom.setFile(rewritten.toFile());
                    pom.setResolved(true);
                    project.addAttachedArtifact(pom);
                }
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed building merged jar with " + jm, ex);
        }
    }

    private String classifier() {
        if (classifier != null) {
            return classifier.trim();
        }
        return "";
    }

    MergeLog newLog(String name, Phase phase, JarMerge settings) {
        return new MLog(name, phase, settings);
    }

    static Map<String, String> toStringMap(Properties props) {
        if (props == null || props.isEmpty()) {
            return emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>(props.size());
        props.forEach((k, v) -> {
            result.put(k.toString(), v.toString());
        });
        return result;
    }

    class MLog implements MergeLog {

        private final String name;
        private final Phase phase;
        private final Log log;

        MLog(String name, Phase phase, JarMerge settings) {
            this.name = name;
            this.phase = phase;
            log = MergeConfigurationMojo.super.getLog();
        }

        private String fmt(String msg) {
            return phase + ":" + name + ": " + msg;
        }

        private MergeLog logIt(String msg, Consumer<String> logMethod) {
            Arrays.asList(msg.split("\n")).forEach(logMethod);
            return this;
        }

        @Override
        public MergeLog log(String msg) {
            return logIt(msg, log::info);
        }

        @Override
        public MergeLog debug(String msg) {
            return logIt(msg, log::debug);
        }

        @Override
        public MergeLog warn(String msg) {
            return logIt(msg, log::warn);
        }

        @Override
        public MergeLog error(String msg) {
            return logIt(msg, log::error);
        }
    }
}
