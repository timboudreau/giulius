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
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.giulius.ShutdownHookRegistry.VMShutdownHookRegistry;
import java.util.Optional;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thing you can add runnables to to be run on orderly vm shutdown (close
 * connections, etc.)
 *
 * @author Tim Boudreau
 * @deprecated use com.mastfrog.shutdown.hooks.ShutdownHookRegistry directly - the
 * code has been moved there, and this class simply delegates to it.
 */
@ImplementedBy(VMShutdownHookRegistry.class)
@Deprecated
public abstract class ShutdownHookRegistry implements ShutdownHooks {

    private static class Delegator extends com.mastfrog.shutdown.hooks.ShutdownHookRegistry {

        private final boolean registerable;
        private ShutdownHookRegistry registry;

        Delegator(boolean registerable) {
            this.registerable = registerable;
        }

        Delegator(long wait, boolean registerable) {
            super(wait);
            this.registerable = registerable;
        }

        void doInstall() {
            super.install();
        }

        void doDeinstall() {
            super.deinstall();
        }

        @Override
        protected void onFirstAdd() {
            if (registerable) {
                install();
            }
        }
    }

    final Delegator delegator;

    @SuppressWarnings("LeakingThisInConstructor")
    ShutdownHookRegistry(Delegator delegator) {
        this.delegator = delegator;
        delegator.registry = this;
    }
    
    com.mastfrog.shutdown.hooks.ShutdownHookRegistry realHooks() {
        return delegator;
    }

    public ShutdownHookRegistry() {
        this(new Delegator(false));
    }

    public ShutdownHookRegistry(long ms) {
        this(new Delegator(ms, false));
    }

    protected void install() {
        delegator.doInstall();
    }

    protected void deinstall() {
        delegator.doDeinstall();
    }

    @Override
    public void add(Runnable toRun) {
        delegator.add(toRun);
    }

    @Override
    public ShutdownHookRegistry addFirst(Runnable toRun) {
        delegator.addFirst(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addLast(Runnable toRun) {
        delegator.addLast(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addWeak(Runnable toRun) {
        delegator.addWeak(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addFirstWeak(Runnable toRun) {
        delegator.addFirstWeak(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addLastWeak(Runnable toRun) {
        delegator.addLastWeak(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry add(Callable<?> toRun) {
        delegator.add(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addFirst(Callable<?> toRun) {
        delegator.addFirst(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addLast(Callable<?> toRun) {
        delegator.addLast(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addWeak(Callable<?> toRun) {
        delegator.addWeak(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addFirstWeak(Callable<?> toRun) {
        delegator.addFirstWeak(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addLastWeak(Callable<?> toRun) {
        delegator.addLastWeak(toRun);
        return this;
    }

    @Override
    public void add(Timer toRun) {
        delegator.add(toRun);
    }

    @Override
    public ShutdownHookRegistry addFirst(Timer toRun) {
        delegator.addFirst(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addLast(Timer toRun) {
        delegator.add(toRun);
        return this;
    }

    @Override
    public void add(AutoCloseable toRun) {
        delegator.addResource(toRun);
    }

    @Override
    public ShutdownHookRegistry addFirst(AutoCloseable toRun) {
        delegator.addResourceFirst(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addLast(AutoCloseable toRun) {
        delegator.addResourceLast(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry add(ExecutorService toRun) {
        delegator.add(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addFirst(ExecutorService toRun) {
        delegator.addFirst(toRun);
        return this;
    }

    @Override
    public ShutdownHookRegistry addLast(ExecutorService toRun) {
        delegator.addLast(toRun);
        return this;
    }

    @Override
    public ShutdownHooks addThrowing(ThrowingRunnable toRun) {
        delegator.addThrowing(toRun);
        return this;
    }

    @Override
    public ShutdownHooks addFirstThrowing(ThrowingRunnable toRun) {
        delegator.addFirstThrowing(toRun);
        return this;
    }

    @Override
    public ShutdownHooks addLastThrowing(ThrowingRunnable toRun) {
        delegator.addLastThrowing(toRun);
        return this;
    }

    @Override
    public int shutdown() {
        return runShutdownHooks();
    }

    protected int runShutdownHooks() {
        return delegator.shutdown();
    }

    @Override
    public boolean isRunningShutdownHooks() {
        return delegator.isRunningShutdownHooks();
    }

    @Singleton
    static final class VMShutdownHookRegistry extends ShutdownHookRegistry implements Runnable {

        private final AtomicBoolean registered = new AtomicBoolean();

        public VMShutdownHookRegistry() {
            super(new Delegator(true));
        }

        public VMShutdownHookRegistry(long wait) {
            super(new Delegator(wait, true));
        }

        @Override
        public void run() {
            if (registered.getAndSet(false)) {
                runShutdownHooks();
            }
        }
    }

    void setDeploymentMode(DeploymentMode mode) {
        delegator.setDeploymentMode(com.mastfrog.shutdown.hooks.DeploymentMode.valueOf(mode.name()));
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
        delegator.setWaitMilliseconds(wait);
    }

    public static Optional<ShutdownHookRegistry> current() {
        Optional<com.mastfrog.shutdown.hooks.ShutdownHookRegistry> opt
                = com.mastfrog.shutdown.hooks.ShutdownHookRegistry.current();
        return opt.flatMap(reg -> {
            if (reg instanceof Delegator) {
                return Optional.of(((Delegator) reg).registry);
            }
            return Optional.empty();
        });
    }
}
