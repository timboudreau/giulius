/*
 * The MIT License
 *
 * Copyright 2021 Mastfrog Technologies.
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
package com.mastfrog.jarmerge.spi;

import com.mastfrog.jarmerge.JarMerge;
import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.util.strings.Strings;
import java.nio.file.Path;
import java.util.jar.JarEntry;

/**
 * Pluggable filter type which can be registered in META-INF/services and used
 * to omit, coalesce and/or rewrite files when copying or merging JAR files.
 *
 * @author Tim Boudreau
 */
public interface JarFilter<C extends Coalescer> extends Comparable<JarFilter<?>>, Cloneable {

    /**
     * Allows filters to run in a specific sort-order, for cases where some
     * filters need to run before others.
     *
     * @return A precedence value; by default, 0. A good practice is to separate
     * values by 10 or 100 to allow for future insertions without code changes
     * in unrelated things.
     */
    default int precedence() {
        return 0;
    }

    /**
     * Whether or not this filter should be enabled if it is found on the
     * classpath; if false, it must be explicitly enabled by an argument passing
     * its name() in the list of enabled filters.
     *
     * @return true by default
     */
    default boolean enabledByDefault() {
        return true;
    }

    /**
     * Determine if this filter is critical to the function of the tool and
     * should not be disablable via command-line arguments or maven properties.
     *
     * @return Whether or not this is a critical filter
     */
    default boolean isCritical() {
        return false;
    }

    /**
     * If the method returns true, this file will be omitted from the final JAR
     * and no further filters will be queried.
     *
     * @param path A path inside a JAR file
     * @param inJar The jar file in question
     * @param log A logger
     * @return true if this file should be filtered out
     */
    default boolean omit(String path, Path inJar, MergeLog log) {
        return false;
    }

    /**
     * If this filter wants to coalesce all files that look like the passed one,
     * return a Coalescer instance here.
     *
     * @param path The path
     * @param inJar The path within the JAR file
     * @param entry The JAR entry
     * @param log A log to log to
     * @return A coalescer or null
     */
    default C coalescer(String path, Path inJar, JarEntry entry, MergeLog log) {
        return null;
    }

    /**
     * Get the name of this coalescer, used by the MergeLog passed to some
     * methods.
     *
     * @return By default, a dash-delimited version of the class name
     */
    default String name() {
        return Strings.camelCaseToDelimited(getClass().getSimpleName(), '-');
    }

    @Override
    default int compareTo(JarFilter<?> o) {
        int result = Integer.compare(precedence(), o.precedence());
        if (result == 0) {
            result = name().compareTo(o.name());
        }
        return result;
    }

    /**
     * A description for this filter, used in CLI help.
     *
     * @return A description - by default just calls name()
     */
    default String description() {
        return name();
    }

    /**
     * If a filter installed on the classpath should completely replace another
     * one, that filter can report that it supersedes the passed filter and it
     * will be removed from the set to be run, even in the case that it is one
     * of the built-in filters and is active by default.
     * <p>
     * For example, an extension that can merge and synthesize a
     * `module-info.class` would want to supersede the built in `OmitModuleInfo`
     * filter so it can handle the set of `module-info.class` files in the
     * merged JARs in its own way.
     *
     * @param other Another filter which != this
     * @return false by default, true if the passed filter should not be used
     */
    default boolean supersedes(JarFilter<?> other) {
        return false;
    }

    /**
     * JarFilters are loaded from META-INF/services and may be cached by the
     * mechanism that loads them; if a JarFilter is stateful, it should not
     * assume it will be used only once per JVM, and should return a new
     * instance here to contain its state. The defualt implementation in
     * AbstractJarFilter simply calls super.clone().
     *
     * @param jarMerge The JarMerge instance holding the configuration for this
     * run
     * @return A filter which != this
     */
    JarFilter<C> configureInstance(JarMerge jarMerge);

    /**
     * Called by the framework if the date for all generated files in the jar
     * should be set to the unix epoch for repeatable builds.
     *
     * @param val Whether or not to zero out the dates
     */
    default void setZeroDates(boolean val) {
        // do nothing
    }
}
