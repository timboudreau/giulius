/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
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
package com.mastfrog.settings;

import com.mastfrog.util.streams.Streams;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 *
 * @author Tim Boudreau
 */
public final class ShortcutsBuilder<T> {

    private final String namespace;
    private final Function<Map<Character, String>, T> converter;
    private final Map<Character, String> longNameForShortcut = new LinkedHashMap<>();
    private static final Pattern PAT = Pattern.compile("^\\s*(\\S)[\\s:]*(\\S*+)\\s*$");

    ShortcutsBuilder(String namespace, Function<Map<Character, String>, T> converter) {
        this.namespace = namespace;
        this.converter = converter;
    }

    public T build() {
        return converter.apply(longNameForShortcut);
    }

    public ShortcutsBuilder<T> loadFromClasspath() {
        return loadFromClasspath(System.err::println);
    }

    public ShortcutsBuilder<T> loadFromClasspath(Consumer<String> errors) {
        String filename = namespace + "-shortcuts.list";
        InputStream[] streams = Streams.locate(filename);
        for (InputStream in : streams) {
            Scanner scanner = new Scanner(in, "UTF-8");
            int lineNumber = 0;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.isEmpty() || line.charAt(0) == '#' || line.length() < 3) {
                    lineNumber++;
                    continue;
                }
                char start = line.charAt(0);
                int ix = line.indexOf(':', 1);
                if (ix < 0) {
                    errors.accept("Unparseable shortcut line " + lineNumber + " in " + in + ": '" + line + "'.");
                    continue;
                }
                String remainder = line.substring(ix + 1).trim();
                String old = longNameForShortcut.put(start, remainder);
                if (old != null && !old.equals(remainder)) {
                    errors.accept("Shortcut '" + start + "' mapped to '" + remainder
                        + " overrides previous mapping to " + old);
                }
                lineNumber++;
            }
        }
        return this;
    }

    public ShortcutBuilder<T> map(char c) {
        return new ShortcutBuilder<>(c, this);
    }

    public static final class ShortcutBuilder<T> {

        private final char c;
        private final ShortcutsBuilder<T> parent;

        ShortcutBuilder(char c, ShortcutsBuilder<T> parent) {
            this.c = c;
            this.parent = parent;
        }

        public ShortcutsBuilder<T> to(String longName) {
            parent.longNameForShortcut.put(c, longName);
            return parent;
        }
    }
}
