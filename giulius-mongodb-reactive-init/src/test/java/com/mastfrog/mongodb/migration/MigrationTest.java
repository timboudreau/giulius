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
import com.google.inject.Provider;
import com.google.inject.name.Named;
import com.google.inject.util.Providers;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.mongodb.reactive.GiuliusMongoReactiveStreamsModule;
import com.mastfrog.giulius.mongodb.reactive.MongoHarness;
import com.mastfrog.giulius.mongodb.reactive.TestSupport;
import com.mastfrog.giulius.mongodb.reactive.util.Subscribers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mastfrog.mongodb.init.MongoInitModule;
import com.mastfrog.mongodb.migration.MigrationTest.Ini;
import com.mastfrog.settings.Settings;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
@IfBinaryAvailable("mongod")
public class MigrationTest {
    
    private static final long TIMEOUT=40000;

    @Inject
    ObjectMapper mapper;

    @Test
    public void testStuff() {
        System.out.println("Test stuff");
    }

    @Test(timeout=TIMEOUT)
    public void test(@Named("stuff") Provider<MongoCollection<Document>> stuff,
            Subscribers subscribers,
            @Named("migrations") Provider<MongoCollection<Document>> migrations,
            Provider<MongoDatabase> db,
            Provider<MongoClient> client,
            Provider<Dependencies> deps) throws InterruptedException, Throwable {
        Function<Class<? extends MigrationWorker>, MigrationWorker> convert = deps.get()::getInstance;
        TestSupport.awaitThrowing(ts -> {
            subscribers.multiple(stuff.get().find())
                    .whenComplete((res, fail) -> {
                        if (fail != null) {
                            ts.apply(fail);
                        }
                        ts.done();
                    })
                    .get()
                    .forEach(d -> {
                        try {
                            assertFalse(d.toString(), d.containsKey("author"));
                            assertTrue(d.toString(), d.containsKey("created"));
                        } finally {
                            ts.done();
                        }
                    });
        });
        TestSupport.awaitThrowing(ts -> {
            subscribers.multiple(migrations.get().find())
                    .whenComplete((res, fail) -> {
                        if (fail != null) {
                            ts.apply(fail);
                        }
                        ts.done();
                    })
                    .get().forEach(d -> {
                        System.out.println("\nMIGRATION DOC:");
                        System.out.println(d);
                    });
        });
        Migration[] m = new Migration[1];
        new MigrationBuilder<>("stuff-new", 10, (mig) -> {
            m[0] = (Migration) mig;
            return null;
        }).backup("stuff", new Document("index", new Document("$lte", 50)))
                .migrateCollection("stuff", mig(false,
                        Providers.of(subscribers))).build();
        assertNotNull(m[0]);
        CompletableFuture<Document> cf = new CompletableFuture<>();
        CompletableFuture<Document> res = m[0].migrate(cf, client.get(), db.get(), convert);
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
        TestSupport.awaitThrowing((TestSupport ts) -> {
            Long val = subscribers.single(migrations.get().countDocuments())
                    .get();
            ts.run(() -> {
                try {
                    assertNotNull(val);
                    assertEquals(1, val.longValue());
                } finally {
                    ts.done();
                }
            });
        });
    }

