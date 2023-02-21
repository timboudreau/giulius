/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.giulius.help;

import com.mastfrog.util.streams.Streams;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import static java.util.Arrays.fill;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reads files generated from <code>&#064;Setting</code> annotations and can
 * generate help output describing the properties and what they do.
 *
 * @author Tim Boudreau
 */
public final class GiuliusHelp {

    private static final String PATH = "META-INF/settings/_generated-descriptions.properties";

    private GiuliusHelp() {
        throw new AssertionError();
    }

    /**
     * Returns the set of shortcuts defined in &#064;Setting annotations.
     *
     * @return A map of shortcut characters for single-dash line switches to the
     * settings they correspond to
     */
    public static Map<Character, String> shortcutMap() {
        Map<Character, String> result = new TreeMap<>();
        visitSettings(set -> {
            set.shortcut().ifPresent(ch -> result.put(ch, set.key()));
        });
        return result;
    }

    /**
     * Get a map of all defined settings, sorted, mapped to tier.
     *
     * @return A map
     */
    public static Map<Tier, Set<SettingRecord>> settings() {
        Map<Tier, Set<SettingRecord>> result = new EnumMap<>(Tier.class);
        visitSettings(rec -> {
            result.computeIfAbsent(rec.tier(), t -> new TreeSet<>()).add(rec);
        });
        return result;
    }

    /**
     * Visit all settings defined by annotation processors on the classpath.
     *
     * @param recordConsumer A consumer
     */
    public static void visitSettings(Consumer<? super SettingRecord> recordConsumer) {
        InputStream[] allStreams = Streams.locate(PATH);
        if (allStreams == null) {
            return;
        }
        for (InputStream in : allStreams) {
            Properties props = new Properties();
            try (InputStream i = in) {
                props.load(i);
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
            for (String k : props.stringPropertyNames()) {
                SettingRecordImpl.parse(k, props.getProperty(k)).ifPresent(rec -> {
                    recordConsumer.accept(rec);
                });
            }
        }
    }

    /**
     * Generate a text-friendly, markdown-ish report of all visible settings.
     *
     * @return a report
     */
    public static String report() {
        return report(val -> null);
    }

    public static String report(Function<String, String> f) {
        StringBuilder sb = new StringBuilder();
        Map<Tier, Set<SettingRecord>> settings = settings();
        settings.forEach((tier, group) -> {
            if (!group.isEmpty()) {
                String s = tier.toString();
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(s).append('\n');
                char[] c = new char[s.length()];
                fill(c, '-');
                sb.append(c);
                sb.append("\n");
                group.forEach(rec -> {
                    SettingRecord r = rec.withDefaultValue(f);
                    sb.append("\n--");
                    sb.append(r.key());
                    r.shortcut().ifPresent(ch -> {
                        sb.append(" / -").append(ch);
                    });
                    for (String ln : wrapLines(r.description())) {
                        sb.append("\n\t").append(ln);
                    }
                    sb.append('\n');
                    r.defaultValue().ifPresent(def -> {
                        sb.append("\n\tdefault:\t").append(r.type().quote(def, false));
                    });
                    sb.append("\n\ttype:\t").append(r.type());
                    sb.append("\n\tfrom:\t").append(r.definingClass()).append(".").append(r.definingField())
                            .append('\n');
                });
            }
        });
        if (sb.length() > 0) {
            sb.append('\n');
        }
        return sb.toString();
    }

    static List<String> wrapLines(String s) {
        List<String> result = new ArrayList<>();
        StringBuilder currLine = new StringBuilder();
        Runnable flush = () -> {
            String ln = currLine.toString();
            if (ln.trim().length() > 0) {
                result.add(ln);
            }
            currLine.setLength(0);
        };
        Consumer<String> onWord = word -> {
            if (currLine.length() > 0 && currLine.charAt(currLine.length() - 1) != '\t') {
                currLine.append(' ');
            }
            if (currLine.length() + word.length() > 72) {
                flush.run();
            }
            currLine.append(word);
        };
        for (String word : s.split("\\s+")) {
            onWord.accept(word);
        }
        flush.run();
        return result;
    }

    public static void printHelpAndExit(Class<?> launcherClass, String usage, int code, String settingsNamespace,
            String... settingsPaths) {
        URL u = launcherClass.getProtectionDomain().getCodeSource().getLocation();
        String file = Paths.get(u.getPath()).getFileName().toString();
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: java -jar ").append(file).append(" --arg1 val1 --someBooleanArg\n\n");
        if (usage != null) {
            sb.append(usage).append("\n\n");
        }
        if (settingsPaths.length > 0) {
            sb.append("Command-line arguments described below can also be defined in");
            if (settingsPaths.length == 1) {
                sb.append(settingsPaths[0]).append(settingsNamespace).append(".properties");
            } else {
                sb.append(" any of");
                for (String p : settingsPaths) {
                    sb.append("\n\t * ").append(p).append(settingsNamespace).append(".properties");
                    if ("./".equals(p)) {
                        sb.append(" (process working dir)");
                    }
                }
                sb.append("\n\n");
            }
        }
        sb.append(GiuliusHelp.report());
        System.out.println(sb);
        System.exit(code);
    }

}
