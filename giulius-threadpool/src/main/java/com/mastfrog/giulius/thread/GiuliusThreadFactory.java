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
package com.mastfrog.giulius.thread;

import com.google.inject.Provider;
import com.mastfrog.settings.Settings;
import java.lang.ref.WeakReference;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author Tim Boudreau
 */
final class GiuliusThreadFactory implements ThreadFactory, ForkJoinPool.ForkJoinWorkerThreadFactory, Provider<Thread> {

    private final String name;
    private final Provider<Thread.UncaughtExceptionHandler> uncaught;
    private final AtomicInteger count = new AtomicInteger();
    private final int priority;
    final ThreadGroup tg;
    private final Provider<Settings> settings;
    private final ConventionalThreadSupplier supplier;
    private final int stackSize;

    GiuliusThreadFactory(String name, Provider<Thread.UncaughtExceptionHandler> app, int priority,
            Provider<Settings> settings, ConventionalThreadSupplier supplier, int stackSize) {
        this.name = name;
        this.uncaught = app;
        tg = new ThreadGroup(Thread.currentThread().getThreadGroup(), name + "s");
        tg.setDaemon(true);
        if (priority <= 0) {
            priority = Thread.NORM_PRIORITY;
        }
        if (priority < Thread.MIN_PRIORITY) {
            priority = Thread.MIN_PRIORITY;
        }
        if (priority > Thread.MAX_PRIORITY) {
            priority = Thread.MAX_PRIORITY;
        }
        this.priority = priority;
        this.settings = settings;
        this.supplier = supplier == null ? ConventionalThreadSupplier.DEFAULT : supplier;
        this.stackSize = stackSize;
    }

    ThreadGroup threadGroup() {
        return tg;
    }

    String name() {
        return name;
    }

    private WeakReference<Thread> lastThread;
    @Override
    public Thread newThread(Runnable r) {
        int index = count.getAndIncrement();
        String threadName = name + "-" + index;
        Thread t = supplier.newThread(tg, r, settings.get(), stackSize, name, threadName);
        t.setPriority(settings.get().getInt(name + ".priority", priority));
        t.setUncaughtExceptionHandler(uncaught.get());
        t.setName(threadName);
        lastThread = new WeakReference<>(t);
        return t;
    }

    public String toString() {
        return "ThreadFactory " + name;
    }

    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        FWT t = new FWT(pool, tg);
        t.setPriority(priority);
        t.setUncaughtExceptionHandler(uncaught.get());
        String nm = name + "-" + count.getAndIncrement();
        t.setName(nm);
        return t;
    }

    @Override
    public Thread get() {
        WeakReference<Thread> lt = lastThread;
        return lt == null ? null : lt.get();
    }

    static class FWT extends java.util.concurrent.ForkJoinWorkerThread {

        public FWT(ForkJoinPool pool, ThreadGroup group) {
            super(pool);
        }
    }
}
