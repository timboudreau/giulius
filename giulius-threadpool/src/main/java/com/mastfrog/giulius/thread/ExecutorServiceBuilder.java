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
import com.google.inject.util.Providers;
import com.mastfrog.giulius.thread.wrap.CallableConverter;
import com.mastfrog.giulius.thread.wrap.ExecutionWrapper;
import static com.mastfrog.giulius.thread.wrap.ExecutionWrapper.executionWrapper;
import com.mastfrog.shutdown.hooks.ShutdownHooks;
import com.mastfrog.util.preconditions.Checks;
import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

/**
 * Builder for ExecutorService / Executor / ScheduledExecutorService bindings
 * which allows customization of the parameters of them.
 *
 * @author Tim Boudreau
 */
public abstract class ExecutorServiceBuilder {

    final String bindingName;
    int defaultThreadCount = Runtime.getRuntime().availableProcessors();
    int explicitThreadCount = -1;
    int priority;
    ThreadPoolType type = null;
    Provider<Thread.UncaughtExceptionHandler> handler;
    boolean daemon;
    String legacyName;
    boolean eager;
    ConventionalThreadSupplier supplier;
    int stackSize;
    RejectedExecutionPolicy rejectedPolicy
            = RejectedExecutionPolicy.defaultPolicy();
    ShutdownBatch shutdownBatch = ShutdownBatch.DEFAULT;
    final List<ExecutionWrapper> wrappers = new ArrayList<>();

    ExecutorServiceBuilder(String bindingName) {
        this.bindingName = bindingName;
    }

    @Override
    public String toString() {
        return "ExecutorServiceBuilder(" + bindingName + ", "
                + " " + defaultThreadCount + ", " + explicitThreadCount
                + ", " + priority + ", " + type + ", "
                + legacyName + " " + eager + ")";
    }

    /**
     * Use one or more ExecutionWrappers to run submit/before/after logic on all
     * submitted runnables or callables - this is useful for propagating
     * thread-local context, incrementing concurrency counters and similar.
     * <p>
     * Note, ExecutionWrappers <i>cannot</i> be used with fork-join /
     * work-stealing pools - there are too many internal paths by which work
     * passed to the pool may be executed to guarantee that wrappers will always
     * be run.
     * </p>
     *
     * @param wrapper A wrapper
     * @param moreWrappers Some more wrappers
     * @return this
     */
    public ExecutorServiceBuilder wrappingSubmissionsWith(ExecutionWrapper wrapper, ExecutionWrapper... moreWrappers) {
        wrappers.add(wrapper);
        if (moreWrappers.length > 0) {
            wrappers.addAll(asList(moreWrappers));
        }
        return this;
    }

    /**
     * Wrap all submitted runnables and callables in before/after logic using
     * the passed converter.
     * <p>
     * Note, ExecutionWrappers <i>cannot</i> be used with fork-join /
     * work-stealing pools - there are too many internal paths by which work
     * passed to the pool may be executed to guarantee that wrappers will always
     * be run.
     * </p>
     *
     * @param cvt A converter
     * @return this
     */
    public ExecutorServiceBuilder wrappingSubmissionsWith(CallableConverter cvt) {
        wrappers.add(executionWrapper(cvt));
        return this;
    }

    /**
     * Wrap all submitted runnables and callables in before/after logic using
     * the passed function.
     * <p>
     * Note, ExecutionWrappers <i>cannot</i> be used with fork-join /
     * work-stealing pools - there are too many internal paths by which work
     * passed to the pool may be executed to guarantee that wrappers will always
     * be run.
     * </p>
     *
     * @param cvt A converter
     * @return this
     */
    public ExecutorServiceBuilder wrappingSubmissionsWith(Function<Runnable, Runnable> cvt) {
        wrappers.add(executionWrapper(cvt));
        return this;
    }

    /**
     * Propagate the value of the passed ThreadLocal (if any) at the time of
     * submission to the same ThreadLocal before invoking any runnable/callable,
     * resetting it for the worker thread after the work has been run.
     * <p>
     * Note, ExecutionWrappers <i>cannot</i> be used with fork-join /
     * work-stealing pools - there are too many internal paths by which work
     * passed to the pool may be executed to guarantee that wrappers will always
     * be run.
     * </p>
     *
     * @param <X> The thread local's parameter type
     * @param tl A thread local
     * @return this
     */
    public <X> ExecutorServiceBuilder propagatingThreadLocal(ThreadLocal<X> tl) {
        wrappers.add(ExecutionWrapper.propagatingThreadLocal(tl));
        return this;
    }

