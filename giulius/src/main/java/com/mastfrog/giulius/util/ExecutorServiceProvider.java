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
package com.mastfrog.giulius.util;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.util.preconditions.Checks;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * A provider of ExecutorServices which automatically registers them with the
 * ShutdownHookRegistry for clean unloading. Use the static methods to create
 * instances during module configuration. This simply saves frequently written
 * code to shut down a thread pool on dependencies shutdown (which may mean the
 * end of a unit test, or application clean reload without stopping the VM -
 * shutdown does not necessarily mean the VM itself is being shut down).
 *
 * @author Tim Boudreau
 * @deprecated The giulius-threadpool library offers all of this and more
 */
@Deprecated
public final class ExecutorServiceProvider implements Provider<ExecutorService> {

    private final AtomicBoolean registered = new AtomicBoolean();
    private final Supplier<ExecutorService> svc;
    private final Provider<ShutdownHookRegistry> reg;
    volatile ExecutorService exe;

    ExecutorServiceProvider(Supplier<ExecutorService> svc, Provider<ShutdownHookRegistry> reg) {
        this.svc = svc;
        this.reg = reg;
    }

    @Override
    public ExecutorService get() {
        ExecutorService result = null;
        if (registered.compareAndSet(false, true)) {
            synchronized (this) {
                if (exe == null) {
                    result = exe = svc.get();
                    reg.get().add(exe);
                } else {
                    result = exe;
                }
            }
        }
        if (result == null) { // two threads entered at the same time, and the other won compareAndSet
            synchronized (this) {
                result = exe;
            }
            assert result != null;
        }
        return result;
    }

    /**
     * Create a provider, using the passed supplier to create the thread pool.
     *
     * @param supplier A supplier
     * @param binder The binder
     * @return A provider
     */
    public static Provider<ExecutorService> provider(Supplier<ExecutorService> supplier, Binder binder) {
        Checks.notNull("binder", binder);
        return new ExecutorServiceProvider(supplier, binder.getProvider(ShutdownHookRegistry.class));
    }

    /**
     * Create a fixed thread pool using the passed number of threads and binder.
     *
     * @param threads The thread count
     * @param binder
     * @return
     */
    public static Provider<ExecutorService> provider(int threads, Binder binder) {
        Checks.nonZero("threads", threads);
        Checks.nonNegative("threads", threads);
        return provider(() -> Executors.newFixedThreadPool(threads), binder);
    }

    /**
     * Create a "work stealing" thread pool using
     * Executors.newWorkStealingPool() and bind it to be shutdown on
     * dependencies shutdown.
     *
     * @param binder The binder
     * @return A provider
     */
    public static Provider<ExecutorService> provider(Binder binder) {
        return provider(() -> Executors.newWorkStealingPool(), binder);
    }
}
