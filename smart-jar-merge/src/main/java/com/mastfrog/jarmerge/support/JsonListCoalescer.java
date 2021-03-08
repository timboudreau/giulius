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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastfrog.jarmerge.MergeLog;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 *
 * @author Tim Boudreau
 */
public class JsonListCoalescer extends AbstractCoalescer {

    private final Map<Path, List<Object>> objsForPath = new LinkedHashMap<>();
    private final boolean unique;

    public JsonListCoalescer(String name, boolean zeroDates) {
        this(name, false, zeroDates);
    }

    public JsonListCoalescer(String name, boolean unique, boolean zeroDates) {
        super(name, zeroDates);
        this.unique = unique;
    }

    @Override
    protected boolean read(Path jar, JarEntry entry, JarFile file, InputStream in, MergeLog log) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Object[] all = mapper.readValue(in, Object[].class);
        objsForPath.put(jar, Arrays.asList(all));
        return true;
    }

    private List<Object> objects(MergeLog log) {
        List<Object> result = new ArrayList<>();
        Set<Object> seen = unique ? new HashSet<>() : null;
        objsForPath.forEach((path, list) -> {
            if (seen != null) {
                for (Object o : list) {
                    if (!seen.contains(o)) {
                        seen.add(o);
                        result.add(o);
                    }
                }
            } else {
                result.addAll(list);
            }
        });
        return result;
    }

    @Override
    protected void write(JarEntry entry, JarOutputStream out, MergeLog log) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        out.write(mapper.writeValueAsBytes(objects(log)));
    }
}
