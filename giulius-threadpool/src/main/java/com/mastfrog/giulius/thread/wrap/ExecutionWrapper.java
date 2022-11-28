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

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Wraps runnables or callables in before/after logic for things such as
 * populating thread locals, intercepting exceptions, etc.
 *
 * @author Tim Boudreau
 */
public interface ExecutionWrapper {

    /**
     * Wrap a runnable.
     *
     * @param work A runnable
     * @return the original runnable or one which delegates to it
     */
    Runnable wrap(Runnable work);

    /**
     * Wrap a callable.
     *
     * @param <V> The callable's return type
     * @param work A callable
     * @return the original callable or one which delegates to it
     */
    <V> Callable<V> wrap(Callable<V> work);

    /**
     * Create an ExecutionWrapper which propagates the contents of a ThreadLocal
     * into the same ThreadLocal on the execution thread before the work is run,
     * and resets it after running the work.
     *
     * @param <X> The thread local's parameter type
     * @param loc A thread-local
     * @return An ExecutionWrapper
     */
    public static <X> ExecutionWrapper propagatingThreadLocal(ThreadLocal<X> loc) {
        return new ThreadLocalPropagator<>(notNull("loc", loc));
    }

    /**
     * Create an ExecutionWrapper using a simplified interface to wrap
     * callables.
     *
     * @param f A converter
     * @return An executionWrapper
     */
    public static ExecutionWrapper executionWrapper(Function<Runnable, Runnable> f) {
        return new RunnableFunctionExecutionWrapper(f);
    }

    /**
     * Create an ExecutionWrapper using a simplified interface to wrap
     * callables.
     *
     * @param f A converter
     * @return An executionWrapper
     */
    public static ExecutionWrapper executionWrapper(Supplier<Function<Runnable, Runnable>> f) {
        return new RunnableFunctionExecutionWrapper(x -> f.get().apply(x));
    }

    /**
     * Create an ExecutionWrapper using a simplified interface to wrap
     * callables.
     *
     * @param converter A converter
     * @return An executionWrapper
     */
    public static ExecutionWrapper executionWrapper(CallableConverter converter) {
        notNull("converter", converter);
        return new CallableConverterExecutionWrapper(converter);
    }

}
