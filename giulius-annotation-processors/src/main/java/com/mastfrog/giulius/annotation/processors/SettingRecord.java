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
package com.mastfrog.giulius.annotation.processors;

import com.mastfrog.annotation.AnnotationUtils;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 *
 * @author Tim Boudreau
 */
final class SettingRecord implements Comparable<SettingRecord> {

    static final String PROPS_DELIMITER = "!;`";
    final String key;
    private final String description;
    private final String type;
    private final String pattern;
    private final char shortcut;
    private final String definedInField;
    private final String definedInClass;
    private final String definedInPackage;
    private final String defaultValue;
    private final int tier;

    SettingRecord(String key, String description, String type, String pattern,
            char shortcut, String definedInField,
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

    Optional<String> defaultValue() {
        if (defaultValue == null || defaultValue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(defaultValue);
    }

    @SuppressWarnings("UnnecessaryTemporaryOnConversionFromString")
    Optional<String> validateDefaultValue() {
        if (defaultValue != null) {
            switch (type.charAt(0)) {
                case 'S':
                    break;
                case 'B':
                    switch (defaultValue) {
                        case "true":
                        case "false":
                            break;
                        default:
                            return Optional.of("Not a valid boolean default: '" + defaultValue + "'");
                    }
                    break;
                case 'I':
                    try {
                    Integer.parseInt(defaultValue);
                } catch (NumberFormatException ex) {
                    return Optional.of("Not a valid integer default: '" + defaultValue + "'");
                }
                break;
                case 'F':
                    try {
                    Double.parseDouble(defaultValue);
                } catch (NumberFormatException ex) {
                    return Optional.of("Not a valid integer default: '" + defaultValue + "'");
                }
                break;
                default:
                    return Optional.of("Not a valid type constant: '" + type + "'");
            }
        }
        return Optional.empty();
    }

    public static Optional<SettingRecord> fromAnnotationMirror(AnnotationUtils utils, AnnotationMirror mir, Element el) {
        VariableElement field;
        switch (el.getKind()) {
            case FIELD:
                if (!el.getModifiers().contains(Modifier.STATIC) || !el.getModifiers().contains(Modifier.FINAL)) {
                    utils.fail("@Setting can only be used on `static final String` fields but got modifiers" + el.getModifiers(), el, mir);
                    return Optional.empty();
                }
                field = (VariableElement) el;
                if (!"java.lang.String".equals(field.asType().toString())) {
                    utils.fail("@Setting can only be applied to a field with type String, but found " + field.asType(), el, mir);
                    return Optional.empty();
                }
                break;
            default:
                utils.fail("@Setting cannot be used on a " + el.getKind(), el, mir);
                return Optional.empty();
        }
        String description = utils.annotationValue(mir, "value", String.class);
        if (description == null || description.isEmpty()) {
            utils.fail("Description (value()) cannot be empty.", el, mir);
            return Optional.empty();
        }
        if (description.contains(PROPS_DELIMITER)) {
            utils.fail("Description may not contain the string '" + PROPS_DELIMITER + "' - it is used "
                    + "as a delimiter in the properties serialization format.", el, mir);
        }
        if (field.getConstantValue() == null) {
            utils.fail("@Setting must be defined on a static final String with a default value", el, mir);
            return Optional.empty();
        }
        String key = field.getConstantValue().toString();
        String defValue = utils.annotationValue(mir, "defaultValue", String.class);
        String pattern = utils.annotationValue(mir, "pattern", String.class);
        if (pattern != null) {
            try {
                Pattern p = Pattern.compile(pattern);
                if (defValue != null && !defValue.isEmpty()) {
                    Matcher m = p.matcher(defValue);
                    if (!m.find()) {
                        utils.fail("Default value ' + " + defValue
                                + "' does not match its own regular expression: '" + pattern + "': "
                                + defValue, el, mir);
                        return Optional.empty();

                    }
                }
            } catch (PatternSyntaxException ex) {
                utils.fail("Not a valid regular expression: '" + pattern + "': " + ex.getMessage(), el, mir);
                return Optional.empty();
            }
        }
        String type = utils.enumConstantValue(mir, "type", "STRING");
        char shortcut = utils.annotationValue(mir, "shortcut", Character.class, Character.valueOf((char) 0));

        TypeElement declaringType = AnnotationUtils.enclosingType(el);
        String definedInPackage = utils.packageName(declaringType);
        String definedInField = field.getSimpleName().toString();
        String definedInClass = declaringType.getSimpleName().toString();

        int tier = 2;
        String tierString = utils.enumConstantValue(mir, "tier", "SECONDARY");
        switch (tierString) {
            case "PRIMARY":
                tier = 0;
                break;
            case "SECONDARY":
                tier = 1;
                break;
            case "TERTIARY":
                tier = 2;
                break;
            default:
                utils.fail("Invalid value for tier '" + tierString + "' not one of PRIMARY/SECONDARY/TERTIARY", el, mir);
        }

        SettingRecord result = new SettingRecord(key, description, type, pattern, shortcut, definedInField,
                definedInClass, definedInPackage, defValue, tier);
        result.validateDefaultValue().ifPresent(err -> {
            utils.fail("Invalid default value '" + defValue + "' for " + key);
        });
        return Optional.of(result);
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
        return Optional.of(new SettingRecord(key, desc, type, pattern, shortcut,
                definedInField, definedInClass, definedInPackage, defValue, tier));
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
        final SettingRecord other = (SettingRecord) obj;
        if (!Objects.equals(this.definedInField, other.definedInField)) {
            return false;
        }
        if (!Objects.equals(this.definedInClass, other.definedInClass)) {
            return false;
        }
        return Objects.equals(this.definedInPackage, other.definedInPackage);
    }

    @Override
    public int compareTo(SettingRecord o) {
        int result = key.compareTo(o.key);
        if (result == 0) {
            result = definedInField.compareTo(o.definedInField);
        }
        if (result == 0) {
            result = definedInClass.compareTo(o.definedInClass);
        }
        if (result == 0) {
            result = definedInPackage.compareTo(o.definedInPackage);
        }
        return result;
    }

}
