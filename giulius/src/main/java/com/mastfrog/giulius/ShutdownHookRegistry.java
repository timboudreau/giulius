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
import com.mastfrog.giulius.ShutdownHookRegistry.VMShutdownHookRegistry;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thing you can add runnables to to be run on orderly vm shutdown (close
 * connections, etc.)
 *
 * @author Tim Boudreau
 */
@ImplementedBy(VMShutdownHookRegistry.class)
public abstract class ShutdownHookRegistry {

    private final List<Runnable> hooks = Collections.synchronizedList(new ArrayList<>(10));
    private DeploymentMode mode = DeploymentMode.PRODUCTION;

    protected ShutdownHookRegistry() {
    }

    /**
     * Add a runnable which should run on VM shutdown or logical shutdown of a
     * subsystem.
     *
     * @see com.mastfrog.guicy.Dependencies.shutdown
     * @param runnable A runnable
     */
    public void add(Runnable runnable) {
        if (!(runnable instanceof Marker)) {
            runnable = new RunnableWrapper(runnable, mode);
        }
        hooks.add(runnable);
    }

    public final void add(AutoCloseable toClose) {
        add(new ShutdownAutoCloseable(toClose, mode));
    }

    public final void add(Callable<?> toCall) {
        add(new ShutdownCallable(toCall, mode));
    }

    public final ShutdownHookRegistry add(ExecutorService svc) {
        add(new ShutdownExecutorService(notNull("svc", svc), mode));
        return this;
    }

    private volatile boolean running;

    public boolean isRunningShutdownHooks() {
        return running;
    }

    protected void runShutdownHooks() {
        if (running) {
            return;
        }
        boolean debug = Boolean.getBoolean("giulius.debug");
        running = true;
        if (debug) {
            System.err.println("Run " + hooks.size() + " for shutdown.");;
        }
        while (!hooks.isEmpty()) {
            try {
                Runnable[] result = hooks.toArray(new Runnable[hooks.size()]);
                for (int i = result.length - 1; i >= 0; i--) {
                    Runnable result1 = result[i];
                    if (debug) {
                        System.out.println("RUN SHUTDOWN HOOK " + result1);
                    }
                    try {
                        result1.run();
                    } catch (Exception | Error e) {
                        Logger.getLogger(ShutdownHookRegistry.class.getName()).log(Level.SEVERE, result1 + " failed", e);
                    } finally {
                        //no matter what, don't try to run it more than once
                        hooks.remove(result1);
                    }
                }
            } finally {
                running = false;
            }
        }
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

    public final ShutdownHookRegistry add(Timer timer) {
        add(new ShutdownTimer(timer, mode));
        return this;
    }

    void setDeploymentMode(DeploymentMode mode) {
        this.mode = mode;
    }

    private static final class ShutdownAutoCloseable implements Marker {

        private final Reference<AutoCloseable> timer;
        private final StackTraceElement allocation;

        ShutdownAutoCloseable(AutoCloseable timer, DeploymentMode mode) {
            allocation = allocationStack(4, mode);
            this.timer = new WeakReference<>(timer);
        }

        @Override
        public void run() {
            AutoCloseable clos = timer.get();
            if (clos != null) {
                try {
                    clos.close();
                } catch (Exception ex) {
                    Logger.getLogger(ShutdownHookRegistry.class.getName()).log(Level.INFO,
                            "Exception closing " + clos + " for shutdown", ex);
                }
            }
        }

        public String toString() {
            return "ShutdownTimer {" + timer.get() + "} allocated at " + allocation;
        }
    }

    private static final class ShutdownTimer implements Marker {

        private final Reference<Timer> timer;
        private final StackTraceElement allocation;

        ShutdownTimer(Timer timer, DeploymentMode mode) {
            allocation = allocationStack(4, mode);
            this.timer = new WeakReference<>(timer);
        }

        @Override
        public void run() {
            Timer t = timer.get();
            if (t != null) {
                t.cancel();
            }
        }

        public String toString() {
            return "ShutdownTimer {" + timer.get() + "} allocated at " + allocation;
        }
    }

    private interface Marker extends Runnable {

    }

    private static final class RunnableWrapper implements Marker {

        private final StackTraceElement allocation;
        private final Runnable ref;

        RunnableWrapper(Runnable r, DeploymentMode mode) {
            allocation = allocationStack(5, mode);
            this.ref = r;
        }

        @Override
        public void run() {
            ref.run();
        }

        public String toString() {
            return "ShutdownRunnable{" + ref + "} allocated at " + allocation;
        }
    }

    private static final class ShutdownCallable implements Marker {

        private final Callable<?> callable;
        private final StackTraceElement allocation;

        public ShutdownCallable(Callable<?> callable, DeploymentMode mode) {
            allocation = allocationStack(4, mode);
            this.callable = callable;
        }

        @Override
        public void run() {
            try {
                callable.call();
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
        }
    }

    private static final class ShutdownExecutorService implements Marker {

        private final Reference<ExecutorService> svc;
        private final StackTraceElement allocation;

        ShutdownExecutorService(ExecutorService svc, DeploymentMode mode) {
            this.svc = new WeakReference<>(svc);
            allocation = allocationStack(4, mode);
        }

        @Override
        public void run() {
            ExecutorService exe = svc.get();
            if (exe != null && !exe.isShutdown()) {
                exe.shutdown();
                try {
                    exe.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Logger.getLogger(ShutdownHookRegistry.class.getName()).log(Level.FINEST,
                            "Interrupted waiting for shutudown of " + exe + " allocated at " + allocation, ex);
                } finally {
                    if (!exe.isTerminated()) {
                        exe.shutdownNow();
                    }
                }
            }
        }

        public String toString() {
            return "ShutdownExecutor " + svc.get() + " allocated at " + allocation;
        }
    }

    private static StackTraceElement allocationStack(int offset, DeploymentMode mode) {
        if (!mode.isProduction()) { // For performance, don't create stack traces in production mode
            Throwable ex = new Exception().fillInStackTrace();
            StackTraceElement[] el = ex.getStackTrace();
            if (el != null && el.length > offset) {
                StackTraceElement theElement = null;
                for (int i = offset; i < el.length; i++) {
                    StackTraceElement te = el[i];
                    if (te.getClassName() != null
                            && (te.getClassName().startsWith("com.google.inject") || te.getClassName().startsWith("org.junit.runners"))) {
                        continue;
                    }
                    theElement = te;
                    break;
                }
                if (theElement != null) {
                    return theElement;
                } else {
                    return el[offset];
                }
            }
        }
        return new StackTraceElement("unknown", "unknown", "unknown", 0);
    }

    @Singleton
    static final class VMShutdownHookRegistry extends ShutdownHookRegistry implements Runnable {

        private final AtomicBoolean registered = new AtomicBoolean();

        @Override
        public void add(Runnable runnable) {
            super.add(runnable);
            if (!registered.compareAndSet(false, true)) {
                register();
            }
        }

        @Override
        public void run() {
            if (registered.getAndSet(false)) {
                runShutdownHooks();
            }
        }

        private void register() {
            Runtime.getRuntime().addShutdownHook(new Thread(this));
        }
    }
}
