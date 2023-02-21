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
package com.mastfrog.giulius.annotations;

import static com.mastfrog.giulius.annotations.Setting.Tier.SECONDARY;
import static com.mastfrog.giulius.annotations.Setting.ValueType.STRING;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation applicable only to static string fields, which allows settings to
 * be documented and have default values associated with them directly.
 *
 * @since 2.9.7
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD})
public @interface Setting {

    /**
     * Description of the setting.
     *
     * @return A string
     */
    String value();

    /**
     * The type the setting is resolved to; default is string.
     *
     * @return A value type
     */
    ValueType type() default STRING;

    /**
     * An optional pattern which is used to verify that the default is sane.
     *
     * @return A regex
     */
    String pattern() default "";

    /**
     * A default value to use if no other value is specified.
     *
     * @return A default value
     */
    String defaultValue() default "";

    /**
     * A shortcut that can be used by SettingsBuilder.parseCommandLineArguments.
     * Since collisions are easy to have, use this only for things that are
     * *highly* likely to be set from the command-line.
     *
     * @return A character
     */
    char shortcut() default 0;

    /**
     * Get an approximate importance for this setting - determines its
     * precedence in generated help documents.
     *
     * @return A tier
     */
    Tier tier() default SECONDARY;

    public enum ValueType {
        STRING,
        BOOLEAN,
        INTEGER,
        FLOAT
    }

    public enum Tier {
        TERTIARY,
        SECONDARY,
        PRIMARY,
    }
}
