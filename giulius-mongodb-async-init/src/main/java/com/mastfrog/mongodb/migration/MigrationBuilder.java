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

import static com.mastfrog.util.Checks.notNull;
import com.mastfrog.util.function.ThrowingTriFunction;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bson.Document;

/**
 *
 * @author Tim Boudreau
 */
public class MigrationBuilder<T> {

    private final String name;
    private final int newVersion;
    private final Map<String, ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void>> migrations = new LinkedHashMap<>();
    private final Map<String, Document> backupQueryForCollection = new LinkedHashMap<>();
    private final Function<Migration, T> consumer;

    public MigrationBuilder(String name, int newVersion, Function<Migration, T> consumer) {
        this.name = notNull("name", name);
        this.newVersion = newVersion;
        this.consumer = consumer;
    }

    public MigrationBuilder<T> backup(String collectionName, Document backupQuery) {
        notNull("backupQuery", backupQuery);
        Document q = backupQueryForCollection.get(notNull("collectionName", collectionName));
        if (q != null) {
            q = new Document("$or", Arrays.asList(q, backupQuery));
        } else {
            q = backupQuery;
        }
        backupQueryForCollection.put(collectionName, q);
        return this;
    }

    public MigrationBuilder<T> noBackup(String collectionName) {
        backupQueryForCollection.remove(collectionName);
        return this;
    }

    public T build() {
        return consumer.apply(new Migration(name, newVersion, migrations, backupQueryForCollection));
    }

    public MigrationBuilder<T> migrateCollection(String collectionName, ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void> func) {
        final ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void> mig = migrations.get(collectionName);
        if (mig == null) {
            migrations.put(collectionName, func);
        } else {
            ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void> nue = new ThrowingTriFunction<CompletableFuture<Document>, MongoDatabase, MongoCollection<Document>, Void>() {
                @Override
                public Void apply(CompletableFuture<Document> t, MongoDatabase u, MongoCollection<Document> v) throws Exception {
                    t.handle((doc, thrown) -> {
                        if (thrown != null) {
                            t.completeExceptionally(thrown);
                            return null;
                        }
                        try {
                            func.apply(t, u, v);
                        } catch (Exception ex) {
                            t.completeExceptionally(thrown);
                        }
                        return null;
                    });
                    mig.apply(t, u, v);
                    return null;
                }
            };
        }
        return this;
    }
}
