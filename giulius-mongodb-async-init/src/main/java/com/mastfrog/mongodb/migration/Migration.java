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
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.function.ThrowingTriFunction;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOneModel;
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
import org.bson.Document;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class Migration {

    private final String name;
    private final int newVersion;
    private final Map<String, ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void>> migrations;
    private final Map<String, Document> backupQueryForCollection;

    public Migration(String name, int newVersion, Map<String, ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void>> migrations, Map<String, Document> backupQueryForCollection) {
        this.name = name;
        this.newVersion = newVersion;
        this.migrations = new LinkedHashMap<>(migrations);
        this.backupQueryForCollection = new LinkedHashMap<>(backupQueryForCollection);
    }

    public boolean isEmpty() {
        return this.migrations.isEmpty();
    }

    public CompletableFuture<Document> migrate(CompletableFuture<Document> f, MongoClient client, MongoDatabase db) {
        return f.thenComposeAsync((dc) -> {
            CompletableFuture<Document> result = new CompletableFuture<>();
            CompletableFuture<Document> resFuture = result;
            MongoCollection<Document> migrationsCollection = db.getCollection("migrations");
            AtomicBoolean completed = new AtomicBoolean();
            Document agg = new Document("migration", name).append("version", newVersion)
                    .append("start", new Date());
            migrationsCollection.find(new Document("migration", name).append("version", newVersion)
                    .append("success", true)).first((found, thrown) -> {
                if (thrown != null) {
                    resFuture.completeExceptionally(thrown);
                    return;
                }
                if (found != null) {
                    found.append("alreadyRun", true);
                    completed.set(true);
                    resFuture.complete(found);
                    return;
                } else {
                    resFuture.complete(new Document());
                }
            });

            AtomicInteger counter = new AtomicInteger();
            for (Map.Entry<String, Document> e : backupQueryForCollection.entrySet()) {
                ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void> bu = backup(e.getKey(), e.getValue());
                result = result.thenCompose((d) -> {
                    CompletableFuture<Document> res = new CompletableFuture<>();
                    if (completed.get()) {
                        res.complete(d);
                        return res;
                    }
                    if (d != null) {
                        agg.put(e.getKey() + "_backup_" + counter.getAndIncrement(), d);
                    }
                    try {
                        bu.apply(res, db, db.getCollection(e.getKey()));
                    } catch (Exception ex) {
                        res.completeExceptionally(ex);
                    }
                    return res;
                });
            }
            for (Iterator<Map.Entry<String, ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void>>> it = migrations.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void>> e = it.next();
                result = result.thenCompose((d) -> {
                    CompletableFuture<Document> res = new CompletableFuture<>();
                    if (completed.get()) {
                        res.complete(d);
                        return res;
                    }
                    if (!d.isEmpty()) {
                        agg.put(e.getKey() + "_migrate_" + counter.getAndIncrement(), d);
                    }
                    try {
                        e.getValue().apply(res, db, db.getCollection(e.getKey()));
                    } catch (Exception ex) {
                        res.completeExceptionally(ex);
                    }
                    return res;
                });
                if (!it.hasNext()) {
                    CompletableFuture<Document> end = new CompletableFuture<>();
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
            CompletableFuture<Document> endResult = new CompletableFuture<>();
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
                migrationsCollection.insertOne(agg, (x, thrown2) -> {
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
                });
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
        System.out.println("ROLLBACK MIGRATION! " + backupQueryForCollection.keySet());
        CountDownLatch latch = new CountDownLatch(backupQueryForCollection.size() - 1);
        Document rollbacks = new Document();
        agg.append("rollback", rollbacks);
        for (String s : backupQueryForCollection.keySet()) {
            MongoCollection<Document> from = db.getCollection(backupCollectionName(s));
            MongoCollection<Document> to = db.getCollection(s);
            Document thisCollection = new Document();
            rollbacks.append(s, thisCollection);
            AtomicInteger batchCount = new AtomicInteger();
            from.find().batchCursor((cursor, thrown) -> {
                if (thrown != null) {
                    latch.countDown();
                    return;
                }
                cursor.setBatchSize(50);
                cursor.next((List<Document> l, Throwable t2) -> {
                    List<ReplaceOneModel<Document>> replacements = new ArrayList<>();
                    if (l == null) {
                        latch.countDown();
                    } else {
                        int ct = batchCount.incrementAndGet();
                        thisCollection.append("batch-" + ct, l.size());
                        for (Document d : l) {
                            replacements.add(new ReplaceOneModel<Document>(new Document("_id", d.getObjectId("_id")), d));
                        }
                        to.bulkWrite(replacements, (bwr, th2) -> {
                            if (th2 != null) {
                                thisCollection.append("batch-" + ct + "-failed", true);
                                thisCollection.append("batch-" + ct + "-succeeded", appendThrowable(th2));
                            } else {
                                thisCollection.append("batch-" + ct + "-succeeded", l.size());
                            }
                        });
                    }
                });
            });
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

    private ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void> backup(String collectionName, Document queryDoc) {
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
            origs.find(queryDoc).batchCursor((cursor, thrown) -> {
                if (thrown != null) {
                    t.completeExceptionally(thrown);
                    return;
                }
                AtomicInteger numSeen = new AtomicInteger();
                AtomicInteger numBackedUp = new AtomicInteger();
                cursor.setBatchSize(50);
                cursor.next((List<Document> l, Throwable thrown2) -> {
                    if (thrown2 != null) {
                        t.completeExceptionally(thrown);
                        return;
                    }
                    if (l != null) {
                        int seen = numSeen.addAndGet(l.size());
                        backupInfo.put("docsSeen", seen);
                    }
                    if (numSeen.get() == numBackedUp.get()) {
                        t.complete(backupInfo);
                    }
                    if (l == null) {
                        return;
                    }
                    backups.insertMany(l, (v2, thrown3) -> {
                        if (thrown3 != null) {
                            t.completeExceptionally(thrown3);
                            return;
                        }
                        if (l != null) {
                            for (Document d : l) {
                                allIds.add(d.getObjectId("_id"));
                            }
                        }
                        int total = numBackedUp.addAndGet(l.size());
                        backupInfo.put("docsBackedUp", total);
                        if (total == numSeen.get()) {
                            t.complete(backupInfo);
                        }
                    });
                });
            });
            return null;
        };
    }
}
