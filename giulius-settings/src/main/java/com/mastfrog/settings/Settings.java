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

import com.mastfrog.util.Checks;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 * Interface for read-only settings - yet another interface to key/value pairs.
 * Settings are typically loaded on startup from some combination of sources,
 * and made available at runtime through things like Guice's <code>&#064;Named</code>
 * binding.
 * <p/>
 * One thing to notice about Settings is that there are no setters.  This is
 * a very intentional decision - while applications may have settings which
 * are dynamic, code which mutates settings tends to be centralized and needs to
 * know how settings are stored.  So, you can have settings that change, but
 * you accomplish that by having the backing storage update itself.
 * <p/>
 * It is possible to have settings whose values change - use SettingsBuilder and
 * either provide a PropertiesSource with a non-zero refresh interval, or
 * implement Settings directly.
 * <p/>
 * Typically you get a Settings from a SettingsBuilder, which may layer up a whole
 * stack of sets of settings.  The default implementation, created by
 * SettingsBuilder.createDefault() contains the following layers (higher numbers
 * override lower ones):
 * <ul>
 * <li>Environment variables</li>
 * <li>System properties</li>
 * <li>The merge of all files named <code>com/mastfrog/generated-defaults.properties</code>
 * on the classpath, merged in classpath-order.  <code>generated-defaults</code> files
 * are created by using the <code>&#064;Namespaced</code> annotation</li>
 * <li>The merge of all files named <code>com/mastfrog/defaults.properties</code>
 * on the classpath, merged in classpath-order</li>
 * <li>The contents of a file named <code>defaults.properties</code> in the home
 * directory of the process's user</li>
 * </ul>
 * 
 * @author Tim Boudreau
 */
public interface Settings extends Iterable<String> {

    public Integer getInt(String name);

    public int getInt(String name, int defaultValue);

    public Long getLong(String name);

    public long getLong(String name, long defaultValue);

    public String getString(String name);

    public String getString(String name, String defaultValue);

    public Boolean getBoolean(String name);

    public boolean getBoolean(String name, boolean defaultValue);

    public Double getDouble(String name);

    public double getDouble(String name, double defaultValue);

    public Set<String> allKeys();

    /**
     * Get this settings as a read-only Properties object
     *
     * @return
     */
    public Properties toProperties();

    default Settings withPrefix(String pfx) {
        if (Checks.notNull("pfx", pfx).length() == 0) {
            return this;
        }
        return new PrefixedSettings(pfx, this);
    }
    
    public Settings EMPTY = new Settings() {

        @Override
        public Integer getInt(String name) {
            return null;
        }

        @Override
        public int getInt(String name, int defaultValue) {
            return defaultValue;
        }

        @Override
        public Long getLong(String name) {
            return null;
        }

        @Override
        public long getLong(String name, long defaultValue) {
            return defaultValue;
        }

        @Override
        public String getString(String name) {
            return null;
        }

        @Override
        public String getString(String name, String defaultValue) {
            return defaultValue;
        }

        @Override
        public Boolean getBoolean(String name) {
            return null;
        }

        @Override
        public boolean getBoolean(String name, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public Double getDouble(String name) {
            return null;
        }

        @Override
        public double getDouble(String name, double defaultValue) {
            return defaultValue;
        }

        @Override
        public Set<String> allKeys() {
            return Collections.<String>emptySet();
        }

        @Override
        public Properties toProperties() {
            return new Properties();
        }

        @Override
        public Iterator<String> iterator() {
            return allKeys().iterator();
        }
    };
}
