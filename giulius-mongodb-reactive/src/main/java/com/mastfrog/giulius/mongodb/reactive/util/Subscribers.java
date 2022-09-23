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
package com.mastfrog.giulius.mongodb.reactive.util;

import com.mastfrog.util.function.EnhCompletableFuture;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Utility methods for adapting old style mongo-async-driver callbacks to the
 * reactive streams style. Also, the reactive streams driver tends to black-hole
 * exceptions or errors thrown in its callback methods, so request handling
 * drops off a cliff with no indication of what happened; the SubscriberContext
 * (which can be injected). SubscriberContext can also be used to set up
 * ThreadLocals such as the Acteur ReentrantScope state.
 *
 * @author Tim Boudreau
 */
public final class Subscribers {

    private final SubscriberContext ctx;

    /**
     * Create a Subscribers for use in tests or other static contexts; not for
     * application runtime use.
     *
     * @return A subscribers
     */
    public static Subscribers create() {
        return new Subscribers(new DefaultSubscriberContext());
    }

    @Inject
    public Subscribers(SubscriberContext ctx) {
        this.ctx = ctx;
    }

    public SubscriberContext context() {
        return ctx;
    }

    public <T> EnhCompletableFuture<T> single(Publisher<T> pub) {
        EnhCompletableFutureSubscriber<T> sub = new EnhCompletableFutureSubscriber<>(ctx);
        pub.subscribe(sub);
        return sub.future();
    }

    public <T> EnhCompletableFuture<List<T>> multiple(Publisher<T> pub) {
        List<T> result = new CopyOnWriteArrayList<>();
        EnhCompletableFutureCollectionSubscriber<T, List<T>> sub = new EnhCompletableFutureCollectionSubscriber<>(result, ctx);
        pub.subscribe(sub);
        return sub.future();
    }

    public <T> EnhCompletableFuture<Set<T>> multipleSet(Publisher<T> pub) {
        Set<T> result = ConcurrentHashMap.newKeySet();
        EnhCompletableFutureCollectionSubscriber<T, Set<T>> sub
                = new EnhCompletableFutureCollectionSubscriber<>(result, ctx);
        pub.subscribe(sub);
        return sub.future();
    }

    public <T> void callback(Publisher<T> pub, BiConsumer<T, Throwable> callback) {
        pub.subscribe(new CallbackSubscriber<>(callback, ctx));
    }

    public <T> void callback(Publisher<T> pub, Consumer<Throwable> callback) {
        pub.subscribe(new CallbackSubscriber<>((ignored, thrown) -> callback.accept(thrown), ctx));
    }

    public <T> Subscriber<T> callback(BiConsumer<T, Throwable> callback) {
        return new CallbackSubscriber<>(callback, ctx);
    }

    public <T> Subscriber<T> first(BiConsumer<T, Throwable> callback) {
        return new SingleCallbackSubscriber<>(callback, ctx);
    }

    public <T> Subscriber<T> first(Consumer<Throwable> callback) {
        return new SingleCallbackSubscriber<>((ignored, thrown) -> callback.accept(thrown), ctx);
    }

    public <T> void first(Publisher<T> pub, BiConsumer<T, Throwable> callback) {
        pub.subscribe(new SingleCallbackSubscriber<>(callback, ctx));
    }

    public <T> T blockingCallback(Publisher<T> pub, BiConsumer<T, Throwable> callback) throws InterruptedException, ExecutionException {
        EnhCompletableFuture<T> fut = single(pub);
        fut.whenComplete(callback);
        return fut.get();
    }

    public <T> EnhCompletableFuture<Void> forEach(Publisher<T> pub, Consumer<T> consumer) {
        ForEachSubscriber<T> sub = new ForEachSubscriber<>(consumer, ctx);
        pub.subscribe(sub);
        return sub.fut;
    }

    public <T> EnhCompletableFuture<Void> forEach(Publisher<T> pub, BiConsumer<T, Throwable> consumer) {
        ForEachSubscriber2<T> sub = new ForEachSubscriber2<>(consumer, ctx);
        pub.subscribe(sub);
        return sub.fut;
    }

    public <T> Subscriber<T> wrap(Subscriber<? super T> sub) {
        return new WrappedSubscriber<>(sub, ctx);
    }

    public <T> Subscriber<T> forEach(BiConsumer<T, Throwable> consumer) {
        return new ForEachSubscriber2<>(consumer, ctx);
    }

    private static class ForEachSubscriber<T> implements Subscriber<T> {