    @Test(timeout=TIMEOUT)
    public void testRollback(@Named("stuff") Provider<MongoCollection<Document>> stuff,
            Subscribers subscribers, @Named("migrations") Provider<MongoCollection<Document>> migrations, Provider<MongoDatabase> db, Provider<MongoClient> client, Provider<Dependencies> deps) throws InterruptedException, Throwable {
        System.out.println("ROLLBACK");
        Function<Class<? extends MigrationWorker>, MigrationWorker> convert = deps.get()::getInstance;

        Migration[] m = new Migration[1];
        new MigrationBuilder<>("stuff-new", 12, (mig) -> {
            m[0] = (Migration) mig;
            return null;
        }).backup("stuff", new Document("index", new Document("$lte", 150)))
                .migrateCollection("stuff", Failer.class).build();
        assertNotNull(m[0]);
        CompletableFuture<Document> cf = new CompletableFuture<>();
        CompletableFuture<Document> res = m[0].migrate(cf, client.get(), db.get(), convert);
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
        if (!(thr[0].getCause() instanceof FooException)) {
            throw thr[0];
        }
        assertEquals("Failed", thr[0].getCause().getMessage());
        assertNull(ds[0]);
        Thread.sleep(750);
        List<Document> found = subscribers.multiple(stuff.get().find())
                .get();
        for (Document d : found) {
            assertFalse(d.toString(), d.containsKey("author"));
            assertTrue(d.toString(), d.containsKey("created"));
            assertFalse(d.toString(), d.containsKey("ix"));
            assertTrue(d.toString(), d.containsKey("index"));
        }
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
            System.out.println("Configure ini");
            MongoInitModule m = new MongoInitModule();
            m.withCollections().add("stuff").insertDocumentsIfCreating(toInsert).buildCollection()
                    .add("migrations").buildCollection().build();
            Provider<Dependencies> deps = binder().getProvider(Dependencies.class);
            Function<Class<? extends MigrationWorker>, MigrationWorker> convert = (c) -> {
                return deps.get().getInstance(c);
            };
            m.addMigration("stuff-new", 10).backup("stuff", new Document("index", new Document("$lte", 50)))
                    .migrateCollection("stuff", mig(false, binder().getProvider(Subscribers.class))).build();
            install(m);

            install(new GiuliusMongoReactiveStreamsModule().bindCollection("stuff").bindCollection("migrations"));
        }

    }

    static MigrationWorker mig(boolean fail, Provider<Subscribers> subscribers) {
        return (CompletableFuture<Document> t, MongoDatabase u, MongoCollection<Document> v, Function<Class<? extends MigrationWorker>, MigrationWorker> f) -> {
            Document results = new Document();
            List<ObjectId> ids = new CopyOnWriteArrayList<>();
            results.append("ids", ids);
            results.append("start", new Date());
            List<UpdateOneModel<Document>> updates = new CopyOnWriteArrayList<>();

            subscribers.get().forEach(v.find(new Document("author", new Document("$exists", true))),
                    (d, thrown) -> {
                        if (d != null) {
                            ids.add(d.getObjectId("_id"));
                            Document query = new Document("_id", d.getObjectId("_id"));
                            Document upd = new Document("$set", new Document("created",
                                    new Document("author", d.get("author")).append("created", d.get("created"))));
                            upd.append("$unset", new Document("author", ""));
                            updates.add(new UpdateOneModel<>(query, upd));
                        } else if (thrown != null) {
                            t.completeExceptionally(thrown);
                        }
                    }
            ).whenComplete((vv, thrown) -> {
                if (thrown != null) {
                    t.completeExceptionally(thrown);
                    return;
                }
                results.append("updateCount", updates.size());
                System.out.println("Applying " + updates.size() + " updates");
                if (!updates.isEmpty()) {
                    v.bulkWrite(updates)
                            .subscribe(subscribers.get().callback((v3, t3) -> {
                                if (t3 != null) {
                                    System.out.println("exceptional complete");
                                    t.completeExceptionally(t3);
                                    return;
                                }
                                if (fail) {
                                    t.completeExceptionally(new RuntimeException("Failed"));
                                    return;
                                }
                                System.out.println("complete it");
                                t.complete(results);
                            }));
                } else {
                    t.complete(results);
                }
            });
        };
    }

    static class Failer implements MigrationWorker {

        private final Subscribers subscribers;

        @Inject
        Failer(Settings settings, Subscribers subscribers) {
            assertNotNull(settings);
            assertNotNull(subscribers);
            this.subscribers = subscribers;
        }

        @Override
        public void accept(CompletableFuture<Document> t, MongoDatabase u, MongoCollection<Document> v, Function<Class<? extends MigrationWorker>, MigrationWorker> f) throws Exception {
            Document results = new Document();
            List<ObjectId> ids = new CopyOnWriteArrayList<>();
            results.append("ids", ids);
            results.append("start", new Date());
            List<UpdateOneModel<Document>> updates = new CopyOnWriteArrayList<>();

            try {
                subscribers.multiple(v.find(new Document("index", new Document("$exists", true))))
                        .get()
                        .forEach(d -> {
                            ids.add(d.getObjectId("_id"));
                            Document query = new Document("_id", d.getObjectId("_id"));
                            Document upd = new Document("$set", new Document("ix", d.get("index")));
                            upd.append("$unset", new Document("index", ""));
                            updates.add(new UpdateOneModel<>(query, upd));
                        });
            } catch (Exception | Error e) {
                t.completeExceptionally(e);
            }
            results.append("updateCount", updates.size());
            System.out.println("Applying " + updates.size() + " updates then will fail");
            if (!updates.isEmpty()) {
                subscribers.blockingCallback(v.bulkWrite(updates), (v3, t3) -> {
                    if (t3 != null) {
                        t.completeExceptionally(t3);
                        return;
                    }
                    t.completeExceptionally(new FooException("Failed"));
                });

                v.bulkWrite(updates).subscribe(subscribers.callback((v3, t3) -> {
                    if (t3 != null) {
                        t.completeExceptionally(t3);
                        return;
                    }
                    t.completeExceptionally(new FooException("Failed"));
                }));
            } else {
                t.complete(results);
            }

        }

    }

    static class FooException extends Exception {

        public FooException(String message) {
            super(message);
        }

    }
}
