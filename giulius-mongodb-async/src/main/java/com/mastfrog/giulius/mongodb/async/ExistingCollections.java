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
package com.mastfrog.giulius.mongodb.async;

import com.google.common.collect.Maps;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mongodb.MongoCommandException;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import org.bson.Document;

/**
 *
 * @author Tim Boudreau
 */
public class ExistingCollections {

    private final Map<String, Supplier<MongoCollection<Document>>> collections
            = Maps.newConcurrentMap();
    private final Provider<String> dbName;
    private final Provider<MongoAsyncInitializer.Registry> reg;
    private Provider<MongoDatabase> dbProvider;
    public static final String SETTINGS_KEY_MAX_WAIT_SECONDS = "mongo.list.collections.max.wait.seconds";
    private final Provider<Settings> settings;

    @Inject
    ExistingCollections(@Named(GiuliusMongoAsyncModule.SETTINGS_KEY_DATABASE_NAME) Provider<String> dbName, Provider<MongoAsyncInitializer.Registry> reg, Provider<Settings> settings) {
        this.dbName = dbName;
        this.reg = reg;
        this.settings = settings;
    }

    MongoCollection<Document> get(String name) {
        Supplier<MongoCollection<Document>> result = collections.get(name);
        if (result == null) {
            synchronized (this) {
                result = collections.get(name);
                if (result == null) {
                    addBound(name, new CreateCollectionOptions());
                    result = collections.get(name);
                }
            }
        }
        return result.get();
    }

    void init(MongoClient client, Provider<MongoClient> clientProvider) {
        CountDownLatch latch = new CountDownLatch(1);
        dbProvider = new MongoDatabaseProvider(clientProvider, dbName.get());
        MongoDatabase db = dbProvider.get();
        Throwable[] th = new Throwable[1];
        db.listCollectionNames().batchSize(1000).forEach((name) -> {
            addExisting(name);
        }, (v, thrown) -> {
            if (thrown != null) {
                th[0] = thrown;
                return;
            }
            latch.countDown();
        });
        try {
            latch.await(settings.get().getInt(SETTINGS_KEY_MAX_WAIT_SECONDS, 60), SECONDS);
        } catch (InterruptedException ex) {
            Exceptions.chuck(ex);
        }
    }

    boolean addExisting(String s) {
        boolean result = collections.get(s) == null || collections.get(s) instanceof CreatingCollectionSupplier;
        collections.put(s, new CollectionSupplier(s));
        return result;
    }

    void addBound(String s, CreateCollectionOptions opts) {
        collections.put(s, new CreatingCollectionSupplier(s, opts));
    }

    class CreatingCollectionSupplier implements Supplier<MongoCollection<Document>> {

        private final String name;
        private final CreateCollectionOptions opts;

        public CreatingCollectionSupplier(String name, CreateCollectionOptions opts) {
            this.name = name;
            this.opts = opts;
        }

        @Override
        public synchronized MongoCollection<Document> get() {
            if (collections.get(name) instanceof CreatingCollectionSupplier) {
                CountDownLatch latch = new CountDownLatch(1);
                Throwable[] th = new Throwable[1];
                dbProvider.get().createCollection(name, opts, (v, thrown) -> {
                    boolean created = true;
                    try {
                        if (thrown != null) {
                            if (!isAlreadyExists(thrown)) {
                                th[0] = thrown;
                                return;
                            } else {
                                created = false;
                            }
                        }
                        CollectionSupplier result = new CollectionSupplier(name);
                        if (created) {
                            reg.get().onCreateCollection(name, result.get());
                        }
                        collections.put(name, result);
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    latch.await();
                } catch (InterruptedException ex) {
                    return Exceptions.chuck(ex);
                }
                if (th[0] != null) {
                    return Exceptions.chuck(th[0]);
                }
            }
            CollectionSupplier result = new CollectionSupplier(name);
            collections.put(name, result);
            return result.get();
        }
    }

    class CollectionSupplier implements Supplier<MongoCollection<Document>> {

        private final String name;

        CollectionSupplier(String name) {
            this.name = name;
        }

        @Override
        public MongoCollection<Document> get() {
            return dbProvider.get().getCollection(name);
        }
    }

    private boolean isAlreadyExists(Throwable thbl) {
        if (thbl instanceof MongoCommandException) {
            MongoCommandException ex = (MongoCommandException) thbl;
            return ex.getCode() == 48;
        }
        return false;
    }

}
