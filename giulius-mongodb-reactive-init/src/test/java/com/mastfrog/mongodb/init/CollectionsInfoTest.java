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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.IfBinaryAvailable;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.jackson.JacksonModule;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.IndexOptionDefaults;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import org.bson.Document;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiceRunner.class)
@TestWith(JacksonModule.class)
@IfBinaryAvailable("mongod")
public class CollectionsInfoTest {

    CollectionsInfo ifo;
    @Inject
    ObjectMapper mapper;

    @Before
    public void setup() {
        CollectionsInfoBuilder<Void> b = new CollectionsInfoBuilder<>(null, (CollectionsInfo info) -> {
            ifo = info;
        });
        b.add("stuff").capped(true).maxDocuments(5).sizeInBytes(80000)
                .validationOptions(new ValidationOptions().validationLevel(ValidationLevel.MODERATE))
                .indexOptionDefaults(new IndexOptionDefaults().storageEngine(new Document("eng", "x")))
                .ensureIndex("num").background(true).index("ix", 1).buildIndex()
                .insertDocumentIfCreating(new Document("baz", "quux").append("ix", 13))
                .buildCollection()
                .add("junk").ensureIndex("wubbles").unique(true).index("word", 1)
                .bits(32).bucketSize(5.2).collation(Collation.builder().locale("en-US").backwards(true).build())
                .languageOverride("fr-FR").sparse(true).unique(true).version(52)
                .buildIndex().buildCollection().build();
    }

    @Test
    public void testSerialization(ObjectMapper mapper) throws Throwable {
        assertNotNull(ifo);
        assertNotNull(mapper);
        String s = mapper.writeValueAsString(ifo);
//        System.out.println("WROTE OUT " + s);
        // PENDING: Write deserializers for the various things that can
        // be serialized here
//        CollectionsInfo ifo2 = mapper.readValue(s, CollectionsInfo.class);
    }

}
