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
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bson.Document;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

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
    void accept(CompletableFuture<Document> a, MongoDatabase b, MongoCollection<Document> s, Function<Class<? extends MigrationWorker>, MigrationWorker> u) throws Exception;

    /**
     * Convenience method for handling any thrown exceptions via the
     * CompletableFuture and only needing to code the success handler.
     *
     * @param <T> The type of the callback
     * @param f A future
     * @param c A consumer
     * @return A callback
     */
    default <T> Subscriber<T> callback(CompletableFuture<?> f, ThrowingConsumer<T> c) {
        return new Subscriber<T>() {
            T obj;

            @Override
            public void onSubscribe(Subscription s) {
                if (f.isCancelled()) {
                    s.cancel();
                } else {
                    s.request(Long.MAX_VALUE);
                }
            }
            
            @Override
            public void onNext(T t) {
                obj = t;
                try {
                    c.accept(t);
                } catch (Exception ex) {
                    f.completeExceptionally(ex);
                }
            }
            
            @Override
            public void onError(Throwable thrwbl) {
                f.completeExceptionally(thrwbl);
            }
            
            @Override
            public void onComplete() {
                f.complete(null);
            }
        };
    }
}
