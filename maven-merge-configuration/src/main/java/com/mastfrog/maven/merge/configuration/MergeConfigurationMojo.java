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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import static com.google.common.base.Charsets.UTF_8;
import static com.mastfrog.util.fileformat.PropertiesFileUtils.printLines;
import static com.mastfrog.util.fileformat.PropertiesFileUtils.savePropertiesFile;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import jdk.internal.joptsimple.internal.Strings;
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        name = "merge-configuration", threadSafe = false)
public class MergeConfigurationMojo extends AbstractMojo {

    // FOR ANYONE UNDER THE ILLUSION THAT WHAT WE DO IS COMPUTER SCIENCE:
    //
    // The following two unused fields are magical.  Remove them and you get
    // a plugin which contains no mojo.
    @Parameter(defaultValue = "${localRepository}")
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    private java.util.List remoteRepositories;
    @Parameter(defaultValue = "${repositorySystemSession}")
    private RepositorySystemSession repoSession;
    @Parameter(property = "mainClass", defaultValue = "none")
    private String mainClass;
    @Parameter(property = "jarName", defaultValue = "none")
    private String jarName;
    @Parameter(property = "exclude", defaultValue = "")
    private String exclude = "";
    @Parameter(property = "excludePaths", defaultValue = "")
    private String excludePaths = "";

    private static final Pattern PAT = Pattern.compile("META-INF\\/settings\\/[^\\/]*\\.properties");
    private static final Pattern SERVICES = Pattern.compile("META-INF\\/services\\/\\S[^\\/]*\\.*");
    private static final Pattern REGISTRATIONS = Pattern.compile("META-INF\\/.*?\\/.*\\.registrations$");
    @Parameter(property = "skipMavenMetadata", defaultValue = "true")
    private boolean skipMavenMetadata = true;
    @Parameter(property = "normalizeMetaInfPropertiesFiles", defaultValue = "true")
    private boolean normalizeMetaInfPropertiesFiles = true;
    @Parameter(property = "skipLicenseFiles", defaultValue = "false")
    private boolean skipLicenseFiles = false;

    private static final Pattern SIG1 = Pattern.compile("META-INF\\/[^\\/]*\\.SF");
    private static final Pattern SIG2 = Pattern.compile("META-INF\\/[^\\/]*\\.DSA");
    private static final Pattern SIG3 = Pattern.compile("META-INF\\/[^\\/]*\\.RSA");

    private static final String PLEXUS_COMPONENTS_FILE = "META-INF/plexus/components.xml";

    @Component
    private ProjectDependenciesResolver resolver;
    @Component
    MavenProject project;

    private static final boolean notSigFile(String name) {
        return !SIG1.matcher(name).find() && !SIG2.matcher(name).find() && !SIG3.matcher(name).find();
    }

    private final boolean shouldSkip(String name) {
        boolean result = "META-INF/MANIFEST.MF".equals(name)
                || "META-INF/".equals(name)
                || "META-INF/INDEX.LIST".equals(name)
                || "META-INF/DEPENDENCIES".equals(name)
                || (skipMavenMetadata && name.startsWith("META-INF/maven"));

        if (!result && skipLicenseFiles && name.startsWith("META-INF")) {
            result = name.toLowerCase().contains("license");
        }
        if (result) {
            getLog().debug("OMIT " + name);
        }
        return result;
    }

    private boolean rewritablePropertiesFile(String name) {
        return normalizeMetaInfPropertiesFiles && name.endsWith(".properties") && name.startsWith("META-INF");
    }

