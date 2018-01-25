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
import com.mongodb.async.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import java.util.function.Consumer;
import org.bson.Document;
import org.bson.conversions.Bson;

final class IndexInfo {

    public String name;
    public final Bson description;
    public final IndexOptions options;

    @JsonCreator
    IndexInfo(@JsonProperty(value = "name") String name, @JsonProperty(value = "description") Bson description, @JsonProperty(value = "options") IndexOptions options) {
        this.name = name;
        this.description = description;
        this.options = options;
    }

    @Override
    public String toString() {
        return name + '{' + description + ',' + options + '}';
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IndexInfo && ((IndexInfo) o).name.equals(name);
    }

    public void create(MongoCollection<Document> coll, Consumer<Throwable> c) {
        coll.createIndex(description, options, (String s, Throwable t) -> {
            c.accept(t);
        });
    }
}
