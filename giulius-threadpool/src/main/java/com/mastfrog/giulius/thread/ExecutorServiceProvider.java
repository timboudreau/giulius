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
import com.mastfrog.giulius.thread.ExecutorServiceBuilder.RejectedExecutionPolicy;
import com.mastfrog.giulius.thread.ExecutorServiceBuilder.ShutdownBatch;
import static com.mastfrog.giulius.thread.ThreadPoolType.FORK_JOIN;
import static com.mastfrog.giulius.thread.ThreadPoolType.SCHEDULED;
import static com.mastfrog.giulius.thread.ThreadPoolType.STANDARD;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private final RejectedExecutionPolicy rejectedPolicy;
    private final ShutdownBatch shutdownBatch;

    ExecutorServiceProvider(GiuliusThreadFactory tf, ThreadCount count, Provider<Settings> settings,
            Provider<Thread.UncaughtExceptionHandler> uncaught,
            ThreadPoolType type, Provider<ShutdownHookRegistry> reg,
            RejectedExecutionPolicy rejectedPolicy, ShutdownBatch shutdownBatch) {
        this.tf = tf;
        this.count = count;
        this.settings = settings;
        this.uncaught = uncaught;
        this.type = type;
        this.reg = reg;
        this.rejectedPolicy = rejectedPolicy;
        this.shutdownBatch = shutdownBatch;
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
        int corePoolSize = settings.get().getInt(tf.name() + ".corePoolSize", threads);
        threads = Math.max(corePoolSize, threads);
        long keepAliveSeconds = settings.get().getLong(tf.name() + ".keepAliveSeconds", Long.MAX_VALUE);
        if (keepAliveSeconds < 0) {
            throw new IllegalArgumentException(tf.name() + ".keepAliveSeconds may not be < 0 but is " + keepAliveSeconds);
        }
        if (corePoolSize < 0) {
            throw new IllegalArgumentException(tf.name() + ".corePoolSize may not be < 0 but is " + corePoolSize);
        }
        switch (type()) {
            case FORK_JOIN:
                if (!rejectedPolicy.isDefault() || corePoolSize != threads || keepAliveSeconds != Long.MAX_VALUE) {
                    T result = (T) reflectivelyCreateJDK11ForkJoinPool(threads, corePoolSize, keepAliveSeconds);
                    if (result != null) {
                        return result;
                    }
                }
                return (T) new ForkJoinPool(threads, tf, uncaught.get(), false);
            case WORK_STEALING:
                if (!rejectedPolicy.isDefault() || corePoolSize != threads || keepAliveSeconds != Long.MAX_VALUE) {
                    T result = (T) reflectivelyCreateJDK11ForkJoinPool(threads, corePoolSize, keepAliveSeconds);
                    if (result != null) {
                        return result;
                    }
                }
                return (T) new ForkJoinPool(threads, tf, uncaught.get(), true);
            case STANDARD:
                if (!rejectedPolicy.isDefault() || corePoolSize != threads || keepAliveSeconds != Long.MAX_VALUE) {
                    return (T) new ThreadPoolExecutor(corePoolSize, threads, keepAliveSeconds, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), tf, rejectedPolicy.policy());
                }
                return (T) (threads == 1 ? Executors.newSingleThreadExecutor(tf) : Executors.newFixedThreadPool(threads, tf));
            case SCHEDULED:
                if (!rejectedPolicy.isDefault()) {
                    return (T) new ScheduledThreadPoolExecutor(threads, tf, rejectedPolicy.policy());
                }
                return (T) (threads == 1 ? Executors.newSingleThreadScheduledExecutor() : Executors.newScheduledThreadPool(threads, tf));
            default:
                throw new AssertionError(type);
        }
    }

    static volatile boolean noForkJoinPoolConstructor;
    static Constructor<ForkJoinPool> cachedConstructor;

    static Constructor<ForkJoinPool> forkJoinPoolJDK11Constructor() {
        // XXX jdk9 get rid of this when we can assume a newer JDK than 8
        if (cachedConstructor != null) {
            return cachedConstructor;
        } else if (noForkJoinPoolConstructor) {
            return null;
        }
        /* Signature:
        ForkJoinPool​(int parallelism, ForkJoinPool.ForkJoinWorkerThreadFactory factory,
        Thread.UncaughtExceptionHandler handler, boolean asyncMode, int corePoolSize,
        int maximumPoolSize, int minimumRunnable, Predicate<? super ForkJoinPool> saturate,
        long keepAliveTime, TimeUnit unit)
         */
        try {
            Constructor<ForkJoinPool> result = ForkJoinPool.class.getConstructor(Integer.TYPE,
                    ForkJoinPool.ForkJoinWorkerThreadFactory.class,
                    Thread.UncaughtExceptionHandler.class,
                    Boolean.TYPE, Integer.TYPE,
                    Integer.TYPE, Integer.TYPE, Predicate.class, Long.TYPE, TimeUnit.class);
            return cachedConstructor = result;
        } catch (Exception | Error e) {
            noForkJoinPoolConstructor = true;
            Logger.getLogger(ExecutorServiceProvider.class.getName()).log(Level.WARNING, "No JDK 11 ForkJoinPool constructor found");
        }
        return null;
    }

    private ForkJoinPool reflectivelyCreateJDK11ForkJoinPool(int threads, int corePoolSize, long keepAliveSeconds) {
        Constructor<ForkJoinPool> con = forkJoinPoolJDK11Constructor();
        if (con == null) {
            return null;
        }
        Logger.getLogger(ExecutorServiceProvider.class.getName()).log(Level.INFO,
                "Reflectively using JDK 11 ForkJoinPool constructor for '" + tf.name() + "'");
        boolean async = type() == ThreadPoolType.FORK_JOIN ? false : true;
        boolean saturate = settings.get().getBoolean(tf.name() + ".saturate", false);
        int maxpoolSize = settings.get().getInt(tf.name() + ".maxPoolSize", threads + 1);
        int minpoolSize = settings.get().getInt(tf.name() + ".minPoolSize", threads);
        /* Signature:
        ForkJoinPool​(int parallelism, ForkJoinPool.ForkJoinWorkerThreadFactory factory,
        Thread.UncaughtExceptionHandler handler, boolean asyncMode, int corePoolSize,
        int maximumPoolSize, int minimumRunnable, Predicate<? super ForkJoinPool> saturate,
        long keepAliveTime, TimeUnit unit)
         */
        try {
            Predicate<ForkJoinPool> pred = pool -> saturate;
            ForkJoinPool result = con.newInstance(threads, tf, uncaught.get(), async, corePoolSize, maxpoolSize,
                    minpoolSize, pred, keepAliveSeconds, TimeUnit.SECONDS);
            System.out.println("  Successfully used JDK > 8 fjp constructor");
            return result;
        } catch (Exception | Error ex) {
            Logger.getLogger(ExecutorServiceProvider.class.getName()).log(Level.WARNING, "Error reflectively invoking JDK 11 ForkJoinPool constructor", ex);
        }
        return null;
    }

    @Override
    public T get() {
        T service = this.svc;
        if (service == null) {
            synchronized (this) {
                service = this.svc;
                if (service == null) {
                    this.svc = service = create();
                    shutdownBatch.apply(service, reg.get());
                }
            }
        }
        return service;
    }

}
