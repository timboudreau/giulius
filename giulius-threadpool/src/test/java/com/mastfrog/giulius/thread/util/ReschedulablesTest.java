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

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ReschedulablesTest {

    private ExecutorService exe;
    private Reschedulables rs;
    private Uncaught uncaught;

    @Test
    public void testSimpleDelay() throws Throwable {
        Runner run = new Runner();
        Reschedulable r = rs.withSimpleDelay(Duration.ofMillis(100), run);
        run.assertWasNotRun();
        Thread.sleep(150);
        run.assertWasNotRun();
        long now = System.currentTimeMillis();
        r.touch();
        Thread.sleep(150);
        run.assertWasRun();
        run.assertRunCount(1);
        uncaught.assertNotThrown();
        run.assertTimestamp(now, 100, 20);
    }

    @Test
    public void testTemporaryDelay() throws Throwable {
        Runner run = new Runner();
        Reschedulable r = rs.withSimpleDelay(Duration.ofMillis(100), run);
        run.assertWasNotRun();
        Thread.sleep(150);
        run.assertWasNotRun();
        long now = System.currentTimeMillis();
        r.touch(Duration.ofMillis(200));
        Thread.sleep(150);
        run.assertWasNotRun();
        Thread.sleep(60);
        run.assertWasRun();
        run.assertRunCount(1);
        uncaught.assertNotThrown();
        run.assertTimestamp(now, 200, 20);
    }

    @Test
    public void testResettingDelay() throws Throwable {
        Runner run = new Runner();
        Reschedulable r = rs.withResettingDelay(Duration.ofMillis(100), run);
        run.assertWasNotRun();
        r.touch();
        Thread.sleep(40);
        long now = System.currentTimeMillis();
        r.touch();
        Thread.sleep(150);
        run.assertWasRun();
        run.assertRunCount(1);
        uncaught.assertNotThrown();
        run.assertTimestamp(now, 100, 20);
    }

    public void testResettingWithMaximumDelay() throws Throwable {
        Runner run = new Runner();
        Reschedulable r = rs.withResettingDelayAndMaximumSinceFirstTouch(Duration.ofMillis(100), run, Duration.ofMillis(200));
        run.assertWasNotRun();
        r.touch();
        Thread.sleep(40);
        long now = System.currentTimeMillis();
        r.touch();
        Thread.sleep(120);
        run.assertWasRun();
        run.assertRunCount(1);
        uncaught.assertNotThrown();
        run.assertTimestamp(now, 100, 20);
        now = System.currentTimeMillis();
        r.touch();
        Thread.sleep(60);
        run.assertWasNotRun();
        r.touch();
        Thread.sleep(60);
        run.assertWasNotRun();
        r.touch();
        Thread.sleep(60);
        run.assertWasNotRun();
        r.touch();
        Thread.sleep(60);
        run.assertWasRun();
    }

    @Test
    public void testSettingDelay() throws Throwable {
        Runner run = new Runner();
        Reschedulable r = rs.withSimpleDelay(Duration.ofMillis(100), run);
        run.assertWasNotRun();
        long now = System.currentTimeMillis();
        r.touch(Duration.ofMillis(200));
        Thread.sleep(120);
        run.assertWasNotRun();
        Thread.sleep(100);

        run.assertWasRun();
        run.assertRunCount(1);
        uncaught.assertNotThrown();
        run.assertTimestamp(now, 200, 20);
    }

    static class Runner implements Runnable {
        AtomicInteger runCount = new AtomicInteger();
        long lastRun = 0;

        void assertTimestamp(long when, long offset, long allowedSchedulerSkew) {
            long lastRun = this.lastRun;
            this.lastRun = 0;
            long skew = (when + offset) - lastRun;
            assertTrue("Schedule off by " + skew + " not within tolerance of " + allowedSchedulerSkew, Math.abs(skew) <= allowedSchedulerSkew);
        }

        void assertWasNotRun() {
            assertTrue(runCount.get() == 0);
        }

        void assertWasRun() {
            assertTrue("RunCount is " + runCount.get(), runCount.get() > 0);
        }

        void assertRunCount(int ct) {
            int val = runCount.getAndSet(0);
            assertEquals(ct, val);
        }

        @Override
        public void run() {
            lastRun = System.currentTimeMillis();
            runCount.incrementAndGet();
        }
    }

    @Before
    public void startup() {
        exe = Executors.newFixedThreadPool(3);
        rs = new Reschedulables(exe, 3, uncaught = new Uncaught());
    }

    @After
    public void shutdown() {
        exe.shutdownNow();
    }

    private static final class Uncaught implements Thread.UncaughtExceptionHandler {

        private Throwable thrown;

        void assertNotThrown() throws Throwable {
            Throwable th = thrown;
            thrown = null;
            if (th != null) {
                throw th;
            }
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            e.printStackTrace();
            thrown = e;
        }
    }
}
