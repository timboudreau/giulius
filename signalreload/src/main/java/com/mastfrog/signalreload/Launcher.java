/*
 * The MIT License
 *
 * Copyright 2013 Mastfrog Technologies.
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
package com.mastfrog.signalreload;

import com.mastfrog.giulius.Dependencies;

/**
 * Runs the code that launches the application. Note that this method should
 * return, not block to avoid system exit - do that with the return value from
 * start() if necessary (and be aware that a signal could make it look like what
 * you're waiting on is done!).
 *
 * @param <T>
 */
public interface Launcher<T> {

    /**
     * Launch the application. Note this is not run on the thread that calls
     * start().
     *
     * @param deps The dependencies/Guice injector
     * @return Some object the caller can use
     */
    public T launch(Dependencies deps) throws Exception;

}