    ExecutionWrapper[] wrappers() {
        if (wrappers.isEmpty()) {
            return null;
        }
        return wrappers.toArray(new ExecutionWrapper[wrappers.size()]);
    }

    /**
     * Set the stack size for created threads (not used if type = FORK_JOIN
     * where this is made not customizable by the JDK). Values &lt;= 0 mean use
     * the JVM's default. Only set this if you know what you're doing.
     *
     * @param stackSize The stack size
     * @return this
     */
    public ExecutorServiceBuilder withStackSize(int stackSize) {
        this.stackSize = greaterThanZero("stackSize", stackSize);
        return this;
    }

    /**
     * Use if you want to customize the thread class instantiated for thread
     * pools, such as using Netty's FastThreadLocalThread instead of the
     * standard Java thread. This allows that sort of customization without this
     * library having a dependency on such classes.
     *
     * @return this
     */
    public ExecutorServiceBuilder withThreadSupplier(ConventionalThreadSupplier supplier) {
        this.supplier = Checks.notNull("supplier", supplier);
        return this;
    }

    /**
     * If called, resulting ExecutorService will be bound as an eager singleton,
     * causing it to be instantiated immediately after injector initialization.
     * Where this is useful is deterministic shutdown order - if you want to be
     * sure that your thread pool is only shut down
     * <i>after anything that uses it is shut down</i> (shutdown hooks are run
     * in the reverse of the order they were added in), this ensures that. Since
     * a class might inject an instance of
     * <code>Provider&lt;ExecutorService&gt;</code>, add itself to the shutdown
     * hook registry and <i>then</i> start itself (triggering initialization of
     * the ExecutorService), you can otherwise wind up with services trying to
     * submit jobs to the ExecutorService after it has been shut down. In
     * practice, this is mainly seen in unit tests, but results in a bunch of
     * ExecutionExceptions polluting the test logs.
     *
     * @return this
     */
    public ExecutorServiceBuilder eager() {
        this.eager = true;
        return this;
    }

    /**
     * If called, all threads will be daemon threads.
     *
     * @return this
     */
    public ExecutorServiceBuilder daemon() {
        this.daemon = true;
        return this;
    }

    /**
     * Explicitly set the UncaughtExceptionHandler for this thread pool (by
     * default, you need to bind UncaughtExceptionHandler).
     *
     * @param ueh A provider for a handler
     * @return this
     */
    public ExecutorServiceBuilder withUncaughtExceptionHandler(
            Provider<UncaughtExceptionHandler> ueh) {
        this.handler = ueh;
        return this;
    }

    /**
     * Explicitly set the UncaughtExceptionHandler for this thread pool (by
     * default, you need to bind UncaughtExceptionHandler).
     *
     * @param ueh A handler
     * @return this
     */
    public ExecutorServiceBuilder withUncaughtExceptionHandler(
            UncaughtExceptionHandler ueh) {
        this.handler = Providers.of(Checks.notNull("ueh", ueh));
        return this;
    }

    /**
     * Set the default thread count if not overridden by an explicit value or
     * settings.
     *
     * @param threadCount The thread count
     * @return this
     */
    public ExecutorServiceBuilder withDefaultThreadCount(int threadCount) {
        this.defaultThreadCount = greaterThanZero("threadCount", threadCount);
        return this;
    }

    /**
     * For the case of classes where an integer value for the thread count can
     * be passed, overriding settings or the default, pass that for the thread
     * count (if &lt; 0 then settings or the default take precedence).
     *
     * @param threadCount The thread count
     * @return this
     */
    public ExecutorServiceBuilder withExplicitThreadCount(int threadCount) {
        this.explicitThreadCount = greaterThanZero("explicitThreadCount", threadCount);
        return this;
    }

    /**
     * Set the priority for threads in this executor service; by default, uses
     * Thread.NORM_PRIORITY.
     *
     * @param prio The priority
     * @return this
     */
    public ExecutorServiceBuilder withThreadPriority(int prio) {
        if (prio < Thread.MIN_PRIORITY || prio > Thread.MAX_PRIORITY) {
            throw new IllegalArgumentException("Priority must be within "
                    + " Thread.MIN_PRIORITY (" + Thread.MIN_PRIORITY + ") and "
                    + "Thread.MAX_PRIORITY (" + Thread.MAX_PRIORITY + ") "
                    + "but got " + prio);
        }
        this.priority = prio;
        return this;
    }

    /**
     * Set the thread pool type - traditional Executor, or fork join, or work
     * stealing or scheduled.
     *
     * @param type The type
     * @return this
     */
    public ExecutorServiceBuilder withThreadPoolType(ThreadPoolType type) {
        this.type = notNull("type", type);
        return this;
    }

