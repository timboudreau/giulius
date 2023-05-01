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

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.state.Obj;
import com.mastfrog.util.preconditions.Exceptions;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractSubscriberContextTest {

    public static boolean quietly(Class<? extends Throwable> expected, Runnable run) {
        try {
            run.run();
            return false;
        } catch (Exception | Error ex) {
            if (!expected.isInstance(ex)) {
                Exceptions.chuck(ex);
            }
            return true;
        }
    }

    public Runnable additionalRunnable() {
        return () -> {
        };
    }

    public void doTestThrownPropagatedForRunnable() {
        UN un = new UN();
        SubscriberContext ctx = createContext(un);
        Bool ran = Bool.create();
        Runnable raw = () -> {
            ran.set();
            if (checksUncaughtHandler()) {
                assertSame("Wrong exception handler", un, Thread.currentThread().getUncaughtExceptionHandler());
            }
            throw new ThrowMe();
        };
        Runnable wrapped = ctx.wrap(raw);
        quietly(ThrowMe.class, wrapped);
        assertTrue("Did not run", ran.getAsBoolean());
        Throwable t = un.assertThrown();
        assertNotNull(t);
        assertTrue(t instanceof ThrowMe);
    }

    public void doTestThrownPropagatedForConsumer() {
        UN un = new UN();
        SubscriberContext ctx = createContext(un);
        Bool ran = Bool.create();
        Consumer<String> raw = str -> {
            ran.set();
            assertEquals("foo", str);
            if (checksUncaughtHandler()) {
                assertSame("Wrong exception handler", un, Thread.currentThread().getUncaughtExceptionHandler());
            }
            throw new ThrowMe();
        };
        Consumer<String> wrapped = ctx.wrap(raw);
        quietly(ThrowMe.class, () -> wrapped.accept("foo"));
        assertTrue("Did not run", ran.getAsBoolean());
        Throwable t = un.assertThrown();
        assertNotNull("Throwable not caught", t);
        assertTrue(t instanceof ThrowMe);
    }

    public void doTestThrownPropagatedForBiConsumer() {
        UN un = new UN();
        SubscriberContext ctx = createContext(un);
        Bool ran = Bool.create();
        BiConsumer<String, String> raw = (a, b) -> {
            ran.set();
            assertEquals("bar", a);
            assertEquals("baz", a);
            if (checksUncaughtHandler()) {
                assertSame("Wrong exception handler", un, Thread.currentThread().getUncaughtExceptionHandler());
            }
            throw new ThrowMe();
        };
        BiConsumer<String, String> wrapped = ctx.wrap(raw);
        quietly(ThrowMe.class, () -> wrapped.accept("bar", "baz"));
        assertTrue("Did not run", ran.getAsBoolean());
        Throwable t = un.assertThrown();
        assertNotNull(t);
        assertTrue(t instanceof ThrowMe);
    }

    public void doTestRunnablesRun() {
        UN un = new UN();
        SubscriberContext ctx = createContext(un);
        Obj<Runnable> wrapped = Obj.create();
        Bool ran = Bool.create();
        Runnable addtl = additionalRunnable();
        Runnable raw = () -> {
            ran.set();
            if (checksUncaughtHandler()) {
                assertSame("Wrong handler.", un, Thread.currentThread().getUncaughtExceptionHandler());
            }
            addtl.run();
        };
        wrapped.set(ctx.wrap(raw));
        assertTrue(wrapped.isSet());
        wrapped.get().run();
        assertTrue("Lambda did not run.", ran.getAsBoolean());
        un.rethrow();
    }

    public Consumer<String> additionalConsumer() {
        return str -> {
        };
    }

    public void doTestConsumersRun() {
        UN un = new UN();
        SubscriberContext ctx = createContext(un);
        Bool ran = Bool.create();
        Consumer<String> addtl = additionalConsumer();
        Consumer<String> raw = str -> {
            ran.set();
            assertEquals("Wrong argument", "foo", str);
            if (checksUncaughtHandler()) {
                assertSame("Wrong handler.", un, Thread.currentThread().getUncaughtExceptionHandler());
            }
            addtl.accept(str);
        };
        Consumer<String> wrapped = ctx.wrap(raw);
        wrapped.accept("foo");
        assertTrue("Lambda did not run.", ran.getAsBoolean());
        un.rethrow();
    }

    public BiConsumer<String, String> additionalBiConsumer() {
        return (a, b) -> {
        };
    }

    public boolean checksUncaughtHandler() {
        return true;
    }

    public void doTestBiConsumersRun() {
        UN un = new UN();
        SubscriberContext ctx = createContext(un);
        Bool ran = Bool.create();
        BiConsumer<String, String> addtl = additionalBiConsumer();
        BiConsumer<String, String> raw = (a, b) -> {
            ran.set();
            assertEquals("Wrong first argument", "bar", a);
            assertEquals("Wrong second argument", "baz", b);
            if (checksUncaughtHandler()) {
                assertSame("Wrong handler.", un, Thread.currentThread().getUncaughtExceptionHandler());
            }
            addtl.accept(a, b);
        };
        BiConsumer<String, String> wrapped = ctx.wrap(raw);
        wrapped.accept("bar", "baz");
        assertTrue("Lambda did not run.", ran.getAsBoolean());
        un.rethrow();
    }

    public SubscriberContext createContext(UN un) {
        Obj<SubscriberContext> obj = Obj.create();
        withUn(un, () -> {
            obj.set(create());
        });
        assertTrue(obj.isSet());
        return obj.get();
    }

    public SubscriberContext create() {
        DefaultSubscriberContext result = new DefaultSubscriberContext();
        assertSame(Thread.currentThread().getUncaughtExceptionHandler(), result.uncaught);
        assertTrue(result.uncaught instanceof UN);
        return result;
    }

    public void withUn(UN un, Runnable run) {
        Thread.UncaughtExceptionHandler old = Thread.currentThread().getUncaughtExceptionHandler();
        try {
            Thread.currentThread().setUncaughtExceptionHandler(un);
            assertSame("Uncaught handler not changed", un, Thread.currentThread().getUncaughtExceptionHandler());
            run.run();
        } finally {
            Thread.currentThread().setUncaughtExceptionHandler(old);
        }
    }

    public static class UN implements Thread.UncaughtExceptionHandler {

        Throwable thrown;

        public synchronized Throwable assertThrown() {
            Throwable t = thrown;
            thrown = null;
            return t;
        }

        public synchronized void rethrow() {
            Throwable t = thrown;
            thrown = null;
            if (t != null) {
                Exceptions.chuck(t);
            }
        }

        @Override
        public synchronized void uncaughtException(Thread t, Throwable e) {
            if (thrown != null) {
                thrown.addSuppressed(e);
            } else {
                thrown = e;
            }
        }
    }

    public static class ThrowMe extends RuntimeException {

    }
}
