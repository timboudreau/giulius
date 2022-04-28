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
package com.mastfrog.jarmerge.spi;

import com.mastfrog.jarmerge.MergeLog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 *
 * @author Tim Boudreau
 */
public interface Coalescer extends Comparable<Coalescer> {

    String path();

    void writeCoalesced(JarOutputStream out, MergeLog log) throws Exception;

    void add(Path jar, JarEntry entry, JarFile in, MergeLog log) throws Exception;

    @Override
    default int compareTo(Coalescer o) {
        return path().compareTo(o.path());
    }

    default List<String> indexPaths() {
        String nm = path();
        // Only folders and files in the package root are included in
        // the index
        if (!nm.contains("/")) {
            return Arrays.asList(nm);
        }
        String[] parts = nm.split("/");
        List<String> result = new ArrayList<>(parts.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(parts[i]);
            result.add(sb.toString() + "/");
        }
        return result;
    }
}
