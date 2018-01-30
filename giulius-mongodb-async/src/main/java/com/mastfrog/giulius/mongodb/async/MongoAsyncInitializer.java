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

import com.mastfrog.giulius.Ordered;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoCollection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Interceptor which can perform actions on various events relating to mongodb
 * initialization and collection creation (e.g. create indexes on collection
 * creation).
 *
 * @author Tim Boudreau
 */
public abstract class MongoAsyncInitializer {

    private final Registry reg;
    /**
     * Create a new MongoAsyncInitializer. Just ask for the Registry to be
     * injected, and pass it to the super constructor.
     *
     * @param reg The registry
     */
    @SuppressWarnings("LeakingThisInConstructor")
    protected MongoAsyncInitializer(Registry reg) {
        this.reg = reg;
        reg.register(this);
    }

    /**
     * Called when a collection didnot yet exist in the database and has just
     * been created.
     *
     * @param name The name of the collection
     * @param collection The collection
     */
    public void onCreateCollection(String name, MongoCollection<?> collection) {
        // do nothing
    }

    /**
     * Called before the MongoClient is created, and can modify the settings
     * being used. Used by MongoHarness to start a temporary MongoDB instance
     * before running a test.
     *
     * @param settings Client settings
     * @return the settings
     */
    public MongoClientSettings onBeforeCreateMongoClient(MongoClientSettings settings) {
        return settings;
    }

    /**
     * Called once the MongoClient has been created.
     *
     * @param client The client
     * @return the client
     */
    public MongoClient onAfterCreateMongoClient(MongoClient client) {
        return client;
    }

    /**
     * Initializers that create collections should call this method to allow
     * other initializers to initialize the collection.
     *
     * @param name The collection name
     * @param collection The collection
     */
    protected final void createdCollection(String name, MongoCollection<?> collection) {
        reg.onCreateCollection(name, collection);
    }

    /**
     * Registry of mongo initializers - has no user-callable methods.  Just
     * pass this to the super constructor of your MongoAsyncInitializer and
     * the right thing will happen.
     */
    @Singleton
    public static class Registry {

        private final List<MongoAsyncInitializer> initializers = Collections.synchronizedList(new LinkedList<MongoAsyncInitializer>());
        @Inject
        Registry() {
        }

        void register(MongoAsyncInitializer init) {
            initializers.add(init);
            Collections.sort(initializers, new Ordered.OrderedObjectComparator());
        }

        void onCreateCollection(String name, MongoCollection<?> collection) {
            for (MongoAsyncInitializer init : initializers) {
                init.onCreateCollection(name, collection);
            }
        }

        MongoClientSettings onBeforeCreateMongoClient(MongoClientSettings settings) {
            for (MongoAsyncInitializer init : initializers) {
                settings = init.onBeforeCreateMongoClient(settings);
            }
            return settings;
        }

        MongoClient onAfterCreateMongoClient(MongoClient client) {
            for (MongoAsyncInitializer init : initializers) {
                client = init.onAfterCreateMongoClient(client);
            }
            return client;
        }
    }
}