    private List<String> readLines(InputStream in) throws IOException {
        List<String> result = new LinkedList<>();
        InputStreamReader isr = new InputStreamReader(in);
        BufferedReader br = new BufferedReader(isr);
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                result.add(line);
            }
        }
        return result;
    }

    private static int copy(final InputStream in, final OutputStream out)
            throws IOException {
        final byte[] buffer = new byte[4096];
        int bytesCopied = 0;
        for (;;) {
            int byteCount = in.read(buffer, 0, buffer.length);
            if (byteCount <= 0) {
                break;
            } else {
                out.write(buffer, 0, byteCount);
                bytesCopied += byteCount;
            }
        }
        return bytesCopied;
    }

    private String strip(String name) {
        int ix = name.lastIndexOf(".");
        if (ix >= 0 && ix != name.length() - 1) {
            name = name.substring(ix + 1);
        }
        return name;
    }

    private Set<String> excludes;

    private boolean isExcluded(String name) {
        if (excludes == null) {
            excludes = new HashSet<>();
            for (String ex : this.exclude.split("[,\\s]")) {
                ex = ex.trim();
                ex = ex.replace('.', '/');
                if (!ex.isEmpty()) {
                    excludes.add(ex);
                }
            }
            for (String ex : this.excludePaths.split("[,\\s]")) {
                ex = ex.trim();
                if (!ex.isEmpty()) {
                    excludes.add(ex);
                }
            }
        }
        for (String s : excludes) {
            if (!s.isEmpty() && name.startsWith(s)) {
                getLog().debug("EXCLUDE " + name);
                return true;
            }
        }
        // Special handling for Graal - native-image goes insane if it finds itself
        // among its classes to compile - there's no use-case I can see for allowing
        // bundling anything but its annotations
        if (name.startsWith("com/oracle/svm") && !name.startsWith("com/oracle/svm/core/annotate")) {
            return true;
        }
        return false;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // XXX a LOT of duplicate code here
        Log log = super.getLog();
        log.info("Merging JAR contents");
        if (repoSession == null) {
            throw new MojoFailureException("RepositorySystemSession is null");
        }
        List<File> jars = new ArrayList<>();
        try {
            DependencyResolutionResult result
                    = resolver.resolve(new DefaultDependencyResolutionRequest(project, repoSession));
            log.info("FOUND " + result.getDependencies().size() + " dependencies");
            for (Dependency d : result.getDependencies()) {
                if (d.isOptional()) {
                    continue;
                }
                switch (d.getScope()) {
                    case "test":
                    case "provided":
                        break;
                    default:
                        log.debug("Include " + d.getArtifact().getGroupId()
                                + ":" + d.getArtifact().getArtifactId()
                                + ":" + d.getArtifact().getVersion());
                        File f = d.getArtifact().getFile();
                        if (f.getName().endsWith(".jar") && f.isFile() && f.canRead()) {
                            jars.add(f);
                        }
                }
            }
        } catch (DependencyResolutionException ex) {
            throw new MojoExecutionException("Collecting dependencies failed", ex);
        }

        Map<String, Properties> propertiesForFileName = new LinkedHashMap<>();

        Map<String, Set<String>> linesForName = new LinkedHashMap<>();
        Map<String, Integer> fileCountForName = new HashMap<>();

        boolean buildMergedJar = true;//mainClass != null && !"none".equals(mainClass);
        JarOutputStream jarOut = null;
        Set<String> seen = new HashSet<>();

        List<Node> plexusComponents = new ArrayList<>();
        List<String> plexusJars = new ArrayList<>();

        List<List<Map<String, Object>>> reflectionInfo = new LinkedList<>();
        Map<String, List<String>> originsOf = new HashMap<>();
        try {
            if (buildMergedJar) {
                try {
                    File outDir = new File(project.getBuild().getOutputDirectory()).getParentFile();
                    File jar = new File(outDir, project.getBuild().getFinalName() + ".jar");
                    if (!jar.exists()) {
                        throw new MojoExecutionException("Could not find jar " + jar);
                    }
                    try (JarFile jf = new JarFile(jar)) {
                        Manifest manifest = new Manifest(jf.getManifest());
                        if (mainClass != null) {
                            manifest.getMainAttributes().putValue("Main-Class", mainClass);
                        }
                        String jn = jarName == null || "none".equals(jarName) ? mainClass == null ? "merged-jar" : strip(mainClass) : jarName;
                        File outJar = new File(outDir, jn + ".jar");
                        log.info("Will build merged JAR " + outJar);
                        if (outJar.equals(jar)) {
                            throw new MojoExecutionException("Merged jar and output jar are the same file: " + outJar);
                        }
                        if (!outJar.exists()) {
                            outJar.createNewFile();
                        }
                        jarOut = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outJar)), manifest);
                        jarOut.setLevel(9);
                        jarOut.setComment("Merged jar created by " + getClass().getName());
                        Enumeration<JarEntry> en = jf.entries();
                        while (en.hasMoreElements()) {
                            JarEntry e = en.nextElement();
                            String name = e.getName();
                            List<String> origins = originsOf.get(name);
                            if (origins == null) {
                                origins = new ArrayList<>(5);
                                originsOf.put(name, origins);
                            }
                            origins.add(jar.getName());
                            if (isExcluded(name)) {
                                continue;
                            }
//                            if (!seen.contains(name)) {
                            switch (name) {
                                case "META-INF/MANIFEST.MF":
                                case ".netbeans_automatic_build":
                                case "META-INF/INDEX.LIST":
                                case "META-INF/":
                                    break;
                                case "META-INF/injection/reflective.json":
                                    try (InputStream in = jf.getInputStream(e)) {
                                    log.info("Will merge META-INF/injection/reflective.json info from " + jar);
                                    reflectionInfo.add(readJsonList(in));
                                }
                                continue;
                                case PLEXUS_COMPONENTS_FILE:
                                    try (InputStream in = jf.getInputStream(e)) {
                                    log.info("Will merge " + PLEXUS_COMPONENTS_FILE + " info from " + jar.getName());
                                    plexusComponents.addAll(readPlexusComponents(jar.getName(), in));
                                    plexusJars.add(jar.getName());
                                }
                                continue;
                                default:
                                    if ("META-INF/LICENSE".equals(name)
                                            || "META-INF/LICENSE.txt".equals(name)
                                            || "META-INF/license".equals(name)
                                            || "META-INF/NOTICE".equals(name)
                                            || "META-INF/notice".equals(name)
                                            || "META-INF/license.txt".equals(name)
                                            || "META-INF/http/pages.list".equals(name)
                                            || "META-INF/http/modules.list".equals(name)
                                            || "META-INF/http/numble.list".equals(name)
                                            || "META-INF/settings/namespaces.list".equals(name)
                                            || (name.startsWith("META-INF") && name.endsWith(".registrations"))) {
                                        if (shouldSkip(name)) {
                                            break;
                                        }
                                        Set<String> s = linesForName.get(name);
                                        if (s == null) {
                                            s = new LinkedHashSet<>();
                                            linesForName.put(name, s);
                                        }
                                        Integer ct = fileCountForName.get(name);
                                        if (ct == null) {
                                            ct = 1;
                                        }
                                        fileCountForName.put(name, ct);
                                        try (InputStream in = jf.getInputStream(e)) {
                                            s.addAll(readLines(in));
                                        }
                                        break;
                                    }

                                    if (name.startsWith("META-INF/services/") && !name.endsWith("/")) {
                                        Set<String> s2 = linesForName.get(name);
                                        if (s2 == null) {
                                            s2 = new HashSet<>();
                                            linesForName.put(name, s2);
                                        }
                                        Integer ct2 = fileCountForName.get(name);
                                        if (ct2 == null) {
                                            ct2 = 1;
                                        }
                                        fileCountForName.put(name, ct2);
                                        try (InputStream in = jf.getInputStream(e)) {
                                            s2.addAll(readLines(in));
                                        }
                                        seen.add(name);
                                    } else if (PAT.matcher(name).matches() || rewritablePropertiesFile(name)) {
                                        log.info("Include " + name);
                                        Properties p = new Properties();
                                        try (InputStream in = jf.getInputStream(e)) {
                                            p.load(in);
                                        }
                                        Properties all = propertiesForFileName.get(name);
                                        if (all == null) {
                                            all = p;
                                            propertiesForFileName.put(name, p);
                                        } else {
                                            for (String key : p.stringPropertyNames()) {
                                                if (all.containsKey(key)) {
                                                    Object old = all.get(key);
                                                    Object nue = p.get(key);
                                                    if (!Objects.equals(old, nue)) {
                                                        log.warn(key + '=' + nue + " in " + jar + '!' + name + " overrides " + key + '=' + old);
                                                    }
                                                }
                                            }
                                            all.putAll(p);
                                        }
                                    } else if (!seen.contains(name) && notSigFile(name)) {
                                        log.debug("Bundle " + name);
                                        JarEntry je = new JarEntry(name);
                                        je.setTime(e.getTime());
                                        try {
                                            jarOut.putNextEntry(je);
                                        } catch (ZipException ex) {
                                            throw new MojoExecutionException("Exception putting zip entry " + name, ex);
                                        }
                                        try (InputStream in = jf.getInputStream(e)) {
                                            copy(in, jarOut);
                                        }
                                        jarOut.closeEntry();
                                        seen.add(name);
                                    } else {
                                        log.warn("Skip " + name);
                                    }
                            }
//                            }
                            seen.add(e.getName());
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to create merged jar", ex);
                }
            }

            for (File f : jars) {
                log.info("Include contents of " + f);
                try (JarFile jar = new JarFile(f)) {
                    Enumeration<JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        JarEntry entry = en.nextElement();
                        String name = entry.getName();
                        if (shouldSkip(name) || isExcluded(name)) {
                            continue;
                        }
                        List<String> origins = originsOf.get(name);
                        if (origins == null) {
                            origins = new ArrayList<>(5);
                            originsOf.put(name, origins);
                        }
                        origins.add(f.getName());
                        if (PAT.matcher(name).matches()) {
                            log.debug("Combine or rewrite " + name + " in " + f);
                            Properties p = new Properties();
                            try (InputStream in = jar.getInputStream(entry)) {
                                p.load(in);
                            }
                            Properties all = propertiesForFileName.get(name);
                            if (all == null) {
                                all = p;
                                propertiesForFileName.put(name, p);
                            } else {
                                for (String key : p.stringPropertyNames()) {
                                    if (all.containsKey(key)) {
                                        Object old = all.get(key);
                                        Object nue = p.get(key);
                                        if (!Objects.equals(old, nue)) {
                                            log.warn(key + '=' + nue + " in " + f + '!' + name + " overrides " + key + '=' + old);
                                        }
                                    }
                                }
                                all.putAll(p);
                            }
                        } else if (REGISTRATIONS.matcher(name).matches() || SERVICES.matcher(name).matches() || "META-INF/settings/namespaces.list".equals(name) || "META-INF/http/pages.list".equals(name) || "META-INF/http/modules.list".equals(name) || "META-INF/http/numble.list".equals(name)) {
                            log.info("Concatenate " + name + " from " + f);
                            try (InputStream in = jar.getInputStream(entry)) {
                                List<String> lines = readLines(in);
                                Set<String> all = linesForName.get(name);
                                if (all == null) {
                                    all = new LinkedHashSet<>();
                                    linesForName.put(name, all);
                                }
                                all.addAll(lines);
                            }
                            Integer ct = fileCountForName.get(name);
                            if (ct == null) {
                                ct = 1;
                            } else {
                                ct++;
                            }
                            fileCountForName.put(name, ct);
                        } else if (PLEXUS_COMPONENTS_FILE.equals(name)) {
                            try (InputStream in = jar.getInputStream(entry)) {
                                log.info("Will merge " + PLEXUS_COMPONENTS_FILE + " info from " + f.getName());
                                plexusComponents.addAll(readPlexusComponents(f.getName(), in));
                                plexusJars.add(f.getName());
                            }
                        } else if (jarOut != null) {
                            switch (name) {
                                case "META-INF/MANIFEST.MF":
                                case "META-INF/":
                                    break;
                                case "META-INF/injection/reflective.json":
                                    try (InputStream in = jar.getInputStream(entry)) {
                                    log.info("Will merge META-INF/injection/reflective.json info from " + f.getName());
                                    reflectionInfo.add(readJsonList(in));
                                }
                                continue;

                                default:
                                    if ("META-INF/LICENSE".equals(name)
                                            || "META-INF/LICENSE.txt".equals(name)
                                            || "META-INF/license".equals(name)
                                            || "META-INF/NOTICE".equals(name)
                                            || "META-INF/notice".equals(name)
                                            || "META-INF/license.txt".equals(name)
                                            || "META-INF/http/pages.list".equals(name)
                                            || "META-INF/http/modules.list".equals(name)
                                            || "META-INF/http/numble.list".equals(name)
                                            || "META-INF/settings/namespaces.list".equals(name)
                                            || (name.startsWith("META-INF") && name.endsWith(".registrations"))) {
                                        if (shouldSkip(name)) {
                                            break;
                                        }
                                        Set<String> s = linesForName.get(name);
                                        if (s == null) {
                                            s = new LinkedHashSet<>();
                                            linesForName.put(name, s);
                                        }
                                        Integer ct = fileCountForName.get(name);
                                        if (ct == null) {
                                            ct = 1;
                                        }
                                        fileCountForName.put(name, ct);
                                        try (InputStream in = jar.getInputStream(entry)) {
                                            s.addAll(readLines(in));
                                        }
                                        break;
                                    }
                                    if (!seen.contains(name)) {
                                        if (!SIG1.matcher(name).find() && !SIG2.matcher(name).find() && !SIG3.matcher(name).find()) {
                                            JarEntry je = new JarEntry(name);
                                            je.setTime(entry.getTime());
                                            try {
                                                jarOut.putNextEntry(je);
                                            } catch (ZipException ex) {
                                                throw new MojoExecutionException("Exception putting zip entry " + name, ex);
                                            }
                                            try (InputStream in = jar.getInputStream(entry)) {
                                                copy(in, jarOut);
                                            }
                                            jarOut.closeEntry();
                                        }
                                    } else {
                                        if (!name.endsWith("/") && !name.startsWith("META-INF")) {
                                            log.warn("Saw more than one " + name + ".  One will clobber the other.");
                                        }
                                    }
                            }
                            seen.add(name);
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Error opening " + f, ex);
                }
            }
            if (!propertiesForFileName.isEmpty()) {
                log.warn("Writing merged files: " + propertiesForFileName.keySet());
            }
            String outDir = project.getBuild().getOutputDirectory();
            File dir = new File(outDir);
            if (!reflectionInfo.isEmpty()) {
                File dest = new File(dir, "META-INF/injection");
                if (!dest.exists()) {
                    dest.mkdirs();
                }
                File rinfo = new File(dest, "reflective.json");
                int sz = 0;
                for (List<Map<String, Object>> l : reflectionInfo) {
                    for (Map<String, Object> m : l) {
                        sz += m.size();
                    }
                }
                log.warn("Writing merged META_INF/injection/reflective.json with " + sz + " entries");
                try {
                    try (OutputStream rout = new BufferedOutputStream(new FileOutputStream(rinfo))) {
                        this.saveJsonLists(rout, reflectionInfo);
                    }
                    if (jarOut != null) {
                        JarEntry je = new JarEntry("META-INF/injection/reflective.json");
                        jarOut.putNextEntry(je);
                        saveJsonLists(jarOut, reflectionInfo);
                        jarOut.closeEntry();
                    }
                } catch (IOException ioe) {
                    throw new MojoFailureException("Exception writing reflection info", ioe);
                }
            } else {
                log.warn("No META_INF/injection/reflective.json data to write");
            }
            if (!plexusComponents.isEmpty()) {
                log.warn("Writing merged " + PLEXUS_COMPONENTS_FILE + " with " + plexusComponents.size() + " components "
                        + " from " + plexusJars);
                String body = assemblePlexusComponents(plexusComponents);
                File dest = new File(dir, "META-INF/plexus");
                if (!dest.exists()) {
                    dest.mkdirs();
                }
                File comps = new File(dest, "components.xml");
                try (OutputStream cout = new BufferedOutputStream(new FileOutputStream(comps))) {
                    cout.write(body.getBytes(UTF_8));;
                } catch (Exception ex) {
                    throw new MojoFailureException("Exception writing reflection info", ex);
                }
                if (jarOut != null) {
                    try {
                        JarEntry je = new JarEntry("META-INF/plexus/components.xml");
                        jarOut.putNextEntry(je);
                        jarOut.write(body.getBytes(UTF_8));;
                        jarOut.closeEntry();
                    } catch (IOException ex) {
                        throw new MojoFailureException("Exception writing reflection info", ex);
                    }
                }
            }
            for (Map.Entry<String, Set<String>> e : linesForName.entrySet()) {
                if (shouldSkip(e.getKey())) {
                    continue;
                }
                File outFile = new File(dir, e.getKey());
                if (originsOf.get(e.getKey()).size() > 1) {
                    log.info("Combining " + outFile + " from " + sortedToString(originsOf.get(e.getKey())));
                } else {
                    log.debug("Rewriting " + outFile + " from " + sortedToString(originsOf.get(e.getKey())) + " for repeatable builds");
                }
                Set<String> lines = e.getValue();
                if (!outFile.exists()) {
                    try {
                        Path path = outFile.toPath();
                        if (!Files.exists(path.getParent())) {
                            Files.createDirectories(path.getParent());
                        }
                        path = Files.createFile(path);
                        outFile = path.toFile();
                    } catch (IOException ex) {
                        throw new MojoFailureException("Could not create " + outFile, ex);
                    }
                }
                if (!outFile.isDirectory()) {
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        printLines(lines, out, true);
                    } catch (IOException ex) {
                        throw new MojoFailureException("Exception writing " + outFile, ex);
                    }
                }
                if (jarOut != null) {
                    int count = fileCountForName.get(e.getKey());
                    if (count > 1) {
                        log.warn("Concatenating " + count + " copies of " + e.getKey());
                    }
                    JarEntry je = new JarEntry(e.getKey());
                    try {
                        jarOut.putNextEntry(je);
                        printLines(lines, jarOut, false);
                        jarOut.closeEntry();
                    } catch (IOException ex) {
                        throw new MojoFailureException("Exception writing " + outFile, ex);
                    }
                }
            }
            for (Map.Entry<String, Properties> e : propertiesForFileName.entrySet()) {
                if (shouldSkip(e.getKey()) || isExcluded(e.getKey())) {
                    continue;
                }
                File outFile = new File(dir, e.getKey());
                Properties local = new Properties();
                if (outFile.exists()) {
                    try {
                        try (InputStream in = new FileInputStream(outFile)) {
                            local.load(in);
                        }
                    } catch (IOException ioe) {
                        throw new MojoExecutionException("Could not read " + outFile, ioe);
                    }
                } else {
                    try {
                        Path path = outFile.toPath();
                        if (!Files.exists(path.getParent())) {
                            Files.createDirectories(path.getParent());
                        }
                        path = Files.createFile(path);
                        outFile = path.toFile();
                    } catch (IOException ex) {
                        throw new MojoFailureException("Could not create " + outFile, ex);
                    }
                }
                Properties merged = e.getValue();
                for (String key : local.stringPropertyNames()) {
                    if (merged.containsKey(key) && !Objects.equals(local.get(key), merged.get(key))) {
                        log.warn("Overriding key=" + merged.get(key) + " with locally defined key=" + local.get(key));
                    }
                }
                merged.putAll(local);
                List<String> origins = originsOf.get(e.getKey());
                if (origins == null) {
                    throw new IllegalStateException("Don't have an origin for " + e.getKey() + " in " + originsOf);
                }
                String ogs = sortedToString(origins);
                if (origins.size() == 1) {
                    log.info("Rewriting properties " + outFile + " from " + ogs + " for repeatable builds");
                } else {
                    log.info("Saving merged properties to " + outFile + " from " + ogs);
                }
                String comment = "Merged by " + getClass().getSimpleName() + " from  " + ogs;
                try {
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        savePropertiesFile(merged, out, comment, true);
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to write " + outFile, ex);
                }
                if (jarOut != null) {
                    JarEntry props = new JarEntry(e.getKey());
                    try {
                        jarOut.putNextEntry(props);
                        savePropertiesFile(merged, jarOut, comment, false);
                    } catch (IOException ex) {
                        throw new MojoExecutionException("Failed to write jar entry " + e.getKey(), ex);
                    } finally {
                        try {
                            jarOut.closeEntry();
                        } catch (IOException ex) {
                            throw new MojoExecutionException("Failed to close jar entry " + e.getKey(), ex);
                        }
                    }
                }
                File copyTo = new File(dir.getParentFile(), "settings");
                if (!copyTo.exists()) {
                    copyTo.mkdirs();
                }
                File toFile = new File(copyTo, outFile.getName());
                try {
                    Files.copy(outFile.toPath(), toFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to copy " + outFile + " to " + toFile, ex);
                }
            }
        } finally {
            if (jarOut != null) {
                try {
                    jarOut.close();
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to close Jar", ex);
                }
            }
        }
    }

    private List<Map<String, Object>> readJsonList(InputStream in) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {
        });
    }

    private void saveJsonLists(OutputStream dest, List<List<Map<String, Object>>> all) throws IOException {
        Set<Map<String, Object>> info = mergedMaps(all);
        if (!info.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).disable(SerializationFeature.CLOSE_CLOSEABLE);
            byte[] b = mapper.writeValueAsBytes(info);
            dest.write(b);
            dest.flush();
        }
    }

    private Set<Map<String, Object>> mergedMaps(List<List<Map<String, Object>>> all) {
        Set<Map<String, Object>> result = new LinkedHashSet<>();
        for (List<Map<String, Object>> l : all) {
            result.addAll(l);
        }
        return result;
    }

    private static String sortedToString(List<String> all) {
        Collections.sort(all);
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = all.iterator(); it.hasNext();) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private Collection<? extends Node> readPlexusComponents(String jar, InputStream in) throws MojoFailureException {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setValidating(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(in);
            doc.getDocumentElement().normalize();

            XPathFactory fac = XPathFactory.newInstance();
            XPath xpath = fac.newXPath();
            XPathExpression findComponents = xpath.compile(
                    "/component-set/components/component");
            NodeList nl = (NodeList) findComponents.evaluate(doc, XPathConstants.NODESET);

            List<Node> nodes = new ArrayList<>();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                nodes.add(n);
            }

            return nodes;
        } catch (Exception ex) {
            throw new MojoFailureException("Could not parse " + PLEXUS_COMPONENTS_FILE + " in " + jar, ex);
        }
    }

    private String assemblePlexusComponents(List<Node> all) throws MojoFailureException {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setValidating(false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            Document doc = dBuilder.newDocument();
            Node root = doc.createElement("component-set");
            Node sub = doc.createElement("components");
            root.appendChild(sub);
            doc.appendChild(root);
            for (Node n : all) {
                Node adopted = doc.adoptNode(n);
                sub.appendChild(adopted);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StreamResult res = new StreamResult(out);
            TransformerFactory tFactory
                    = TransformerFactory.newInstance();
            Transformer transformer = tFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
//            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", Integer.toString(4));
            transformer.transform(new DOMSource(doc), res);

            String result = new String(out.toByteArray(), "UTF-8");
            return result;
        } catch (Exception ex) {
            throw new MojoFailureException("Could not assemble a " + PLEXUS_COMPONENTS_FILE, ex);
        }

    }
}
