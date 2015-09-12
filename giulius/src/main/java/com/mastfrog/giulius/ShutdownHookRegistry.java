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
import com.mastfrog.util.Checks;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
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

    private final List<Runnable> hooks = Collections.synchronizedList(new ArrayList<Runnable>(20));

    protected ShutdownHookRegistry() {
        //package private
    }

    /**
     * Add a runnable which should run on VM shutdown or logical shutdown of a
     * subsystem.
     *
     * @see com.mastfrog.guicy.Dependencies.shutdown
     * @param runnable A runnable
     */
    public void add(Runnable runnable) {
        Checks.notNull("runnable", runnable);
        hooks.add(runnable);
    }

    public ShutdownHookRegistry add(ExecutorService svc) {
        Checks.notNull("svc", svc);
        add(new ShutdownExecutorService(svc));
        return this;
    }

    private volatile boolean running;

    public boolean isRunningShutdownHooks() {
        return running;
    }

    protected void runShutdownHooks() {
        running = true;
        try {
            Set<Integer> identities = new HashSet<>();
            Runnable[] result;
            synchronized (hooks) {
                result = hooks.toArray(new Runnable[hooks.size()]);
                hooks.clear();
            }
            for (int i = 0; i < result.length; i++) {
                int id = System.identityHashCode(result[i]);
                if (identities.contains(id)) {
                    continue;
                }
                identities.add(id);
                try {
                    result[i].run();
                } catch (Exception e) {
                    Logger.getLogger(ShutdownHookRegistry.class.getName()).log(
                            Level.SEVERE, result[i] + " failed", e);
                } finally {
                    //no matter what, don't try to run it more than once
                    hooks.remove(result[i]);
                }
            }
        } finally {
            running = false;
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

    public ShutdownHookRegistry add(Timer timer) {
        Checks.notNull("timer", timer);
        add(new ShutdownTimer(timer));
        return this;
    }

    private static final class ShutdownTimer implements Runnable {

        private final Reference<Timer> timer;

        public ShutdownTimer(Timer timer) {
            this.timer = new WeakReference<>(timer);
        }

        @Override
        public void run() {
            Timer t = timer.get();
            t.cancel();
        }
    }

    private static final class ShutdownExecutorService implements Runnable {

        private final Reference<ExecutorService> svc;

        public ShutdownExecutorService(ExecutorService svc) {
            this.svc = new WeakReference<>(svc);
        }

        @Override
        public void run() {
            ExecutorService exe = svc.get();
            if (exe != null) {
                exe.shutdownNow();
            }
        }
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
