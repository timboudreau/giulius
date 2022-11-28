/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.giulius.thread.util;

import com.google.inject.Inject;
import com.mastfrog.settings.Settings;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import static com.mastfrog.util.preconditions.Checks.nonNegative;
import static com.mastfrog.util.preconditions.Checks.nonZero;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Reschedulables - wrappers for jobs which are submitted to a
 * background thread pool to run after some delay. Call the touch() method on a
 * Reschedulable to enqueue it to run after the set delay. Depending on the
 * policy for the individual Reschedulable, a subsequent call to touch() may
 * cause it to be delayed further, or have no effect. Reschedulables can be
 * configured with a maximum elapsed interval since last run, which allows a
 * flurry of calls to touch() to delay the job up to some maximum interval, but
 * guarantee that it does get run.
 * <p>
 * Useful for anything that needs to do some sort of expensive write operation
 * where there is a benefit to batching work in larger chunks, but ensuring a
 * maximum latency between writes; or for scheduling work that should only run
 * when the system is quiet.
 *
 * @author Tim Boudreau
 */
public class Reschedulables {

    public static final String SETTINGS_KEY_RESCHEDULABLES_THREADS = "reschedulables.threads";
    private static final ReschedulePolicy SIMPLE_DELAY = (DelayQueue<ReschedulableImpl> queue, Info info, ReschedulableImpl delayed) -> {
        if (!info.isEnqueued) {
            info.touchAndEnqueue();
            queue.offer(delayed);
        }
    };

    private static final ReschedulePolicy RESET_DELAY = (DelayQueue<ReschedulableImpl> queue, Info info, ReschedulableImpl delayed) -> {
        info.touchAndEnqueue();
        if (!queue.contains(delayed)) {
            queue.offer(delayed);
        }
    };

    private static final ReschedulePolicy RESET_DELAY_LAST_RUN_MAX = new ReschedulePolicy() {
        @Override
        public void onTouch(DelayQueue<ReschedulableImpl> queue, Info info, ReschedulableImpl delayed) {
            info.touchAndEnqueue();
            if (!queue.contains(delayed)) {
                queue.offer(delayed);
            }
        }

        @Override
        public long getDelay(Info info) {
            return info.millisUntilNextRun(true);
        }

    };

    private final ExecutorService threadPool;
    private final DelayQueue<ReschedulableImpl> queue = new DelayQueue<>();
    private static int pullerThreadIndex;

    @Inject
    public Reschedulables(Settings settings, ShutdownHookRegistry reg, UncaughtExceptionHandler onError) {
        this(settings.getInt(SETTINGS_KEY_RESCHEDULABLES_THREADS, 2), reg, onError);
    }

    private Reschedulables(int count, ShutdownHookRegistry reg, UncaughtExceptionHandler onError) {
        this(Executors.newFixedThreadPool(count), count, onError);
        reg.add(threadPool);
    }

    public Reschedulables(ExecutorService threadPool, int threads, UncaughtExceptionHandler onError) {
        this.threadPool = notNull("threadPool", threadPool);
        notNull("onError", onError);
        for (int i = 0; i < nonNegative("threads", nonZero("threads", threads)); i++) {
            threadPool.submit(new Puller(onError, queue, ++pullerThreadIndex));
        }
    }

    /**
     * Build a Reschedulable whose touch() method simply schedules it on a
     * delay, and subsequent calls to touch() do not change when it runs until
     * it has been run.
     *
     * @param delay The delay after which it should run
     * @param runnable The thing to run
     * @return A reschedulable
     */
    public Reschedulable withSimpleDelay(Duration delay, Runnable runnable) {
        return withSimpleDelay(delay, new CallableForRunnable(runnable));
    }

    /**
     * Build a Reschedulable whose touch() method simply schedules it on a
     * delay, and subsequent calls to touch() do not change when it runs until
     * it has been run.
     *
     * @param delay The delay after which it should run
     * @param callable The thing to run
     * @return A reschedulable
     */
    public Reschedulable withSimpleDelay(Duration delay, Callable<?> callable) {
        return new ReschedulableImpl(callable, delay.toMillis(), SIMPLE_DELAY, queue, null);
    }

