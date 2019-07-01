/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
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
package com.mastfrog.giulius.postgres.async;

import com.mastfrog.function.BooleanBiFunction;
import io.vertx.core.AsyncResult;
import java.util.function.Consumer;

/**
 * A generic interface for things that handle application-level errors,
 * which also deals with creating error contexts for dealing with
 * the postgres driver's asynchronous error handling with minimal
 * code-mess.  Not bound by default, and not required, but useful.
 *
 * @author Tim Boudreau
 */
public interface PgErrorHandler extends Thread.UncaughtExceptionHandler, Consumer<Throwable> {

    default boolean isFatal(Throwable thrown) {
        return false;
    }

    default void accept(Throwable thrown) {
        this.uncaughtException(Thread.currentThread(), thrown);
    }

    default ErrorContext context(BooleanBiFunction<AsyncResult<?>, Throwable> onError) {
        return new ErrorContext(this, onError);
    }
}
