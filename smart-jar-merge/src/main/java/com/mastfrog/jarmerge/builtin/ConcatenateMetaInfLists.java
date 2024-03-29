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
import com.mastfrog.jarmerge.support.AbstractJarFilter;
import com.mastfrog.jarmerge.support.Concatenator;
import static com.mastfrog.jarmerge.support.Concatenator.Features.ENSURE_TRAILING_NEWLINE;
import static com.mastfrog.jarmerge.support.Concatenator.Features.OMIT_BLANK_LINES;
import static com.mastfrog.jarmerge.support.Concatenator.Features.TRANSFORM_CLASS_NAMES;
import static com.mastfrog.jarmerge.support.Concatenator.Features.maybeWithZeroDates;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;

/**
 *
 * @author Tim Boudreau
 */
public class ConcatenateMetaInfLists extends AbstractJarFilter<Concatenator> {

    private final Map<String, Concatenator> concatForPath = new HashMap<>();

    @Override
    public String description() {
        return "Concatenates .list files in META-INF/http and META-INF/settings "
                + "generated by annotation processors for Acteur and Guice.";
    }

    @Override
    public Concatenator findCoalescer(String name, Path inJar, JarEntry entry, MergeLog log) {
        Concatenator.Features[] features
                = maybeWithZeroDates(zeroDates(),
                        OMIT_BLANK_LINES,
                        ENSURE_TRAILING_NEWLINE,
                        TRANSFORM_CLASS_NAMES);

        switch (name) {
            case "META-INF/http/pages.list":
            case "META-INF/http/modules.list":
            case "META-INF/http/numble.list":
            case "META-INF/settings/namespaces.list":
                return concatForPath.computeIfAbsent(name, nm -> new Concatenator(nm,
                        features));
            default:
                if (name.startsWith("META-INF") && name.endsWith(".registrations")) {
                    return concatForPath.computeIfAbsent(name, nm -> new Concatenator(nm,
                            features));
                }
                return null;
        }
    }
}
