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

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.name.Names;
import static com.mastfrog.giulius.thread.ThreadPoolType.FORK_JOIN;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.ConfigurationError;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Building and injection thread pools of various types. Works with Settings to
 * allow for customization of parameters at startup time - e.g. if you bind an
 * executor namded "foo", if settings contains an integer "foo", that's the
 * thread count; if settings contains "foo.priority" that's the priority. THe
 * thread group, thread factory and executor service will all be bound with the
 * binding name, so any of those can be injected with &#064Named. foo.type can
 * determine which ThreadPoolType is used.
 * <p>
 * Binds both the types ExecutorService and Executor (and
 * ScheduledExecutorService if that type is set). You also get a binding to
 * &#064;Named(bindingName) for <code>Thread.class</code>, which will return the
 * most recently created thread for a given pool, if it has not been garbage
 * collected. This is only useful for the case of single-thread pools where
 * under some circumstances you want to interrupt the one thread in the pool.
 * Always inject a Provider&lt;Thread&gt; to use that, not an instance (which
 * will be null if the thread is not started), and do a null check.
 * <p>
 * Created ExecutorServices are automatically registered with
 * ShutdownHookRegistry so that they will be cleanly shut down in the event
 * Dependencies.shutdown() is called.
 * <p>
 * Limitations: ForkJoinPools offer less flexibility, so some features (thread
 * group, stack size) are not supported for them.
 *
 * @author Tim Boudreau
 */
public class ThreadModule extends AbstractModule {

    private final List<ExecutorServiceBuilderImpl> modules = new ArrayList<>();

    @Override
    protected void configure() {
        modules.forEach((m) -> {
            install(m);
        });
    }

    /**
     * Create a new ExecutorServiceBuilder.
     *
     * @param bindingName The &#064;named binding for it
     * @return a builder
     */
    public ExecutorServiceBuilder builder(String bindingName) {
        return new ExecutorServiceBuilderImpl(notNull("bindingName", bindingName));
    }

    private final class ExecutorServiceBuilderImpl extends ExecutorServiceBuilder implements Module {

        ExecutorServiceBuilderImpl(String bindingName) {
            super(bindingName);
        }

        @Override
        public String toString() {
            return "ExecutorServiceBuilder(" + bindingName + ")";
        }

        /**
         * Attach this executor service to the ThreadModule to be bound on
         * startup.
         *
         * @return The module
         */
        @Override
        public ThreadModule bind() {
            modules.stream().filter((e) -> (e.bindingName.equals(bindingName))).forEachOrdered((_item) -> {
                throw new ConfigurationError("Attempting to bind ExecutorService to the name " + bindingName + " twice.");
            });
            modules.add(this);
            return ThreadModule.this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void configure(Binder binder) {
            Provider<ShutdownHookRegistry> shutdown = binder.getProvider(ShutdownHookRegistry.class);
            Provider<UncaughtExceptionHandler> ueh = this.handler == null ? binder.getProvider(UncaughtExceptionHandler.class) : this.handler;
            Provider<Settings> settings = binder.getProvider(Settings.class);

            GiuliusThreadFactory threadFactory = new GiuliusThreadFactory(bindingName, ueh, priority, settings, supplier, stackSize);
            binder.bind(ThreadGroup.class).annotatedWith(Names.named(bindingName)).toInstance(threadFactory.tg);
            ThreadCount threadCount = new ThreadCount(binder.getProvider(Settings.class),
                    defaultThreadCount, explicitThreadCount, bindingName);
            if (legacyName != null) {
                threadCount.legacyPropertyName(legacyName);
            }
            binder.bind(ThreadCount.class).annotatedWith(Names.named(bindingName)).toInstance(threadCount);
            if (legacyName != null) {
                binder.bind(ThreadCount.class).annotatedWith(Names.named(legacyName)).toInstance(threadCount);
            }
            binder.bind(ThreadFactory.class).annotatedWith(Names.named(bindingName))
                    .toInstance(threadFactory);
            if (type != ThreadPoolType.SCHEDULED) {
                Provider<ExecutorService> exeProvider = new ExecutorServiceProvider<>(threadFactory,
                        threadCount, settings, ueh, type, shutdown, rejectedPolicy, shutdownBatch);
                bindOne(binder, ExecutorService.class, bindingName, exeProvider);
                bindOne(binder, Executor.class, bindingName, exeProvider);
                bindOne(binder, Thread.class, bindingName, threadFactory);
                if (legacyName != null) {
                    bindOne(binder, ExecutorService.class, legacyName, exeProvider);
                    bindOne(binder, Executor.class, legacyName, exeProvider);
                    bindOne(binder, Thread.class, legacyName, threadFactory);
                }
                if (type == FORK_JOIN) {
                    bindOne(binder, ForkJoinPool.class, bindingName, (Provider) exeProvider);
                    if (legacyName != null) {
                        bindOne(binder, ForkJoinPool.class, legacyName, (Provider) exeProvider);
                    }
                }
            } else {
                Provider<ScheduledExecutorService> exeProvider = new ExecutorServiceProvider<>(threadFactory,
                        threadCount, settings, ueh, type, shutdown, rejectedPolicy, shutdownBatch);
                bindOne(binder, ScheduledExecutorService.class, bindingName, exeProvider);
                bindOne(binder, ExecutorService.class, bindingName, exeProvider);
                bindOne(binder, Executor.class, bindingName, exeProvider);
                bindOne(binder, Thread.class, bindingName, threadFactory);
                if (legacyName != null) {
                    bindOne(binder, ScheduledExecutorService.class, legacyName, exeProvider);
                    bindOne(binder, ExecutorService.class, legacyName, exeProvider);
                    bindOne(binder, Executor.class, legacyName, exeProvider);
                    bindOne(binder, Thread.class, legacyName, threadFactory);
                }
            }
        }

        private <T> void bindOne(Binder binder, Class<? super T> type, String name, Provider<T> provider) {
            if (eager) {
                binder.bind(type).annotatedWith(Names.named(name)).toProvider(provider).asEagerSingleton();
            } else {
                binder.bind(type).annotatedWith(Names.named(name)).toProvider(provider);
            }
        }
    }
}
