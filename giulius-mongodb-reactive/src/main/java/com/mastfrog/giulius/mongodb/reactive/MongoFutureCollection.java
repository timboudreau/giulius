/*
 * The MIT License
 *
 * Copyright 2018 tim.
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

import com.google.inject.util.Providers;
import com.mastfrog.util.function.EnhCompletableFuture;
import com.mongodb.Function;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.CountOptions;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.InsertOneOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.session.ClientSession;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.inject.Provider;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Wraps a MongoCollection's methods that take SingleResultCallback to instead
 * return a CompletableFuture.
 *
 * @author Tim Boudreau
 * @param T the collection value type
 */
public final class MongoFutureCollection<T> {

    private final Provider<MongoCollection<T>> coll;

    MongoFutureCollection(Provider<MongoCollection<T>> coll) {
        this.coll = coll;
    }

    public static <T> MongoFutureCollection<T> forProvider(Provider<MongoCollection<T>> prov) {
        return new MongoFutureCollection<>(prov);
    }

    public static <T> MongoFutureCollection<T> forCollection(MongoCollection<T> prov) {
        return new MongoFutureCollection<>(Providers.of(prov));
    }

    public MongoCollection<T> collection() {
        return coll.get();
    }

    public EnhCompletableFuture<List<T>> aggregate(List<? extends Bson> list) {
        return iterFuture(collection().aggregate(list), null);
    }