    /**
     * Build a Reschedulable whose touch() method simply schedules it on a
     * delay, and each subsequent call to touch() resets that delay to current
     * timestamp + delay.
     *
     * @param delay The delay after which it should run
     * @param runnable The thing to run
     * @return A reschedulable
     */
    public Reschedulable withResettingDelay(Duration delay, Runnable runnable) {
        return withResettingDelay(delay, new CallableForRunnable(runnable));
    }

    /**
     * Build a Reschedulable whose touch() method simply schedules it on a
     * delay, and each subsequent call to touch() resets that delay to current
     * timestamp + delay.
     *
     * @param delay The delay after which it should run
     * @param callable The thing to run
     * @return A reschedulable
     */
    public Reschedulable withResettingDelay(Duration delay, Callable<?> callable) {
        return new ReschedulableImpl(callable, delay.toMillis(), RESET_DELAY, queue, null);
    }

    /**
     * Build a Reschedulable whose touch() method simply schedules it on a
     * delay; subsequent calls to touch() do not delay; but under no
     * circumstances, following a call to touch(Duration) will it run more than
     * maxElapsed time in the future.
     *
     * @param delay The delay after which it should run
     * @param runnable The thing to run
     * @param maxElapsed
     * @return A reschedulable
     */
    public Reschedulable withSimpleDelayAndMaximum(Duration delay, Runnable runnable, Duration maxElapsed) {
        return withSimpleDelayAndMaximum(delay, new CallableForRunnable(runnable), maxElapsed);
    }

    /**
     * Build a Reschedulable whose touch() method simply schedules it on a
     * delay; subsequent calls to touch() do not delay; but under no
     * circumstances, following a call to touch(Duration) will it run more than
     * maxElapsed time in the future.
     *
     * @param delay The delay after which it should run
     * @param callable The thing to run
     * @param maxElapsed
     * @return A reschedulable
     */
    public Reschedulable withSimpleDelayAndMaximum(Duration delay, Callable<?> callable, Duration maxElapsed) {
        return new ReschedulableImpl(callable, delay.toMillis(), SIMPLE_DELAY, queue, maxElapsed);
    }

    /**
     * Build a Reschedulable whose touch() method simply schedules it on a
     * delay, and each subsequent call to touch() resets that delay to current
     * timestamp + delay; but subsequent calls to touch() cannot delay execution
     * more than <code>maxElapsed</code> time after the first call to touch()
     * following a run.
     * <p>
     * This gets you a reschedulable which can be pushed forward into the future
     * by more calls to touch(), but only so far, so if you have a Reschedulable
     * that is being bombarded with calls to touch(), that cannot cause it never
     * to run at all.
     *
     * @param delay The delay after which it should run
     * @param runnable The thing to run
     * @return A reschedulable
     */
    public Reschedulable withResettingDelayAndMaximumSinceFirstTouch(Duration delay, Runnable runnable, Duration maxElapsed) {
        return withResettingDelayAndMaximumSinceFirstTouch(delay, new CallableForRunnable(runnable), maxElapsed);
    }

    /**
     * Build a Reschedulable whose touch() method simply schedules it on a
     * delay, and each subsequent call to touch() resets that delay to current
     * timestamp + delay; but subsequent calls to touch() cannot delay execution
     * more than <code>maxElapsed</code> time after the first call to touch()
     * following a run.
     * <p>
     * This gets you a reschedulable which can be pushed forward into the future
     * by more calls to touch(), but only so far, so if you have a Reschedulable
     * that is being bombarded with calls to touch(), that cannot cause it never
     * to run at all.
     *
     * @param delay The delay after which it should run
     * @param callable The thing to run
     * @return A reschedulable
     */
    public Reschedulable withResettingDelayAndMaximumSinceFirstTouch(Duration delay, Callable<?> callable, Duration maxElapsed) {
        return new ReschedulableImpl(callable, delay.toMillis(), RESET_DELAY, queue, maxElapsed);
    }

    /**
     * Similar to <code>withResettingDelayAndMaximumSinceFirstTouch()</code>,
     * but bases the maximum elapsed interval on the last time this Resettable
     * was <i>run</i>, not the first time touch() was called after a run.
     * <p>
     * This gets you a resettable that will run, at worst, every maxElapsed time
     * interval if there is anything for it to do - for example, if you were
     * buffering log records but wanted to be sure anything cached was written
     * out within five seconds - writing something immediately if it has been
     * more than five seconds since the last run, this does that.
     *
     * @param delay The delay between runs
     * @param runnable The thing to run
     * @param maxElapsed The maximum time that can elapse between a run and the
     * next run, assuming touch() is called in the intervening interval
     * @return A reschedulable
     */
    public Reschedulable withResettingDelayAndMaximumSinceLastRun(Duration delay, Runnable runnable, Duration maxElapsed) {
        return withResettingDelayAndMaximumSinceFirstTouch(delay, new CallableForRunnable(runnable), maxElapsed);
    }

