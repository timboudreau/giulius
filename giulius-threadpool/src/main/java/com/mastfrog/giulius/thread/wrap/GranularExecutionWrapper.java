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

import static com.mastfrog.util.preconditions.Exceptions.chuck;
import java.util.concurrent.Callable;

/**
 * Wraps runnables and callables with submission, before and after logic.
 *
 * @author Tim Boudreau
 */
public interface GranularExecutionWrapper<T, R> extends ExecutionWrapper {

    /**
     * Called on the submitting thread when work is submitted.
     *
     * @return An object which will be passed to onBeforeRun
     */
    default T onSubmit() {
        return null;
    }

    /**
     * Called on the thread the work will run on immediately before it is run.
     *
     * @param t The object returned from onSubmit
     * @return An object which will be passed to onAfterRun
     */
    default R onBeforeRun(T t) {
        return null;
    }

    /**
     * Called after the work is run, regardless of outcome.
     *
     * @param fromSubmit The object returned by onSubmit when the work was
     * submitted
     * @param fromRun The object returned by onBeforeRun
     * @param thrown A throwable if any was thrown
     * @return true if any throwable should be rethrown to the thread's uncaught
     * exception handler or other wrappers in the chain
     */
    default boolean onAfterRun(T fromSubmit, R fromRun, Throwable thrown) {
        return true;
    }

    @Override
    default Runnable wrap(Runnable run) {
        T sub = onSubmit();
        return () -> {
            R before = onBeforeRun(sub);
            Throwable thrown = null;
            boolean shouldRethrow = true;
            try {
                run.run();
            } catch (Throwable th) {
                thrown = th;

            } finally {
                shouldRethrow = onAfterRun(sub, before, thrown);
            }
            if (shouldRethrow && thrown != null) {
                chuck(thrown);
            }
        };
    }

    @Override
    public default <V> Callable<V> wrap(Callable<V> run) {
        T sub = onSubmit();
        return () -> {
            R before = onBeforeRun(sub);
            Throwable thrown = null;
            boolean shouldRethrow = true;
            V result = null;
            try {
                result = run.call();
            } catch (Throwable th) {
                thrown = th;

            } finally {
                shouldRethrow = onAfterRun(sub, before, thrown);
            }
            if (shouldRethrow && thrown != null) {
                return chuck(thrown);
            }
            return result;
        };
    }

}
