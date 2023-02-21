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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class SettingRecordImpl extends SettingRecord {

    // Keep in sync with version in giulius-annotation-processors
    //
    // If we put this in a shared dependency, its use in annotation processors
    // would REMOVE it from classpaths it ought to be on in some maven builds,
    // resulting in inscrutable errors for users of libraries that use it as a library
    // library and also use it via the annotation processor.  It's not worth it.
    final String key;
    final String description;
    final String type;
    final String pattern;
    final char shortcut;
    final String definedInField;
    final String definedInClass;
    final String definedInPackage;
    final String defaultValue;
    final int tier;

    SettingRecordImpl(String key, String description, String type, String pattern, char shortcut, String definedInField,
            String definedInClass, String definedInPackage, String defaultValue, int tier) {
        this.key = key;
        this.description = description;
        this.type = Character.toString(type.charAt(0));
        this.pattern = pattern;
        this.shortcut = shortcut;
        this.definedInField = definedInField;
        this.definedInClass = definedInClass;
        this.definedInPackage = definedInPackage;
        this.defaultValue = defaultValue;
        this.tier = tier;
    }

    public SettingRecordImpl withDefaultValue(Function<String, String> f) {
        String newDefaultValue = f.apply(key);
        if (newDefaultValue != null && !newDefaultValue.equals(defaultValue)) {
            return new SettingRecordImpl(key, description, type, pattern, shortcut, definedInField,
                    definedInClass, definedInPackage, newDefaultValue, tier);
        }
        return this;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public ValueType type() {
        return ValueType.fromString(type);
    }

    @Override
    public Tier tier() {
        return Tier.values()[tier];
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String definingField() {
        return definedInField;
    }

    @Override
    public String definingClass() {
        return definedInClass;
    }

    @Override
    public String definingPackage() {
        return definedInPackage;
    }

    @Override
    public Optional<String> pattern() {
        if (pattern == null || pattern.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pattern);
    }

    @Override
    public Optional<Character> shortcut() {
        if (shortcut == 0) {
            return Optional.empty();
        }
        return Optional.of(shortcut);
    }

    @Override
    public Optional<String> defaultValue() {
        if (defaultValue == null || defaultValue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(defaultValue);
    }

    String stringify() {
        StringBuilder sb = new StringBuilder();
        Consumer<String> appender = s -> {
            if (sb.length() > 0) {
                sb.append(PROPS_DELIMITER);
            }
            sb.append(s);
        };
        appender.accept(Integer.toString(tier)); // 0
        appender.accept(type.substring(0, 1)); // 1
        appender.accept(description); // 2
        appender.accept(definedInField); // 3
        appender.accept(definedInClass); // 4
        appender.accept(definedInPackage); // 5
        if (pattern != null) { // 6
            appender.accept(pattern);
        } else {
            appender.accept("");
        }
        if (shortcut != 0) { // 7
            appender.accept(Character.toString(shortcut));
        } else {
            appender.accept("");
        }
        if (defaultValue != null && !defaultValue.isEmpty()) { // 8?
            appender.accept(defaultValue);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return stringify();
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + Objects.hashCode(this.definedInField);
        hash = 71 * hash + Objects.hashCode(this.definedInClass);
        hash = 71 * hash + Objects.hashCode(this.definedInPackage);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SettingRecordImpl other = (SettingRecordImpl) obj;
        if (!Objects.equals(this.definedInField, other.definedInField)) {
            return false;
        }
        if (!Objects.equals(this.definedInClass, other.definedInClass)) {
            return false;
        }
        return Objects.equals(this.definedInPackage, other.definedInPackage);
    }

}
