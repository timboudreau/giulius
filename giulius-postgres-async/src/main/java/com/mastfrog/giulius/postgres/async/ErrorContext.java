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
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Makes error handling easier and less drenched in boilerplate than the
 * driver makes it by default, especially when the same code will handle
 * errors regardless of which in a chain of nested methods was the trigger.
 * Example:
 * <pre>
 *  ErrorContext ctx = ctx();
 *  ctx.handle(p::getConnection, conn -> {
 *      ctx.handle("select * from things", conn::preparedQuery, (RowSet rowSet) -> {
 *          rowSet.forEach(row -> {
 *              String name = row.getString("name");
 *              assertNotNull(name);
 *              names.add(name);
 *              if (names.size() == 2) {
 *                  latch.countDown();
 *              }
 *          });
 *      });
 *  });
 * </pre>
 *
 * @author Tim Boudreau
 */
public final class ErrorContext {

    private final Consumer<Throwable> defaultHandler;
    // Sigh, the same crap I had to write for NodeJS's error handling
    // to avoid everything becoming spaghetti
    private final BooleanBiFunction<AsyncResult<?>, Throwable> onError;
    private final ThrowingRunnable errorCloses = ThrowingRunnable.composable();

    public ErrorContext(Consumer<Throwable> defaultHandler, BooleanBiFunction<AsyncResult<?>, Throwable> onError) {
        this.defaultHandler = defaultHandler;
        this.onError = onError;
    }

    public void ifError(ThrowingRunnable run) {
        errorCloses.andAlways(run);
    }

    public void onError(AsyncResult<?> res, Throwable thrown) {
        Exception closeThrew = null;
        try {
            errorCloses.run();
        } catch (Exception ex) {
            closeThrew = ex;
        }
        if (!this.onError.apply(res, thrown)) {
            if (thrown == null) {
                thrown = res.cause();
            }
            if (thrown != null) {
                defaultHandler.accept(thrown);
            }
            if (closeThrew != null) {
                defaultHandler.accept(closeThrew);
            }
        }
    }

    public <P, T> P handle(Function<Handler<AsyncResult<T>>, P> c, ThrowingConsumer<T> consumer) {
        return c.apply(ifNoError(consumer));
    }

    public <T, R> void handle(R r, BiConsumer<R, Handler<AsyncResult<T>>> c, ThrowingConsumer<T> consumer) {
        c.accept(r, ifNoError(consumer));
    }

    public <T> void handle(Consumer<Handler<AsyncResult<T>>> c, ThrowingConsumer<T> consumer) {
        c.accept(ifNoError(consumer));
    }

    public <T> Handler<AsyncResult<T>> ifNoError(ThrowingConsumer<T> c) {
        return (res) -> {
            ifNoError(res, c);
        };
    }

    public <T> boolean ifNoError(T obj, Throwable thrown, ThrowingConsumer<T> cons) {
        if (thrown == null) {
            try {
                cons.accept(obj);
                return true;
            } catch (Exception ex) {
                onError(null, ex);
                return false;
            }
        } else {
            onError(null, thrown);
            return false;
        }
    }

    public <T> boolean ifNoError(AsyncResult<T> res, ThrowingConsumer<T> cons) {
        if (res.succeeded()) {
            try {
                cons.accept(res.result());
                return true;
            } catch (Exception ex) {
                onError(res, ex);
                return false;
            }
        } else {
            if (res.cause() != null) {
                onError(res, res.cause());
            }
            return false;
        }
    }
}
