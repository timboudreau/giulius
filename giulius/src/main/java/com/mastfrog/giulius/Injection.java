/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.giulius;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.mastfrog.abstractions.instantiate.Instantiator;

/**
 * <i>Injectable</i> wrapper for Guice's injector.
 *
 * @author Tim Boudreau
 */
public interface Injection extends Instantiator {

    /**
     * Get the injector, creating it if necessary. This is the typical
     * entry-point for starting an application, e.g.:
     * <pre>
     * Dependencies deps = new Dependencies(new Module1(), new Module2());
     * Server server = deps.getInjector().getInstance(Server.class);
     * server.start();
     * </pre>
     *
     *
     * @return
     */
    Injector getInjector();

    /**
     * Get an instance of the passed type; throws an exception if nothing is
     * bound.
     *
     * @param <T> A type
     * @param type The type
     * @return An instance of that class
     */
    <T> T getInstance(Class<T> type);

    /**
     * Get an instance of the passed key; throws an exception if nothing is
     * bound.
     *
     * @param <T> A type
     * @param type The type
     * @return An instance of that class
     */
    <T> T getInstance(Key<T> key);

    /**
     * Same as getInjector().injectMembers(arg)
     *
     * @param arg
     */
    void injectMembers(Object arg);

}
