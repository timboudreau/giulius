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

import com.mastfrog.jarmerge.JarMerge;
import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.spi.Coalescer;
import com.mastfrog.jarmerge.spi.JarFilter;
import java.nio.file.Path;
import java.util.jar.JarEntry;

/**
 *
 * @author Tim Boudreau
 */
public class AbstractJarFilter<C extends Coalescer> implements JarFilter<C> {

    private boolean zeroDates;

    @Override
    public final C coalescer(String path, Path inJar, JarEntry entry, MergeLog log) {
        if (entry.isDirectory()) {
            return null;
        }
        return findCoalescer(path, inJar, entry, log);
    }

    protected C findCoalescer(String path, Path inJar, JarEntry entry, MergeLog log) {
        return null;
    }

    @Override
    public int precedence() {
        if (getClass().getSimpleName().startsWith("Omit")) {
            return 100;
        } else {
            return 200;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public JarFilter<C> configureInstance(JarMerge jarMerge) {
        try {
            JarFilter<C> result = (JarFilter<C>) super.clone();
            result.setZeroDates(jarMerge.zerodates);
            return result;
        } catch (CloneNotSupportedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String toString() {
        return name();
    }

    @Override
    public void setZeroDates(boolean val) {
        this.zeroDates = val;
    }

    protected final boolean zeroDates() {
        return zeroDates;
    }
}
