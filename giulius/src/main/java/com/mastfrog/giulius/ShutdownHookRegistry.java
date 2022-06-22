/*
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.giulius;

import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import com.mastfrog.function.state.Obj;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.giulius.ShutdownHookRegistry.VMShutdownHookRegistry;
import static com.mastfrog.util.preconditions.Checks.notNull;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Timer;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thing you can add runnables to to be run on orderly vm shutdown (close
 * connections, etc.)
 *
 * @author Tim Boudreau
 */
@ImplementedBy(VMShutdownHookRegistry.class)
public abstract class ShutdownHookRegistry implements ShutdownHooks {

    private static final Logger LOG = Logger.getLogger(ShutdownHookRegistry.class.getName());
    private final ThrowingRunnable first = ThrowingRunnable.oneShot(true);
    private final ThrowingRunnable middle = ThrowingRunnable.oneShot(true);
    private final ThrowingRunnable last = ThrowingRunnable.oneShot(false);
    // main needs to be re-runnable and not drop first/middle/last after they run
    private final ThrowingRunnable main = ThrowingRunnable.composable(true);
    private final Set<ExecutorService> waitFor = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>()));
    // XXX may want to make this lazy and store it in an atomic, as creating 
    // thread instances has a cost; on the other hand, when are we going to
    // have an application that makes thousands of instances of 
    // ShutdownHookRegistry?
    private final ShutdownThread shutdownThread = new ShutdownThread(this);
    private long executorsWait;
    private final AtomicInteger count = new AtomicInteger();
    private volatile boolean running;
    private DeploymentMode mode = DeploymentMode.PRODUCTION;

    public ShutdownHookRegistry() {
        this(500);
    }

    public ShutdownHookRegistry(long wait) {
        this.executorsWait = Math.max(0, wait);
        main.andAlways(last);
        main.andAlways(middle);
        main.andAlways(first);
    }

    protected void install() {
        shutdownThread.register();
    }

    protected void deinstall() {
        shutdownThread.dergister();
    }

    @Override
    public void add(Runnable toRun) {
        add(toRun, Phase.MIDDLE, false);
    }

    @Override
    public ShutdownHookRegistry addFirst(Runnable toRun) {
        return add(toRun, Phase.FIRST, false);
    }

    @Override
    public ShutdownHookRegistry addLast(Runnable toRun) {
        return add(toRun, Phase.LAST, false);
    }

    @Override
    public ShutdownHookRegistry addWeak(Runnable toRun) {
        return add(toRun, Phase.MIDDLE, true);
    }

    @Override
    public ShutdownHookRegistry addFirstWeak(Runnable toRun) {
        return add(toRun, Phase.FIRST, true);
    }

    @Override
    public ShutdownHookRegistry addLastWeak(Runnable toRun) {
        return add(toRun, Phase.LAST, true);
    }

    @Override
    public ShutdownHookRegistry add(Callable<?> toRun) {
        return add(toRun, Phase.FIRST, false);
    }

    @Override
    public ShutdownHookRegistry addFirst(Callable<?> toRun) {
        return add(toRun, Phase.FIRST, false);
    }

    @Override
    public ShutdownHookRegistry addLast(Callable<?> toRun) {
        return add(toRun, Phase.LAST, false);
    }

    @Override
    public ShutdownHookRegistry addWeak(Callable<?> toRun) {
        return add(toRun, Phase.MIDDLE, true);
    }

    @Override
    public ShutdownHookRegistry addFirstWeak(Callable<?> toRun) {
        return add(toRun, Phase.FIRST, true);
    }

    @Override
    public ShutdownHookRegistry addLastWeak(Callable<?> toRun) {
        return add(toRun, Phase.LAST, true);
    }

    @Override
    public void add(Timer toRun) {
        add(toRun, Phase.MIDDLE, true);
    }

    @Override
    public ShutdownHookRegistry addFirst(Timer toRun) {
        return add(toRun, Phase.FIRST, true);
    }

    @Override
    public ShutdownHookRegistry addLast(Timer toRun) {
        return add(toRun, Phase.LAST, true);
    }

    @Override
    public void add(AutoCloseable toRun) {
        add(toRun, Phase.MIDDLE, true);
    }

    @Override
    public ShutdownHookRegistry addFirst(AutoCloseable toRun) {
        return add(toRun, Phase.FIRST, true);
    }

    @Override
    public ShutdownHookRegistry addLast(AutoCloseable toRun) {
        return add(toRun, Phase.LAST, true);
    }

    @Override
    public ShutdownHookRegistry add(ExecutorService toRun) {
        return add(toRun, Phase.MIDDLE, false);
    }

    @Override
    public ShutdownHookRegistry addFirst(ExecutorService toRun) {
        return add(toRun, Phase.FIRST, false);
    }

    @Override
    public ShutdownHookRegistry addLast(ExecutorService toRun) {
        return add(toRun, Phase.LAST, false);
    }

    @Override
    public ShutdownHooks addThrowing(ThrowingRunnable toRun) {
        middle.andAlways(toRun);
        return this;
    }

    @Override
    public ShutdownHooks addFirstThrowing(ThrowingRunnable toRun) {
        first.andAlways(toRun);
        return this;
    }

    @Override
    public ShutdownHooks addLastThrowing(ThrowingRunnable toRun) {
        last.andAlways(toRun);
        return this;
    }

    public int shutdown() {
        return runShutdownHooks();
    }

    protected synchronized int runShutdownHooks() {
        if (running) {
            LOG.log(Level.WARNING, "Attempt to reenter runShutdownHooks");
            return 0;
        }
        int result;
        try {
            Obj<Throwable> thrown = Obj.create();
            result = internalRunShutdownHooks(thrown);
            thrown.ifNotNull(th -> {
                LOG.log(Level.WARNING, "Exceptions thrown in shutdown hooks", th);
            });
        } finally {
            try {
                // If we are running an an application thread due to explicit
                // shutdown from the application
                shutdownThread.dergister();
            } catch (IllegalStateException ex) {
                // Ok, that just means we really are running in VM shutdown
            }
        }
        return result;
    }

    @SuppressWarnings("ThrowableResultIgnored")
    int internalRunShutdownHooks(Obj<Throwable> thrown) {
        running = true;
        try {
            int result = 0;
            try {
                while (count.get() > 0) {
                    result += count.get();
                    try {
                        main.run();
                    } catch (Exception | Error ex) {
                        thrown.apply(old -> {
                            if (old != null) {
                                old.addSuppressed(ex);
                                return old;
                            }
                            return ex;
                        });
                    }
                }
            } finally {
                long interval = 50;
                Timeout timeout = new Timeout(this.executorsWait);
                Set<ExecutorService> unterminated = new HashSet<>(this.waitFor);
                while (!waitFor.isEmpty()) {
                    waitFor.clear();
                    Set<ExecutorService> done = new HashSet<>();
                    while (!unterminated.isEmpty()) {
                        for (ExecutorService svc : unterminated) {
                            if (svc.isTerminated()) {
                                done.add(svc);
                                if (done.size() == unterminated.size() || timeout.isDone()) {
                                    break;
                                }
                                continue;
                            }
                            try {
                                svc.awaitTermination(interval, TimeUnit.MILLISECONDS);
                            } catch (Exception | Error ex) {
                                thrown.apply(old -> {
                                    if (old != null) {
                                        old.addSuppressed(ex);
                                        return old;
                                    }
                                    return ex;
                                });
                            }
                            if (svc.isTerminated()) {
                                done.add(svc);
                            }
                        }
                        if (timeout.isDone()) {
                            break;
                        }
                    }
                    unterminated.removeAll(done);
                    if (timeout.isDone()) {
                        break;
                    }
                }
                if (!unterminated.isEmpty()) {
                    LOG.info(() -> "Some execututors did not terminate: " + unterminated);
                }
            }
            return result;
        } finally {
            running = false;
        }
    }

    @Override
    public boolean isRunningShutdownHooks() {
        return running;
    }

    int remaining() {
        return count.get();
    }

    protected ShutdownHookRegistry add(Object toRun, Phase phase, boolean weak) {
        ThrowingRunnable target;
        switch (phase) {
            case FIRST:
                target = first;
                break;
            case MIDDLE:
                target = middle;
                break;
            case LAST:
                target = last;
                break;
            default:
                throw new AssertionError(phase);
        }
        ThrowingRunnable toAdd;
        if (weak) {
            toAdd = new WeakRun(notNull("toRun", toRun));
        } else {
            toAdd = new NormalRun(notNull("toRun", toRun));
        }
        target.andAlways(toAdd);
        count.incrementAndGet();
        return this;
    }

    final class NormalRun implements ThrowingRunnable {

        private final Object toRun;
        private volatile boolean ran;

        public NormalRun(Object toRun) {
            this.toRun = toRun;
        }

        @Override
        public void run() throws Exception {
            if (ran) {
                return;
            }
            ran = true;
            try {
                runOne(toRun);
            } finally {
                count.decrementAndGet();
            }
        }

        @Override
        public String toString() {
            return "Hook(" + toRun + ")";
        }
    }

    final class WeakRun implements ThrowingRunnable {

        private final Reference<Object> weakRun;
        private final String stringValue;
        private volatile boolean ran;

        WeakRun(Object o) {
            this.weakRun = new WeakReference<>(o);
            if (mode == DeploymentMode.DEVELOPMENT) {
                stringValue = Objects.toString(o);
            } else {
                stringValue = null;
            }
        }

        @Override
        public void run() throws Exception {
            if (ran) {
                return;
            }
            ran = true;
            try {
                Object referent = weakRun.get();
                if (referent == null) {
                    return;
                }
                runOne(referent);
            } finally {
                count.decrementAndGet();
            }
        }

        public String toString() {
            Object o = weakRun.get();
            return "WeakHook(" + (stringValue == null ? Objects.toString(o) : stringValue)
                    + ")";
        }
    }

    private void runOne(Object toRun) throws Exception {
        if (toRun instanceof ExecutorService) {
            ExecutorService svc = (ExecutorService) toRun;
            if (!svc.isShutdown()) {
                svc.shutdown();
            }
            if (!svc.isTerminated()) {
                waitFor.add(svc);
            }
        } else if (toRun instanceof Timer) {
            Timer timer = (Timer) toRun;
            timer.cancel();
        } else if (toRun instanceof Thread) {
            Thread t = (Thread) toRun;
            t.interrupt();
        } else if (toRun instanceof AutoCloseable) {
            AutoCloseable ac = (AutoCloseable) toRun;
            ac.close();
        } else if (toRun instanceof Callable<?>) {
            Callable<?> call = (Callable<?>) toRun;
            call.call();
        } else if (toRun instanceof ThrowingRunnable) {
            ThrowingRunnable tr = (ThrowingRunnable) toRun;
            tr.run();
        } else if (toRun instanceof Runnable) {
            Runnable r = (Runnable) toRun;
            r.run();
        } else if (toRun != null) {
            throw new AssertionError("I don't know how to run " + toRun);
        }
    }

    enum Phase {
        FIRST,
        MIDDLE,
        LAST
    }

    @Singleton
    static final class VMShutdownHookRegistry extends ShutdownHookRegistry implements Runnable {

        private final AtomicBoolean registered = new AtomicBoolean();

        public VMShutdownHookRegistry() {
        }

        public VMShutdownHookRegistry(long wait) {
            super(wait);
        }

        @Override
        protected ShutdownHookRegistry add(Object toRun, Phase phase, boolean weak) {
            if (!registered.compareAndSet(false, true)) {
                install();
            }
            return super.add(toRun, phase, weak);
        }

        @Override
        public void run() {
            if (registered.getAndSet(false)) {
                runShutdownHooks();
            }
        }
    }

    void setDeploymentMode(DeploymentMode mode) {
        this.mode = mode;
    }

    /**
     * Get a shutdown hook registry instance. This method is only for use in
     * things like ServletContextListeners where there is no control over
     * lifecycle. The returned instance is not a singleton.
     *
     * @return A registry of shutdown hooks.
     */
    public static ShutdownHookRegistry get() {
        VMShutdownHookRegistry result = new VMShutdownHookRegistry();
        return result;
    }

    /**
     * Get a shutdown hook registry instance. This method is only for use in
     * things like ServletContextListeners where there is no control over
     * lifecycle. The returned instance is not a singleton.
     *
     * @return A registry of shutdown hooks.
     */
    public static ShutdownHookRegistry get(int msToWait) {
        VMShutdownHookRegistry result = new VMShutdownHookRegistry(msToWait);
        return result;
    }

    void setWaitMilliseconds(long wait) {
        this.executorsWait = Math.max(0, wait);
    }

    private static final class Timeout {

        private final long at;

        Timeout(long millis) {
            at = System.currentTimeMillis() + millis;
        }

        boolean isDone() {
            return System.currentTimeMillis() > at;
        }
    }

    /**
     *
     *
     * @return
     */
    public static Optional<ShutdownHookRegistry> current() {
        for (ShutdownThread thread : REGISTERED_HOOKS) {
            Optional<ShutdownHookRegistry> result = thread.get();
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private static final Set<ShutdownThread> REGISTERED_HOOKS
            = ConcurrentHashMap.newKeySet();

    private static final class ShutdownThread extends Thread {

        private final ShutdownHookRegistry registry;
        private final AtomicBoolean registered = new AtomicBoolean();

        ShutdownThread(ShutdownHookRegistry registry) {
            this.registry = registry;
        }

        ShutdownHookRegistry registry() {
            return registry;
        }

        Optional<ShutdownHookRegistry> get() {
            if (registered.get() && getContextClassLoader() == Thread.currentThread().getContextClassLoader()) {
                return Optional.of(registry);
            }
            return Optional.empty();
        }

        boolean register() {
            boolean result = registered.compareAndSet(false, true);
            if (result) {
                REGISTERED_HOOKS.add(this);
                try {
                    Runtime.getRuntime().addShutdownHook(this);
                } catch (IllegalStateException ex) {
                    result = false;
                }
            }
            return result;
        }

        boolean dergister() {
            boolean result = registered.compareAndSet(true, false);
            if (result) {
                try {
                    Runtime.getRuntime().removeShutdownHook(this);
                } catch (IllegalStateException ex) {
                    result = false;
                }
                REGISTERED_HOOKS.remove(this);
            }
            return result;
        }

        @Override
        public void run() {
            try {
                registry.runShutdownHooks();
            } finally {
                registered.set(false);
                REGISTERED_HOOKS.remove(this);
            }
        }
    }
}
