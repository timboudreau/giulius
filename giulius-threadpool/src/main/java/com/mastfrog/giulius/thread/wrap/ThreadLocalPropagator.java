/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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
package com.mastfrog.giulius.thread.wrap;

import java.util.concurrent.Callable;

/**
 *
 * @author Tim Boudreau
 */
final class ThreadLocalPropagator<T> implements GranularExecutionWrapper<T, T> {

    private final ThreadLocal<T> threadLocal;

    ThreadLocalPropagator(ThreadLocal<T> threadLocal) {
        this.threadLocal = threadLocal;
    }

    @Override
    public T onSubmit() {
        return threadLocal.get();
    }

    @Override
    public T onBeforeRun(T t) {
        T old = threadLocal.get();
        threadLocal.set(t);
        return old;
    }

    @Override
    public boolean onAfterRun(T fromSubmit, T fromRun, Throwable thrown) {
        if (fromRun == null) {
            threadLocal.remove();
        } else {
            threadLocal.set(fromRun);
        }
        return true;
    }

}
