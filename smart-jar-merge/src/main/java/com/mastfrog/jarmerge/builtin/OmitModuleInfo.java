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

/**
 *
 * @author Tim Boudreau
 */
public class OmitModuleInfo extends AbstractJarFilter<Coalescer> {

    @Override
    public boolean omit(String path, Path inJar, MergeLog log) {
        return "module-info.class".equals(path)
                || isVersionedModuleInfo(path);
    }

    private static boolean isVersionedModuleInfo(String path) {
        return path.startsWith("META-INF/versions/")
                && path.endsWith("/module-info.class");
    }

    @Override
    public String description() {
        return "Omits the module descriptor from the default package, which "
                + "may exist multiply when merging JARs.";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }
}