    public EnhCompletableFuture<List<T>> aggregate(ClientSession cs, List<? extends Bson> list) {
        return iterFuture(collection().aggregate(list), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> aggregate(List<? extends Bson> list, Class<TResult> type) {
        return iterFuture(collection().aggregate(list, type), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> aggregate(ClientSession sess, List<? extends Bson> list, Class<TResult> type) {
        return iterFuture(collection().aggregate(list, type), null);
    }

    public EnhCompletableFuture<BulkWriteResult> bulkWrite(List<? extends WriteModel<? extends T>> list) {
        EnhCompletableFuture<BulkWriteResult> result = new EnhCompletableFuture<>();
        collection().bulkWrite(list, new BulkWriteOptions())
                .subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<BulkWriteResult> bulkWrite(List<? extends WriteModel<? extends T>> list, BulkWriteOptions bwo) {
        EnhCompletableFuture<BulkWriteResult> result = new EnhCompletableFuture<>();
        collection().bulkWrite(list, bwo).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<UpdateResult> replaceOne(Bson bson, T td) {
        EnhCompletableFuture<UpdateResult> result = new EnhCompletableFuture<>();
        collection().replaceOne(bson, td).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<UpdateResult> replaceOne(Bson bson, T td, ReplaceOptions uo) {
        EnhCompletableFuture<UpdateResult> result = new EnhCompletableFuture<>();
        collection().replaceOne(bson, td, uo).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<List<Document>> listIndexes() {
        return iterFuture(collection().listIndexes(), null);
    }

    public EnhCompletableFuture<Long> count() {
        EnhCompletableFuture<Long> result = new EnhCompletableFuture<>();
        collection().countDocuments().subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<Long> count(Bson bson) {
        EnhCompletableFuture<Long> result = new EnhCompletableFuture<>();
        collection().countDocuments(bson).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<Long> count(Bson bson, CountOptions co) {
        EnhCompletableFuture<Long> result = new EnhCompletableFuture<>();
        collection().countDocuments(bson, co).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<InsertOneResult> insertOne(T td) {
        EnhCompletableFuture<InsertOneResult> result = new EnhCompletableFuture<>();
        collection().insertOne(td).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<InsertOneResult> insertOne(T td, InsertOneOptions ioo) {
        EnhCompletableFuture<InsertOneResult> result = new EnhCompletableFuture<>();
        collection().insertOne(td, ioo).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<InsertManyResult> insertMany(List<? extends T> list) {
        EnhCompletableFuture<InsertManyResult> result = new EnhCompletableFuture<>();
        collection().insertMany(list).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<InsertManyResult> insertMany(List<? extends T> list, InsertManyOptions imo) {
        EnhCompletableFuture<InsertManyResult> result = new EnhCompletableFuture<>();
        collection().insertMany(list, imo).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<DeleteResult> deleteOne(Bson bson) {
        EnhCompletableFuture<DeleteResult> result = new EnhCompletableFuture<>();
        collection().deleteOne(bson).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<DeleteResult> deleteOne(Bson bson, DeleteOptions d) {
        EnhCompletableFuture<DeleteResult> result = new EnhCompletableFuture<>();
        collection().deleteOne(bson, d).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<DeleteResult> deleteOne(ClientSession cs, Bson bson) {
        EnhCompletableFuture<DeleteResult> result = new EnhCompletableFuture<>();
        collection().deleteOne(bson).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<DeleteResult> deleteOne(ClientSession cs, Bson bson, DeleteOptions d) {
        EnhCompletableFuture<DeleteResult> result = new EnhCompletableFuture<>();
        collection().deleteOne(bson, d).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<DeleteResult> deleteMany(Bson bson) {
        EnhCompletableFuture<DeleteResult> result = new EnhCompletableFuture<>();
        collection().deleteMany(bson).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<DeleteResult> deleteMany(Bson bson, DeleteOptions d) {
        EnhCompletableFuture<DeleteResult> result = new EnhCompletableFuture<>();
        collection().deleteMany(bson, d).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<DeleteResult> deleteMany(ClientSession cs, Bson bson) {
        EnhCompletableFuture<DeleteResult> result = new EnhCompletableFuture<>();
        collection().deleteMany(bson).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<DeleteResult> deleteMany(ClientSession cs, Bson bson, DeleteOptions d) {
        EnhCompletableFuture<DeleteResult> result = new EnhCompletableFuture<>();
        collection().deleteMany(bson, d).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<UpdateResult> updateOne(Bson query, Bson update) {
        EnhCompletableFuture<UpdateResult> result = new EnhCompletableFuture<>();
        collection().updateOne(query, update).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<UpdateResult> updateOne(Bson query, Bson update, UpdateOptions uo) {
        EnhCompletableFuture<UpdateResult> result = new EnhCompletableFuture<>();
        collection().updateOne(query, update, uo).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<UpdateResult> updateMany(Bson query, Bson update) {
        EnhCompletableFuture<UpdateResult> result = new EnhCompletableFuture<>();
        collection().updateMany(query, update).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<UpdateResult> updateMany(Bson bson, Bson bson1, UpdateOptions uo) {
        EnhCompletableFuture<UpdateResult> result = new EnhCompletableFuture<>();
        collection().updateMany(bson, bson1, uo).subscribe(new SRC<>(result));
        return result;
    }

    static class SRC<T> implements Subscriber<T> {

        private final EnhCompletableFuture<T> fut;
        private T value;

        public SRC(EnhCompletableFuture<T> fut) {
            this.fut = fut;
        }

        @Override
        public void onSubscribe(Subscription s) {
            // do nothing
        }

        @Override
        public void onNext(T t) {

        }

        @Override
        public void onError(Throwable thrwbl) {
            fut.completeExceptionally(thrwbl);
        }

        @Override
        public void onComplete() {
            fut.complete(value);
        }

    }

    public EnhCompletableFuture<List<T>> find() {
        return iterFuture(collection().find(), null);
    }

    private <T, R extends Publisher<T>> EnhCompletableFuture<List<T>> iterFuture(R ft,
            Consumer<R> cons) {
        EnhCompletableFuture<List<T>> result = new EnhCompletableFuture<>();
        List<T> l = new CopyOnWriteArrayList<>();
        EnhCompletableFutureCollectionSubscriber<T, List<T>> sub = new EnhCompletableFutureCollectionSubscriber<>(result, l);

        if (cons != null) {
            cons.accept(ft);
        }
        ft.subscribe(sub);
        return result;
    }

    public EnhCompletableFuture<T> findOneAndUpdate(Bson query, Bson update, FindOneAndUpdateOptions foauo) {
        EnhCompletableFuture<T> result = new EnhCompletableFuture<>();
        collection().findOneAndUpdate(query, update, foauo).subscribe(new SRC<>(result));
        return result;
    }

    public EnhCompletableFuture<T> findOneAndUpdate(Bson query, Bson update) {
        return findOneAndUpdate(query, update, new FindOneAndUpdateOptions());
    }

    public EnhCompletableFuture<T> findOneAndUpdate(ObjectId id, Bson update) {
        return findOneAndUpdate(new Document("_id", id), update, new FindOneAndUpdateOptions());
    }

    public EnhCompletableFuture<T> findOne() {
        EnhCompletableFuture<T> result = new EnhCompletableFuture<>();
        collection().find().subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public EnhCompletableFuture<T> findOne(ObjectId id) {
        return findOne(new Document("_id", id));
    }

    public EnhCompletableFuture<T> findOne(Bson query) {
        EnhCompletableFuture<T> result = new EnhCompletableFuture<>();
        collection().find(query).subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public EnhCompletableFuture<T> findOne(Bson query, Bson projection) {
        EnhCompletableFuture<T> result = new EnhCompletableFuture<>();
        collection().find(query).projection(projection).subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public <R> EnhCompletableFuture<R> findOne(Bson query, Class<R> type) {
        EnhCompletableFuture<R> result = new EnhCompletableFuture<>();
        collection().find(query, type).subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public <R> EnhCompletableFuture<R> findOne(Bson query, Bson projection, Class<R> type) {
        EnhCompletableFuture<R> result = new EnhCompletableFuture<>();
        collection().find(query, type).projection(projection).subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public EnhCompletableFuture<T> findOne(ClientSession cs, Bson query) {
        EnhCompletableFuture<T> result = new EnhCompletableFuture<>();
        collection().find(query).subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public EnhCompletableFuture<T> findOne(ClientSession cs, Bson query, Bson projection) {
        EnhCompletableFuture<T> result = new EnhCompletableFuture<>();
        collection().find(query).projection(projection).subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public <R> EnhCompletableFuture<R> findOne(ClientSession cs, Bson query, Class<R> type) {
        EnhCompletableFuture<R> result = new EnhCompletableFuture<>();
        collection().find(query, type).subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public <R> EnhCompletableFuture<R> findOne(ClientSession cs, Bson query, Bson projection, Class<R> type) {
        EnhCompletableFuture<R> result = new EnhCompletableFuture<>();
        collection().find(query, type).projection(projection).subscribe(new FirstItemSubscriber<>(result));
        return result;
    }

    public <TResult> EnhCompletableFuture<List<TResult>> find(Class<TResult> type) {
        return iterFuture(collection().find(type), null);
    }

    public EnhCompletableFuture<List<T>> find(Bson bson) {
        return iterFuture(collection().find(bson), null);
    }

    public EnhCompletableFuture<List<T>> find(Bson bson, Bson projection) {
        return iterFuture(collection().find(bson).projection(projection), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> find(Bson bson, Class<TResult> type) {
        return iterFuture(collection().find(bson, type), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> find(Bson bson, Bson projection, Class<TResult> type) {
        return iterFuture(collection().find(bson, type).projection(projection), null);
    }

    public EnhCompletableFuture<List<T>> find(ClientSession cs) {
        return iterFuture(collection().find(), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> find(ClientSession cs, Class<TResult> type) {
        return iterFuture(collection().find(type), null);
    }

    public EnhCompletableFuture<List<T>> find(ClientSession cs, Bson bson) {
        return iterFuture(collection().find(bson), null);
    }

    public EnhCompletableFuture<List<T>> find(ClientSession cs, Bson bson, Bson projection) {
        return iterFuture(collection().find(bson).projection(projection), null);
    }

    public <R> EnhCompletableFuture<List<R>> find(ClientSession cs, Bson bson, Bson projection, Class<R> type) {
        return iterFuture(collection().find(bson, type).projection(projection), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> distinct(String string, Class<TResult> type) {
        return iterFuture(collection().distinct(string, type), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> distinct(String string, Bson bson, Class<TResult> type) {
        return iterFuture(collection().distinct(string, bson, type), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> distinct(ClientSession cs, String string, Class<TResult> type) {
        return iterFuture(collection().distinct(string, type), null);
    }

    public <TResult> EnhCompletableFuture<List<TResult>> distinct(ClientSession cs, String string, Bson bson, Class<TResult> type) {
        return iterFuture(collection().distinct(string, bson, type), null);
    }

    public <NewTDocument> MongoFutureCollection<NewTDocument> withDocumentClass(Class<NewTDocument> type) {
        return new MongoFutureCollection<>(xform(cl -> cl.withDocumentClass(type)));
    }

    public MongoFutureCollection<T> withReadPreference(ReadPreference rp) {
        return new MongoFutureCollection<>(xform(cl -> cl.withReadPreference(rp)));
    }

    public MongoFutureCollection<T> withWriteConcern(WriteConcern wc) {
        return new MongoFutureCollection<>(xform(cl -> cl.withWriteConcern(wc)));
    }

    public MongoFutureCollection<T> withReadConcern(ReadConcern rc) {
        return new MongoFutureCollection<>(xform(cl -> cl.withReadConcern(rc)));
    }

    private <R> Provider<MongoCollection<R>> xform(Function<MongoCollection<T>, MongoCollection<R>> xform) {
        return new TransformProvider<>(coll, xform);
    }

    private static final class TransformProvider<T, R> implements Provider<MongoCollection<R>> {

        private final Provider<? extends MongoCollection<T>> orig;
        private final Function<MongoCollection<T>, MongoCollection<R>> xform;

        public TransformProvider(Provider<? extends MongoCollection<T>> orig, Function<MongoCollection<T>, MongoCollection<R>> xform) {
            this.orig = orig;
            this.xform = xform;
        }

        @Override
        public MongoCollection<R> get() {
            MongoCollection<T> o = orig.get();
            return xform.apply(o);
        }
    }

    static class FirstItemSubscriber<T> implements Subscriber<T> {

        private final EnhCompletableFuture<T> fut;
        private Subscription sub;

        public FirstItemSubscriber(EnhCompletableFuture<T> fut) {
            this.fut = fut;
        }

        public FirstItemSubscriber() {
            this(new EnhCompletableFuture<>());
        }

        EnhCompletableFuture<T> future() {
            return fut;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (fut.isCancelled()) {
                s.cancel();
            }
            synchronized (this) {
                sub = s;
            }
            s.request(1);
        }

        @Override
        public void onNext(T t) {
            fut.complete(t);
            Subscription s;
            synchronized (this) {
                s = sub;
            }
            if (s != null) {
                s.cancel();
            }
        }

        @Override
        public void onError(Throwable thrwbl) {
            fut.completeExceptionally(thrwbl);
        }

        @Override
        public void onComplete() {
            if (!fut.isDone()) {
                fut.complete(null);
            }
        }
    }
}
