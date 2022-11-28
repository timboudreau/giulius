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

import com.mastfrog.function.state.Obj;
import com.mastfrog.util.preconditions.Exceptions;
import java.util.concurrent.Callable;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
final class RunnableFunctionExecutionWrapper implements ExecutionWrapper {

    private final Function<Runnable, Runnable> f;

    public RunnableFunctionExecutionWrapper(Function<Runnable, Runnable> f) {
        this.f = f;
    }

    @Override
    public Runnable wrap(Runnable work) {
        return f.apply(work);
    }

    @Override
    public <V> Callable<V> wrap(Callable<V> work) {
        Obj<V> obj = Obj.create();
        Runnable wrapped = f.apply(() -> {
            try {
                obj.set(work.call());
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
        });
        return () -> {
            wrapped.run();
            return obj.get();
        };
    }
}
