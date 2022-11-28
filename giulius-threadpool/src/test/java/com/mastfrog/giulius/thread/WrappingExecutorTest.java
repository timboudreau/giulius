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
package com.mastfrog.giulius.thread;

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.mastfrog.function.state.Obj;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.thread.wrap.ExecutionWrapper;
import com.mastfrog.giulius.thread.wrap.GranularExecutionWrapper;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Tim Boudreau
 */
public class WrappingExecutorTest {

    private static final ThreadLocal<String> LOC = new ThreadLocal<>();
    private static final Key<ExecutorService> stuffKey = Key.get(ExecutorService.class, Names.named("stuff"));
    private Dependencies deps;
    private ExecutorService stuff;
    private TestExeWrapper exeWrap;

    @Test
    public void testPropagation() throws Throwable {
        LOC.set("hey");
        Obj<String> val = Obj.createAtomic();
        Obj<String> val2 = Obj.createAtomic();
        CountDownLatch latch = new CountDownLatch(2);
        stuff.submit(() -> {
            val.set(LOC.get());
            LOC.set("whee");
            stuff.submit(() -> {
                val2.set(LOC.get());
                latch.countDown();
            });
            latch.countDown();
        });
        latch.await(10, TimeUnit.SECONDS);
        assertEquals("hey", val.get());
        assertEquals("whee", val2.get());
        assertEquals("hey", LOC.get());
        exeWrap.assertSubmits(2)
                .assertBeforeRuns(2)
                .assertAfterRuns(2)
                .rethrow();
    }

    @Before
    public void before() throws IOException {
        exeWrap = new TestExeWrapper();
        ThreadModule tm = new ThreadModule();
        tm.builder("stuff")
                .withExplicitThreadCount(1)
                .withDefaultThreadCount(1)
                .withThreadPoolType(ThreadPoolType.STANDARD)
                .withUncaughtExceptionHandler((Thread t, Throwable e) -> {
                    e.printStackTrace();
                })
                .propagatingThreadLocal(LOC)
                .wrappingSubmissionsWith(exeWrap)
                .daemon()
                .bind();
        deps = new Dependencies(Settings.builder().build(), tm);
        stuff = deps.getInstance(stuffKey);
    }

    @After
    public void after() {
        if (deps != null) {
            deps.shutdown();
        }
    }

    static class TestExeWrapper implements GranularExecutionWrapper<String, String> {

        private final AtomicInteger submits = new AtomicInteger();
        private final AtomicInteger beforeRuns = new AtomicInteger();
        private final AtomicInteger afterRuns = new AtomicInteger();
        private final CopyOnWriteArrayList<Throwable> throwns = new CopyOnWriteArrayList<>();

        public TestExeWrapper assertSubmits(int count) {
            assertEquals("Wrong number of calls to onSubmit", count, submits.get());
            return this;
        }

        public TestExeWrapper assertBeforeRuns(int count) {
            assertEquals("Wrong number of calls to onBeforeRun", count, beforeRuns.get());
            return this;
        }

        public TestExeWrapper assertAfterRuns(int count) {
            assertEquals("Wrong number of calls to onAfterRun", count, afterRuns.get());
            return this;
        }

        public TestExeWrapper rethrow() throws Exception {
            if (!throwns.isEmpty()) {
                Exception ex = new Exception("Exceptions were thrown");
                throwns.forEach(ex::addSuppressed);
                throwns.clear();
                throw ex;
            }
            return this;
        }

        @Override
        public String onSubmit() {
            submits.incrementAndGet();
            return "submitting";
        }

        @Override
        public String onBeforeRun(String t) {
            beforeRuns.incrementAndGet();
            assertEquals("submitting", t);
            return "beforeRun";
        }

        @Override
        public boolean onAfterRun(String fromSubmit, String fromRun, Throwable thrown) {
            afterRuns.incrementAndGet();
            if (thrown != null) {
                throwns.add(thrown);
            }
            assertEquals("submitting", fromSubmit);
            assertEquals("beforeRun", fromRun);
            assertNull(thrown);
            return true;
        }
    }
}
