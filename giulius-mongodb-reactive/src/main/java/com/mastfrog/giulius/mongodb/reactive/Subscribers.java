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

import com.mastfrog.util.function.EnhCompletableFuture;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Utility methods for adapting old style mongo-async-driver callbacks to the
 * reactive streams style.
 *
 * @author Tim Boudreau
 */
public final class Subscribers {

    public static <T> EnhCompletableFuture<T> single(Publisher<T> pub) {
        EnhCompletableFutureSubscriber<T> sub = new EnhCompletableFutureSubscriber<>();
        pub.subscribe(sub);
        return sub.future();
    }

    public static <T> EnhCompletableFuture<List<T>> multiple(Publisher<T> pub) {
        List<T> result = new CopyOnWriteArrayList<>();
        EnhCompletableFutureCollectionSubscriber<T, List<T>> sub = new EnhCompletableFutureCollectionSubscriber<>(result);
        pub.subscribe(sub);
        return sub.future();
    }

    public static <T> EnhCompletableFuture<Set<T>> multipleSet(Publisher<T> pub) {
        Set<T> result = ConcurrentHashMap.newKeySet();
        EnhCompletableFutureCollectionSubscriber<T, Set<T>> sub
                = new EnhCompletableFutureCollectionSubscriber<>(result);
        pub.subscribe(sub);
        return sub.future();
    }

    public static <T> void callback(Publisher<T> pub, BiConsumer<T, Throwable> callback) {
        pub.subscribe(new CallbackSubscriber<>(callback));
    }

    public static <T> void callback(Publisher<T> pub, Consumer<Throwable> callback) {
        pub.subscribe(new CallbackSubscriber<>((ignored, thrown) -> callback.accept(thrown)));
    }

    public static <T> Subscriber<T> callback(BiConsumer<T, Throwable> callback) {
        return new CallbackSubscriber<>(callback);
    }

    public static <T> Subscriber<T> first(BiConsumer<T, Throwable> callback) {
        return new SingleCallbackSubscriber<>(callback);
    }

    public static <T> Subscriber<T> first(Consumer<Throwable> callback) {
        return new SingleCallbackSubscriber<>((ignored, thrown) -> callback.accept(thrown));
    }

    public static <T> void first(Publisher<T> pub, BiConsumer<T, Throwable> callback) {
        pub.subscribe(new SingleCallbackSubscriber<>(callback));
    }

    public static <T> T blockingCallback(Publisher<T> pub, BiConsumer<T, Throwable> callback) throws InterruptedException, ExecutionException {
        EnhCompletableFuture<T> fut = single(pub);
        fut.whenComplete(callback);
        return fut.get();
    }

    public static <T> EnhCompletableFuture<Void> forEach(Publisher<T> pub, Consumer<T> consumer) {
        ForEachSubscriber<T> sub = new ForEachSubscriber<>(consumer);
        pub.subscribe(sub);
        return sub.fut;
    }

    public static <T> EnhCompletableFuture<Void> forEach(Publisher<T> pub, BiConsumer<T, Throwable> consumer) {
        ForEachSubscriber2<T> sub = new ForEachSubscriber2<>(consumer);
        pub.subscribe(sub);
        return sub.fut;
    }

    public static <T> Subscriber<T> forEach(BiConsumer<T, Throwable> consumer) {
        return new ForEachSubscriber2<>(consumer);
    }

    private static class ForEachSubscriber<T> implements Subscriber<T> {

        private final Consumer<T> each;
        private final EnhCompletableFuture<Void> fut = new EnhCompletableFuture<>();

        public ForEachSubscriber(Consumer<T> each) {
            this.each = each;
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

        public ForEachSubscriber2(BiConsumer<T, Throwable> each) {
            this.each = each;
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

        CallbackSubscriber(BiConsumer<T, Throwable> callback) {
            this.callback = callback;
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

        SingleCallbackSubscriber(BiConsumer<T, Throwable> callback) {
            this.callback = callback;
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

}
