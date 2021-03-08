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

import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.spi.Coalescer;
import com.mastfrog.jarmerge.spi.JarFilter;
import com.mastfrog.jarmerge.support.AbstractJarFilter;
import com.mastfrog.jarmerge.support.Concatenator;
import static com.mastfrog.jarmerge.support.Concatenator.Features.ENSURE_TRAILING_NEWLINE;
import static com.mastfrog.jarmerge.support.Concatenator.Features.OMIT_BLANK_LINES;
import static com.mastfrog.jarmerge.support.Concatenator.Features.OMIT_DUPLICATE_LINES;
import static com.mastfrog.jarmerge.support.Concatenator.Features.TRIM_LINES;
import static com.mastfrog.jarmerge.support.Concatenator.Features.ZERO_DATES;
import static com.mastfrog.jarmerge.support.Concatenator.Features.maybeWithZeroDates;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;

/**
 *
 * @author Tim Boudreau
 */
public class ConcatenateMetaInfServices extends AbstractJarFilter<Coalescer> {

    private final Map<String, Concatenator> concatForPath = new HashMap<>();

    private static final String SERVICES = "META-INF/services/";
    private static final String NAMED_SERVICES = "META-INF/namedservices/";

    @Override
    public String description() {
        return "Concatenates service registration files in " + SERVICES;
    }

    @Override
    public Coalescer findCoalescer(String path, Path inJar, JarEntry entry, MergeLog log) {
        if (path.startsWith(SERVICES)) {
            String tail = path.substring(SERVICES.length());
            if (!tail.contains("/")) {
                Concatenator.Features[] features
                        = maybeWithZeroDates(zeroDates(),
                                OMIT_BLANK_LINES,
                                OMIT_DUPLICATE_LINES,
                                TRIM_LINES, ENSURE_TRAILING_NEWLINE);
                return concatForPath.computeIfAbsent(path, pt -> new Concatenator(pt, features));
            }
        }
        if (path.startsWith(NAMED_SERVICES)) {
            Concatenator.Features[] features
                    = maybeWithZeroDates(zeroDates(),
                            OMIT_BLANK_LINES,
                            OMIT_DUPLICATE_LINES,
                            TRIM_LINES, ENSURE_TRAILING_NEWLINE);
            return concatForPath.computeIfAbsent(path, pt -> new Concatenator(pt, features));
        }
        return null;
    }
}
