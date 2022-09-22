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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;
import com.mastfrog.giulius.mongodb.reactive.Subscribers;
import static com.mastfrog.mongodb.init.InitCollectionsInitializer.LOG;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mongodb.MongoCommandException;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.InsertManyOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.bson.Document;
import org.bson.types.ObjectId;

final class OneCollectionInfo {

    public final String name;
    public final Set<IndexInfo> indexInfos = new HashSet<>();
    private final CreateCollectionOptions opts;
    private final Set<Document> prepopulate;

    @JsonCreator
    OneCollectionInfo(@JsonProperty(value = "name") String name,
            @JsonProperty(value = "opts") CreateCollectionOptions opts,
            @JsonProperty(value = "indexInfos") IndexInfo[] infos, @JsonProperty(value = "prepopulate") Document... prepopulate) {
        this.name = Checks.notNull("name", name);
        this.indexInfos.addAll(Arrays.asList(Checks.notNull("infos", infos)));
        this.opts = opts;
        this.prepopulate = CollectionUtils.setOf(prepopulate);
    }

    @Override
    public String toString() {
        return name + '{' + Strings.join(',', indexInfos) + ',' + opts + ',' + Strings.join(',', prepopulate) + '}';
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OneCollectionInfo && ((OneCollectionInfo) o).name.equals(name);
    }

    void init(MongoDatabase db, Set<String> existingCollectionNames, Consumer<Throwable> c, BiConsumer<String, MongoCollection<?>> onCreate) {
        boolean creating = !existingCollectionNames.contains(name);
        if (creating) {
            if (LOG) {
                System.err.println("creating collection " + name);
            }
            Exception stack = new Exception("Creating collection " + name);
            Subscribers.callback(db.createCollection(name, opts), (v, thrown) -> {
                System.out.println("CREATE COLLECTION CALLBACK " + name + " " + v + " " + thrown);
                if (thrown != null) {
                    thrown.addSuppressed(stack);
                    if (thrown instanceof MongoCommandException) {
                        MongoCommandException e = (MongoCommandException) thrown;
                        if ("collection already exists".equals(e.getErrorMessage()) || e.getErrorCode() == -1) {
                            thrown.printStackTrace();
                            ensureIndexes(db, false, c);
                            return;
                        }
                    }
                    c.accept(thrown);
                    return;
                }
                MongoCollection<?> coll = ensureIndexes(db, creating, c);
                System.out.println("ON CREATE " + name + " " + coll.getNamespace());
                onCreate.accept(name, coll);
//                c.accept(null);
            });
        } else {
            ensureIndexes(db, creating, c);
        }
    }

    private MongoCollection<Document> ensureIndexes(MongoDatabase db, boolean creating, Consumer<Throwable> c) {
        System.out.println("Ensure indexes " + name + " on " + db.getName() + " creating " + creating);
        MongoCollection<Document> coll = db.getCollection(name).withWriteConcern(WriteConcern.JOURNALED);
        Iterator<IndexInfo> indexInfo = ImmutableSet.copyOf(indexInfos).iterator();
        Map<String, Document> indexForName = new ConcurrentHashMap<>();
        boolean populated[] = new boolean[1];
        Consumer<Throwable> c1 = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable thrown) {
                System.out.println("C1 ACCEPT " + thrown);
                if (thrown != null) {
                    c.accept(thrown);
                    return;
                }
                IndexInfo next = null;
                while (indexInfo.hasNext()) {
                    IndexInfo index = indexInfo.next();
                    Document existing = indexForName.get(index.name);
                    if (existing == null) {
                        next = index;
                        break;
                    }
                }
                if (next != null) {
                    if (LOG) {
                        System.err.println("Create index " + next.name + " on " + name);
                    }
                    next.create(coll, this);
                    if (creating) {
//                        populateDocuments(coll, this);
                    }
                } else {
                    System.out.println("populated " + populated[0] + " creating " + creating
                            + " prepopulate " + prepopulate.size());
                    if (!populated[0] && creating && !prepopulate.isEmpty()) {
                        populated[0] = true;
                        System.out.println("  call populate documents ");
                        populateDocuments(coll, this);
                    } else {
                        if (indexInfo.hasNext()) {
                            this.accept(null);
                        } else {
                            c.accept(null);
                        }
                    }
                }
            }
        };
        Subscribers.forEach(coll.listIndexes(), (ix, thrown) -> {
            String indexName = ix.getString("name");
            if (indexName == null) {
                new IllegalStateException("Index document has no name field in " + db.getName() + "." + indexName + ": " + ix).printStackTrace();
                return;
            }
            indexForName.put(indexName, ix);
        }).whenComplete((ignored, thrown) -> {
            System.out.println("HAVE INDEXES " + indexForName.keySet());
            c1.accept(thrown);
        });
        return coll;
    }

    private void populateDocuments(MongoCollection<Document> coll, Consumer<Throwable> c) {
        System.out.println("POPULATE DOCS " + coll.getNamespace());
        if (LOG) {
            System.err.println("Prepopulate " + prepopulate.size() + " documents in " + name);
        }
        for (Document d : prepopulate) {
            Object id = d.get("_id");
            // JSON defined docs will have string ids, which mongodb will accept,
            // but queries will fail:
            if (id instanceof String && ObjectId.isValid((String) id)) {
                d.put("_id", new ObjectId((String) id));
            }
        }
        System.out.println("INSERTING " + prepopulate.size() + " docs");
        Exception e = new Exception("Insert " + prepopulate.size() + " docs into " + coll.getNamespace());
        InsertManyOptions insertOptions = new InsertManyOptions().ordered(true);
        Subscribers.first(coll.insertMany(new ArrayList<>(prepopulate), insertOptions),
                (v, thrown) -> {
                    if (thrown != null) {
                        thrown.addSuppressed(e);
                    }
                    System.out.println("Insert many of " + prepopulate.size() + " " + v + " " + thrown);
                    c.accept(thrown);
                }
        );
    }
}
