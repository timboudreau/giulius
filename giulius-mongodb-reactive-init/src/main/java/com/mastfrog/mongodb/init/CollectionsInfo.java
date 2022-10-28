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
import com.google.common.collect.ImmutableSet;
import com.mastfrog.giulius.mongodb.reactive.util.Subscribers;
import static com.mastfrog.mongodb.init.InitCollectionsInitializer.LOG;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Singleton;

/**
 * Encapsulates information about a collection of collections which should be
 * created if they do not exist.
 *
 * @author Tim Boudreau
 */
@Singleton
final class CollectionsInfo {

    public final Set<OneCollectionInfo> infos = new HashSet<>();

    @JsonCreator
    CollectionsInfo(@JsonProperty(value = "infos") Set<OneCollectionInfo> infos) {
        this.infos.addAll(infos);
    }

    CollectionsInfo register(OneCollectionInfo info) {
        infos.add(info);
        return this;
    }

    void init(MongoDatabase db, Subscribers subscribers, Consumer<Throwable> c,
            BiConsumer<String, MongoCollection<?>> onCreate) {
        try {
            Set<String> all = subscribers.multipleSet(db.listCollectionNames())
                    .get();
            Iterator<OneCollectionInfo> ones = ImmutableSet.copyOf(infos).iterator();
            if (all.isEmpty() && !ones.hasNext()) {
                c.accept(null);
                return;
            }
            Consumer<Throwable> c1 = new Consumer<Throwable>() {
                @Override
                public void accept(Throwable thrown) {
                    if (thrown != null) {
                        thrown.printStackTrace();
                        c.accept(thrown);
                        return;
                    }
                    if (ones.hasNext()) {
                        OneCollectionInfo info = ones.next();
                        if (LOG) {
                            System.err.println("Init collection " + info.name);
                        }
                        info.init(db, subscribers, all, this, onCreate);
                    } else {
                        if (LOG) {
                            System.err.println("Done creating collections");
                        }
                        System.out.println("Pass null to " + c);
                        c.accept(null);
                    }
                }
            };
            c1.accept(null);
        } catch (InterruptedException | ExecutionException ex) {
            c.accept(ex);
        }
    }

    @Override
    public String toString() {
        return "CollectionsInfo{" + infos + '}';
    }
}
