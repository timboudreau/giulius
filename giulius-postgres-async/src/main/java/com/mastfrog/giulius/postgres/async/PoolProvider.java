/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
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

package com.mastfrog.giulius.postgres.async;

import com.mastfrog.giulius.ShutdownHookRegistry;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class PoolProvider implements Provider<PgPool>, Runnable {

    private final Provider<PgConnectOptions> pConnectOpts;
    private final Provider<PoolOptions> ppoolOpts;
    private PgPool pool;

    @SuppressWarnings(value = "LeakingThisInConstructor")
    @Inject
    PoolProvider(Provider<PgConnectOptions> pConnectOpts, Provider<PoolOptions> ppoolOpts, ShutdownHookRegistry reg) {
        this.pConnectOpts = pConnectOpts;
        this.ppoolOpts = ppoolOpts;
        reg.add(this);
    }

    @Override
    public synchronized PgPool get() {
        if (pool == null) {
            pool = PgPool.pool(pConnectOpts.get(), ppoolOpts.get());
        }
        return pool;
    }

    @Override
    public void run() {
        PgPool p;
        synchronized (this) {
            p = pool;
            pool = null;
        }
        if (p != null) {
            p.close();
        }
    }
}
