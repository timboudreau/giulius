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

import java.util.Optional;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
public abstract class SettingRecord implements Comparable<SettingRecord> {

    static final String PROPS_DELIMITER = "!;`";

    SettingRecord() {
        // package private to prevent subclassing outside this package
    }

    public static Optional<SettingRecord> parse(String key, String line) {
        String[] parts = line.split(PROPS_DELIMITER);
        switch (parts.length) {
            case 8:
            case 9:
                break;
            default:
                return Optional.empty();
        }
        int tier = Integer.parseInt(parts[0]);
        String type = parts[1];
        String desc = parts[2];
        String definedInField = parts[3];
        String definedInClass = parts[4];
        String definedInPackage = parts[5];
        String pattern = parts[6].isEmpty() ? null : parts[6];
        char shortcut = parts[7].length() > 0 ? parts[7].charAt(0) : 0;
        String defValue = null;
        if (parts.length == 9) {
            defValue = parts[8];
        }
        return Optional.of(new SettingRecordImpl(key, desc, type, pattern, shortcut,
                definedInField, definedInClass, definedInPackage, defValue, tier));
    }

    public abstract ValueType type();

    public abstract Tier tier();

    public abstract String definingField();

    public abstract String definingClass();

    public abstract String definingPackage();

    public abstract Optional<String> pattern();

    public abstract Optional<Character> shortcut();

    public abstract Optional<String> defaultValue();

    public abstract String key();

    public abstract String description();

    public final String provenance() {
        return definingPackage() + "." + definingClass() + "." + definingField();
    }

    public abstract SettingRecord withDefaultValue(Function<String, String> f);

    @Override
    public int compareTo(SettingRecord o) {
        int result = tier().compareTo(o.tier());
        if (result == 0) {
            result = key().compareTo(o.key());
        }
        if (result == 0) {
            result = definingField().compareTo(o.definingField());
        }
        if (result == 0) {
            result = definingClass().compareTo(o.definingClass());
        }
        if (result == 0) {
            result = definingPackage().compareTo(o.definingPackage());
        }
        return result;
    }

}
