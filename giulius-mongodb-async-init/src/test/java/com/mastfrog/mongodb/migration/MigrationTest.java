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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule;
import com.mastfrog.giulius.mongodb.async.MongoHarness;
import com.mastfrog.giulius.mongodb.async.TestSupport;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.mongodb.init.MongoInitModule;
import com.mastfrog.mongodb.migration.MigrationTest.Ini;
import com.mastfrog.util.function.ThrowingTriFunction;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.model.UpdateOneModel;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bson.Document;
import org.bson.types.ObjectId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({MongoHarness.Module.class, Ini.class, JacksonModule.class})
public class MigrationTest {

    @Inject
    ObjectMapper mapper;

    @Test
    public void test(@Named("stuff") MongoCollection<Document> stuff, @Named("migrations") MongoCollection<Document> migrations, MongoDatabase db, MongoClient client) throws InterruptedException, Throwable {
        TestSupport.await(ts -> {
            stuff.find().forEach(d -> {
                assertFalse(d.toString(), d.containsKey("author"));
                assertTrue(d.toString(), d.containsKey("created"));
            }, ts.doneCallback());
        });
        TestSupport.await(ts -> {
            migrations.find().forEach(d -> {
                System.out.println("\nMIGRATION DOC:");
                System.out.println(d);
            }, ts.doneCallback());
        });
        Migration[] m = new Migration[1];
        new MigrationBuilder("stuff-new", 10, (mig) -> {
            m[0] = (Migration) mig;
            return null;
        }).backup("stuff", new Document("index", new Document("$lte", 50)))
                .migrateCollection("stuff", mig(false)).build();
        assertNotNull(m[0]);
        CompletableFuture<Document> cf = new CompletableFuture<>();
        CompletableFuture<Document> res = m[0].migrate(cf, client, db);
        Document[] ds = new Document[1];
        Throwable[] thr = new Throwable[1];
        CountDownLatch latch = new CountDownLatch(1);
        res.whenCompleteAsync((doc, thrown) -> {
            System.out.println("COMPLETE SECOND MIGRATION: " + doc);
            thr[0] = thrown;
            ds[0] = doc;
            latch.countDown();
        });
        cf.complete(new Document());
        latch.await(20, TimeUnit.SECONDS);
        if (thr[0] != null) {
            throw thr[0];
        }
        assertNotNull(ds[0]);
        assertEquals(ds[0].toString(), Boolean.TRUE, ds[0].getBoolean("alreadyRun"));
        TestSupport.await(new Consumer<TestSupport>() {
            @Override
            public void accept(TestSupport ts) {
                migrations.count(new Document(), (lng, thr) -> {
                    ts.run(() -> {
                        try {
                            assertEquals(1, lng.longValue());
                        } finally {
                            ts.done();
                        }
                    });
                });
            }
        });
    }

    @Test
    public void testRollback(@Named("stuff") MongoCollection<Document> stuff, @Named("migrations") MongoCollection<Document> migrations, MongoDatabase db, MongoClient client) throws InterruptedException, Throwable {
        Migration[] m = new Migration[1];
        new MigrationBuilder("stuff-new", 12, (mig) -> {
            m[0] = (Migration) mig;
            return null;
        }).backup("stuff", new Document("index", new Document("$lte", 150)))
                .migrateCollection("stuff", willFail()).build();
        assertNotNull(m[0]);
        CompletableFuture<Document> cf = new CompletableFuture<>();
        CompletableFuture<Document> res = m[0].migrate(cf, client, db);
        Document[] ds = new Document[1];
        Throwable[] thr = new Throwable[1];
        CountDownLatch latch = new CountDownLatch(1);
        res.whenCompleteAsync((doc, thrown) -> {
            thr[0] = thrown;
            ds[0] = doc;
            latch.countDown();
        });
        cf.complete(new Document());
        latch.await(20, TimeUnit.SECONDS);
        assertNotNull(thr[0]);
        assertTrue(thr[0] instanceof CompletionException);
        assertTrue(thr[0].getCause() instanceof FooException);
        assertEquals("Failed", thr[0].getCause().getMessage());
        assertNull(ds[0]);
        Thread.sleep(750);
        TestSupport.await(ts -> {
            stuff.find().forEach(d -> {
                assertFalse(d.toString(), d.containsKey("author"));
                assertTrue(d.toString(), d.containsKey("created"));
                assertFalse(d.toString(), d.containsKey("ix"));
                assertTrue(d.toString(), d.containsKey("index"));
            }, ts.doneCallback());
        });
    }

