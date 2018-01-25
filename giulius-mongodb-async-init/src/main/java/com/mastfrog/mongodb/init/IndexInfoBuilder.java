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

import com.mastfrog.util.Checks;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.IndexOptions;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * Builder for MongoDB indexes. Many of the methods here delegate to Mongo's
 * IndexOptions class - see the javadoc for that for details.
 *
 * @author Tim Boudreau
 */
public final class IndexInfoBuilder<T extends OneCollectionInfoBuilder<R, X>, R extends CollectionsInfoBuilder<X>, X> {

    private final String name;
    private final T parent;
    private final Document description = new Document();
    private final IndexOptions options = new IndexOptions();

    IndexInfoBuilder(T parent, String name) {
        this.parent = parent;
        this.name = Checks.notNull("name", name);
        options.name(name);
    }

    /**
     * Add a key/value pair to the index.
     *
     * @param key
     * @param value
     * @return
     */
    public IndexInfoBuilder<T, R, X> put(String key, Object value) {
        description.append(key, value);
        return this;
    }

    /**
     * Add all key/value pairs in the passed document to the index definition.
     * Useful with Mongo's Indexes utility class.
     *
     * @param desc The description document
     * @return this
     */
    public IndexInfoBuilder<T, R, X> putDescription(Document desc) {
        for (String key : desc.keySet()) {
            description.put(key, desc.get(key));
        }
        return this;
    }

    /**
     * Add this index to the parent collection builder, returning that.
     *
     * @return A collection builder
     */
    public T build() {
        IndexInfo info = new IndexInfo(name, description, options);
        parent.indexInfos.add(info);
        return parent;
    }

    public IndexInfoBuilder<T, R, X> background(boolean background) {
        options.background(background);
        return this;
    }

    public IndexInfoBuilder<T, R, X> unique(boolean unique) {
        options.unique(unique);
        return this;
    }

    public IndexInfoBuilder<T, R, X> name(String name) {
        options.name(name);
        return this;
    }

    public IndexInfoBuilder<T, R, X> sparse(boolean sparse) {
        options.sparse(sparse);
        return this;
    }

    public IndexInfoBuilder<T, R, X> expireAfter(Long expireAfter, TimeUnit timeUnit) {
        options.expireAfter(expireAfter, timeUnit);
        return this;
    }

    public IndexInfoBuilder<T, R, X> version(Integer version) {
        options.version(version);
        return this;
    }

    public IndexInfoBuilder<T, R, X> weights(Bson weights) {
        options.weights(weights);
        return this;
    }

    public IndexInfoBuilder<T, R, X> defaultLanguage(String defaultLanguage) {
        options.defaultLanguage(defaultLanguage);
        return this;
    }

    public IndexInfoBuilder<T, R, X> languageOverride(String languageOverride) {
        options.languageOverride(languageOverride);
        return this;
    }

    public IndexInfoBuilder<T, R, X> textVersion(Integer textVersion) {
        options.textVersion(textVersion);
        return this;
    }

    public IndexInfoBuilder<T, R, X> sphereVersion(Integer sphereVersion) {
        options.sphereVersion(sphereVersion);
        return this;
    }

    public IndexInfoBuilder<T, R, X> bits(Integer bits) {
        options.bits(bits);
        return this;
    }

    public IndexInfoBuilder<T, R, X> min(Double min) {
        options.min(min);
        return this;
    }

    public IndexInfoBuilder<T, R, X> max(Double max) {
        options.max(max);
        return this;
    }

    public IndexInfoBuilder<T, R, X> bucketSize(Double bucketSize) {
        options.bucketSize(bucketSize);
        return this;
    }

    public IndexInfoBuilder<T, R, X> storageEngine(Bson storageEngine) {
        options.storageEngine(storageEngine);
        return this;
    }

    public IndexInfoBuilder<T, R, X> partialFilterExpression(Bson partialFilterExpression) {
        options.partialFilterExpression(partialFilterExpression);
        return this;
    }

    public IndexInfoBuilder<T, R, X> collation(Collation collation) {
        options.collation(collation);
        return this;
    }
}
