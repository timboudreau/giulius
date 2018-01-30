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
package com.mastfrog.giulius.mongodb.async;

import com.mastfrog.util.Exceptions;
import com.mongodb.MongoCommandException;
import com.mongodb.async.SingleResultCallback;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
class MongoTypedCollectionProvider<T> implements Provider<MongoCollection<T>> {

    private final Provider<MongoDatabase> dbProvider;
    private final String collectionName;
    private final Class<T> collectionType;
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final Provider<KnownCollections> knownProvider;
    private final CreateCollectionOptions createOpts;
    private final Provider<MongoAsyncInitializer.Registry> inits;
    private final Provider<MongoClient> client;

    MongoTypedCollectionProvider(Provider<MongoDatabase> dbProvider, String collectionName, Class<T> collectionType, Provider<KnownCollections> knownProvider, CreateCollectionOptions createOpts, Provider<MongoAsyncInitializer.Registry> inits, Provider<MongoClient> client) {
        this.dbProvider = dbProvider;
        this.collectionName = collectionName;
        this.collectionType = collectionType;
        this.knownProvider = knownProvider;
        this.createOpts = createOpts;
        this.inits = inits;
        this.client = client;
    }

    @Override
    public MongoCollection<T> get() {
        // Ensure we initialize the client outside of a call to
        // KnownCollections, or we risk deadlock
        client.get();
        boolean created = false;
        if (initialized.compareAndSet(false, true)) {
            created = ensureCollectionExists();
        }
        MongoCollection<T> result = dbProvider.get().getCollection(collectionName, collectionType);
        if (created) {
            inits.get().onCreateCollection(collectionName, result);
        }
        return result;
    }

    private boolean ensureCollectionExists() {
        if (!knownProvider.get().exists(collectionName)) {
            final AtomicBoolean created = new AtomicBoolean();
            final Throwable[] thrown = new Throwable[1];
            final CountDownLatch latch = new CountDownLatch(1);
            dbProvider.get().createCollection(collectionName, createOpts, new SingleResultCallback<Void>() {

                private boolean isAlreadyExists(Throwable thbl) {
                    if (thbl instanceof MongoCommandException) {
                        MongoCommandException ex = (MongoCommandException) thbl;
                        return ex.getCode() == 48;
                    }
                    return false;
                }

                @Override
                public void onResult(Void t, Throwable thrwbl) {
                    boolean alreadyExisted = isAlreadyExists(thrwbl);
                    if (alreadyExisted) { // not a failure
                        thrwbl = null;
                    }
                    thrown[0] = thrwbl;
                    if (thrwbl == null) {
                        knownProvider.get().add(collectionName);
                        created.set(!alreadyExisted);
                    }
                    latch.countDown();
                }
            });
            try {
                latch.await();
                if (thrown[0] != null) {
                    Exceptions.chuck(thrown[0]);
                }
                return created.get();
            } catch (InterruptedException ex) {
                return Exceptions.chuck(ex);
            }
        }
        return false;
    }
}
