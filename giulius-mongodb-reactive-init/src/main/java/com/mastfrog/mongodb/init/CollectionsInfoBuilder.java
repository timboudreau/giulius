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

import com.mastfrog.util.preconditions.Checks;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Builder for a set of prepopulated collections which will be initialized if
 * not present upon connecting.
 *
 * @author Tim Boudreau
 */
public final class CollectionsInfoBuilder<T> {

    private final Set<OneCollectionInfo> collections = new HashSet<>();
    private final Consumer<CollectionsInfo> consumer;
    private final T parent;

    CollectionsInfoBuilder(T parent, Consumer<CollectionsInfo> consumer) {
        this.parent = parent;
        this.consumer = consumer;
    }

    CollectionsInfoBuilder<T> add(OneCollectionInfo info) {
        collections.add(Checks.notNull("info", info));
        return this;
    }

    /**
     * Add a collection, using the returned builder to define it.
     * 
     * @param collectionName The collection name
     * @return A builder for a single collection, whose build method returns
     * this CollectionsInfoBuilder
     */
    public OneCollectionInfoBuilder<CollectionsInfoBuilder<T>, T> add(String collectionName) {
        return new OneCollectionInfoBuilder<>(collectionName, this);
    }

    /**
     * Build and install
     * 
     * @return The parent object, most likely MongoInitModule
     */
    public T build() {
        CollectionsInfo result = new CollectionsInfo(collections);
        consumer.accept(result);
        return parent;
    }
}
