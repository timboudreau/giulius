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

import com.google.common.base.Objects;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
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
    private static final Pattern PAT = Pattern.compile("META-INF\\/settings\\/[^\\/]*\\.properties");
    private static final Pattern SERVICES = Pattern.compile("META-INF\\/services\\/\\S[^\\/]*\\.*");
    @Component
    private ProjectDependenciesResolver resolver;
    @Component
    MavenProject project;

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = super.getLog();
        log.info("Merging properties files");
        if (repoSession == null) {
            throw new MojoFailureException("RepositorySystemSession is null");
        }
        List<File> jars = new ArrayList<>();
        try {
            DependencyResolutionResult result
                    = resolver.resolve(new DefaultDependencyResolutionRequest(project, repoSession));
            log.info("FOUND " + result.getDependencies().size() + " dependencies");
            for (Dependency d : result.getDependencies()) {
                switch (d.getScope()) {
                    case "test":
                    case "provided":
                        break;
                    default:
                        File f = d.getArtifact().getFile();
                        if (f.getName().endsWith(".jar") && f.isFile() && f.canRead()) {
                            jars.add(f);
                        }
                }
            }
        } catch (DependencyResolutionException ex) {
            throw new MojoExecutionException("Collecting dependencies failed", ex);
        }

        Map<String, Properties> m = new LinkedHashMap<>();

        Map<String, Set<String>> linesForName = new HashMap<>();
        Map<String, Integer> fileCountForName = new HashMap<>();

        boolean buildMergedJar = mainClass != null && !"none".equals(mainClass);
        JarOutputStream jarOut = null;
        Set<String> seen = new HashSet<>();

        try {
            if (buildMergedJar) {
                try {
                    File outDir = new File(project.getBuild().getOutputDirectory()).getParentFile();
                    File jar = new File(outDir, project.getBuild().getFinalName() + ".jar");
                    System.out.println("Load from " + jar);
                    if (!jar.exists()) {
                        throw new MojoExecutionException("Could not find jar " + jar);
                    }
                    try (JarFile jf = new JarFile(jar)) {
                        Manifest manifest = new Manifest(jf.getManifest());
                        manifest.getMainAttributes().putValue("Main-Class", "com.visitrend.query.server.QueryServer");
                        String jn = jarName == null || "none".equals(jarName) ? strip(mainClass) : jarName;
                        File outJar = new File(outDir, jn + ".jar");
                        log.info("Will build merged JAR " + outJar);
                        if (outJar.equals(jar)) {
                            throw new MojoExecutionException("Merged jar and output jar are the same file: " + outJar);
                        }
                        if (!outJar.exists()) {
                            outJar.createNewFile();
                        }
                        jarOut = new JarOutputStream(new FileOutputStream(outJar), manifest);
                        jarOut.setLevel(9);
                        jarOut.setComment("Merged jar created by " + getClass().getName());
                        Enumeration<JarEntry> en = jf.entries();
                        while (en.hasMoreElements()) {
                            JarEntry e = en.nextElement();
                            String name = e.getName();
                            if (!seen.contains(name)) {
                                switch (name) {
                                    case "META-INF/MANIFEST.MF":
                                    case "META-INF/":
                                        break;
                                    default:
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
                                }
                            }
                            seen.add(e.getName());
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to create merged jar", ex);
                }
            }

            for (File f : jars) {
                try (JarFile jar = new JarFile(f)) {
                    Enumeration<JarEntry> en = jar.entries();
                    while (en.hasMoreElements()) {
                        JarEntry entry = en.nextElement();
                        String name = entry.getName();
                        Matcher match = PAT.matcher(name);
                        if (match.matches()) {
                            log.info("Include " + name + " in " + f);
                            Properties p = new Properties();
                            try (InputStream in = jar.getInputStream(entry)) {
                                p.load(in);
                            }
                            Properties all = m.get(name);
                            if (all == null) {
                                all = p;
                                m.put(name, p);
                            } else {
                                for (String key : p.stringPropertyNames()) {
                                    if (all.containsKey(key)) {
                                        Object old = all.get(key);
                                        Object nue = p.get(key);
                                        if (!Objects.equal(old, nue)) {
                                            log.warn(key + '=' + nue + " in " + f + '!' + name + " overrides " + key + '=' + old);
                                        }
                                    }
                                }
                                all.putAll(p);
                            }
                        } else if (SERVICES.matcher(name).matches()) {
                            log.info("Include " + name + " in " + f);
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
                        } else if (jarOut != null) {
                            if (!seen.contains(name)) {
                                switch (name) {
                                    case "META-INF/MANIFEST.MF":
                                    case "META-INF/":
                                        break;
                                    default:
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
                            seen.add(name);
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Error opening " + f, ex);
                }
            }
            if (!m.isEmpty()) {
                log.warn("Writing merged files: " + m.keySet());
            } else {
                return;
            }
            String outDir = project.getBuild().getOutputDirectory();
            File dir = new File(outDir);
            // Don't bother rewriting META-INF/services files of which there is
            // only one
            for (Map.Entry<String, Integer> e : fileCountForName.entrySet()) {
                if (e.getValue() == 1) {
                    linesForName.remove(e.getKey());
                }
            }
            log.info("Rewrite META-INF/services files " + linesForName);
            for (Map.Entry<String, Set<String>> e : linesForName.entrySet()) {
                File outFile = new File(dir, e.getKey());
                log.info("Merge configurating rewriting " + outFile);
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
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    try (PrintStream ps = new PrintStream(out)) {
                        for (String line : lines) {
                            ps.println(line);
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoFailureException("Exception writing " + outFile, ex);
                }
                if (jarOut != null) {
                    JarEntry je = new JarEntry(e.getKey());
                    try {
                        jarOut.putNextEntry(je);
                        PrintStream ps = new PrintStream(jarOut);
                        for (String line : lines) {
                            ps.println(line);
                        }
                        jarOut.closeEntry();
                    } catch (IOException ex) {
                        throw new MojoFailureException("Exception writing " + outFile, ex);
                    }
                }
            }
            for (Map.Entry<String, Properties> e : m.entrySet()) {
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
                    if (merged.containsKey(key) && !Objects.equal(local.get(key), merged.get(key))) {
                        log.warn("Overriding key=" + merged.get(key) + " with locally defined key=" + local.get(key));
                    }
                }
                merged.putAll(local);
                try {
                    log.info("Saving merged properties to " + outFile);
                    try (FileOutputStream out = new FileOutputStream(outFile)) {
                        merged.store(out, getClass().getName());
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed to write " + outFile, ex);
                }
                if (jarOut != null) {
                    JarEntry props = new JarEntry(e.getKey());
                    try {
                        jarOut.putNextEntry(props);
                        merged.store(jarOut, getClass().getName() + " merged " + e.getKey());
                        jarOut.closeEntry();
                    } catch (IOException ex) {
                        throw new MojoExecutionException("Failed to write jar entry " + e.getKey(), ex);
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
}