    /**
     * Handles the case that for backward compatibility, some applications may
     * use a different name for the binding which has been deprecated.
     *
     * @param name The alternate binding name
     * @return this
     */
    public ExecutorServiceBuilder legacyThreadCountName(String name) {
        this.legacyName = name;
        return this;
    }

    /**
     * Actually add this builder's parameters as an Executor / ExecutorService
     * to be bound.
     *
     * @return The module that owns this builder
     */
    public abstract ThreadModule bind();

    /**
     * Convenience method for
     * <code>withThreadPoolType(ThreadPoolType.FORK_JOIN)</code>
     *
     * @return this
     */
    public ExecutorServiceBuilder forkJoin() {
        return this.withThreadPoolType(ThreadPoolType.FORK_JOIN);
    }

    /**
     * Convenience method for
     * <code>withThreadPoolType(ThreadPoolType.WORK_STEALING)</code>
     *
     * @return this
     */
    public ExecutorServiceBuilder workStealing() {
        return this.withThreadPoolType(ThreadPoolType.WORK_STEALING);
    }

    /**
     * Convenience method for
     * <code>withThreadPoolType(ThreadPoolType.STANDARD)</code>
     *
     * @return this
     */
    public ExecutorServiceBuilder standard() {
        return this.withThreadPoolType(ThreadPoolType.STANDARD);
    }

    /**
     * Convenience method for
     * <code>withThreadPoolType(ThreadPoolType.SCHEDULED)</code>
     *
     * @return this
     */
    public ExecutorServiceBuilder scheduled() {
        return this.withThreadPoolType(ThreadPoolType.SCHEDULED);
    }

    public ExecutorServiceBuilder withRejectedExecutionPolicy(RejectedExecutionPolicy policy) {
        this.rejectedPolicy = policy;
        return this;
    }

    void validate() {
        if (type != null) {
            switch (type) {
                case FORK_JOIN:
                case WORK_STEALING:
                    if (!rejectedPolicy.isDefault()) {
                        throw new IllegalArgumentException("Cannot build a "
                                + "work-stealing or fork-join pool with a non-default "
                                + "RejectedExecutionPolicy - ForkJoinPool does not support "
                                + "RejectedExecutionHandlers, but have policy " + rejectedPolicy);
                    }
                    if (!wrappers.isEmpty()) {
                        throw new IllegalArgumentException("Cannot wrap runnables in a Fork-Join pool");
                    }
            }
        }
    }

    public enum RejectedExecutionPolicy {
        ABORT,
        CALLER_RUNS_IF_NOT_SHUTDOWN,
        CALLER_ALWAYS_RUNS,
        DISCARD,
        DISCARD_OLDEST;

        static RejectedExecutionPolicy defaultPolicy() {
            return ABORT;
        }

        boolean isDefault() {
            return this == ABORT;
        }

        RejectedExecutionHandler policy() {
            switch (this) {
                case ABORT:
                    return new ThreadPoolExecutor.AbortPolicy();
                case CALLER_RUNS_IF_NOT_SHUTDOWN:
                    return new ThreadPoolExecutor.CallerRunsPolicy();
                case CALLER_ALWAYS_RUNS:
                    return (r, exe) -> {
                        r.run();
                    };
                case DISCARD:
                    return new ThreadPoolExecutor.DiscardPolicy();
                case DISCARD_OLDEST:
                    return new ThreadPoolExecutor.DiscardOldestPolicy();
                default:
                    return new ThreadPoolExecutor.AbortPolicy();
            }
        }
    }

    public ExecutorServiceBuilder shutdownCoordination(ShutdownBatch batch) {
        this.shutdownBatch = batch;
        return this;
    }

    /**
     * ShutdownHooks contains first, middle and last batches of shutdown hooks,
     * which are used to shut down and wait for executor services. Hooks are run
     * in LIFO order. Ordinarily this is sufficient, but there are some cases
     * where, due to Guice lazily instantiating things, a thing that uses a
     * thread pool may be shut down before a thing that uses it is finished with
     * it; or it may be desirable to have shutdown occur before other shutdown
     * tasks.
     * <p>
     * This enum is used to specify if something other than the default batch
     * should be used for a given executor.
     */
    public enum ShutdownBatch {
        EARLY,
        DEFAULT,
        LATE,
        IGNORE;

        void apply(ExecutorService svc, ShutdownHooks to) {
            switch (this) {
                case EARLY:
                    to.addFirst(svc);
                    break;
                case DEFAULT:
                    to.add(svc);
                    break;
                case LATE:
                    to.addLast(svc);
                    break;
                case IGNORE:
                    // do nothing
                    break;
                default:
                    throw new AssertionError(this);
            }
        }
    }
}
