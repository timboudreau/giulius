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
import com.mastfrog.util.fileformat.PropertiesFileUtils;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 *
 * @author Tim Boudreau
 */
public class PropertiesFileCoalescer extends AbstractCoalescer {

    private final Map<Path, Properties> properties = new LinkedHashMap<>(8);
    private final boolean sortedKeys;

    public PropertiesFileCoalescer(String name, boolean zeroDates) {
        this(name, zeroDates, true);
    }

    public PropertiesFileCoalescer(String name, boolean zeroDates, boolean sortedKeys) {
        super(name, zeroDates);
        this.sortedKeys = sortedKeys;
    }

    @Override
    protected boolean read(Path jar, JarEntry entry, JarFile file, InputStream in, MergeLog log) throws Exception {
        Properties props = new Properties();
        props.load(in);
        if (!props.isEmpty()) {
            properties.put(jar, props);
            return true;
        }
        return false;
    }

    @Override
    protected void write(JarEntry entry, JarOutputStream out, MergeLog log) throws Exception {
        Properties output = new Properties();
        Map<String, Path> keyForFile = new HashMap<>();
        StringBuilder jarNamesComment = new StringBuilder();
        properties.forEach((jar, props) -> {
            if (!props.isEmpty()) {
                if (jarNamesComment.length() > 0) {
                    jarNamesComment.append(", ");
                }
                jarNamesComment.append(jar.getFileName());
            }
            Collection<? extends String> keys;
            if (sortedKeys) {
                List<String> all = new ArrayList<>(props.stringPropertyNames());
                Collections.sort(all);
                keys = all;
            } else {
                keys = props.stringPropertyNames();
            }
            for (String k : keys) {
                Path p = keyForFile.get(k);
                String val = props.getProperty(k);
                if (p != null) {
                    Properties other = properties.get(p);
                    String a = other.getProperty(k);
                    if (!Objects.equals(a, val)) {
                        log.warn("Property `{0}` exists in both "
                                + "{1} and {2} with different values. "
                                + "Using value from the former.",
                                k, p.getFileName(), jar.getFileName());
                    }
                    continue;
                }
                keyForFile.put(k, jar);
                output.setProperty(k, val);
            }
        });
        if (keyForFile.size() > 1) {
            jarNamesComment.insert(0, "Merged from properties files in ");
        } else {
            jarNamesComment.insert(0, "Processed from properties files in ");
        }
        jarNamesComment.append(" by ").append(getClass().getSimpleName())
                .append(" in smart-jar-merge");
        PropertiesFileUtils.savePropertiesFile(output, out,
                jarNamesComment.toString(), false);
    }
}
