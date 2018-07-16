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

import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.multivariate.OneOf;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bson.Document;

/**
 * Supports "migration" of data in MongoDB collections, generically. You pass
 * either a MigrationWorker or a type for a MigrationWorker, and it will be run
 * to completion before the database becomes available to the application.
 * <p/>
 * Note that if a migration may take a long time, you may need to set the
 * setting <code>mongo.list.collections.max.wait.seconds</code> to a high value
 * to ensure migration is complete before any collections are provided to the
 * application.
 * <p/>
 * Migrations support backing up objects which match a query to a backup
 * collection, and will restore the backup if any migration completes
 * exceptionally.
 * <p/>
 * You pass a name and integer version for each migration; these are stored in
 * the <code>migrations</code> collection which tracks what migrations have
 * already been run. Migration support will not re-run a migration which has
 * already been run successfully, so historical migrations are near-zero
 * overhead.
 *
 * @author Tim Boudreau
 */
public class MigrationBuilder<T> {

    private final String name;
    private final int newVersion;
    private final Map<String, OneOf<MigrationWorker, Class<? extends MigrationWorker>>> migrations = new LinkedHashMap<>();
    private final Map<String, Document> backupQueryForCollection = new LinkedHashMap<>();
    private final Function<Migration, T> consumer;

    /**
     * Create a MigrationBuilder.
     *
     * @param name The name of the migration
     * @param newVersion
     * @param consumer
     */
    public MigrationBuilder(String name, int newVersion, Function<Migration, T> consumer) {
        this.name = notNull("name", name);
        this.newVersion = newVersion;
        this.consumer = consumer;
    }

    /**
     * Provide a query to use to back up objects to a backup collection before
     * applying migrations to that collection. If this method has already been
     * called, a $or query will be generated which will resolve either query.
     *
     * @param collectionName The collection name
     * @param backupQuery The query
     * @return this
     */
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

    /**
     * Remove any backup information that has been set for a collection, causing
     * it not to be backed up.
     *
     * @param collectionName
     * @return
     */
    public MigrationBuilder<T> noBackup(String collectionName) {
        backupQueryForCollection.remove(collectionName);
        return this;
    }

    public T build() {
        return consumer.apply(new Migration(name, newVersion, migrations, backupQueryForCollection));
    }

    /**
     * Set up migration of a collection, passing a class object so the migration
     * worker will be created with Guice and injected if needed.
     * <p/>
     * <b>Note:</b> Classes passed here should take care not to inject
     * MongoDatabase or MongoCollection or MongoClient, or anything that will
     * indirectly inject those - migration is run <i>during</i> MongoClient
     * creation, and those objects are not available for injection until
     * migration is complete. If a migration appears to deadlock, this is
     * probably why.
     *
     * @param collectionName The collection name
     * @param workerClass The type of the worker class
     * @return this
     */
    public MigrationBuilder<T> migrateCollection(String collectionName, Class<? extends MigrationWorker> workerClass) {
        notNull("collectionName", collectionName);
        notNull("type", workerClass);
        MigrationWorker instantiatingWrapper = (CompletableFuture<Document> a, MongoDatabase b, MongoCollection<Document> s, Function<Class<? extends MigrationWorker>, MigrationWorker> u) -> {
            MigrationWorker actual = u.apply(workerClass);
            actual.apply(a, b, s, u);
        };
        return migrateCollection(collectionName, instantiatingWrapper);
    }

    /**
     * Set up a migration.
     *
     * @param collectionName The collection
     * @param func The function
     * @return this
     */
    public MigrationBuilder<T> migrateCollection(String collectionName, MigrationWorker func) {
        final OneOf<MigrationWorker, Class<? extends MigrationWorker>> oneOf
                = migrations.get(collectionName);
        if (oneOf == null) {
            migrations.put(collectionName, OneOf.ofA(func));
        } else {
            MigrationWorker next = (CompletableFuture<Document> t, MongoDatabase u, MongoCollection<Document> v, Function<Class<? extends MigrationWorker>, MigrationWorker> f) -> {
                t.handle((doc, thrown) -> {
                    if (thrown != null) {
                        t.completeExceptionally(thrown);
                        return null;
                    }
                    try {
                        func.apply(t, u, v, f);
                    } catch (Exception ex) {
                        t.completeExceptionally(thrown);
                    }
                    return null;
                });
                func.apply(t, u, v, f);
            };
            migrations.put(collectionName, OneOf.ofA(next));
        }
        return this;
    }
}
