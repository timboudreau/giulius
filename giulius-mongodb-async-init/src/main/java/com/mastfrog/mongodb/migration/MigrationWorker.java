/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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
package com.mastfrog.mongodb.migration;

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingQuadConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bson.Document;

/**
 * Convenience interface to avoid huge generic signatures when implementing.
 *
 * @author Tim Boudreau
 */
public interface MigrationWorker extends ThrowingQuadConsumer<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Function<Class<? extends MigrationWorker>, MigrationWorker>> {

    /**
     * Perform a migration on one collection, completing the passed
     * CompletableFuture with a summary document of what was done.
     *
     * @param a A future to complete
     * @param b The database
     * @param s The collection
     * @param u A function which can convert a migration worker class into a
     * worker
     * @throws Exception
     */
    @Override
    void apply(CompletableFuture<Document> a, MongoDatabase b, MongoCollection<Document> s, Function<Class<? extends MigrationWorker>, MigrationWorker> u) throws Exception;

    /**
     * Convenience method for handling any thrown exceptions via the
     * CompletableFuture and only needing to code the success handler.
     *
     * @param <T> The type of the callback
     * @param f A future
     * @param c A consumer
     * @return A callback
     */
    default <T> SingleResultCallback<T> callback(CompletableFuture<?> f, ThrowingConsumer<T> c) {
        return (t, thrown) -> {
            if (thrown != null) {
                f.completeExceptionally(thrown);
                return;
            }
            try {
                c.accept(t);
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        };
    }

    /**
     * Convenience method for handling any thrown exceptions via the
     * CompletableFuture and only needing to code the success handler.
     *
     * @param <T> The type of the callback
     * @param f A future
     * @param c A consumer
     * @return A callback
     */
    default <T> SingleResultCallback<T> callback(String context, CompletableFuture<?> f, ThrowingConsumer<T> c) {
        return (t, thrown) -> {
            if (thrown != null) {
                IllegalStateException ex = new IllegalStateException(thrown.getMessage() + " in " + context, thrown);
                f.completeExceptionally(ex);
                return;
            }
            try {
                c.accept(t);
            } catch (Exception e) {
                IllegalStateException ex = new IllegalStateException(e.getMessage() + " in " + context, e);
                f.completeExceptionally(ex);
            }
        };
    }

    /**
     * Convenience method for handling any thrown exceptions via the
     * CompletableFuture, for the case that the parameter of the callback is
     * unused.
     *
     * @param <T> The type parameter of the callback
     * @param f A future
     * @param r A consumer
     * @return A callback
     */
    default <T> SingleResultCallback<T> emptyCallback(CompletableFuture<?> f, ThrowingRunnable r) {
        return (t, thrown) -> {
            if (thrown != null) {
                f.completeExceptionally(thrown);
                return;
            }
            try {
                r.run();
            } catch (Exception e) {
                f.completeExceptionally(e);
            }
        };
    }

    /**
     * Convenience method for handling any thrown exceptions via the
     * CompletableFuture, for the case that the parameter of the callback is
     * unused.
     *
     * @param <T> The type parameter of the callback
     * @param f A future
     * @param r A consumer
     * @return A callback
     */
    default <T> SingleResultCallback<T> emptyCallback(String context, CompletableFuture<?> f, ThrowingRunnable r) {
        return (t, thrown) -> {
            if (thrown != null) {
                IllegalStateException ex = new IllegalStateException(thrown.getMessage() + " in " + context, thrown);
                f.completeExceptionally(ex);
                return;
            }
            try {
                r.run();
            } catch (Exception e) {
                IllegalStateException ex = new IllegalStateException(e.getMessage() + " in " + context, e);
                f.completeExceptionally(ex);
            }
        };
    }

}
