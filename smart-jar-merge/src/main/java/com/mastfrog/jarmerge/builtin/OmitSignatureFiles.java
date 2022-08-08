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
import com.mastfrog.jarmerge.support.AbstractJarFilter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
public class OmitSignatureFiles extends AbstractJarFilter<Coalescer> {

    private static final Pattern SIG1 = Pattern.compile("META-INF\\/[^\\/]*\\.SF");
    private static final Pattern SIG2 = Pattern.compile("META-INF\\/[^\\/]*\\.DSA");
    private static final Pattern SIG3 = Pattern.compile("META-INF\\/[^\\/]*\\.RSA");
    private static final String INDEX = "META-INF/INDEX.LIST";
    private static final String DEPENDENCIES = "META-INF/DEPENDENCIES";
    private static final String MANIFEST = "META-INF/MANIFEST.MF";
    private static final List<String> MUST_SKIP = Arrays.asList(DEPENDENCIES,
            INDEX, MANIFEST);

    private static final Pattern[] patterns = new Pattern[]{SIG1, SIG2, SIG3};

    @Override
    public String description() {
        return "Omits jar signature files and similar which will be wrong in a "
                + "merged jar.";
    }

    @Override
    public int precedence() {
        return 10;
    }

    @Override
    public boolean isCritical() {
        return true;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    private boolean matches(String path) {
        if (MUST_SKIP.contains(path)) {
            return true;
        }
        for (Pattern p : patterns) {
            if (p.matcher(path).find()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean omit(String path, Path inJar, MergeLog log) {
        boolean result = matches(path);
        if (result) {
            log.log("Omit {0}", path);
        }
        return result;
    }
}