    /**
     * Similar to <code>withResettingDelayAndMaximumSinceFirstTouch()</code>,
     * but bases the maximum elapsed interval on the last time this Resettable
     * was <i>run</i>, not the first time touch() was called after a run.
     * <p>
     * This gets you a resettable that will run, at worst, every maxElapsed time
     * interval if there is anything for it to do - for example, if you were
     * buffering log records but wanted to be sure anything cached was written
     * out within five seconds - writing something immediately if it has been
     * more than five seconds since the last run, this does that.
     *
     * @param delay The delay between runs
     * @param callable The thing to run
     * @param maxElapsed The maximum time that can elapse between a run and the
     * next run, assuming touch() is called in the intervening interval
     * @return A reschedulable
     */
    public Reschedulable withResettingDelayAndMaximumSinceLastRun(Duration delay, Callable<?> callable, Duration maxElapsed) {
        return new ReschedulableImpl(callable, delay.toMillis(), RESET_DELAY_LAST_RUN_MAX, queue, maxElapsed);
    }

    class Puller implements Runnable {

        private final Thread.UncaughtExceptionHandler handler;
        private final DelayQueue<ReschedulableImpl> callables;
        private final int index;

        public Puller(Thread.UncaughtExceptionHandler handler, DelayQueue<ReschedulableImpl> callables, int index) {
            this.handler = handler;
            this.callables = callables;
            this.index = index;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("Reschedulables-" + index);
            List<ReschedulableImpl> pulled = new LinkedList<>();
            boolean done = false;
            for (;;) {
                ReschedulableImpl r = null;
                try {
                    r = callables.take();
                } catch (InterruptedException ex) {
                    if (threadPool.isShutdown()) {
                        done = true;
                    } else {
                        handler.uncaughtException(Thread.currentThread(), ex);
                    }
                }
                if (r != null) {
                    pulled.add(r);
                }
                callables.drainTo(pulled);
                try {
                    for (ReschedulableImpl toRun : pulled) {
                        try {
                            toRun.run();
                        } catch (InterruptedException inter) {
                            if (threadPool.isShutdown()) {
                                done = true;
                            } else {
                                handler.uncaughtException(Thread.currentThread(), inter);
                            }
                        } catch (Exception ex) {
                            handler.uncaughtException(Thread.currentThread(), ex);
                        }
                    }
                } finally {
                    pulled.clear();
                }
                if (done) {
                    break;
                }
            }
        }
    }

    static class ReschedulableImpl implements Reschedulable, Callable<Void> {

        final Info info;
        final Callable<?> job;
        final ReschedulePolicy policy;
        final DelayQueue<ReschedulableImpl> queue;

        ReschedulableImpl(final Callable<?> job, long defaultDelay, ReschedulePolicy policy, DelayQueue<ReschedulableImpl> queue, Duration maximumDelay) {
            info = new Info(defaultDelay, maximumDelay == null ? Long.MAX_VALUE : maximumDelay.toMillis());
            this.policy = policy;
            this.queue = queue;
            this.job = job;
        }

        public String toString() {
            return "Reschedulable(" + job + ", " + info + ")";
        }

        void run() throws Exception {
            info.run(this);
        }

        @Override
        public void touch() {
            policy.onTouch(queue, info, this);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(policy.getDelay(info), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            long mine = getDelay(TimeUnit.MILLISECONDS);
            long theirs = o.getDelay(TimeUnit.MILLISECONDS);
            return mine == theirs ? 0 : mine > theirs ? 1 : -1;
        }

        @Override
        public void cancel() {
            queue.remove(job);
            info.cancelled();
        }

        @Override
        public void touch(Duration temporaryDelay) {
            info.updateCurrentDelay(temporaryDelay.toMillis());
            policy.onTouch(queue, info, this);
        }

        @Override
        public Void call() throws Exception {
            job.call();
            return null;
        }
    }

