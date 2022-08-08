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
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.builtin.MergeLicenseFiles.LicenseConcatenator;
import com.mastfrog.jarmerge.support.AbstractCoalescer;
import com.mastfrog.jarmerge.support.AbstractJarFilter;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Escaper;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
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
            return NonWordCharEscaper.INSTANCE.escape(text);
        }

        @Override
        protected boolean read(Path jar, JarEntry entry, JarFile file,
                InputStream in, MergeLog log) throws Exception {
            // License files frequestly differ by small amounts of whitespace and
            // formatting, so make a hash sans those things and use that as a key
            // to avoid duplicates
            String text = Streams.readString(in, UTF_8);
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

        private void writeLine(String txt, JarOutputStream out) throws Exception {
            out.write(txt.getBytes(UTF_8));
        }

        @Override
        protected void write(JarEntry entry, JarOutputStream out, MergeLog log) throws Exception {
            Int curr = Int.create();
            eachJarSetAndLicense((jars, licenseText) -> {
                if (curr.increment() > 0) {
                    out.write('\n');
                    out.write('\n');
                }
                writeLine("# License or notice found in " + jars, out);
                out.write('\n');
                writeLine(licenseText, out);
            });
        }

        private void eachJarSetAndLicense(ThrowingBiConsumer<String, String> c) throws Exception {
            for (Map.Entry<String, String> e : licenseForJarList().entrySet()) {
                c.accept(e.getKey(), e.getValue());
            }
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
