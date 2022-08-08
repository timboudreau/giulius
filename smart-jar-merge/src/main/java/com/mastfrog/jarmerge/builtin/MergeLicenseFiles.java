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
package com.mastfrog.jarmerge.builtin;

import com.mastfrog.function.state.Int;
import com.mastfrog.function.state.Obj;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.builtin.MergeLicenseFiles.LicenseConcatenator;
import com.mastfrog.jarmerge.support.AbstractCoalescer;
import com.mastfrog.jarmerge.support.AbstractJarFilter;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Escaper;
import static com.mastfrog.util.strings.Strings.sha1;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Coalesces all common license file names to a single file in the JAR named
 * META-INF/LICENSE.txt.
 *
 * @author Tim Boudreau
 */
public class MergeLicenseFiles extends AbstractJarFilter<LicenseConcatenator> {

    private static final String MERGED_LICENSE_FILE = "META-INF/LICENSE.txt";
    private static final String[] LICENSE_FILE_NAMES_WITH_LEADING_SLASH
            = new String[]{"/license", "/notice", "/credits", "/about"};
    private static final String LICENSE_HEAD = "# SmartJarMerge Coalesced License Format 1.0";
    private static final String JAR_SET_LINE_PREFIX = "# License or notice found in ";

    private LicenseConcatenator coa;

    @Override
    public String description() {
        return "Deduplicates and concatenates license files in the jar root and "
                + "under META-INF/ that use "
                + "common naming conventions into " + MERGED_LICENSE_FILE
                + ", noting which licenses came from where.";
    }

    @Override
    public LicenseConcatenator findCoalescer(String path, Path inJar, JarEntry entry, MergeLog log) {
        switch (path) {
            case "about.html":
            case "LICENSE":
            case "ASL2.0":
            case "LICENSE.txt":
            case "LICENSE.md":
            case "license":
            case "license.md":
            case "license.txt":
            case "META-INF/ASL2.0":
            case "META-INF/LICENSE":
            case "META-INF/LICENSE.md":
            case "META-INF/LICENSE.txt":
            case "META-INF/license":
            case "META-INF/NOTICE":
            case "META-INF/NOTICE.md":
            case "META-INF/NOTICE.txt":
            case "META-INF/NOTICE-BINARY":
            case "META-INF/NOTICE-binary":
            case "META-INF/notice":
            case "META-INF/notice-binary":
            case "META-INF/notice.md":
            case "META-INF/notice.txt":
            case "META-INF/license.txt":
            case "META-INF/CREDITS":
            case "META-INF/credits":
            case "META-INF/credits.txt":
                return coa();
            default:
                // Catches strays like META-INF/licenses-binary/LICENSE.protobuf.txt
                if (path.startsWith("META-INF/") && isPossibleLicense(path)) {
                    return coa();
                }
                return null;
        }
    }

    private static boolean isPossibleLicense(String path) {
        path = path.toLowerCase();
        int ix = path.lastIndexOf('/');
        return ix > 0
                && isPossibleLicenseTextFileExtension(path)
                && ix == lastPositionOf(path,
                        LICENSE_FILE_NAMES_WITH_LEADING_SLASH);
    }

    private static int lastPositionOf(String path, String... words) {
        path = path.toLowerCase();
        for (String w : words) {
            int ix = path.lastIndexOf(w);
            if (ix > 0) {
                return ix;
            }
        }
        return -1;
    }

    private static boolean isPossibleLicenseTextFileExtension(String path) {
        path = path.toLowerCase();
        if (path.indexOf('.') < 0) {
            // Files like "META-INF/NOTICE"
            return true;
        }
        return path.endsWith(".txt")
                || path.endsWith(".md")
                || path.endsWith(".html")
                || path.endsWith(".notice");
    }

    private LicenseConcatenator coa() {
        if (coa == null) {
            coa = new LicenseConcatenator(zeroDates());
        }
        return coa;
    }

    static final class LicenseConcatenator extends AbstractCoalescer {

        private final Map<String, Set<Path>> pathsForHash = new HashMap<>();
        private final Map<String, String> textForHash = new HashMap<>();

        public LicenseConcatenator(boolean z) {
            super(MERGED_LICENSE_FILE, z);
        }

        private String hashForText(String text) {
            return sha1(NonWordCharEscaper.INSTANCE.escape(text));
        }

        @Override
        protected boolean read(Path jar, JarEntry entry, JarFile file,
                InputStream in, MergeLog log) throws Exception {
            // License files frequestly differ by small amounts of whitespace and
            // formatting, so make a hash sans those things and use that as a key
            // to avoid duplicates
            String text = Streams.readString(in, UTF_8);
            if (consumeCoalescedLicenseFile(text)) {
                log.log("Found an existing coalesced license file in {0}. Merging it.",
                        file.getName());
                return true;
            }
            String hash = hashForText(text.trim().toLowerCase());
            if (!textForHash.containsKey(hash)) {
                textForHash.put(hash, text);
            }
            Set<Path> set = pathsForHash.computeIfAbsent(hash, hs -> new HashSet<>());
            int sz = set.size();
            set.add(jar);
            if (sz > 0) {
                log.log("Merge license {0} in {1} into {2} coalesced with {3} "
                        + "other copies.", entry.getName(), jar.getFileName(),
                        MERGED_LICENSE_FILE, sz);
            } else {
                log.log("Merge license {0} in {1} into {2}.", entry.getName(),
                        jar.getFileName(), MERGED_LICENSE_FILE);
            }
            return true;
        }

