package com.mastfrog.signalreload;

/**
 *
 * @author Tim Boudreau
 */
public interface LaunchControl<T> {
    public T get() throws Exception;
    public void restart();
    public void shutdown();
}
