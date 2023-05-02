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
package com.mastfrog.giulius.mongodb.async;

import com.google.inject.AbstractModule;
import com.google.inject.name.Named;
import com.mastfrog.giulius.mongodb.async.MongoAsyncClientTest.TestModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.IfBinaryAvailable;
import com.mastfrog.giulius.tests.anno.TestWith;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoCollection;
import java.util.Map;
import javax.inject.Inject;
import org.bson.Document;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
    public void testClient(MongoClient client, @Named("stuff") MongoCollection<Document> stuff, @Named("maps") MongoCollection<Map> maps, @Named("maps") MongoCollection<Document> mapsAsDocs) {
        assertNotNull(stuff);
        assertNotNull(maps);
        assertNotNull(mapsAsDocs);
        assertEquals(Map.class, maps.getDocumentClass());
        assertEquals(Document.class, mapsAsDocs.getDocumentClass());
    }

    static class TestModule extends AbstractModule {

        @Override
        protected void configure() {
            GiuliusMongoAsyncModule m = new GiuliusMongoAsyncModule();
            m.bindCollection("stuff").withInitializer(Init.class);
            m.bindCollection("maps", Map.class);
            install(m);
        }
    }

    static class Init extends MongoAsyncInitializer {

        @Inject
        public Init(Registry reg) {
            super(reg);
        }

        @Override
        public MongoClientSettings onBeforeCreateMongoClient(MongoClientSettings settings) {
            return super.onBeforeCreateMongoClient(settings);
        }

        @Override
        public MongoClient onAfterCreateMongoClient(MongoClient client) {
            return super.onAfterCreateMongoClient(client);
        }

        @Override
        public void onCreateCollection(String name, MongoCollection<?> collection) {
            super.onCreateCollection(name, collection);
        }

    }
}
