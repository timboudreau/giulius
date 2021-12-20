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

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class WritableSettings implements MutableSettings {
    private final String ns;

    protected Settings settings;
    private final Object lock = new Object();
    private final Properties writeLayer = new Properties();
    private final Set<String> cleared = Collections.synchronizedSet(new HashSet<>());

    WritableSettings(String ns, Settings settings) {
        this.ns = ns;
        this.settings = settings;
    }
    
    @Override
    public String toString() {
        return super.toString() + " for " + ns + " over " + settings;
    }

    @Override
    public void setInt(String name, int value) {
        cleared.remove(name);
        writeLayer.setProperty(name, "" + value);
    }

    @Override
    public void setBoolean(String name, boolean val) {
        cleared.remove(name);
        writeLayer.setProperty(name, "" + val);
    }

    @Override
    public void setDouble(String name, double val) {
        cleared.remove(name);
        writeLayer.setProperty(name, "" + val);
    }

    @Override
    public void setLong(String name, long val) {
        cleared.remove(name);
        writeLayer.setProperty(name, "" + val);
    }

    @Override
    public void setString(String name, String val) {
        cleared.remove(name);
        writeLayer.setProperty(name, "" + val);
    }

    @Override
    public Iterator<String> iterator() {
        return allKeys().iterator();
    }

    @Override
    public void clear(String name) {
        cleared.add(name);
        writeLayer.remove(name);
    }

    @Override
    public Set<String> allKeys() {
        Set<String> s = new HashSet<>(getSettings().allKeys());
        s.addAll(writeLayer.stringPropertyNames());
        s.removeAll(cleared);
        return s;
    }

    @Override
    public String getString(String name, String defaultValue) {
        if (cleared.contains(name)) {
            return defaultValue;
        }
        return getSettings().getString(name, defaultValue);
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        Settings s;
        synchronized (lock) {
            s = settings;
        }
        return new LayeredSettings(ns, new PropertiesWrapper(writeLayer), s);
    }

    @Override
    public Properties toProperties() {
        Properties p = new Properties(getSettings().toProperties());
        p.putAll(writeLayer);
        for (String k : cleared) {
            p.remove(k);
        }
        return p;
    }

    /**
     * @param settings the settings to set
     */
    public void setSettings(Settings settings) {
        synchronized (lock) {
            this.settings = settings;
        }
    }

    @Override
    public Integer getInt(String name) {
        if (cleared.contains(name)) {
            return null;
        }
        return getSettings().getInt(name);
    }

    @Override
    public int getInt(String name, int defaultValue) {
        if (cleared.contains(name)) {
            return defaultValue;
        }
        return getSettings().getInt(name, defaultValue);
    }

    @Override
    public Long getLong(String name) {
        if (cleared.contains(name)) {
            return null;
        }
        return getSettings().getLong(name);
    }

    @Override
    public long getLong(String name, long defaultValue) {
        if (cleared.contains(name)) {
            return defaultValue;
        }
        return getSettings().getLong(name, defaultValue);
    }

    @Override
    public String getString(String name) {
        if (cleared.contains(name)) {
            return null;
        }
        return getSettings().getString(name);
    }

    @Override
    public Boolean getBoolean(String name) {
        if (cleared.contains(name)) {
            return null;
        }
        return getSettings().getBoolean(name);
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue) {
        if (cleared.contains(name)) {
            return defaultValue;
        }
        return getSettings().getBoolean(name, defaultValue);
    }

    @Override
    public Double getDouble(String name) {
        if (cleared.contains(name)) {
            return null;
        }
        return getSettings().getDouble(name);
    }

    @Override
    public double getDouble(String name, double defaultValue) {
        if (cleared.contains(name)) {
            return defaultValue;
        }
        return getSettings().getDouble(name, defaultValue);
    }
}
