/*
 * The MIT License
 *
 * Copyright 2018 tim.
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
import com.mastfrog.mongodb.migration.Migration;
import com.mastfrog.util.Exceptions;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoDatabase;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import javax.inject.Inject;
import javax.inject.Named;
import org.bson.Document;

/**
 *
 * @author Tim Boudreau
 */
@Ordered(Integer.MAX_VALUE - 10)
public class InitMigrationsInitializer extends MongoAsyncInitializer {

    private final Set<Migration> migrations;

    private final String dbName;

    @Inject
    public InitMigrationsInitializer(Registry reg, Set<Migration> migrations, @Named(SETTINGS_KEY_DATABASE_NAME) String dbName) {
        super(reg);
        this.migrations = migrations;
        this.dbName = dbName;
    }

    @Override
    public MongoClient onAfterCreateMongoClient(MongoClient client) {

        if (migrations.isEmpty()) {
            return client;
        }

        MongoDatabase db = client.getDatabase(dbName);
        CountDownLatch latch = new CountDownLatch(1);

        Iterator<Migration> iter = migrations.iterator();
        CompletableFuture<Document> first = new CompletableFuture<>();

        CompletableFuture<Document> last = first;
        for (Migration m : migrations) {
            last = m.migrate(last, client, db);
        }

        Throwable[] t = new Throwable[1];
        last.whenComplete((doc, thrown) -> {
            t[0] = thrown;
            latch.countDown();
        });
        try {
            first.complete(new Document());
            latch.await();
        } catch (InterruptedException ex) {
            if (t[0] != null) {
                t[0].addSuppressed(ex);
            } else {
                t[0] = ex;
            }
        }
        if (t[0] != null) {
            return Exceptions.chuck(t[0]);
        }
        return client;
    }

}
