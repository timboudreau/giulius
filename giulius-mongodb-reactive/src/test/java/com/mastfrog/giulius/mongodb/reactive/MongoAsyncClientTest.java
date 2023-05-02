/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.giulius.mongodb.reactive;

import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import com.mastfrog.giulius.mongodb.reactive.MongoAsyncClientTest.TestModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.IfBinaryAvailable;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import static java.util.Collections.synchronizedSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.bson.Document;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith({TestModule.class, MongoHarness.Module.class})
@IfBinaryAvailable("mongod")
public class MongoAsyncClientTest {

    @Test
    public void testClient(MongoClient client,
            @Named("stuff") MongoCollection<Document> stuff,
            @Named("maps") MongoCollection<Map> maps,
            @Named("maps") MongoCollection<Document> mapsAsDocs,
            Init init) {
        assertNotNull(stuff);
        assertNotNull(maps);
        assertNotNull(mapsAsDocs);
        assertEquals(Map.class, maps.getDocumentClass());
        assertEquals(Document.class, mapsAsDocs.getDocumentClass());
        init.assertOnBeforeCreateCalled()
                .assertOnAfterCreateCalled();
        init.assertOnCreateCalledForCollection("stuff");
        init.assertOnCreateCalledForCollection("maps");
    }

    @Test
    public void testMongoDatabaseIsBound(MongoDatabase db) {
        assertNotNull(db);
    }

    static class TestModule extends AbstractModule {

        @Override
        protected void configure() {
            GiuliusMongoReactiveStreamsModule m = new GiuliusMongoReactiveStreamsModule();
            m.bindCollection("stuff").withInitializer(Init.class);
            m.bindCollection("maps", Map.class);
            install(m);
        }
    }

    static class Init extends MongoAsyncInitializer {

        boolean onBeforeCreateCalled;
        boolean onAfterCreateCalled;
        private final Set<String> onCreate = synchronizedSet(new HashSet<>());

        Init assertOnCreateCalledForCollection(String name) {
            assertTrue("onCreateCollection not called for " + name + ". Have: "
                    + onCreate.toString(), onCreate.contains(name));
            return this;
        }

        Init assertOnBeforeCreateCalled() {
            assertTrue("onBeforeCreateMongoClient not called", onBeforeCreateCalled);
            return this;
        }

        Init assertOnAfterCreateCalled() {
            assertTrue("onAfterCreateMongoClient not called", onAfterCreateCalled);
            return this;
        }

        @Inject
        public Init(Registry reg) {
            super(reg);
        }

        @Override
        public MongoClientSettings onBeforeCreateMongoClient(MongoClientSettings settings) {
            onBeforeCreateCalled = true;
            return super.onBeforeCreateMongoClient(settings);
        }

        @Override
        public MongoClient onAfterCreateMongoClient(MongoClient client) {
            onAfterCreateCalled = true;
            return super.onAfterCreateMongoClient(client);
        }

        @Override
        public void onCreateCollection(String name, MongoCollection<?> collection) {
            onCreate.add(name);
            super.onCreateCollection(name, collection);
        }

    }
}
