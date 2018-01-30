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

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
class MongoTypedCollectionProvider<T> implements Provider<MongoCollection<T>> {

    private final String collectionName;
    private final Class<T> collectionType;
    private final Provider<ExistingCollections> knownProvider;
    private final Provider<MongoClient> client;
    private final AtomicBoolean initialized = new AtomicBoolean();

    MongoTypedCollectionProvider(String collectionName, Class<T> collectionType, Provider<ExistingCollections> knownProvider, Provider<MongoClient> client) {
        this.collectionName = collectionName;
        this.collectionType = collectionType;
        this.knownProvider = knownProvider;
        this.client = client;
    }

    @Override
    public MongoCollection<T> get() {
        // Ensure we initialize the client outside of a call to
        // KnownCollections, or we risk deadlock
        if (initialized.compareAndSet(false, true)) {
            client.get();
        }
        MongoCollection<T> result = knownProvider.get().get(collectionName).withDocumentClass(collectionType);
        return result;
    }
}
