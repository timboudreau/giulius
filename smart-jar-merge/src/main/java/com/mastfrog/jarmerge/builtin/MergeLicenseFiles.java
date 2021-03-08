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
import com.mastfrog.jarmerge.spi.Coalescer;
import com.mastfrog.jarmerge.spi.JarFilter;
import com.mastfrog.jarmerge.support.AbstractCoalescer;
import com.mastfrog.jarmerge.support.AbstractJarFilter;
import com.mastfrog.jarmerge.support.Concatenator;
import com.mastfrog.util.streams.Streams;
import com.mastfrog.util.strings.Strings;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
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
    private LicenseConcatenator coa;

    @Override
    public String description() {
        return "Dedups and concatenates license files in META-INF that use "
                + "common naming conventions into " + MERGED_LICENSE_FILE
                + ", noting which licenses came from where.";
    }

    @Override
    public LicenseConcatenator findCoalescer(String path, Path inJar, JarEntry entry, MergeLog log) {
        switch (path) {
            case "META-INF/LICENSE":
            case "META-INF/LICENSE.txt":
            case "META-INF/license":
            case "META-INF/NOTICE":
            case "META-INF/NOTICE.txt":
            case "META-INF/notice":
            case "META-INF/notice.txt":
            case "META-INF/license.txt":
            case "META-INF/CREDITS":
            case "META-INF/credits":
            case "META-INF/credits.txt":
                if (coa == null) {
                    coa = new LicenseConcatenator(zeroDates());
                }
                return coa;
            default:
                return null;
        }
    }

    static final class LicenseConcatenator extends AbstractCoalescer {

        private final Map<String, Set<Path>> pathsForHash = new HashMap<>();
        private final Map<String, String> textForHash = new HashMap<>();

        public LicenseConcatenator(boolean z) {
            super(MERGED_LICENSE_FILE, z);
        }

        private String hashForText(String text) {
            return Strings.sha1(text.replaceAll("\\s+", ""));
        }

        @Override
        protected boolean read(Path jar, JarEntry entry, JarFile file, InputStream in, MergeLog log) throws Exception {
            log.log("Merge license {0} in {1} into {2}", entry.getName(), jar.getFileName(), MERGED_LICENSE_FILE);
            // License files frequestly differ by small amounts of whitespace and
            // formatting, so make a hash sans those things and use that as a key
            // to avoid duplicates
            String text = Streams.readString(in, UTF_8);
            String hash = hashForText(text.trim().toLowerCase());
            if (!textForHash.containsKey(hash)) {
                textForHash.put(hash, text);
            }
            pathsForHash.computeIfAbsent(hash, hs -> new HashSet<>()).add(jar);
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
}