    static class Info {

        final AtomicLong lastTouch = new AtomicLong(System.currentTimeMillis() + ((Long.MAX_VALUE - System.currentTimeMillis()) / 2));
        final AtomicLong lastRunStart = new AtomicLong(System.currentTimeMillis());
        final long defaultDelay;
        final AtomicLong currentDelay = new AtomicLong(-1);
        volatile boolean isEnqueued;
        final AtomicInteger runCount = new AtomicInteger();
        private volatile boolean isRunning;
        final long maximumElapsed;
        final AtomicLong firstTouchSinceLastRun = new AtomicLong();

        Info(long defaultDelay) {
            this.defaultDelay = defaultDelay;
            maximumElapsed = Long.MAX_VALUE;
        }

        Info(Duration defaultDelay) {
            this(defaultDelay.toMillis());
        }

        Info(long defaultDelay, long maximumDelay) {
            if (maximumDelay != Long.MAX_VALUE && maximumDelay < defaultDelay) {
                throw new IllegalArgumentException("Maximum delay is less than default - maximum will always be used");
            }
            this.defaultDelay = defaultDelay;
            this.maximumElapsed = maximumDelay;
        }

        Info(Duration defaultDelay, Duration maximumDelay) {
            this(defaultDelay.toMillis(), maximumDelay == null ? Long.MAX_VALUE : maximumDelay.toMillis());
        }

        @Override
        public String toString() {
            return "lastTouch=" + lastTouch.get() + ", lastRunStart=" + lastRunStart.get()
                    + ", defaultDelay=" + defaultDelay + ", currentDelay=" + currentDelay.get()
                    + ", isEnqueued=" + isEnqueued + ", runCount=" + runCount.get()
                    + ", isRunning=" + isRunning + ", maximumElapsed=" + maximumElapsed;
        }

        long created = System.currentTimeMillis();

        void touched() {
            if (!isEnqueued) {
                firstTouchSinceLastRun.set(System.currentTimeMillis());
            }
            lastTouch.set(System.currentTimeMillis());
        }

        void enqueued() {
            isEnqueued = true;
        }

        void touchAndEnqueue() {
            touched();
            enqueued();
        }

        void cancelled() {
            isEnqueued = false;
        }

        void updateCurrentDelay(long delay) {
            if (delay > 0) {
                currentDelay.set(delay);
            }
        }

        void clearCurrentDelay() {
            currentDelay.set(-1);
        }

        void run(Callable<?> call) throws Exception {
            if (isRunning || !isEnqueued) {
                return;
            }
            lastRunStart.set(System.currentTimeMillis());
            isEnqueued = false;
            isRunning = true;
            clearCurrentDelay();
            try {
                call.call();
            } finally {
                runCount.getAndIncrement();
                isRunning = false;
            }
        }

        long millisSinceFirstTouchAfterLastRun() {
            return Math.max(0L, System.currentTimeMillis() - firstTouchSinceLastRun.get());
        }

        long millisSinceLastRun() {
            return Math.max(0L, System.currentTimeMillis() - lastRunStart.get());
        }

        long delay() {
            long delay = currentDelay.get();
            if (delay == -1) {
                delay = defaultDelay;
            }
            return delay;
        }

        long millisUntilNextRun(boolean useLastRun) {
            long result = Math.max(0L, (lastTouch.get() + delay()) - System.currentTimeMillis());
            if (result > 0 && maximumElapsed != Long.MAX_VALUE) {
                long targetTime = (useLastRun ? lastRunStart : firstTouchSinceLastRun).get() + maximumElapsed;
                long altResult = Math.max(0L, targetTime - System.currentTimeMillis());
                return Math.min(result, altResult);
            }
            return result;
        }
    }

    private static interface ReschedulePolicy {

        void onTouch(DelayQueue<ReschedulableImpl> queue, Info info, ReschedulableImpl delayed);

        default long getDelay(Info info) {
            return info.millisUntilNextRun(false);
        }
    }

    static final class CallableForRunnable implements Callable<Void> {

        private final Runnable run;

        public CallableForRunnable(Runnable run) {
            this.run = run;
        }

        @Override
        public Void call() throws Exception {
            run.run();
            return null;
        }

        public String toString() {
            return "c4r(" + run + ")";
        }
    }
}