    static class Ini extends AbstractModule {

        static List<Document> toInsert = new ArrayList<>();

        static {
            for (int i = 0; i < 100; i++) {
                Document d = new Document("index", i)
                        .append("author", "author-" + i)
                        .append("created", new Date())
                        .append("hoo", "hah");
                toInsert.add(d);
            }
        }

        @Override
        protected void configure() {
            MongoInitModule m = new MongoInitModule();
            m.withCollections().add("stuff").insertDocumentsIfCreating(toInsert).build()
                    .add("migrations").build().build();
            m.addMigration("stuff-new", 10).backup("stuff", new Document("index", new Document("$lte", 50)))
                    .migrateCollection("stuff", mig(false)).build();
            install(m);

            install(new GiuliusMongoAsyncModule().bindCollection("stuff").bindCollection("migrations"));
        }

    }

    static ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void> mig(boolean fail) {
        return (CompletableFuture<Document> t, MongoDatabase u, MongoCollection<Document> v) -> {
            Document results = new Document();
            List<ObjectId> ids = new CopyOnWriteArrayList<>();
            results.append("ids", ids);
            results.append("start", new Date());
            List<UpdateOneModel<Document>> updates = new CopyOnWriteArrayList<>();
            v.find(new Document("author", new Document("$exists", true))).forEach((Document d) -> {
                ids.add(d.getObjectId("_id"));
                Document query = new Document("_id", d.getObjectId("_id"));
                Document upd = new Document("$set", new Document("created",
                        new Document("author", d.get("author")).append("created", d.get("created"))));
                upd.append("$unset", new Document("author", ""));
                updates.add(new UpdateOneModel<>(query, upd));
            }, (v1, thrown) -> {
                if (thrown != null) {
                    t.completeExceptionally(thrown);
                    return;
                }
                results.append("updateCount", updates.size());
                System.out.println("Applying " + updates.size() + " updates");
                if (!updates.isEmpty()) {
                    v.bulkWrite(updates, (v3, t3) -> {
                        if (t3 != null) {
                            t.completeExceptionally(t3);
                            return;
                        }
                        if (fail) {
                            t.completeExceptionally(new RuntimeException("Failed"));
                            return;
                        }
                        t.complete(results);
                    });
                } else {
                    t.complete(results);
                }
            });

            return null;
        };
    }

    static ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void> willFail() {
        return (CompletableFuture<Document> t, MongoDatabase u, MongoCollection<Document> v) -> {
            Document results = new Document();
            List<ObjectId> ids = new CopyOnWriteArrayList<>();
            results.append("ids", ids);
            results.append("start", new Date());
            List<UpdateOneModel<Document>> updates = new CopyOnWriteArrayList<>();
            v.find(new Document("index", new Document("$exists", true))).forEach((Document d) -> {
                ids.add(d.getObjectId("_id"));
                Document query = new Document("_id", d.getObjectId("_id"));
                Document upd = new Document("$set", new Document("ix", d.get("index")));
                upd.append("$unset", new Document("index", ""));
                updates.add(new UpdateOneModel<>(query, upd));
            }, (v1, thrown) -> {
                if (thrown != null) {
                    t.completeExceptionally(thrown);
                    return;
                }
                results.append("updateCount", updates.size());
                System.out.println("Applying " + updates.size() + " updates then will fail");
                if (!updates.isEmpty()) {
                    v.bulkWrite(updates, (v3, t3) -> {
                        if (t3 != null) {
                            t.completeExceptionally(t3);
                            return;
                        }
                        t.completeExceptionally(new FooException("Failed"));
                    });
                } else {
                    t.complete(results);
                }
            });

            return null;
        };
    }

    static class FooException extends Exception {

        public FooException(String message) {
            super(message);
        }

    }
}
