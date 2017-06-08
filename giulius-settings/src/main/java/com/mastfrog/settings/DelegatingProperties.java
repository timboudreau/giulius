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
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Properties which overrides all of the awful stuff.  Properties is much
 * too wide a class to inherit from safely.
 *
 * @author Tim Boudreau
 */
class DelegatingProperties extends Properties {

    protected Properties delegate;
    private final Object lock = new Object();

    /**
     * @return the delegate
     */
    public Properties getDelegate() {
        synchronized (lock) {
            return delegate;
        }
    }

    /**
     * @param delegate the delegate to set
     */
    public void setDelegate(Properties delegate) {
        synchronized (lock) {
            this.delegate = delegate;
        }
    }

    @Override
    public void save(OutputStream out, String comments) {
        getDelegate().save(out, comments);
    }

    @Override
    public void store(Writer writer, String comments) throws IOException {
        getDelegate().store(writer, comments);
    }

    @Override
    public void store(OutputStream out, String comments) throws IOException {
        getDelegate().store(out, comments);
    }

    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        getDelegate().storeToXML(os, comment);
    }

    @Override
    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        getDelegate().storeToXML(os, comment, encoding);
    }

    @Override
    public String getProperty(String key) {
        return getDelegate().getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return getDelegate().getProperty(key, defaultValue);
    }

    @Override
    public Enumeration<?> propertyNames() {
        return getDelegate().propertyNames();
    }

    @Override
    public Set<String> stringPropertyNames() {
        return getDelegate().stringPropertyNames();
    }

    @Override
    public void list(PrintStream out) {
        getDelegate().list(out);
    }

    @Override
    public void list(PrintWriter out) {
        getDelegate().list(out);
    }

    @Override
    public synchronized int size() {
        return getDelegate().size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return getDelegate().isEmpty();
    }

    @Override
    public synchronized Enumeration<Object> keys() {
        return getDelegate().keys();
    }

    @Override
    public synchronized Enumeration<Object> elements() {
        return getDelegate().elements();
    }

    @Override
    public synchronized boolean contains(Object value) {
        return getDelegate().contains(value);
    }

    @Override
    public boolean containsValue(Object value) {
        return getDelegate().containsValue(value);
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return getDelegate().containsKey(key);
    }

    @Override
    public synchronized Object get(Object key) {
        return getDelegate().get(key);
    }

    @Override
    public Set<Object> keySet() {
        return Collections.unmodifiableSet(getDelegate().keySet());
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return Collections.unmodifiableSet(getDelegate().entrySet());
    }

    @Override
    public Collection<Object> values() {
        return Collections.unmodifiableCollection(getDelegate().values());
    }

    @Override
    public synchronized Object setProperty(String key, String value) {
        throw new UnsupportedOperationException("Read only");
    }

    @Override
    public synchronized void load(Reader reader) throws IOException {
        throw new UnsupportedOperationException("Read only");
    }

    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        throw new UnsupportedOperationException("Read only");
    }

    @Override
    public synchronized void loadFromXML(InputStream in) throws IOException, InvalidPropertiesFormatException {
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
    public String toString() {
        return getClass().getSimpleName() + "@" + System.identityHashCode(this) + "{" + getDelegate() + "}";
    }

    @Override
    public boolean equals (Object o) {
        return o == this;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public synchronized Object clone() {
        return super.clone(); //To change body of generated methods, choose Tools | Templates.
    }
}
