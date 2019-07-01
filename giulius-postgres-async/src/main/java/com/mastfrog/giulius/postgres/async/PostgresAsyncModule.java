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

import com.google.inject.AbstractModule;
import com.mastfrog.settings.SettingsBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Guice bindings for the postgres async client. Binds PgPool and some
 * supporting classes.
 *
 * @author Tim Boudreau
 */
public final class PostgresAsyncModule extends AbstractModule {

    public static final String SETTINGS_KEY_PG_URI = "pg-db";
    public static final String SETTINGS_KEY_MAX_POOL_SIZE
            = "pg-max-pool-size";
    public static final String SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE
            = "pg-max-wait-queue-size";
    public static final String SETTINGS_KEY_CACHE_PREPARED_STATEMENTS
            = "pg-cache-prepared-statements";
    public static final String SETTINGS_KEY_CONNECT_TIMEOUT
            = "pg-connect-timeout";
    public static final String SETTINGS_KEY_TCP_CORK = "pg-tcp-cork";
    public static final String SETTINGS_KEY_USE_ALPN = "pg-use-alpn";
    public static final String SETTINGS_KEY_QUICK_ACK = "pg-quick-ack";
    public static final String SETTINGS_KEY_TCP_NODELAY = "pg-tcp-nodelay";
    public static final String SETTINGS_KEY_TCP_KEEP_ALIVE
            = "pg-tcp-keep-alive";
    public static final String SETTINGS_KEY_TCP_FAST_OPEN = "pg-tcp-fast-open";
    public static final String SETTINGS_KEY_RECONNECT_ATTEMPTS
            = "pg-tcp-fast-open";
    public static final String SETTINGS_KEY_LOG_ACTIVITY = "pg-log-activity";
    public static final String SETTINGS_KEY_POOLED_BUFFERS = "pg-pooled-buffers";
    public static final String SETTINGS_KEY_TRUST_ALL = "pg-trust-all";

    static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    static final int DEFAULT_RECONNECT_ATTEMPTS = 20;
    static final boolean DEFAULT_TCP_CORK = false;
    static final boolean DEFAULT_USE_ALPN = false;
    static final boolean DEFAULT_TCP_NO_DELAY = true;
    static final boolean DEFAULT_TCP_QUICK_ACK = true;
    static final boolean DEFAULT_TCP_KEEP_ALIVE = true;
    static final boolean DEFAULT_TCP_FAST_OPEN = true;
    static final boolean DEFAULT_LOG_ACTIVITY = true;
    static final boolean DEFAULT_USE_POOLED_BUFFERS = true;
    static final boolean DEFAULT_TRUST_ALL = false;
    static final int DEFAULT_MAX_POOL_SIZE = 12;
    static final int DEFAULT_MAX_WAIT_QUEUE_SIZE = 100;
    static final boolean DEFAULT_CACHE_PREPARED_STATEMENTS = true;
    static final String DEFAULT_PG_URI
            = "postgres://postgres@postgres.timboudreau.org:5432/sensors";

    public static SettingsBuilder populateDefaults(SettingsBuilder b) {
        return b.add(SETTINGS_KEY_PG_URI, DEFAULT_PG_URI)
                .add(SETTINGS_KEY_MAX_POOL_SIZE, DEFAULT_MAX_POOL_SIZE)
                .add(SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE,
                        DEFAULT_MAX_WAIT_QUEUE_SIZE)
                .add(SETTINGS_KEY_CACHE_PREPARED_STATEMENTS,
                        DEFAULT_CACHE_PREPARED_STATEMENTS);
    }

    @Override
    protected void configure() {
        bind(PgConnectOptions.class).toProvider(PgConnectOptionsProvider.class);
        bind(PoolOptions.class).toProvider(PoolOptionsProvider.class);
        bind(PgPool.class).toProvider(PoolProvider.class);
    }
}
