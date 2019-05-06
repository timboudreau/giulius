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

import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule;
import com.mastfrog.giulius.mongodb.async.MongoHarness;
import com.mastfrog.giulius.mongodb.async.TestSupport;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.mongodb.init.InitCollectionsInitializerTest.Ini;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiceRunner.class)
@TestWith({MongoHarness.Module.class, Ini.class})
@IfBinaryAvailable("mongod")
public class InitCollectionsInitializerTest {

    @Test
    public void test(MongoDatabase db, @Named("stuff") MongoCollection<Document> stuff, @Named("junk") MongoCollection<Document> junk, CollectionsInfo info) {

        System.out.println("INFO IS " + info);

        List<Document> stuffDocs = allDocs(stuff);
        assertEquals(stuffDocs + "", 2, stuffDocs.size());

        List<Document> junkDocs = allDocs(junk);
        assertEquals(junkDocs + "", 3, junkDocs.size());

        List<Document> stuffIndexes = allIndexes(stuff);

        assertIndex("num", "ix", 1, stuffIndexes);

        List<Document> junkIndexes = allIndexes(junk);

        assertIndex("wubbles", "word", 1, junkIndexes);

        AtomicReference<String> created = new AtomicReference<>();
        // Now try re-running the initialization, which should not throw
        // exceptions and have no side effects
        TestSupport.await((ts) -> {
            info.init(db, (thrown) -> {
                if (thrown != null) {
                    ts.apply(thrown);
                } else {
                    ts.done();
                }
            }, (name, coll) -> {
                created.set(name);
            });
        });
        assertNull("Re-running initializer should not create any collections", created.get());

        List<Document> stuffDocs2 = allDocs(stuff);
        assertEquals(stuffDocs2 + " - documents added twice", 2, stuffDocs2.size());

        List<Document> junkDocs2 = allDocs(junk);
        assertEquals(junkDocs2 + " - documents added twice", 3, junkDocs2.size());

        List<Document> stuffIndexes2 = allIndexes(stuff);

        assertIndex("num", "ix", 1, stuffIndexes2);

        List<Document> junkIndexes2 = allIndexes(junk);

        assertIndex("wubbles", "word", 1, junkIndexes2);

        assertEquals("Indexes created twice: " + stuffIndexes2, stuffIndexes.size(), stuffIndexes2.size());
        assertEquals("Indexes created twice: " + junkIndexes2, junkIndexes.size(), junkIndexes2.size());
    }

    private void assertIndex(String name, String key, Object val, List<Document> docs) {
        Document it = null;
        for (Document d : docs) {
            String nm = d.getString("name");
            if (name.equals(nm)) {
                it = d;
                break;
            }
        }
        assertNotNull("Did not find an index named " + name, it);
        Document k = it.get("key", Document.class);
        assertNotNull("no index key in " + it + " for " + name, k);
        assertNotNull("No " + key + " in " + it, k.get(key));
        assertEquals("Wrong value in key " + k + " for " + key, val, k.get(key));
    }

    List<Document> allIndexes(MongoCollection<Document> coll) {
        List<Document> docs = new CopyOnWriteArrayList<>();
        TestSupport.await((ts) -> {
            coll.listIndexes().forEach((Document d) -> {
                docs.add(d);
            }, ts.callback((v) -> {
                ts.done();
            }));
        });
        return docs;
    }

    List<Document> allDocs(MongoCollection<Document> coll) {
        List<Document> docs = new CopyOnWriteArrayList<>();
        TestSupport.await((TestSupport supp) -> {
            coll.find(new Document()).forEach((Document d) -> {
                docs.add(d);
            }, supp.callback((v) -> {
                System.out.println("FOUND DOCS IN " + coll.getNamespace() + ": " + docs);
                supp.done();
            }));
        });
        return docs;
    }

    static class Ini extends AbstractModule {

        @Override
        protected void configure() {
            MongoInitModule mod = new MongoInitModule();
            mod.withCollections().add("stuff").capped(true).maxDocuments(5).sizeInBytes(80000)
                    .ensureIndex("num").background(true).index("ix", 1).buildIndex()
                    .insertDocumentIfCreating(new Document("foo", "bar").append("ix", 22))
                    .insertDocumentIfCreating(new Document("baz", "quux").append("ix", 13))
                    .buildCollection()
                    .add("junk").ensureIndex("wubbles").unique(true).index("word", 1).buildIndex()
                    .insertDocumentIfCreating(new Document("word", "hey"))
                    .insertDocumentIfCreating(new Document("word", "foo"))
                    .insertDocumentIfCreating(new Document("word", "zoopa"))
                    .buildCollection().build();
            install(mod);

            install(new GiuliusMongoAsyncModule().bindCollection("stuff").bindCollection("junk"));
        }
    }
}
