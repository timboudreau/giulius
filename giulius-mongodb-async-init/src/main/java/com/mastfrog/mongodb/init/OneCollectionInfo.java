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
import static com.mastfrog.mongodb.init.InitCollectionsInitializer.LOG;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mongodb.MongoCommandException;
import com.mongodb.WriteConcern;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
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
    OneCollectionInfo(@JsonProperty(value = "name") String name, @JsonProperty(value = "opts") CreateCollectionOptions opts, @JsonProperty(value = "indexInfos") IndexInfo[] infos, @JsonProperty(value = "prepopulate") Document... prepopulate) {
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
            db.createCollection(name, opts, (v, thrown) -> {
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
                onCreate.accept(name, coll);
            });
        } else {
            ensureIndexes(db, creating, c);
        }
    }

    private MongoCollection<Document> ensureIndexes(MongoDatabase db, boolean creating, Consumer<Throwable> c) {
        MongoCollection<Document> coll = db.getCollection(name).withWriteConcern(WriteConcern.FSYNC_SAFE);
        Iterator<IndexInfo> indexInfo = ImmutableSet.copyOf(indexInfos).iterator();
        Map<String, Document> indexForName = new ConcurrentHashMap<>();
        boolean populated[] = new boolean[1];
        Consumer<Throwable> c1 = new Consumer<Throwable>() {
            @Override
            public void accept(Throwable thrown) {
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
                } else {
                    if (!populated[0] && creating && !prepopulate.isEmpty()) {
                        populated[0] = true;
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

        coll.listIndexes().forEach((Document ix) -> {
            String name = ix.getString("name");
            if (name == null) {
                throw new IllegalStateException("Index document has no name field in " + db.getName() + "." + name + ": " + ix);
            }
            indexForName.put(name, ix);
        }, (v, thrown) -> {
            if (thrown != null) {
                c.accept(thrown);
                return;
            }
            if (LOG) {
                System.err.println("found existing indexes on " + name + ": " + indexForName);
            }
            c1.accept(null);
        });
        return coll;
    }

    private void populateDocuments(MongoCollection<Document> coll, Consumer<Throwable> c) {
        if (LOG) {
            System.err.println("Prepopulate " + prepopulate.size() + " documents in " + name);
        }
        for (Document d : prepopulate) {
            Object id = d.get("_id");
            if (id instanceof String && ObjectId.isValid((String) id)) {
                d.put("_id", new ObjectId((String) id));
            }
        }
        coll.insertMany(new ArrayList<>(prepopulate), new InsertManyOptions().ordered(true), (v, thrown) -> {
            c.accept(thrown);
        });
    }
}
