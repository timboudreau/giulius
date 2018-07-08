/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

package com.mastfrog.maven.merge.configuration;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Utilities for writing properties files repeatably.
 *
 * @author Tim Boudreau
 */
public class PropertiesFileUtils {

    private PropertiesFileUtils() {
    }

    /**
     * Saves a properties file in standard properties-file format, sans
     * the generated Date comment which makes builds non-repeatable.
     *
     * @param props The properties
     * @param out The output
     * @param comment An optional comment
     * @param close If true, close the stream when done
     * @throws IOException if something goes wrong
     */
    public static final void savePropertiesFile(Properties props, OutputStream out, String comment, boolean close) throws IOException {
        // Stores properties file without date comments, with consistent key ordering and line terminators, for
        // repeatable builds
        List<String> keys = new ArrayList<>(props.stringPropertyNames());
        Collections.sort(keys);
        List<String> lines = new ArrayList<>();
        if (comment != null) {
            lines.add("# " + comment);
        }
        for (String key : keys) {
            String val = props.getProperty(key);
            key = convert(key, true);
            /* No need to escape embedded and trailing spaces for value, hence
                 * pass false to flag.
             */
            val = convert(val, false);
            lines.add(key + "=" + val);

        }
        printLines(lines, out, ISO_8859_1, close);
    }

    private static String convert(String keyVal, boolean escapeSpace) {
        int len = keyVal.length();
        StringBuilder sb = new StringBuilder(len * 2 < 0 ? Integer.MAX_VALUE : len * 2);

        for (int i = 0; i < len; i++) {
            char ch = keyVal.charAt(i);
            if ((ch > 61) && (ch < 127)) {
                if (ch == '\\') {
                    sb.append("\\\\");
                } else {
                    sb.append(ch);
                }
                continue;
            }
            switch (ch) {
                case ' ':
                    sb.append(escapeSpace ? ESCAPED_SPACE : ' ');
                    break;
                case '\n':
                    appendEscaped('n', sb);
                    break;
                case '\r':
                    appendEscaped('r', sb);
                    break;
                case '\t':
                    appendEscaped('t', sb);
                    break;
                case '\f':
                    appendEscaped('f', sb);
                    break;
                case '#':
                case '=':
                case '!':
                case ':':
                    sb.append('\\').append(ch);
                    break;
                default:
                    if (((ch < 0x0020) || (ch > 0x007e))) {
                        appendEscapedHex(ch, sb);
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }

    private static void appendEscaped(char c, StringBuilder sb) {
        sb.append('\\').append(c);
    }

    private static void appendEscapedHex(char c, StringBuilder sb) {
        sb.append('\\').append('u');
        for (int i : NIBBLES) {
            char hex = HEX[(c >> i) & 0xF];
            sb.append(hex);
        }
    }

    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;
    private static final char[] ESCAPED_SPACE = "\\ ".toCharArray();
    private static final int[] NIBBLES = new int[]{12, 8, 4, 0};
    private static final char[] HEX = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public static int printLines(Iterable<String> lines, OutputStream out, boolean close) throws IOException {
        return printLines(lines, out, UTF_8, close);
    }

    public static int printLines(Iterable<String> lines, OutputStream out, Charset encoding, boolean close) throws IOException {
        // Ensures UTF-8 encoding and avoids non-repeatable builds due to Windows line endings
        int count = 0;
        for (String line : lines) {
            byte[] bytes = line.getBytes(encoding);
            out.write(bytes);
            out.write('\n');
            count++;
        }
        if (close) {
            out.close();
        }
        return count;
    }
}
