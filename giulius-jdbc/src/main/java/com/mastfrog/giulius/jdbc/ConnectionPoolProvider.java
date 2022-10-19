/*
 * The MIT License
 *
 * Copyright 2014 Tim Boudreau.
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
package com.mastfrog.giulius.jdbc;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mastfrog.shutdown.hooks.ShutdownHookRegistry;
import com.mastfrog.util.preconditions.Exceptions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.pool.HikariPool;

/**
 * Provides a BoneCP connection pool
 *
 * @author Tim Boudreau
 */
@Singleton
class ConnectionPoolProvider implements Provider<HikariPool>, Runnable {

    private volatile HikariPool instance;
    private final Provider<HikariConfig> config;

    @Inject
    public ConnectionPoolProvider(Provider<HikariConfig> config, ShutdownHookRegistry reg) {
        this.config = config;
        reg.add(this);
    }

    @Override
    public HikariPool get() {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    try {
                        HikariPool pool = new HikariPool(config.get());
                        instance = pool;
                    } catch (Exception ex) {
                        Exceptions.chuck(ex);
                    }
                }
            }
        }
        return instance;
    }

    @Override
    public void run() {
        synchronized(this) {
            if (instance != null) {
                try {
                    instance.shutdown();
                } catch (InterruptedException ex) {
                    Exceptions.chuck(ex);
                }
            }
        }
    }
}
