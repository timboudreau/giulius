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
import com.mastfrog.jarmerge.spi.Coalescer;
import com.mastfrog.jarmerge.spi.JarFilter;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingSeptaConsumer;
import static com.mastfrog.jarmerge.Phase.WRITE;
import com.mastfrog.util.streams.HashingInputStream;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipException;

/**
 *
 * @author Tim Boudreau
 */
final class PhaseRunner {

    private final Phase phase;
    private final JarMerge settings;
    private final TriFunction<String, Phase, JarMerge, MergeLog> logFactory;

    public static void main(String[] args) throws Exception {
        JarMerge.main(new String[]{"--list"});
    }

    PhaseRunner(Phase phase, JarMerge settings, PhaseOutput prevPhaseOut,
            TriFunction<String, Phase, JarMerge, MergeLog> logFactory) {
        this.phase = phase;
        this.settings = settings;
        this.logFactory = logFactory;
        last = prevPhaseOut;
    }

    PhaseOutput last;

    PhaseOutput run() throws Exception {
        switch (phase) {
            case DRY_RUN:
                return last = dryRun(last);
            case WRITE:
                return last = write(last);
            default:
                throw new AssertionError(phase);
        }
    }

    void iterateJarsAndFilters(PhaseOutput output,
            ThrowingSeptaConsumer<Path, JarFile, JarEntry, JarFilter, MergeLog, PhaseOutput, Boolean> c) throws Exception {
        settings.eachJar((path, last) -> {
            withJar(path, jarFile -> {
                output.onOpenJar(path, jarFile);
                eachEntry(jarFile, en -> {
                    for (JarFilter filter : settings.filters) {
                        MergeLog log = logFactory.apply(filter.name(), phase, settings);
                        c.accept(path, jarFile, en, filter, log, output, last);
                    }
                });
            });
        });;
    }

    private PhaseOutput dryRun(PhaseOutput last) throws Exception {
        PhaseOutput output = new PhaseOutput(phase, last);
        iterateJarsAndFilters(output, this::runDryRun);
        return output;
    }

    private JarOutputStream jarOut(Path path, PhaseOutput out) throws IOException {
        if (settings.zerodates) {
            Manifest man = out.createOutputManifest(settings);
            Attributes attrs = man.getMainAttributes();
            StringBuilder sb = new StringBuilder();
            attrs.forEach((name, val) -> {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(name).append(": ").append(val);
            });
            // Critical that the main manifest end with two newlines,
            // or all sorts of things break
            sb.append("\n\n");
            // PENDING:  If we're doing this anyway, we could make a minor
            // improvement to performance by putting the manifest at the *end*
            // of the file - a zip file's central directory is at the end, so
            // we will already be seeked there and it is less likely to incur
            // a cache miss
            JarOutputStream result = new JarOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(path, CREATE, StandardOpenOption.WRITE, TRUNCATE_EXISTING), 1024 * 1024
            ));
            // Manually writing the manifest seems to be the only way to be able
            // to set the times on its entry
            JarEntry manifest = new JarEntry("META-INF/MANIFEST.MF");
            configureFolderEntry(manifest);
            result.putNextEntry(manifest);
            result.write(sb.toString().getBytes(UTF_8));

