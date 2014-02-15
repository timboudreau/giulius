/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mastfrog.signalreload;

import com.google.inject.Provider;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.settings.SettingsRefreshInterval;
import com.mastfrog.util.Exceptions;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 * Application launcher which uses Dependencies.shutdown() and recreating a new
 * Dependencies, to have an application reload its configuration on receiving an
 * OS-level signal. Note that this uses sun.misc.Signal and probably will not
 * work on a non-Sun JVM.
 *
 * @author Tim Boudreau
 */
public final class Signalizer<T> {

    private final Signals reload;
    private final Provider<Dependencies> depsProvider;
    private final Launcher<T> launcher;

    public Signalizer(Signals reload, DependenciesBuilder depsBuilder, Launcher<T> launcher) {
        this(reload, new DepsProvider(depsBuilder), launcher);
    }

    public Signalizer(Signals reload, Provider<Dependencies> depsBuilder, Launcher<T> launcher) {
        this.reload = reload;
        this.depsProvider = depsBuilder;
        this.launcher = launcher;
    }

    private static class DepsProvider implements Provider<Dependencies> {

        private final DependenciesBuilder builder;

        public DepsProvider(DependenciesBuilder builder) {
            this.builder = builder;
        }

        @Override
        public Dependencies get() {
            try {
                return builder.build();
            } catch (IOException ex) {
                return Exceptions.chuck(ex);
            }
        }

    }

    volatile boolean disabled;

    /**
     * Disable signal handling, restoring standard Java handling for that
     * signal.
     */
    public void disable() {
        disabled = true;
    }

    /**
     * Start the application and block until it has launched and returned a
     * value.
     *
     * @return A value
     * @throws Exception If launch failed
     */
    public LaunchControl<T> start() throws Exception {
        Dependencies deps = depsProvider.get();
        Reloader reloader = new Reloader(deps);
        Signal signal = new Signal(reload.name());
        Signal.handle(signal, reloader);
        reloader.handle(null);
        return reloader;
    }

    private class Reloader implements SignalHandler, Runnable, LaunchControl<T> {

        volatile Dependencies deps;
        volatile Exception exception;
        volatile T obj;
        private final CountDownLatch latch = new CountDownLatch(1);

        Reloader(Dependencies deps) {
            this.deps = deps;
        }

        @Override
        public void handle(Signal sig) {
            if (disabled) {
                SignalHandler.SIG_DFL.handle(sig);
                return;
            }
            System.out.println("Reload configuration on signal " + sig);
            String threadName = deps == null ? "Launch thread" : "Relaunch Thread";
            if (deps != null) {
                shutdown();
                // force configuration reload
                SettingsRefreshInterval.refreshNow();
            }
            Thread t = new Thread(this, threadName);
            t.setDaemon(true);
            System.out.println("launching");
            t.start();
        }

        synchronized void await() throws InterruptedException {
            latch.await();
        }

        @Override
        public void run() {
            boolean wasNull = deps == null;
            try {
                deps = depsProvider.get();
                obj = launcher.launch(deps);
            } catch (Exception ex) {
                ex.printStackTrace();
                if (wasNull) {
                    exception = ex;
                } else {
                    Logger.getLogger(Signalizer.class.getName()).log(Level.SEVERE, null, ex);
                }
            } finally {
                if (wasNull) {
                    latch.countDown();
                }
            }
        }

        @Override
        public T get() throws InterruptedException, Exception {
            latch.await();
            if (exception != null) {
                throw exception;
            }
            return obj;
        }

        @Override
        public void restart() {
            handle(null);
        }

        @Override
        public void shutdown() {
            if (deps != null) {
                deps.shutdown();
            }
            deps = null;
            exception = null;
            obj = null;
        }
    }

//    static class L implements Launcher<String> {
//
//        @Override
//        public String launch(Dependencies deps) {
//            return "hello";
//        }
//    }
//    public static void main(String[] args) throws InterruptedException, Exception {
//
//        L launcher = new L();
//
//        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
//
//            @Override
//            public void run() {
//                System.out.println("HOOK!");
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException ex) {
//                    Logger.getLogger(Signalizer.class.getName()).log(Level.SEVERE, null, ex);
//                }
//            }
//        }));
//
//        Signalizer<String> sig = new Signalizer(Signals.USR2, new DependenciesBuilder(), launcher);
//
//        String s = sig.start();
//        System.out.println("Got " + s);
//
//        Thread.sleep(60000);
//    }
}
