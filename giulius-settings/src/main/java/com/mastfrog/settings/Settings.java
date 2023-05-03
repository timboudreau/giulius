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

import com.mastfrog.function.ByteSupplier;
import com.mastfrog.function.FloatSupplier;
import com.mastfrog.function.ShortSupplier;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.strings.Strings;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Interface for read-only settings - yet another interface to key/value pairs.
 * Settings are typically loaded on startup from some combination of sources,
 * and made available at runtime through things like Guice's
 * <code>&#064;Named</code> binding.
 * <p/>
 * One thing to notice about Settings is that there are no setters. This is a
 * very intentional decision - while applications may have settings which are
 * dynamic, code which mutates settings tends to be centralized and needs to
 * know how settings are stored. So, you can have settings that change, but you
 * accomplish that by having the backing storage update itself.
 * <p/>
 * It is possible to have settings whose values change - use SettingsBuilder and
 * either provide a PropertiesSource with a non-zero refresh interval, or
 * implement Settings directly.
 * <p/>
 * Typically you get a Settings from a SettingsBuilder, which may layer up a
 * whole stack of sets of settings. The default implementation, created by
 * SettingsBuilder.createDefault() contains the following layers (higher numbers
 * override lower ones):
 * <ul>
 * <li>Environment variables</li>
 * <li>System properties</li>
 * <li>The merge of all files named
 * <code>com/mastfrog/generated-defaults.properties</code> on the classpath,
 * merged in classpath-order.  <code>generated-defaults</code> files are created
 * by using the <code>&#064;Namespaced</code> annotation</li>
 * <li>The merge of all files named
 * <code>com/mastfrog/defaults.properties</code> on the classpath, merged in
 * classpath-order</li>
 * <li>The contents of a file named <code>defaults.properties</code> in the home
 * directory of the process's user</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public interface Settings extends Iterable<String> {

    String getString(String name);

    /**
     * Get a string using the passed key, and convert the value (which may be
     * null!) using the passed function.
     *
     * @param <T> The type
     * @param name The key
     * @param converter A conversion function
     * @return The value returned by the conversion function when passed the
     * string value for the passed key
     */
    default <T> T get(String name, Function<String, T> converter) {
        return converter.apply(getString(name));
    }

    /**
     * Get the string value, given a key, and using the passed default value
     * as a fallback.
     *
     * @param name The key
     * @param defaultValue the fallback value
     * @return A string
     */
    default String getString(String name, String defaultValue) {
        String result = getString(name);
        if (result == null) {
            result = defaultValue;
        }
        return result;
    }

    /**
     * Get a string, using the passed supplier to provide a default value if the
     * key is not present.
     *
     * @param name The key
     * @param defaultValue A factory for a default value
     * @return A string or null if the supplier returns null
     */
    default String string(String name, Supplier<String> defaultValue) {
        String result = getString(name);
        if (result == null) {
            result = defaultValue.get();
        }
        return result;
    }

    /**
     * Create a Settings from the passed Properties - changes in the Properties
     * will be reflected in the Settings.
     *
     * @param props A properties
     * @return A settings
     */
    public static Settings fromProperties(Properties props) {
        PropertiesSettings result = new PropertiesSettings(null);
        result.setDelegate(props);
        return result;
    }

    /**
     * Create a Settings whose contents can be set by providing a Properties
     * object to the consumer passed to the consumer passed here.
     *
     * @param delegateSetterConsumer A consumer that accepts a setter for the
     * properties delegated to (to keep the implementation of mutation separate
     * from the interface exposed to consumers of this Settings).
     * @return A Settings
     */
    public static Settings delegating(Consumer<Consumer<Properties>> delegateSetterConsumer) {
        PropertiesSettings result = new PropertiesSettings(null);
        delegateSetterConsumer.accept(result::setDelegate);
        return result;
    }

    /**
     * Get the set of all known keys in this instance.  Depending on the implementation,
     * this may <i>not</i> be the set of all keys for which a non-null value may
     * be obtained - some implementations (such as the etcd over-the-network one) do
     * not know all of the values they can be queried for.
     *
     * @return A set of strings
     */
    default Set<String> allKeys() {
        return toProperties().stringPropertyNames();
    }

    /**
     * Create a settings builder.
     *
     * @return A settings builder
     */
    static SettingsBuilder builder() {
        return new SettingsBuilder();
    }

    /**
     * Create a settings builder with a specific namespace (determines the name for properties
     * files looked for).
     *
     * @param ns A namespace
     * @return A settings builder
     */
    static SettingsBuilder builder(String ns) {
        return new SettingsBuilder(ns);
    }

    default boolean ifPresent(String key, Consumer<String> valConsumer) {
        String s = getString(key);
        if (s != null) {
            valConsumer.accept(s);
            return true;
        }
        return false;
    }

    default boolean ifIntPresent(String key, IntConsumer valConsumer) {
        Integer val = getInt(key);
        if (val != null) {
            valConsumer.accept(val);
            return true;
        }
        return false;
    }

    default boolean ifLongPresent(String key, LongConsumer valConsumer) {
        Long val = getLong(key);
        if (val != null) {
            valConsumer.accept(val);
            return true;
        }
        return false;
    }

    default Integer getInt(String name) {
        String prop = getString(name);
        try {
            return prop == null ? null : Integer.parseInt(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default int getInt(String name, int defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue : Integer.parseInt(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default int getInt(String name, IntSupplier defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue.getAsInt() : Integer.parseInt(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default Short getShort(String name) {
        String prop = getString(name);
        try {
            return prop == null ? null : Short.parseShort(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default int getShort(String name, short defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue : Short.parseShort(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default int getShort(String name, ShortSupplier defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue.getAsShort() : Short.parseShort(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default Byte getByte(String name) {
        String prop = getString(name);
        try {
            return prop == null ? null : Byte.parseByte(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default byte getByte(String name, byte defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue : Byte.parseByte(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default byte getByte(String name, ByteSupplier defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue.getAsByte() : Byte.parseByte(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default Float getFloat(String name) {
        String prop = getString(name);
        try {
            return prop == null ? null : Float.parseFloat(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default float getFloat(String name, float defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue : Float.parseFloat(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default float getFloat(String name, FloatSupplier defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue.getAsFloat() : Float.parseFloat(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default Long getLong(String name) {
        String prop = getString(name);
        try {
            return prop == null ? null : Long.parseLong(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default long getLong(String name, long defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue : Long.parseLong(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default long getLong(String name, LongSupplier defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue.getAsLong() : Long.parseLong(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default Boolean getBoolean(String name) {
        String prop = getString(name);
        return prop == null ? null : Boolean.parseBoolean(prop);
    }

    default boolean getBoolean(String name, boolean defaultValue) {
        String prop = getString(name);
        return prop == null ? defaultValue : Boolean.parseBoolean(prop);
    }

    default boolean getBoolean(String name, BooleanSupplier defaultValue) {
        String prop = getString(name);
        return prop == null ? defaultValue.getAsBoolean() : Boolean.parseBoolean(prop);
    }

    default Double getDouble(String name) {
        String prop = getString(name);
        try {
            return prop == null ? null : Double.parseDouble(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default double getDouble(String name, double defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue : Double.parseDouble(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default double getDouble(String name, DoubleSupplier defaultValue) {
        String prop = getString(name);
        try {
            return prop == null ? defaultValue.getAsDouble() : Double.parseDouble(prop);
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name);
            nue.initCause(ex);
            throw nue;
        }
    }

    default byte[] getBase64(String name) {
        String prop = getString(name);
        return prop == null ? null : Base64.getDecoder().decode(prop);
    }

    default byte[] getBase64(String name, byte[] defaultValue) {
        byte[] result = getBase64(name);
        return result == null ? defaultValue : result;
    }

    default byte[] base64(String name, Supplier<byte[]> defaultValue) {
        byte[] result = getBase64(name);
        return result == null ? defaultValue.get() : result;
    }

    /**
     * Get an integer array, assuming the stored value is a comma-delimited list
     * of integers. Values are trimmed and empty strings eliminated before
     * conversion.
     *
     * @throws NumberFormatException if non number characters are encountered
     * @param name The key name
     * @return An array of integers, or null if the key is not present
     */
    default int[] getIntArray(String name) {
        String val = getString(name);
        if (val == null) {
            return null;
        }
        CharSequence[] seqs = Strings.trim(Strings.split(',', val));
        int[] result = new int[seqs.length];
        try {
            for (int i = 0; i < seqs.length; i++) {
                result[i] = Strings.parseInt(seqs[i]);
            }
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for "
                    + name + ": " + val);
            nue.initCause(ex);
            throw nue;
        }
        return result;
    }

    /**
     * Get a long array, assuming the stored value is a comma-delimited list of
     * longs. Values are trimmed and empty strings pruned before conversion.
     *
     * @throws NumberFormatException if non number characters are encountered
     * @param name The key name
     * @return An array of longs, or null if the key is not present
     */
    default long[] getLongArray(String name) {
        String val = getString(name);
        if (val == null) {
            return null;
        }
        CharSequence[] seqs = Strings.trim(Strings.split(',', val));
        long[] result = new long[seqs.length];
        try {
            for (int i = 0; i < seqs.length; i++) {
                result[i] = Strings.parseLong(seqs[i]);
            }
        } catch (NumberFormatException ex) {
            NumberFormatException nue = new NumberFormatException("Invalid value for " + name + ": " + val);
            nue.initCause(ex);
            throw nue;
        }
        return result;
    }

    default BigInteger[] getBigIntegerArray(String name) {
        String val = getString(name);
        if (val == null) {
            return null;
        }
        CharSequence[] seqs = Strings.trim(Strings.split(',', val));
        BigInteger[] result = new BigInteger[seqs.length];
        for (int i = 0; i < seqs.length; i++) {
            result[i] = new BigInteger(seqs[i].toString());
        }
        return result;
    }

    default BigInteger[] getBigIntegerArray(String name, BigInteger[] defaultValue) {
        BigInteger[] result = getBigIntegerArray(name);
        return result == null ? defaultValue : result;
    }

    /**
     * Get a long array, assuming the stored value is a comma-delimited list of
     * longs. Values are trimmed and empty strings pruned before conversion.
     *
     * @throws NumberFormatException if non number characters are encountered
     * @param name The key name
     * @return An array of longs, or the passed default value if the key is not
     * present
     */
    default long[] getLongArray(String name, long... defaultValue) {
        long[] result = getLongArray(name);
        return result == null ? defaultValue : result;
    }

    /**
     * Get an integer array, assuming the stored value is a comma-delimited list
     * of integers. Values are trimmed and empty strings pruned before
     * conversion.
     *
     * @throws NumberFormatException if non number characters are encountered
     * @param name The key name
     * @return An array of integers, or the passed default value if the key is
     * not present
     */
    default int[] getIntArray(String name, int... defaultValue) {
        int[] result = getIntArray(name);
        return result == null ? defaultValue : result;
    }

    /**
     * Get a string array, assuming the stored value is a comma-delimited list
     * of strings. Values are trimmed and empty strings pruned.
     *
     * @param name The key name
     * @return A string array or null if the key is not present
     */
    default String[] getStringArray(String name) {
        String val = getString(name);
        if (val == null) {
            return null;
        }
        return Strings.trim(val.split(","));
    }

    /**
     * Get a string array, assuming the stored value is a comma-delimited list
     * of strings. Values are trimmed and empty strings pruned.
     *
     * @param name The key name
     * @return A string array or the passed default value if the key is not
     * present
     */
    default String[] getStringArray(String name, String... defaultValue) {
        String[] val = getStringArray(name);
        return val == null ? defaultValue : val;
    }

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
