package com.mastfrog.signalreload;

import com.mastfrog.giulius.Dependencies;

/**
 * Runs the code that launches the application. Note that this method should
 * return, not block to avoid system exit - do that with the return value from
 * start() if necessary (and be aware that a signal could make it look like what
 * you're waiting on is done!).
 *
 * @param <T>
 */
public interface Launcher<T> {

    /**
     * Launch the application. Note this is not run on the thread that calls
     * start().
     *
     * @param deps The dependencies/Guice injector
     * @return Some object the caller can use
     */
    public T launch(Dependencies deps) throws Exception;

}
