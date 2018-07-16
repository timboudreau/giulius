/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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

import com.mastfrog.util.preconditions.Checks;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class PrefixedSettings implements Settings {

    private final String prefix;
    private final Settings delegate;

    PrefixedSettings(String prefix, Settings delegate) {
        this.prefix = Checks.notNull("prefix", prefix);
        this.delegate = Checks.notNull("delegate", delegate);
    }

    private String appendPrefix(String key) {
        return prefix + key;
    }

    @Override
    public Integer getInt(String name) {
        return delegate.getInt(appendPrefix(name));
    }

    @Override
    public int getInt(String name, int defaultValue) {
        return delegate.getInt(appendPrefix(name), defaultValue);
    }

    @Override
    public Long getLong(String name) {
        return delegate.getLong(appendPrefix(name));
    }

    @Override
    public long getLong(String name, long defaultValue) {
        return delegate.getLong(appendPrefix(name), defaultValue);
    }

    @Override
    public String getString(String name) {
        return delegate.getString(appendPrefix(name));
    }

    @Override
    public String getString(String name, String defaultValue) {
        return delegate.getString(appendPrefix(name), defaultValue);
    }

    @Override
    public Boolean getBoolean(String name) {
        return delegate.getBoolean(appendPrefix(name));
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue) {
        return delegate.getBoolean(appendPrefix(name), defaultValue);
    }

    @Override
    public Double getDouble(String name) {
        return delegate.getDouble(appendPrefix(name));
    }

    @Override
    public double getDouble(String name, double defaultValue) {
        return delegate.getDouble(appendPrefix(name), defaultValue);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("prefix:").append(prefix).append("{");
        for (Iterator<String> iter = iterator(); iter.hasNext();) {
            String key = iter.next();
            sb.append(key).append('=').append(getString(key));
            if (iter.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.append("}").toString();
    }

    @Override
    public Set<String> allKeys() {
        Set<String> orig = delegate.allKeys();
        Set<String> result = new HashSet<>();
        for (String key : orig) {
            if (key.startsWith(prefix)) {
                result.add(key.substring(prefix.length()));
            }
        }
        return result;
    }

    @Override
    public Properties toProperties() {
        Properties result = new Properties();
        for (String key : allKeys()) {
            result.setProperty(key, getString(key));
        }
        return result;
    }

    @Override
    public Iterator<String> iterator() {
        return allKeys().iterator();
    }
}
