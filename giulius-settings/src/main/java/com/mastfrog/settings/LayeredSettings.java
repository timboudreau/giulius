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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class LayeredSettings implements Settings {

    private final Iterable<Settings> all;
    private final String ns;

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
        return new FakeProperties();
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

    protected class FakeProperties extends Properties implements Cloneable {

        @Override
        public synchronized Object setProperty(String key, String value) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public synchronized Object put(Object key, Object value) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public synchronized Object remove(Object key) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public synchronized void putAll(Map<? extends Object, ? extends Object> t) {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public synchronized void clear() {
            throw new UnsupportedOperationException("Read only");
        }

        @Override
        public synchronized Object clone() {
            return new FakeProperties();
        }

        @Override
        public String getProperty(String key) {
            return LayeredSettings.this.getString(key);
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            String result = getProperty(key);
            return result == null ? defaultValue : result;
        }

        @Override
        public Enumeration<?> propertyNames() {
            return Collections.enumeration(allKeys());
        }

        @Override
        public Set<String> stringPropertyNames() {
            return allKeys();
        }

        @Override
        public synchronized int size() {
            return allKeys().size();
        }

        @Override
        public synchronized boolean isEmpty() {
            return allKeys().isEmpty();
        }

        @Override
        public synchronized Enumeration<Object> keys() {
            List<Object> l = new ArrayList<Object>(allKeys());
            return Collections.enumeration(l);
        }

        @Override
        public synchronized Enumeration<Object> elements() {
            return super.elements();
        }

        @Override
        @SuppressWarnings("element-type-mismatch")
        public synchronized boolean contains(Object value) {
            return allKeys().contains(value);
        }

        @Override
        public boolean containsValue(Object value) {
            if (value == null) {
                return false;
            }
            String s = value + "";
            for (String key : allKeys()) {
                String val = getProperty(key);
                if (val != null) {
                    if (val.equals(s)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("element-type-mismatch")
        public synchronized boolean containsKey(Object key) {
            return allKeys().contains(key);
        }

        @Override
        public synchronized Object get(Object key) {
            if (key instanceof String) {
                return getString((String) key);
            }
            return null;
        }

        @Override
        public Set<Object> keySet() {
            Set<Object> result = new HashSet<Object>(allKeys());
            return result;
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            Set<Entry<Object, Object>> result = new HashSet<>();
            for (String key : allKeys()) {
                result.add(new En(key));
            }
            return result;
        }

        @Override
        public Collection<Object> values() {
            List<Object> result = new ArrayList<Object>();
            for (String key : allKeys()) {
                result.add(getString(key));
            }
            return result;
        }

        class En implements Map.Entry<Object, Object> {

            private final String key;

            En(String key) {
                this.key = key;
            }

            @Override
            public Object getKey() {
                return key;
            }

            @Override
            public Object getValue() {
                return getString(key);
            }

            @Override
            public Object setValue(Object value) {
                throw new UnsupportedOperationException("Read only");
            }

            @Override
            public int hashCode() {
                int hash = 7;
                hash = 53 * hash + Objects.hashCode(this.key);
                return hash;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final En other = (En) obj;
                if (!Objects.equals(this.key, other.key)) {
                    return false;
                }
                return true;
            }
        }
    }
}
