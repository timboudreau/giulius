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
package com.mastfrog.giulius;

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * A registry of shutdown hooks which run in LIFO order on shutdown; for cases
 * where the order cannot be determined by instantiaton/addition order, first,
 * last and middle (default) batches of hooks are provided.
 * <p>
 * Hooks are guaranteed to be run <i>regardless of any other hook aborting or
 * throwing an exception</i>.
 * </p><p>
 * Hooks may be added such that they are weakly referenced, and will be omitted
 * if garbage-collected.
 * </p><p>
 * Additions <i>during</i> shutdown are guaranteed to be run.
 * </p><p>
 * Executor services will have termination waited for for a settable timeout;
 * the timeout is collective and is how long <i>all</i> executors will be waited
 * for in aggregate. The default is three minutes.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface ShutdownHooks {

    /**
     * Returns true if shutdown hooks are currently being run.
     *
     * @return True if hooks are running
     */
    boolean isRunningShutdownHooks();

    /**
     * Add a Runnable to the default batch.
     *
     * @param toRun A runnable
     */
    void add(Runnable toRun);

    /**
     * Add a Runnable to the batch which runs last.
     *
     * @param toRun A runnable
     * @return this
     */
    ShutdownHooks addLast(Runnable toRun);

    /**
     * Add a Runnable to the batch which runs first.
     *
     * @param toRun A runnable
     * @return this
     */
    ShutdownHooks addFirst(Runnable toRun);

    /**
     * Add a Runnable to the default batch, without keeping it strongly
     * referenced.
     *
     * @param toRun A runnable
     */
    ShutdownHooks addWeak(Runnable toRun);

    /**
     * Add a Runnable to the batch which runs first, without keeping it strongly
     * referenced.
     *
     * @param toRun A runnable
     */
    ShutdownHooks addFirstWeak(Runnable toRun);

    /**
     * Add a Runnable to the batch which runs first, without keeping it strongly
     * referenced.
     *
     * @param toRun A runnable
     */
    ShutdownHooks addLastWeak(Runnable toRun);

    /**
     * Add an executor service to the default batch; the executor service will
     * be strongly referenced; when hooks are run, all executors are first shut
     * down, and then in a separate pass, awaited for termination en-banc up to
     * the expiration of a timeout.
     *
     * @param toShutdown An ExecutorService
     * @return this
     */
    ShutdownHooks add(ExecutorService toShutdown);

    /**
     * Add an executor service to the batch which runs first; the executor
     * service will be strongly referenced; when hooks are run, all executors
     * are first shut down, and then in a separate pass, awaited for termination
     * en-banc up to the expiration of a timeout.
     *
     * @param toShutdown An ExecutorService
     * @return this
     */
    ShutdownHooks addFirst(ExecutorService toShutdown);

    /**
     * Add an executor service to the batch which runs last; the executor
     * service will be strongly referenced; when hooks are run, all executors
     * are first shut down, and then in a separate pass, awaited for termination
     * en-banc up to the expiration of a timeout.
     *
     * @param toShutdown An ExecutorService
     * @return this
     */
    ShutdownHooks addLast(ExecutorService toShutdown);

    /**
     * Add a timer to be cancelled to the default batch; the timer will be
     * <i>weakly</i> referenced.
     *
     * @param toCancel A timer
     * @return this
     */
    void add(Timer toCancel);

    /**
     * Add a timer to be cancelled to the batch which runs first; the timer will
     * be <i>weakly</i> referenced.
     *
     * @param toCancel A timer
     * @return this
     */
    ShutdownHooks addFirst(Timer toCancel);

    /**
     * Add a timer to be cancelled to the batch which runs last; the timer will
     * be <i>weakly</i> referenced.
     *
     * @param toCancel A timer
     * @return this
     */
    ShutdownHooks addLast(Timer toCancel);

    /**
     * Add an AutoCloseable to be cancelled to the default batch; the
     * AutoCloseable will be <i>weakly</i> referenced.
     *
     * @param toClose A resource to be closed
     * @return this
     */
    void add(AutoCloseable toClose);

    /**
     * Add an AutoCloseable to be cancelled to the batch which runs first; the
     * AutoCloseable will be <i>weakly</i> referenced.
     *
     * @param toClose A resource to be closed
     * @return this
     */
    ShutdownHooks addFirst(AutoCloseable toClose);

    /**
     * Add an AutoCloseable to be cancelled to the batch which runs last; the
     * AutoCloseable will be <i>weakly</i> referenced.
     *
     * @param toClose A resource to be closed
     * @return this
     */
    ShutdownHooks addLast(AutoCloseable toClose);

    ShutdownHooks add(Callable<?> toRun);

    ShutdownHooks addFirst(Callable<?> toRun);

    ShutdownHooks addLast(Callable<?> toRun);

    ShutdownHooks addFirstWeak(Callable<?> toRun);

    ShutdownHooks addLastWeak(Callable<?> toRun);

    ShutdownHooks addWeak(Callable<?> toRun);

    ShutdownHooks addThrowing(ThrowingRunnable toRun);

    ShutdownHooks addFirstThrowing(ThrowingRunnable toRun);

    ShutdownHooks addLastThrowing(ThrowingRunnable toRun);

    default ShutdownHooks addWeakThrowing(ThrowingRunnable toRun) {
        return addThrowing(ThrowingRunnable.weak(toRun));
    }

    default ShutdownHooks addFirstWeakThrowing(ThrowingRunnable toRun) {
        return addFirstThrowing(ThrowingRunnable.weak(toRun));
    }

    default ShutdownHooks addLastWeakThrowing(ThrowingRunnable toRun) {
        return addLastThrowing(ThrowingRunnable.weak(toRun));
    }

    /**
     * Imperatively run shutdown tasks, clearing the set of tasks and
     * deregistering the ShutdownHooks as a VM shutdown hook if it has been
     * registered.
     * <p>
     * In general this should <b>only be called by test frameworks and a few
     * rare cases of applications that cleanly shut down all their state and
     * reload themselves, or things run inside an isolating classloader whose
     * shutdown tasks must run before the classloader is closed.
     * </p>
     *
     * @return The count of tasks run during shutdown
     */
    int shutdown();
}
