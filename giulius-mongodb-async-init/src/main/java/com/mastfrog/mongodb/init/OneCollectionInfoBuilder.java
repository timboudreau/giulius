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
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.IndexOptionDefaults;
import com.mongodb.client.model.ValidationOptions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.conversions.Bson;

/**
 * Builder for a single collection; many of the methods here delegate to Mongo's
 * CreateCollectionOptions - see that for details.
 *
 * @author Tim Boudreau
 */
public final class OneCollectionInfoBuilder<T extends CollectionsInfoBuilder<R>, R> {

    public final String name;
    public final Set<IndexInfo> indexInfos = new HashSet<>();
    private final CreateCollectionOptions opts = new CreateCollectionOptions();
    private final List<Document> prepopulate = new ArrayList<>();
    private final T parent;

    OneCollectionInfoBuilder(String name, T parent) {
        this.name = Checks.notNull("name", name);
        this.parent = parent;
    }

    /**
     * Add the collection that has been defined here to the set of collections,
     * returning that.
     *
     * @return The parent CollectionsInfoBuilder
     */
    public T buildCollection() {
        OneCollectionInfo result = new OneCollectionInfo(name, opts, indexInfos.toArray(new IndexInfo[0]), prepopulate.toArray(new Document[0]));
        parent.add(result);
        return parent;
    }

    /**
     * Add an index to this collection.
     *
     * @param name The name of the index
     * @return A builder for that index, whose build method returns this object
     */
    public IndexInfoBuilder<OneCollectionInfoBuilder<T, R>, T, R> ensureIndex(String name) {
        return new IndexInfoBuilder<>(this, name);
    }

    /**
     * Add a document to be inserted. <b>Note:</b> The document will <u>only</u>
     * be inserted if the collection did not exist in the database <i>at all</i>
     * and was newly created. This method may be called multiple times to add
     * multiple documents.
     *
     * @param d A document.
     * @return this
     */
    public OneCollectionInfoBuilder<T, R> insertDocumentIfCreating(Document d) {
        prepopulate.add(d);
        return this;
    }

    public OneCollectionInfoBuilder<T, R> insertDocumentsIfCreating(Collection<Document> d) {
        prepopulate.addAll(d);
        return this;
    }


    public OneCollectionInfoBuilder<T, R> autoIndex(boolean autoIndex) {
        opts.autoIndex(autoIndex);
        return this;
    }

    public OneCollectionInfoBuilder<T, R> maxDocuments(long maxDocuments) {
        opts.maxDocuments(maxDocuments);
        return this;
    }

    public OneCollectionInfoBuilder<T, R> capped(boolean capped) {
        opts.capped(capped);
        return this;
    }

    public OneCollectionInfoBuilder<T, R> sizeInBytes(long sizeInBytes) {
        opts.sizeInBytes(sizeInBytes);
        return this;
    }

    public OneCollectionInfoBuilder<T, R> storageEngineOptions(Bson storageEngineOptions) {
        opts.storageEngineOptions(storageEngineOptions);
        return this;
    }

    public OneCollectionInfoBuilder<T, R> indexOptionDefaults(IndexOptionDefaults indexOptionDefaults) {
        opts.indexOptionDefaults(indexOptionDefaults);
        return this;
    }

    public OneCollectionInfoBuilder<T, R> validationOptions(ValidationOptions validationOptions) {
        opts.validationOptions(validationOptions);
        return this;
    }

    public OneCollectionInfoBuilder<T, R> collation(Collation collation) {
        opts.collation(collation);
        return this;
    }
}
