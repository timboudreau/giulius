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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;

/**
 *
 * @author Tim Boudreau
 */
final class EnvironmentProperties extends Properties {

    private final Map<String, String> props;

    EnvironmentProperties() {
        this(Collections.emptySet());
    }

    EnvironmentProperties(Set<String> keys) {
        this(keys, System.getenv());
    }

    EnvironmentProperties(Set<String> keys, Map<String, String> props) {
        Map<String, String> m;
        if (keys.isEmpty()) {
            m = props;
        } else {
            m = new HashMap<>(keys.size());
            for (String key : keys) {
                if (props.containsKey(key)) {
                    m.put(key, props.get(key));
                }
            }
        }
        this.props = Collections.unmodifiableMap(m);
    }

    @Override
    public synchronized EnvironmentProperties clone() {
        return new EnvironmentProperties(Collections.emptySet(), props);
    }

    @Override
    protected void rehash() {
        // do nothing
    }

    @Override
    public synchronized Object replace(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized boolean replace(Object key, Object oldValue, Object newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object putIfAbsent(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void replaceAll(BiFunction<? super Object, ? super Object, ?> function) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        if (!(defaultValue instanceof String)) {
            throw new ClassCastException("Not a string: " + defaultValue);
        }
        return props.getOrDefault(key, (String) defaultValue);
    }

    @Override
    public synchronized int hashCode() {
        return props.hashCode();
    }

    @Override
    public synchronized boolean equals(Object o) {
        return props.equals(o);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Map.Entry<Object, Object>> entrySet() {
        Set result = props.entrySet();
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<Object> values() {
        Collection c = props.values();
        return c;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Object> keySet() {
        Set result = props.keySet();
        return result;
    }

    @Override
    public synchronized String toString() {
        return "Environment: " + props.toString();
    }

    @Override
    public synchronized void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void putAll(Map<?, ?> t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object put(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object get(Object key) {
        return props.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        return props.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return props.containsValue(value);
    }

    @Override
    @SuppressWarnings("element-type-mismatch")
    public boolean contains(Object value) {
        return props.containsKey(value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Enumeration<Object> elements() {
        Set s = props.keySet();
        return Collections.<Object>enumeration(s);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Enumeration<Object> keys() {
        Set s = props.keySet();
        return Collections.enumeration(s);
    }

    @Override
    public boolean isEmpty() {
        return props.isEmpty();
    }

    @Override
    public int size() {
        return props.size();
    }

    @Override
    public void list(PrintWriter out) {
        p().list(out);
    }

    @Override
    public void list(PrintStream out) {
        p().list(out);
    }

    @Override
    public Set<String> stringPropertyNames() {
        return props.keySet();
    }

    @Override
    public Enumeration<?> propertyNames() {
        Set<String> s = props.keySet();
        return Collections.enumeration(s);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return props.getOrDefault(key, defaultValue);
    }

    @Override
    public String getProperty(String key) {
        return props.get(key);
    }

    @Override
    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        p().storeToXML(os, comment, encoding);
    }

    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        p().storeToXML(os, comment);
    }

    @Override
    public synchronized void loadFromXML(InputStream in) throws IOException, InvalidPropertiesFormatException {
        throw new UnsupportedOperationException();
    }

    private Properties p() {
        Properties p = new Properties();
        p.putAll(props);
        return p;
    }

    @Override
    public void store(OutputStream out, String comments) throws IOException {
        p().store(out, comments);
    }

    @Override
    public void store(Writer writer, String comments) throws IOException {
        p().store(writer, comments);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void save(OutputStream out, String comments) {
        p().save(out, comments);
    }

    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void load(Reader reader) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Object setProperty(String key, String value) {
        throw new UnsupportedOperationException();
    }
}
