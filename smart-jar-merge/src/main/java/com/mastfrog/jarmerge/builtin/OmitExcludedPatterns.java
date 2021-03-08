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

import com.mastfrog.jarmerge.JarMerge;
import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.spi.Coalescer;
import com.mastfrog.jarmerge.spi.JarFilter;
import com.mastfrog.jarmerge.support.AbstractJarFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
public class OmitExcludedPatterns extends AbstractJarFilter<Coalescer> {

    private final List<Pattern> patterns = new ArrayList<>();

    public OmitExcludedPatterns() {
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public String description() {
        return "Omits the exclude patterns specified on the command-line.";
    }

    public OmitExcludedPatterns(JarMerge mg) {
        for (String pat : mg.excludePatterns) {
            patterns.add(patternFor(pat));
        }
    }

    @Override
    public boolean omit(String path, Path inJar, MergeLog log) {
        if (patterns.isEmpty()) {
            return false;
        }
        for (Pattern p : patterns) {
            if (p.matcher(path).find()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public JarFilter<Coalescer> configureInstance(JarMerge jarMerge) {
        return new OmitExcludedPatterns(jarMerge);
    }

    private static Pattern patternFor(String pattern) {
        String[] parts = pattern.split("/");
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (s.isEmpty()) {
                continue;
            }
            switch (s) {
                case "**":
                    sb.append(".*");
                    break;
                case "?":
                    sb.append("[^\\/]+?");
                    break;
                case "*":
                    sb.append("[^\\/]*?");
                    break;
                default:
                    if (s.contains("*") || s.contains("?")) {
                        int max = s.length();
                        StringBuilder curr = new StringBuilder();
                        for (int j = 0; j < max; j++) {
                            char c = s.charAt(j);
                            if (c == '*' || c == '?') {
                                boolean any = c == '*';
                                if (curr.length() > 0) {
                                    sb.append(Pattern.quote(curr.toString()));
                                    curr.setLength(0);
                                }
                                if (any) {
                                    sb.append("[^\\/]*?");
                                } else {
                                    sb.append("[^\\/]+?");
                                }
                            } else {
                                sb.append(c);
                            }
                        }
                        if (curr.length() > 0) {
                            sb.append(Pattern.quote(curr.toString()));
                            curr.setLength(0);
                        }
                    } else {
                        sb.append(Pattern.quote(s));
                    }
            }
            if (i != parts.length - 1) {
                sb.append("\\/");
            }
        }
        return Pattern.compile(sb.toString());
    }
}
