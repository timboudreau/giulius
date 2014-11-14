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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class LayeredSettings implements Settings {

    private final Iterable<Settings> all;
    private final String ns;
    private final boolean log = Boolean.getBoolean("settings.log");

    LayeredSettings(String ns, Iterable<Settings> all) {
        this.all = all;
        this.ns = ns == null ? "defaults" : ns;
    }

    LayeredSettings(String ns, Settings... all) {
        this(ns, Arrays.asList(all));
    }

    @Override
    public Iterator<String> iterator() {
        return allKeys().iterator();
    }

    @Override
    public String getString(String name, String defaultValue) {
        String result = getString(name);
        return result == null ? defaultValue : result;
    }

    @Override
    public Set<String> allKeys() {
        Set<String> keys = new HashSet<>();
        for (Settings s : all) {
            keys.addAll(s.allKeys());
        }
        return keys;
    }

    @Override
    public Integer getInt(String name) {
        if (log) System.out.println("I: " + name);
        Integer val = null;
        for (Settings s : all) {
            val = s.getInt(name);
            if (val != null) {
                break;
            }
        }
        return val;
    }

    @Override
    public int getInt(String name, int defaultValue) {
        Integer val = getInt(name);
        return val == null ? defaultValue : val;
    }

    @Override
    public Long getLong(String name) {
        Long val = null;
        for (Settings s : all) {
            val = s.getLong(name);
            if (val != null) {
                break;
            }
        }
        return val;
    }

    @Override
    public long getLong(String name, long defaultValue) {
        Long result = getLong(name);
        return result == null ? defaultValue : result;
    }

    @Override
    public String getString(String name) {
        if (log) System.out.println("S: " + name);
        for (Settings s : all) {
            String result = s.getString(name);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public Boolean getBoolean(String name) {
        if (log) System.out.println("B: " + name);
        Boolean result = null;
        for (Settings s : all) {
            result = s.getBoolean(name);
            if (result != null) {
                break;
            }
        }
        return result;
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue) {
        Boolean result = getBoolean(name);
        return result == null ? defaultValue : result;
    }

    @Override
    public Double getDouble(String name) {
        if (log) System.out.println("D: " + name);
        Double result = null;
        for (Settings s : all) {
            result = s.getDouble(name);
            if (result != null) {
                break;
            }
        }
        return result;
    }

    @Override
    public double getDouble(String name, double defaultValue) {
        Double result = getDouble(name);
        if (result == null) {
            result = defaultValue;
        }
        return result;
    }

    @Override
    public Properties toProperties() {
        Properties result = new Properties();
        for (String key : this) {
            result.setProperty(key, getString(key));
        }
        return result;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LayeredSettings for " + ns + "{");
        toString(sb, 0);
        return sb.append("\n}").toString();
    }
    
    private void toString(StringBuilder sb, int depth) {
        char[] space = new char[(depth + 1) * 2];
        Arrays.fill(space, ' ');
        for (Settings s : all) {
            if (s instanceof LayeredSettings) {
                ((LayeredSettings) s).toString(sb, depth + 1);
            } else {
                sb.append ('\n');
                sb.append(space);
                String ss = s.toString();
                if (ss.length() > 160) {
                    ss = ss.substring(0, 160) + "...";
                }
                sb.append(ss);
            }
        }
    }
}
