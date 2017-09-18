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
import com.mastfrog.giulius.thread.ThreadPoolType;
import static com.mastfrog.giulius.thread.ThreadPoolType.FORK_JOIN;
import static com.mastfrog.giulius.thread.ThreadPoolType.SCHEDULED;
import static com.mastfrog.giulius.thread.ThreadPoolType.STANDARD;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.settings.Settings;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

/**
 *
 * @author Tim Boudreau
 */
final class ExecutorServiceProvider<T extends ExecutorService> implements Provider<T> {

    private final GiuliusThreadFactory tf;
    private volatile T svc;
    private final ThreadCount count;
    private final Provider<Settings> settings;
    private final Provider<Thread.UncaughtExceptionHandler> uncaught;
    private final ThreadPoolType type;
    private final Provider<ShutdownHookRegistry> reg;

    public ExecutorServiceProvider(GiuliusThreadFactory tf, ThreadCount count, Provider<Settings> settings, 
            Provider<Thread.UncaughtExceptionHandler> uncaught,
            ThreadPoolType type, Provider<ShutdownHookRegistry> reg) {
        this.tf = tf;
        this.count = count;
        this.settings = settings;
        this.uncaught = uncaught;
        this.type = type;
        this.reg = reg;
    }

    ThreadPoolType type() {
        ThreadPoolType type = this.type;
        Boolean useForkJoin = settings.get().getBoolean("acteur.fork.join");
        if (useForkJoin != null && useForkJoin && this.type != SCHEDULED) { // legacy support
            type = FORK_JOIN;
        } else if (useForkJoin != null && !useForkJoin) {
            type = this.type == SCHEDULED ? SCHEDULED : STANDARD;
        }
        if (type == null) {
            String typeName = settings.get().getString(tf.name() + ".type", ThreadPoolType.FORK_JOIN.name());
            type = ThreadPoolType.valueOf(typeName);
        }
        return type;
    }

    @SuppressWarnings("unchecked")
    private T create() {
        int threads = count.get();
        switch (type()) {
            case FORK_JOIN:
                return (T) new ForkJoinPool(threads, tf, uncaught.get(), false);
            case WORK_STEALING:
                return (T) new ForkJoinPool(threads, tf, uncaught.get(), true);
            case STANDARD:
                return (T) (threads == 1 ? Executors.newSingleThreadExecutor(tf) : Executors.newFixedThreadPool(threads, tf));
            case SCHEDULED:
                return (T) (threads == 1 ? Executors.newSingleThreadScheduledExecutor() : Executors.newScheduledThreadPool(threads, tf));
            default:
                throw new AssertionError(type);
        }
    }

    @Override
    public T get() {
        T service = this.svc;
        if (service == null) {
            synchronized (this) {
                service = this.svc;
                if (service == null) {
                    this.svc = service = create();
                    reg.get().add(service);
                }
            }
        }
        return service;
    }

}
