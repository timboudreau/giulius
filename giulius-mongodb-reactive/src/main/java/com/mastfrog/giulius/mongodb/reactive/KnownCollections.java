/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.giulius.mongodb.reactive;

import com.mastfrog.settings.Settings;
import com.mastfrog.util.preconditions.Exceptions;
import com.mongodb.internal.async.AsyncBatchCursor;
import com.mongodb.reactivestreams.client.MongoDatabase;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
class KnownCollections implements Iterable<String> {

    private Set<String> knownCollections;
    private final Provider<MongoDatabase> dbProvider;
    public static final String SETTINGS_KEY_MAX_WAIT_SECONDS = "mongo.list.collections.max.wait.seconds";
    private final long maxWaitSeconds;

    // PENDING: AsyncMongoClientProvider should create the set maintained
    // here, and let initializers augment it, so there is no need for the
    // blocking / locking done here
    @Inject
    KnownCollections(Provider<MongoDatabase> dbProvider, Settings settings) {
        this.dbProvider = dbProvider;
        maxWaitSeconds = settings.getInt(SETTINGS_KEY_MAX_WAIT_SECONDS, 30);
    }

    synchronized void add(String created) {
        knownCollections().add(created);
    }

    public boolean exists(String collection) {
        return knownCollections().contains(collection);
    }

    private synchronized Set<String> knownCollections() {
        if (knownCollections == null) {
            MongoDatabase db = dbProvider.get();
            Publisher<String> iter = db.listCollectionNames();
            CollectionListReceiver recv = new CollectionListReceiver(maxWaitSeconds);
            iter.subscribe(recv);
            knownCollections = recv.await();
        }
        return knownCollections;
    }

    @Override
    public Iterator<String> iterator() {
        return knownCollections().iterator();
    }

    static class CollectionListReceiver implements Subscriber<String> {

        private final Set<String> allCollections = Collections.synchronizedSet(new HashSet<>(36));
        private volatile Throwable toThrow;
        private volatile AsyncBatchCursor<String> cursor;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final long maxWaitSeconds;

        private CollectionListReceiver(long maxWaitSeconds) {
            this.maxWaitSeconds = maxWaitSeconds;
        }

        @Override
        public void onSubscribe(Subscription s) {
            // do nothing
        }

        @Override
        public void onNext(String t) {
            allCollections.add(t);
        }

        @Override
        public void onError(Throwable thrwbl) {
            if (thrwbl != null && toThrow == null) {
                toThrow = thrwbl;
            }
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }

        Set<String> await() {
            try {
                latch.await(maxWaitSeconds, TimeUnit.SECONDS);
                if (toThrow != null) {
                    return Exceptions.chuck(toThrow);
                }
                return allCollections;
            } catch (InterruptedException ex) {
                return Exceptions.chuck(ex);
            }
        }
    }
}