        private final Consumer<T> each;
        private final EnhCompletableFuture<Void> fut = new EnhCompletableFuture<>();

        public ForEachSubscriber(Consumer<T> each, SubscriberContext ctx) {
            this.each = ctx.wrap(each);
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T t) {
            each.accept(t);
        }

        @Override
        public void onError(Throwable thrwbl) {
            fut.completeExceptionally(thrwbl);
        }

        @Override
        public void onComplete() {
            fut.complete(null);
        }
    }

    private static class ForEachSubscriber2<T> implements Subscriber<T> {

        private final BiConsumer<T, Throwable> each;
        private final EnhCompletableFuture<Void> fut = new EnhCompletableFuture<>();

        public ForEachSubscriber2(BiConsumer<T, Throwable> each, SubscriberContext ctx) {
            this.each = ctx.wrap(each);
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T t) {
            each.accept(t, null);
        }

        @Override
        public void onError(Throwable thrwbl) {
            each.accept(null, thrwbl);
            fut.completeExceptionally(thrwbl);
        }

        @Override
        public void onComplete() {
            fut.complete(null);
        }
    }

    private static class CallbackSubscriber<T> implements Subscriber<T> {

        private final BiConsumer<T, Throwable> callback;
        T obj;
        Throwable thrown;

        CallbackSubscriber(BiConsumer<T, Throwable> callback, SubscriberContext ctx) {
            this.callback = ctx.wrap(callback);
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public synchronized void onNext(T t) {
            obj = t;
        }

        @Override
        public synchronized void onError(Throwable thrwbl) {
            thrown = thrwbl;
        }

        @Override
        public void onComplete() {
            T o;
            Throwable thr;
            synchronized (this) {
                o = obj;
                thr = thrown;
            }
            callback.accept(o, thr);
        }
    }

    private static class SingleCallbackSubscriber<T> implements Subscriber<T> {

        private final BiConsumer<T, Throwable> callback;
        T obj;
        Throwable thrown;
        Subscription sub;
        private final AtomicBoolean done = new AtomicBoolean();

        SingleCallbackSubscriber(BiConsumer<T, Throwable> callback, SubscriberContext ctx) {
            this.callback = ctx.wrap(callback);
        }

        @Override
        public void onSubscribe(Subscription s) {
            synchronized (this) {
                sub = s;
            }
            s.request(1);
        }

        @Override
        public void onNext(T t) {

            Subscription s;
            Throwable th;
            synchronized (this) {
                obj = t;
                s = sub;
                th = thrown;
            }
            if (done.compareAndSet(false, true)) {
                try {
                    callback.accept(t, th);
                } finally {
                    if (s != null) {
                        s.cancel();
                    }
                }
            }
        }

        @Override
        public void onError(Throwable thrwbl) {
            T o;
            Subscription s;
            synchronized (this) {
                thrown = thrwbl;
                s = sub;
                o = obj;
            }
            if (done.compareAndSet(false, true)) {
                try {
                    callback.accept(o, thrwbl);
                } finally {
                    if (s != null) {
                        s.cancel();
                    }
                }
            }

        }

        @Override
        public void onComplete() {
            if (done.compareAndSet(false, true)) {
                T o;
                Throwable thr;
                synchronized (this) {
                    o = obj;
                    thr = thrown;
                }
                callback.accept(o, thr);
            }
        }
    }

    static class WrappedSubscriber<T> implements Subscriber<T> {

        private final Consumer<Subscription> onSubscribe;
        private final Consumer<? super T> onNext;
        private final Consumer<Throwable> onError;
        private final Runnable onComplete;
        private final Supplier<String> toString;

        WrappedSubscriber(Subscriber<? super T> orig, SubscriberContext ctx) {
            onSubscribe = ctx.wrap(orig::onSubscribe);
            onNext = ctx.wrap(orig::onNext);
            onError = ctx.wrap(orig::onError);
            onComplete = ctx.wrap(orig::onComplete);
            toString = ctx.wrap(orig::toString);
        }

        @Override
        public String toString() {
            return "W(" + toString.get() + ")";
        }

        @Override
        public void onSubscribe(Subscription s) {
            onSubscribe.accept(s);
        }

        @Override
        public void onNext(T t) {
            onNext.accept(t);
        }

        @Override
        public void onError(Throwable thrwbl) {
            onError.accept(thrwbl);
        }

        @Override
        public void onComplete() {
            onComplete.run();
        }

    }
}
