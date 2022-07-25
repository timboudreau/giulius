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
package com.mastfrog.jarmerge.support;

import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.spi.ClassNameRewriter;
import static com.mastfrog.jarmerge.support.Concatenator.Features.TRANSFORM_CLASS_NAMES;
import static com.mastfrog.jarmerge.support.Concatenator.Features.TRANSFORM_FILE_NAME;
import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 * Concatenates all the files it sees as it sees them, and outputs a
 * concatenated version of all of them.
 *
 * @author Tim Boudreau
 */
public final class Concatenator extends AbstractCoalescer {

    private final Map<Path, List<String>> linesForPath = new LinkedHashMap<>(7);
    private final Charset encoding;
    private final ClassNameRewriter rewriter;
    private Set<Features> features;

    public Concatenator(String name, Features... features) {
        this(name, UTF_8, features);
    }

    public Concatenator(String name, Charset encoding, Features... features) {
        super(name, Arrays.asList(features).contains(Features.ZERO_DATES));
        this.encoding = encoding;
        Set<Features> f = EnumSet.noneOf(Features.class);
        if (features.length > 0) {
            f.addAll(Arrays.asList(features));
        }
        this.features = Collections.unmodifiableSet(f);
        if (this.features.contains(TRANSFORM_CLASS_NAMES) || this.features.contains(TRANSFORM_FILE_NAME)) {
            rewriter = ClassNameRewriter.get();
        } else {
            rewriter = null;
        }
    }

    @Override
    protected boolean isTransformFileNames() {
        return features.contains(TRANSFORM_FILE_NAME);
    }

    @Override
    protected boolean read(Path jar, JarEntry entry, JarFile file, InputStream in, MergeLog log) throws Exception {
        String content = Streams.readString(in, UTF_8);
        List<String> lns = Arrays.asList(content.split("\r?\n+"));
        if (!lns.isEmpty()) {
            linesForPath.computeIfAbsent(jar, j -> new ArrayList<>()).addAll(lns);
            return true;
        }
        return false;
    }

    @Override
    protected void write(JarEntry entry, JarOutputStream out, MergeLog log) throws Exception {
        List<String> lines = lines(log);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                newline(out);
            }
            String ln = lines.get(i);
            out.write(ln.getBytes(encoding));
        }
        if (features.contains(Features.ENSURE_TRAILING_NEWLINE)) {
            newline(out);
        }
    }

    public void newline(JarOutputStream out) throws IOException {
        if (features.contains(Features.WINDOWS_NEWLINES)) {
            out.write('\r');
            out.write('\n');
        } else {
            out.write('\n');;
        }
    }

    private List<String> lines(MergeLog log) {
        ClassNameRewriter rewriter = this.rewriter;
        List<String> result = new ArrayList<>();
        Map<String, Path> pathForLine = features.contains(Features.OMIT_DUPLICATE_LINES) ? new HashMap<>(256) : null;
        linesForPath.forEach((pth, lns) -> {
            if (features.contains(Features.SHELL_COMMENT_HEADINGS)) {
                result.add("# Merged from " + pth.getFileName());
            }
            for (int i = 0; i < lns.size(); i++) {
                String line = lns.get(i);
                if (features.contains(Features.TRIM_LINES)) {
                    line = line.trim();
                }
                if (features.contains(Features.OMIT_BLANK_LINES) && line.isEmpty()) {
                    continue;
                }
                if (features.contains(Features.OMIT_SHELL_COMMENT_LINES) && !line.isEmpty() && line.charAt(0) == '#') {
                    continue;
                }
                if (rewriter != null) {
                    line = rewriter.apply(line);
                }
                if (pathForLine != null) {
                    Path old = pathForLine.get(line);
                    if (old != null) {
                        if (!line.trim().isEmpty()) {
                            log.warn("Omitting line " + (i + 1) + " of "
                                    + path() + " from " + pth.getFileName()
                                    + " because the same line was seen in " + old.getFileName());
                        }
                        continue;
                    } else {
                        pathForLine.put(line, pth);
                    }
                }
                result.add(line);
            }
        });
        return result;
    }

    public enum Features {
        OMIT_BLANK_LINES,
        OMIT_DUPLICATE_LINES,
        OMIT_SHELL_COMMENT_LINES,
        TRIM_LINES,
        ENSURE_TRAILING_NEWLINE,
        WINDOWS_NEWLINES,
        SHELL_COMMENT_HEADINGS,
        ZERO_DATES,
        TRANSFORM_CLASS_NAMES,
        TRANSFORM_FILE_NAME;

        public static Features[] maybeWithZeroDates(boolean z, Features... orig) {
            if (!z) {
                return orig;
            }
            Arrays.sort(orig);
            if (Arrays.binarySearch(orig, ZERO_DATES) < 0) {
                orig = Arrays.copyOf(orig, orig.length + 1);
                orig[orig.length - 1] = ZERO_DATES;
            }
            return orig;
        }
    }
}