        private void writeText(String txt, JarOutputStream out) throws Exception {
            out.write(txt.getBytes(UTF_8));
        }

        private boolean consumeCoalescedLicenseFile(String text) {
            if (isCoalescedLicenseFile(text) && text.length() > LICENSE_HEAD.length() + 1) {
                text = text.substring(LICENSE_HEAD.length() + 1);
                StringBuilder curr = new StringBuilder();
                Obj<String> currHash = Obj.create();
                Runnable next = () -> {
                    if (curr.length() > 0 && currHash.isSet()) {
                        String theHash = currHash.set(null);
                        textForHash.putIfAbsent(theHash, curr.toString().trim());
                        curr.setLength(0);
                    }
                };
                for (String line : text.split("\n")) {
                    line = line.trim();
                    String newHash = jarsAndHash(line);
                    if (newHash != null) {
                        next.run();
                        currHash.set(newHash);
                    } else {
                        curr.append(line).append('\n');
                    }
                }
                next.run();
                return true;
            }
            return false;
        }

        private static boolean isCoalescedLicenseFile(String text) {
            return text.startsWith(LICENSE_HEAD + "\n");
        }

        @Override
        protected void write(JarEntry entry, JarOutputStream out, MergeLog log) throws Exception {
            Int curr = Int.create();
            writeText(LICENSE_HEAD + "\n", out);
            eachJarSetLicenseAndHash((jars, licenseText, licenseHash) -> {
                if (curr.increment() > 0) {
                    out.write('\n');
                    out.write('\n');
                }
                writeText(jarSetLine(jars, licenseHash), out);
                out.write('\n');
                writeText(licenseText, out);
            });
        }

        private static String jarSetLine(String jars, String hash) {
            return JAR_SET_LINE_PREFIX + jars + " sha1::" + hash + "\n";
        }

        private String jarsAndHash(String text) {
            text = text.trim();
            if (text.startsWith(JAR_SET_LINE_PREFIX)) {
                text = text.substring(JAR_SET_LINE_PREFIX.length());
                int ix = text.lastIndexOf(" sha1::");
                if (ix >= 0 && ix < text.length() - " sha1::".length()) {
                    String hash = text.substring(ix + " sha1::".length()).trim();
                    String jars = text.substring(0, ix);
                    for (String jar : jars.split(",")) {
                        jar = jar.trim();
                        pathsForHash.computeIfAbsent(hash, j -> new HashSet<>()).add(Paths.get(jar));
                    }
                    return hash;
                }
            }
            return null;
        }

        private void eachJarSetLicenseAndHash(ThrowingTriConsumer<String, String, String> c) throws Exception {
            for (Map.Entry<String, HashEntry> e : licenseAndHashForJarList().entrySet()) {
                c.accept(e.getKey(), e.getValue().text, e.getValue().hash);
            }
        }

        static final class HashEntry {

            final String hash;
            final String text;

            public HashEntry(String hash, String text) {
                this.hash = hash;
                this.text = text;
            }
        }

        private Map<String, HashEntry> licenseAndHashForJarList() {
            Map<String, HashEntry> result = new TreeMap<>();
            pathsForHash.forEach((licenseTextHash, jarPaths) -> {
                result.put(toS(jarPaths), new HashEntry(licenseTextHash, textForHash.get(licenseTextHash)));
            });
            return result;
        }

        private Map<String, String> licenseForJarList() {
            Map<String, String> result = new TreeMap<>();
            pathsForHash.forEach((licenseTextHash, jarPaths) -> {
                result.put(toS(jarPaths), textForHash.get(licenseTextHash));
            });
            return result;
        }

        String toS(Set<Path> paths) {
            Set<String> jarNames = new TreeSet<>();
            for (Path p : paths) {
                jarNames.add(p.getFileName().toString());
            }
            StringBuilder sb = new StringBuilder();
            for (String jarName : jarNames) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(jarName);
            }
            return sb.toString();
        }
    }

    static class NonWordCharEscaper implements Escaper {
        // XXX with the next release, can use the copy of this
        // code in Escaper.OMIT_NON_WORD_CHARACTERS

        private static final NonWordCharEscaper INSTANCE
                = new NonWordCharEscaper();

        @Override
        public CharSequence escape(char c) {
            if (!Character.isDigit(c) && !Character.isLetter(c)) {
                return "";
            }
            return Character.toString(c);
        }
    }
}
