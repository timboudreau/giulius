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

import com.google.common.collect.Sets;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.function.NamedCompletableFuture;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.mastfrog.giulius.mongodb.reactive.util.Subscribers;
import com.mastfrog.util.collections.AtomicLinkedQueue;
import com.mastfrog.util.multivariate.OneOf;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.bson.Document;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class Migration {

    private final String name;
    private final int newVersion;
    private final Map<String, OneOf<MigrationWorker, Class<? extends MigrationWorker>>> migrations;
    private final Map<String, Document> backupQueryForCollection;
    private static final boolean LOG = true; // Boolean.getBoolean("migration.log");
    // We are running at application startup, before the server is live, so
    // there is no need to use the injectable Subscribers that will reconstitute
    // the request scope for what we're doing here - it will always be empty.
    private final Subscribers subscribers = Subscribers.create();

    public Migration(String name, int newVersion, Map<String, OneOf<MigrationWorker, Class<? extends MigrationWorker>>> migrations, Map<String, Document> backupQueryForCollection) {
        this.name = name;
        this.newVersion = newVersion;
        this.migrations = new LinkedHashMap<>(migrations);
        this.backupQueryForCollection = new LinkedHashMap<>(backupQueryForCollection);
        for (Map.Entry<String, OneOf<MigrationWorker, Class<? extends MigrationWorker>>> e : migrations.entrySet()) {
            if (e.getValue() == null) {
                throw new IllegalArgumentException("Null value for " + e.getKey() + " in " + migrations);
            }
            if (!e.getValue().isSet()) {
                throw new IllegalArgumentException("Value not set for " + e.getKey() + ": " + e.getValue());
            }
        }
    }

    public boolean isEmpty() {
        return this.migrations.isEmpty();
    }

    public static <T> CompletableFuture<T> future(String name) {
        return NamedCompletableFuture.<T>loggingFuture(name, LOG);
    }

    private static void log(String what) {
        if (LOG) {
            System.out.println(what);
        }
    }

    public CompletableFuture<Document> migrate(CompletableFuture<Document> f, MongoClient client, MongoDatabase db, Function<Class<? extends MigrationWorker>, MigrationWorker> converter) {
        notNull("converter", converter);
        notNull("f", f);
        notNull("client", client);
        notNull("db", db);
        log("MIGRATE " + name);
        // Pending: Could parallelize these by collection
        return f.thenComposeAsync((dc) -> {
            CompletableFuture<Document> result = future("migration-initial-" + name);
            CompletableFuture<Document> resFuture = result;
            MongoCollection<Document> migrationsCollection = db.getCollection("migrations");
            AtomicBoolean completed = new AtomicBoolean();
            Document agg = new Document("migration", name).append("version", newVersion)
                    .append("start", new Date());
            migrationsCollection.find(new Document("migration", name).append("version", newVersion)
                    .append("success", true)).first().subscribe(subscribers.callback((found, thrown) -> {
                if (thrown != null) {
                    resFuture.completeExceptionally(thrown);
                    return;
                }
                if (found != null) {
                    found.append("alreadyRun", true);
                    completed.set(true);
                    resFuture.complete(found);
                } else {
                    resFuture.complete(new Document());
                }
            }));

            AtomicInteger counter = new AtomicInteger();
            for (Map.Entry<String, Document> e : backupQueryForCollection.entrySet()) {
                ThrowingTriConsumer<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>> bu = backup(e.getKey(), e.getValue());
                result = result.thenCompose((d) -> {
                    CompletableFuture<Document> res = future("post-backup-" + name);
                    if (completed.get()) {
                        res.complete(d);
                        return res;
                    }
                    if (d != null) {
                        agg.put(e.getKey() + "_backup_" + counter.getAndIncrement(), d);
                    }
                    try {
                        bu.accept(res, db, db.getCollection(e.getKey()));
                    } catch (Exception ex) {
                        res.completeExceptionally(ex);
                    }
                    return res;
                });
            }
            Iterator<Map.Entry<String, OneOf<MigrationWorker, Class<? extends MigrationWorker>>>> it;
            for (it = migrations.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, OneOf<MigrationWorker, Class<? extends MigrationWorker>>> e = it.next();
                result = result.thenCompose((Document d) -> {
                    CompletableFuture<Document> res = future("run-migration-" + name + "-" + e.getKey());
                    if (completed.get()) {
                        res.complete(d);
                        return res;
                    }
                    if (!d.isEmpty()) {
                        agg.put(e.getKey() + "_migrate_" + counter.getAndIncrement(), d);
                    }
                    try {
                        OneOf<MigrationWorker, Class<? extends MigrationWorker>> mig = e.getValue();
                        notNull("Null return from " + e, mig);
                        MigrationWorker curr = mig.get(converter);
                        curr.accept(res, db, db.getCollection(e.getKey()), converter);
                    } catch (Exception ex) {
                        res.completeExceptionally(ex);
                    }
                    return res;
                });
                if (!it.hasNext()) {
                    CompletableFuture<Document> end = future("finish-migration-" + name + "-" + e.getKey());
                    result = result.thenCompose((doc) -> {
                        if (completed.get()) {
                            end.complete(doc);
                            return end;
                        }
                        if (!doc.isEmpty()) {
                            agg.put(e.getKey() + "_migrate_" + counter.getAndIncrement(), doc);
                        }
                        end.complete(agg);
                        return end;
                    });
                }
            }
            CompletableFuture<Document> endResult = future("finish-all-" + name);
            result.whenComplete((doc, thrown) -> {
                if (completed.get()) {
                    endResult.complete(doc);
                    return;
                }
                agg.append("success", thrown == null);
                agg.append("end", new Date());
                if (thrown != null) {
                    agg.append("thrown", appendThrowable(thrown));
                    rollback(db, agg);
                }
                migrationsCollection.insertOne(agg).subscribe(subscribers.callback((x, thrown2) -> {
                    if (thrown != null && thrown2 != null) {
                        thrown.addSuppressed(thrown2);
                        thrown2 = thrown;
                    } else if (thrown != null) {
                        thrown2 = thrown;
                    }
                    if (thrown2 != null) {
                        endResult.completeExceptionally(thrown2);
                    } else {
                        endResult.complete(agg);
                    }
                }));
            });
            return endResult;
        });
    }

    private Document appendThrowable(Throwable thrown) {
        Document throwRecord = new Document();
        throwRecord.append("thrown", thrown.getClass().getName());
        throwRecord.append("message", thrown.getMessage());
        ByteArrayOutputStream in = new ByteArrayOutputStream();
        thrown.printStackTrace(new PrintStream(in));
        try {
            throwRecord.append("stack", new String(in.toByteArray(), "UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            Exceptions.chuck(ex);
        }
        return throwRecord;
    }

    private void rollback(MongoDatabase db, Document agg) {
        CountDownLatch latch = new CountDownLatch(backupQueryForCollection.size() - 1);
        Document rollbacks = new Document();
        agg.append("rollback", rollbacks);

        AtomicLinkedQueue<Document> pending = new AtomicLinkedQueue<>();

        for (String s : backupQueryForCollection.keySet()) {
            MongoCollection<Document> from = db.getCollection(backupCollectionName(s));
            MongoCollection<Document> to = db.getCollection(s);
            Document thisCollection = new Document();
            rollbacks.append(s, thisCollection);
            AtomicInteger batchCount = new AtomicInteger();
            Runnable doBatch = () -> {
                List<Document> all = pending.drain();
                List<ReplaceOneModel<Document>> replacements = new ArrayList<>();
                int ct = batchCount.incrementAndGet();
                thisCollection.append("batch-" + ct, all.size());
                for (Document d : all) {
                    replacements.add(new ReplaceOneModel<>(new Document("_id", d.getObjectId("_id")), d));
                }
                to.bulkWrite(replacements).subscribe(subscribers.callback((bwr, th2) -> {
                    if (th2 != null) {
                        thisCollection.append("batch-" + ct + "-failed", true);
                        thisCollection.append("batch-" + ct + "-succeeded", appendThrowable(th2));
                    } else {
                        thisCollection.append("batch-" + ct + "-succeeded", all.size());
                    }
                }));
            };

            subscribers.forEach(from.find().batchSize(50).allowDiskUse(true), (item, thrown) -> {
                if (thrown != null) {
                    latch.countDown();
                    return;
                }
                if (item != null) {
                    pending.add(item);
                    if (pending.size() >= 50) {
                        doBatch.run();
                    }
                }
            }).whenComplete((ignored, ignored2) -> latch.countDown());
        }
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Exceptions.chuck(ex);
        }
    }

    private String backupCollectionName(String collectionName) {
        return collectionName + "_migrated_to_v_" + newVersion;
    }

    private ThrowingTriConsumer<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>> backup(String collectionName, Document queryDoc) {
        return (CompletableFuture<Document> t, MongoDatabase u, MongoCollection<Document> origs) -> {
            String backupCollectionName = backupCollectionName(collectionName);
            MongoCollection<Document> backups = u.getCollection(backupCollectionName);
            Set<ObjectId> allIds = Sets.newConcurrentHashSet();
            Document backupInfo = new Document("collection", collectionName)
                    .append("backedUpTo", backupCollectionName)
                    .append("name", name)
                    .append("toVersion", newVersion)
                    .append("when", new Date())
                    .append("ids", allIds);

            AtomicInteger numSeen = new AtomicInteger();
            AtomicInteger numBackedUp = new AtomicInteger();

            AtomicLinkedQueue<Document> pending = new AtomicLinkedQueue<>();
            Runnable oneBatch = () -> {
                List<Document> drained = pending.drain();
                if (!drained.isEmpty()) {
                    backups.insertMany(drained).subscribe(subscribers.callback((v2, thrown3) -> {
                        if (thrown3 != null) {
                            t.completeExceptionally(thrown3);
                            return;
                        }
                        for (Document d : drained) {
                            allIds.add(d.getObjectId("_id"));
                        }
                        int total = numBackedUp.addAndGet(drained.size());
                        backupInfo.put("docsBackedUp", total);
                        if (total == numSeen.get()) {
                            t.complete(backupInfo);
                        }
                    }));
                }
            };

            subscribers.forEach(origs.find(queryDoc).batchSize(50), (doc, thrown) -> {
                if (thrown != null) {
                    t.completeExceptionally(thrown);
                    return;
                }
                numSeen.incrementAndGet();
                pending.add(doc);

                if (pending.size() >= 50) {
                    oneBatch.run();
                }

            }).whenComplete((x, thr) -> {
                if (thr != null) {
                    t.completeExceptionally(thr);
                }
                oneBatch.run();
            });
        };
    }
}
