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
import com.mastfrog.util.strings.Strings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public class OmitExcludedPrefixes extends AbstractJarFilter<Coalescer> {

    private final List<String> prefixen;

    public OmitExcludedPrefixes() {
        prefixen = Collections.emptyList();
    }

    public OmitExcludedPrefixes(JarMerge mg) {
        prefixen = new ArrayList<>(mg.excludePathPrefixes);
        Collections.sort(prefixen, (a, b) -> Integer.compare(b.length(), a.length()));
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public boolean omit(String path, Path inJar, MergeLog log) {
        if (prefixen.isEmpty()) {
            return false;
        }
        for (String pfx : prefixen) {
            if (path.length() < pfx.length()) {
                continue;
            } else if (path.length() == pfx.length() && path.equals(pfx)) {
                return true;
            } else {
                if (path.startsWith(pfx)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + "(" + Strings.join(",", prefixen) + ")";
    }

    @Override
    public JarFilter<Coalescer> configureInstance(JarMerge jarMerge) {
        return new OmitExcludedPrefixes(jarMerge);
    }

    @Override
    public String description() {
        return "Omits jar-paths which start with any of the user-specified exclude prefixes";
    }
}
