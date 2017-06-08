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

import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 * Implements Settings over a properties object
 *
 * @author Tim Boudreau
 */
final class PropertiesSettings extends DelegatingProperties implements Settings, PropertiesContainer {
    private final String origin;
    
    PropertiesSettings(String origin) {
        this.origin = origin;
    }
    
    @Override
    public String toString() {
        return origin == null ? "[unknown] " + asString() : origin + " " + asString();
    }
    
    @Override
    public Iterator<String> iterator() {
        return allKeys().iterator();
    }

    @Override
    public String getString(String name, String defaultValue) {
        return getProperty(name, defaultValue);
    }

    @Override
    public Properties toProperties() {
        return this;
    }

    private String prop(String s) {
        String result = getProperty(s);
        return result == null ? null : result.trim();
    }

    @Override
    public Set<String> allKeys() {
        return stringPropertyNames();
    }

    @Override
    public Integer getInt(String name) {
        String prop = prop(name);
        return prop == null ? null : Integer.parseInt(prop);
    }

    @Override
    public int getInt(String name, int defaultValue) {
        String prop = prop(name);
        return prop == null ? defaultValue : Integer.parseInt(prop);
    }

    @Override
    public Long getLong(String name) {
        String prop = prop(name);
        return prop == null ? null : Long.parseLong(prop);
    }

    @Override
    public long getLong(String name, long defaultValue) {
        String prop = prop(name);
        return prop == null ? defaultValue : Long.parseLong(prop);
    }

    @Override
    public String getString(String name) {
        return prop(name);
    }

    @Override
    public Boolean getBoolean(String name) {
        String prop = prop(name);
        return prop == null ? null : Boolean.parseBoolean(prop);
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue) {
        String prop = prop(name);
        return prop == null ? defaultValue : Boolean.parseBoolean(prop);
    }

    @Override
    public Double getDouble(String name) {
        String prop = prop(name);
        return prop == null ? null : Double.parseDouble(prop);
    }

    @Override
    public double getDouble(String name, double defaultValue) {
        String prop = prop(name);
        return prop == null ? defaultValue : Double.parseDouble(prop);
    }

    String asString() {
        StringBuilder sb = new StringBuilder("[");
        for (String key : this) {
            sb.append(key).append('=').append(getString(key)).append(" ");
        }
        sb.append("]");
        return sb.toString();
    }
}
