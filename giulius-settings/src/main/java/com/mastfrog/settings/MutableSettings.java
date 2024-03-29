/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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

/**
 * Mutable interface to settings.
 *
 * @author Tim Boudreau
 */
public interface MutableSettings extends Settings {

    default void setByte(String name, byte value) {
        setString(name, Byte.toString(value));
    }

    default void setShort(String name, short value) {
        setString(name, Short.toString(value));
    }

    default void setFloat(String name, float value) {
        setString(name, Float.toString(value));
    }

    default void setInt(String name, int value) {
        setString(name, Integer.toString(value));
    }

    default void setIntArray(String name, int... value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(value);
        }
        setString(name, sb.toString());
    }

    default void setBoolean(String name, boolean val) {
        setString(name, Boolean.toString(val));
    }

    default void setDouble(String name, double val) {
        setString(name, Double.toString(val));
    }

    default void setLong(String name, long val) {
        setString(name, Long.toString(val));
    }

    public void setString(String name, String value);

    public void clear(String name);
}