            return result;
        } else {
            return new JarOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(path, CREATE, StandardOpenOption.WRITE, TRUNCATE_EXISTING), 1024 * 1024
            ), out.createOutputManifest(settings));
        }
    }

    static final class HashCollector {

        private Map<String, Map<String, Set<Path>>> paths = new HashMap<>();

        InputStream wrap(Path jar, JarFile file, JarEntry entry) throws IOException {
            InputStream raw = file.getInputStream(entry);
            HashingInputStream result = HashingInputStream.sha1(raw);
            return new FIS(result, entry.getName(), jar);
        }

        void spool(Path jar, JarFile file, JarEntry entry) throws IOException {
            try (InputStream in = wrap(jar, file, entry)) {
                Streams.copy(in, Streams.nullOutputStream());
            }
        }

        static Set<Path> simplePathSet(Set<Path> pths) {
            // If we do not have two JAR files with the same file name
            // on different paths, then return a set of paths which are
            // only file names, since they are sufficient to distinguish
            // what is what in a log message
            Set<Path> result = new TreeSet<>();
            pths.forEach(pth -> result.add(pth.getFileName()));
            if (result.size() == pths.size()) {
                return result;
            }
            return pths;
        }

        void withDifferingDuplicates(BiConsumer<String, Map<String, String>> c) {
            paths.forEach((path, pathsForHash) -> {
                if (pathsForHash.size() > 1) {
                    Map<String, String> inverse = new LinkedHashMap<>();
                    pathsForHash.forEach((hsh, pathSet) -> {
                        Set<Path> simple = simplePathSet(pathSet);
                        StringBuilder sb = new StringBuilder();
                        for (Path p : simple) {
                            if (sb.length() > 0) {
                                sb.append(", ");
                            }
                            sb.append(p);
                        }
                        inverse.put(sb.toString(), hsh);
                    });
                    c.accept(path, inverse);
                }
            });
        }

        void logWarnings(MergeLog log) {
            withDifferingDuplicates((path, jarsForHash) -> {
                if (path.endsWith(".class")) {
                    StringBuilder sb = new StringBuilder("Differing files encountered: ")
                            .append(path);
                    int ln = sb.length();
                    jarsForHash.forEach((jars, hash) -> {
                        char ch = sb.length() == ln ? '+' : '-';
                        sb.append("\n ").append(ch).append(' ')
                                .append(hash).append(" in ").append(jars);
                    });
                    log.warn(sb.toString());
                }
            });
        }

        final class FIS extends FilterInputStream {

            private final HashingInputStream his;
            private final String path;
            private final Path inJar;

            public FIS(HashingInputStream in, String path, Path inJar) {
                super(in);
                this.his = in;
                this.path = path;
                this.inJar = inJar;
            }

            @Override
            public void close() throws IOException {
                super.close();
                String hash = Base64.getEncoder().encodeToString(his.getDigest());
                Map<String, Set<Path>> m = paths.computeIfAbsent(path, pth -> new LinkedHashMap<>());
                Set<Path> paths = m.computeIfAbsent(hash, h -> new LinkedHashSet<>());
                paths.add(inJar);
            }
        }
    }

    private PhaseOutput write(PhaseOutput previousPhase) throws Exception {
        PhaseOutput output = new PhaseOutput(Phase.WRITE, previousPhase);
        Path outputJar = Paths.get(settings.jarName);
        Path parent = outputJar.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        MergeLog outputLog = logFactory.apply("output", phase, settings);

        HashCollector hasher = new HashCollector();

        try (JarOutputStream jarOut = jarOut(outputJar, output)) {
            Manifest m = output.createOutputManifest(settings);
            if (settings.compressionLevel > 0) {
                jarOut.setLevel(settings.compressionLevel);
                jarOut.setMethod(JarOutputStream.DEFLATED);
            }
            Set<String> written = new HashSet<>();
            Set<String> indexPaths = new LinkedHashSet<>();
            // Writing the manifest takes care of META-INF/ so mark it as
            // written so we don't try to write it twice and throw an exception
            // as a result
            written.add("META-INF/");
            indexPaths.add("META-INF/");
            Set<String> coas = new HashSet<>();
            output.eachCoalescer(coa -> {
                List<String> indexParentPaths = coa.indexPaths();
                writeParentDirs(indexParentPaths, indexPaths, jarOut, written);

                coa.writeCoalesced(jarOut, outputLog);

                PhaseOutput.CoalescerEntry<?> en = output.entryForCoa.get(coa);
                coas.addAll(en.pathForJars.keySet());

                written.add(coa.path());
            });

            settings.eachJar((jarPath, last) -> {
                withJar(jarPath, jarFile -> {
                    eachEntry(jarFile, entry -> {
                        String entryName = entry.getName();
                        if (output.isSkipped(entryName)) {
                            return;
                        }
                        if (written.contains(entryName)) {
                            if (!entry.isDirectory()) {
                                if (coas.contains(entryName)) {
                                    outputLog.debug("Already coalesced {0} in {1} and wrote it.",
                                            entryName, jarPath.getFileName());
                                } else {
                                    outputLog.debug("Skip duplicate {0} in {1}", entryName, jarPath.getFileName());
                                    hasher.spool(jarPath, jarFile, entry);
                                }
                            }
                            return;
                        }
                        boolean write = true;
                        for (JarFilter<?> filter : settings.filters) {
                            MergeLog log = logFactory.apply(filter.name(), phase, settings);
                            write = writeCheck(jarOut, jarPath, jarFile, entry, filter, log, output, last);
                            if (!write) {
                                log.log("Omit {0} in {1}", entryName, jarPath.getFileName());
                                return;
                            }
                        }
                        if (write) {
                            written.add(entryName);
                            writeParentDirs(indexPathsOf(entryName), indexPaths, jarOut, written);

                            JarEntry en = new JarEntry(entry.getName());
                            if (settings.zerodates) {
                                configureFolderEntry(en);
                            } else {
                                if (entry.getCreationTime() != null) {
                                    en.setCreationTime(entry.getCreationTime());
                                }
                                if (entry.getLastAccessTime() != null) {
                                    en.setLastAccessTime(entry.getLastAccessTime());
                                }
                                if (entry.getLastModifiedTime() != null) {
                                    en.setLastModifiedTime(entry.getLastModifiedTime());
                                }
                            }
                            try {
                                jarOut.putNextEntry(en);
                                if (!entry.isDirectory()) {
                                    outputLog.debug("Include {0} from {1}", entryName, jarPath.getFileName());
                                    try (InputStream in = hasher.wrap(jarPath, jarFile, entry)) {
                                        Streams.copy(in, jarOut, Math.min(2048, Math.max(0, (int) entry.getSize())));
                                    }
                                } else {
                                    indexPaths.add(entryName);
                                }
                            } catch (ZipException e) {
                                throw new IOException("Failure to copy " + entryName + " from "
                                        + jarPath.getFileName() + " - previously written: "
                                        + Strings.join(' ', written), e);
                            }
                        }
                    });
                });
            });
            if (settings.generateIndex && !indexPaths.isEmpty()) {
                outputLog.log("Generating index with {0} jar entries",
                        new Object[]{indexPaths.size()});
                JarEntry nue = new JarEntry("META-INF/INDEX.LIST");
                configureFolderEntry(nue);
                jarOut.putNextEntry(nue);
                jarOut.write(("JarIndex-Version: 1.0\n\n" + outputJar.getFileName()).getBytes(US_ASCII));
                for (String path : indexPaths) {
                    jarOut.write('\n');
                    if (path.charAt(path.length() - 1) == '/') {
                        path = path.substring(0, path.length() - 1);
                    }
                    jarOut.write(path.getBytes(UTF_8));
                }
                jarOut.write('\n');
            }
        }
        hasher.logWarnings(outputLog);
        System.out.println("Wrote " + outputJar.toAbsolutePath());
        return output;
    }

    private static final FileTime EPOCH = FileTime.from(Instant.EPOCH);

    private void configureFolderEntry(JarEntry en) {
        en.setCreationTime(EPOCH);
        en.setLastModifiedTime(EPOCH);
        en.setLastAccessTime(EPOCH);
    }

    public void writeParentDirs(List<String> indexParentPaths, Set<String> indexPaths, final JarOutputStream jarOut, Set<String> written) throws IOException {
        for (String parentPath : indexParentPaths) {
            if (!indexPaths.contains(parentPath)) {
                indexPaths.add(parentPath);
                if (!written.contains(parentPath)) {
                    JarEntry je = new JarEntry(parentPath);
                    configureFolderEntry(je);
                    jarOut.putNextEntry(je);
                    written.add(parentPath);
                }
            }
        }
    }

    private static List<String> indexPathsOf(String nm) {
        // Only folders and files in the package root are included in
        // the index
        if (!nm.contains("/")) {
            return Arrays.asList(nm);
        }
        String[] parts = nm.split("/");
        List<String> result = new ArrayList<>(parts.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(parts[i]);
            result.add(sb.toString() + "/");
        }
        return result;
    }

    private void withJar(Path jar, ThrowingConsumer<JarFile> c) throws Exception {
        try (JarFile jf = new JarFile(jar.toFile())) {
            c.accept(jf);
        }
    }

    private void eachEntry(JarFile file, ThrowingConsumer<JarEntry> c) throws Exception {
        Enumeration<JarEntry> entries = file.entries();
        while (entries.hasMoreElements()) {
            c.accept(entries.nextElement());;
        }
    }

    <C extends Coalescer> boolean handle(Path jar, JarFile jarFile, JarEntry entry, JarFilter<C> filter, MergeLog log, PhaseOutput output, boolean last) throws Exception {
        String entryName = entry.getName();
        boolean isFolder = entry.isDirectory();
        if (output.isSkipped(entryName) || output.isCoalesced(entry.getName(), jar)) {
            return true;
        }
        if (filter.omit(entry.getName(), jar, log)) {
            log.debug("Skip {0} in {1}", entryName, jar.getFileName());
            output.skip.add(entry.getName());
            return true;
        }
        if (!isFolder) {
            C coa = filter.coalescer(entryName, jar, entry, log);
            if (coa != null) {
                log.debug("{0} in {1} coalesced by {2}", entryName, jar.getFileName(), filter.name());
                coa.add(jar, entry, jarFile, log);
                output.coalescing(coa, filter, entryName, jar);
                return true;
            }
        }
        return false;
    }

    private boolean writeCheck(JarOutputStream jarOut, Path jar, JarFile jarFile, JarEntry entry,
            JarFilter<?> filter, MergeLog log, PhaseOutput output, boolean last) throws Exception {
        if (output.previousPhase == null) {
            // We did not already examine this entry with filters, so do it now
            boolean handled = handle(jar, jarFile, entry, filter, log, output, last);
            if (handled) {
                return false;
            }
        }
        if (output.isSkipped(entry.getName())) {
            return false;
        }
        if (output.isCoalesced(entry.getName(), jar)) {
            return false;
        }
        if ("META-INF/MANIFEST.MF".equals(entry.getName())) {
            return false;
        }
        Path existing = output.alreadyEncountered(entry.getName(), jar);
        if (existing != null && !existing.equals(jar)) {
            if (!entry.isDirectory()) {
                log.warn("Already encountered {0} in {1} - not writing the version from {2}.", entry.getName(),
                        existing.getFileName(), jar.getFileName());
            }
            return false;
        }
        return true;
    }

    private void runDryRun(Path jar, JarFile jarFile, JarEntry entry, JarFilter<?> filter, MergeLog log, PhaseOutput output, boolean last) throws Exception {
        if (!handle(jar, jarFile, entry, filter, log, output, last)) {

            Path existing = output.alreadyEncountered(entry.getName(), jar);
//            if (existing != null) {
//                if (!isFolderLike(entry)) {
//                    log.warn("Already encountered {0} in {1} - the version from {2} will be ignored.", entry.getName(),
//                            existing.getFileName(), jar.getFileName());
//                }
//            }
        }
    }

    static final class PhaseOutput {

        private final Phase phase;
        private final Set<String> coaPaths = new HashSet<>();
        private final Map<Coalescer, CoalescerEntry> entryForCoa = new HashMap<>();
        private Manifest firstManifest;
        private final Phase previousPhase;
        private final Map<String, Path> encounteredFiles = new HashMap<>();

        final Set<String> skip = new HashSet<>();

        public PhaseOutput(Phase phase) {
            this.phase = phase;
            this.previousPhase = null;
        }

        public PhaseOutput(Phase phase, PhaseOutput previousPhaseOutput) {
            this.phase = phase;
            if (previousPhaseOutput != null) {
                previousPhase = previousPhaseOutput.phase;
                this.coaPaths.addAll(previousPhaseOutput.coaPaths);
                this.skip.addAll(previousPhaseOutput.skip);
                this.entryForCoa.putAll(previousPhaseOutput.entryForCoa);
                this.firstManifest = previousPhaseOutput.firstManifest;
            } else {
                this.previousPhase = null;
            }
        }

        Path alreadyEncountered(String path, Path inJar) {
            Path existing = encounteredFiles.get(path);
            if (existing != null) {
                return existing;
            }
            encounteredFiles.put(path, inJar);
            return null;
        }

        void eachCoalescer(ThrowingConsumer<Coalescer> c) throws Exception {
            List<Coalescer> coalescers = new ArrayList<>(entryForCoa.keySet());
            Collections.sort(coalescers);
            for (Coalescer coa : coalescers) {
                c.accept(coa);
            }
        }

        boolean isSkipped(String path) {
            return skip.contains(path);
        }

        boolean isCoalesced(String path, Path jar) {
            if (!coaPaths.contains(path)) {
                return false;
            }
            for (CoalescerEntry<?> ce : entryForCoa.values()) {
                if (ce.contains(path, jar)) {
                    return true;
                }
            }
            return false;
        }

        <C extends Coalescer> void coalescing(C c, JarFilter<C> filter, String path, Path jar) {
            CoalescerEntry ce = entryForCoa.computeIfAbsent(c, cc -> new CoalescerEntry<C>(filter, c));
            ce.add(path, jar);
            coaPaths.add(path);
        }

        Coalescer coalescerFor(String path, Path jar) {
            if (!coaPaths.contains(path)) {
                return null;
            }
            for (CoalescerEntry<?> ce : entryForCoa.values()) {
                if (ce.contains(path, jar)) {
                    return ce.coalescer;
                }
            }
            return null;
        }

        private void onOpenJar(Path path, JarFile jarFile) throws IOException {
            if (firstManifest == null) {
                firstManifest = jarFile.getManifest();
            }
        }

        Manifest createOutputManifest(JarMerge settings) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VENDOR, "smart-jar-merge-2.7.1");

            Attributes.Name main = Attributes.Name.MAIN_CLASS;
            if (settings.mainClass != null && !settings.mainClass.trim().isEmpty()) {
                manifest.getMainAttributes().put(main, settings.mainClass);
            } else if (firstManifest != null) {
                Object mc = firstManifest.getMainAttributes().get(main);
                if (mc != null) {
                    manifest.getMainAttributes().put(main, mc);
                }
            }
            return manifest;
        }

        static class CoalescerEntry<C extends Coalescer> {

            private final JarFilter<C> filter;
            private final C coalescer;
            private final Map<String, Set<Path>> pathForJars = new HashMap<>();

            CoalescerEntry(JarFilter<C> filter, C coalescer) {
                this.filter = filter;
                this.coalescer = coalescer;
            }

            public String toString() {
                return coalescer + " for " + filter + " with" + pathForJars;
            }

            void add(String pathInJar, Path jar) {
                pathForJars.computeIfAbsent(pathInJar, pij -> new HashSet<>())
                        .add(jar);
            }

            boolean contains(String pathInJar, Path jar) {
                Set<Path> paths = pathForJars.get(pathInJar);
                return paths == null ? false : paths.contains(jar);
            }
        }
    }
}
