/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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

import com.mastfrog.function.state.Obj;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.mongodb.reactive.util.Subscribers;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.anno.IfBinaryAvailable;
import com.mastfrog.util.preconditions.Exceptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bson.Document;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@IfBinaryAvailable("mongod")
public class SubscriptionBehaviorTest {

    private static final int DOC_COUNT = 200;
    private Dependencies deps;
    private MongoHarness harn;
    private MongoClient client;
    private MongoDatabase db;
    private MongoCollection<Document> coll;

    @Test
    public void testIt() throws InterruptedException {
        assertNotNull(coll);
        List<Document> dox = new CopyOnWriteArrayList<>();
        S s = new S(10, batch -> {
            dox.addAll(batch);
        });
        coll.find().subscribe(s);
        s.await().rethrow();
        assertEquals(DOC_COUNT, dox.size());
    }

    static class S implements Subscriber<Document> {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final List<Document> documents = new ArrayList<>();
        private final int batchSize;
        private final Consumer<List<Document>> c;
        private Throwable thrown;
        private Subscription subscription;
        private boolean done;

        public S(int batchSize, Consumer<List<Document>> c) {
            this.batchSize = batchSize;
            this.c = c;
        }

        synchronized void emit() {
            List<Document> nue = new ArrayList<>(documents);
            documents.clear();
            if (!nue.isEmpty()) {
                c.accept(nue);
            }
            System.out.println("emitted " + nue.size());
            if (!done) {
                System.out.println("req another batch");
                subscription.request(batchSize);
            }
        }

        synchronized S rethrow() {
            if (thrown != null) {
                return Exceptions.chuck(thrown);
            }
            return this;
        }

        @Override
        public void onSubscribe(Subscription s) {
            System.out.println("onSubscribe");
            synchronized (this) {
                this.subscription = s;
            }
            s.request(batchSize);
        }

        @Override
        public synchronized void onNext(Document t) {
            documents.add(t);
            System.out.println("onNext " + documents.size() + " " + t.getInteger("item"));
            if (documents.size() >= batchSize) {
                emit();
            }
        }

        @Override
        public void onError(Throwable thrwbl) {
            thrown = thrwbl;
        }

        @Override
        public void onComplete() {
            try {
                done = true;
                System.out.println("on complete");
                emit();
            } finally {
                latch.countDown();
            }
        }

        S await() throws InterruptedException {
            latch.await(30, TimeUnit.SECONDS);
            return this;
        }

    }

    @Before
    @SuppressWarnings("ThrowableResultIgnored")
    public void before() throws Exception {
        System.setProperty("acteur.debug", "true");
        deps = new Dependencies(new MongoHarness.Module());
        harn = deps.getInstance(MongoHarness.class);
        harn.start();
        client = MongoClients.create("mongodb://localhost:" + harn.port());
        db = client.getDatabase("SubscriptionBehaviorTest");
        Obj<Throwable> thr = Obj.createAtomic();
        
        Subscribers.create().blockingCallback(db.createCollection("stuff"), (res, thrown) -> {
            thr.set(thrown);
            System.out.println("RES " + res + " " + (res == null ? "" : res.getClass().getName()) + " " + thrown);
        });
        if (thr.isSet()) {
            Exceptions.chuck(thr.get());
        }
        coll = db.getCollection("stuff");
        List<InsertOneModel<Document>> inserts = new ArrayList<>();
        for (int i = 0; i < DOC_COUNT; i++) {
            Document d = new Document("item", i);
            InsertOneModel<Document> mod = new InsertOneModel<>(d);
            inserts.add(mod);
        }
        Subscribers.create().blockingCallback(coll.bulkWrite(inserts), (res, thrown) -> {
            thr.set(thrown);
            System.out.println("RES " + res + " " + (res == null ? "" : res.getClass().getName()) + " " + thrown);

        });
        if (thr.isSet()) {
            Exceptions.chuck(thr.get());
        }

    }

    @After
    public void after() {
//        MongoHarness h = harn;
//        if (h != null) {
//            h.stop();
//        }
        if (client != null) {
            client.close();
        }
        if (deps != null) {
            deps.shutdown();
        }
    }
}
