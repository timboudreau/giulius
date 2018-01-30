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
package com.mastfrog.mongodb.init;

import com.mastfrog.giulius.Ordered;
import static com.mastfrog.giulius.mongodb.async.MongoAsyncConfig.SETTINGS_KEY_DATABASE_NAME;
import com.mastfrog.giulius.mongodb.async.MongoAsyncInitializer;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoDatabase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;

/**
 *
 * @author Tim Boudreau
 */
@Ordered(Integer.MIN_VALUE + 10)
class InitCollectionsInitializer extends MongoAsyncInitializer {

    private final CollectionsInfo info;
    private final String dbName;
    public static final String SETTINGS_KEY_DB_INIT_BLOCKING = "mongo.init.blocking";
    public static final boolean DEFAULT_BLOCKING = true;
    private final boolean blocking;

    static boolean LOG = false;

    @Inject
    InitCollectionsInitializer(Registry reg, CollectionsInfo info, @Named(SETTINGS_KEY_DATABASE_NAME) String dbName, Settings settings) {
        super(reg);
        this.info = info;
        this.dbName = dbName;
        blocking = settings.getBoolean(SETTINGS_KEY_DATABASE_NAME, DEFAULT_BLOCKING);
    }

    @Override
    public MongoClient onAfterCreateMongoClient(MongoClient client) {
        MongoDatabase db = client.getDatabase(dbName);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Consumer<Throwable> c = (th) -> {
            if (th != null) {
                Throwable t = thrown.get();
                if (t == null) {
                    thrown.set(th);
                } else {
                    t.addSuppressed(th);
                }
                th.printStackTrace();
            }
            latch.countDown();
            if (!blocking && thrown.get() != null) {
                thrown.get().printStackTrace();
            }
            if (LOG) {
                System.err.println("Finish onAfterCreateMongoClient");
            }
        };
        if (LOG) {
            System.err.println("init " + c);
        }
        info.init(db, c, (t, u) -> {
            super.createdCollection(dbName, u);
        });
        if (blocking) {
            try {
                if (LOG) {
                    System.err.println("Blocking " + Thread.currentThread().getName()
                            + " (" + Thread.currentThread().getId() + ") while database is "
                            + "initialized");
                }
                latch.await();
            } catch (InterruptedException ex) {
                if (thrown.get() != null) {
                    thrown.get().addSuppressed(ex);
                } else {
                    Exceptions.chuck(ex);
                }
            }
            if (thrown.get() != null) {
                Exceptions.chuck(thrown.get());
            }
        }
        return client;
    }
}
