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
import com.mastfrog.giulius.annotations.Setting;
import static com.mastfrog.giulius.annotations.Setting.Tier.PRIMARY;
import static com.mastfrog.giulius.annotations.Setting.Tier.SECONDARY;
import static com.mastfrog.giulius.annotations.Setting.Tier.TERTIARY;
import static com.mastfrog.giulius.annotations.Setting.ValueType.BOOLEAN;
import static com.mastfrog.giulius.annotations.Setting.ValueType.INTEGER;
import com.mastfrog.settings.SettingsBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

/**
 * Guice bindings for the postgres async client. Binds PgPool and some
 * supporting classes. Note: The vertx driver seems to routinely break binary
 * compatibility, and there is nothing that can be done about that here - for
 * example, with version 3.9.1, a bunch of layers of async calls are added to
 * creating and using a prepared statement, which will break existing code, and
 * PgConnectOptions curiously, lost overloaded methods that returned its own
 * type, returning the parent type instead.
 * <p>
 * On the other hand, it <i>is</i> a true async postgres driver, and that is
 * critical if you're in the async framework business.
 *
 * @author Tim Boudreau
 */
public final class PostgresAsyncModule extends AbstractModule {

    @Setting(value = "The postgres database URL.  Note this is a *postgres* URL, **not** a "
            + "JDBC URL (e.g. `postgres://user:pw@host:port/db`).",
            tier = PRIMARY,
            shortcut = 'd',
            defaultValue = "postgres://localhost:5432/postgres")
    public static final String SETTINGS_KEY_PG_URI = "pg-db";

    @Setting(value = "The maximum number of connections to postgres the driver will make.",
            type = INTEGER, tier = SECONDARY)
    public static final String SETTINGS_KEY_MAX_POOL_SIZE
            = "pg-max-pool-size";
    @Setting(value = "The number of asynchronous requests allowed to be queued up to use a "
            + "postgres connection in the pool before attempts to use it will fail.  In an "
            + "async server, this should probably be a very large number.",
            type = INTEGER, tier = SECONDARY)
    public static final String SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE
            = "pg-max-wait-queue-size";
    @Setting(value = "If true, the driver should cache prepared statements (this involves a "
            + "small amount of per-connection overhead on the database side).",
            type = BOOLEAN, tier = TERTIARY)
    public static final String SETTINGS_KEY_CACHE_PREPARED_STATEMENTS
            = "pg-cache-prepared-statements";
    @Setting(value = "Timeout in seconds before attempting to connect to postgres is judged "
            + "to have failed.",
            type = INTEGER, tier = SECONDARY)
    public static final String SETTINGS_KEY_CONNECT_TIMEOUT
            = "pg-connect-timeout";
    @Setting(value = "Use tcp-cork on postgres connections", type = BOOLEAN)
    public static final String SETTINGS_KEY_TCP_CORK = "pg-tcp-cork";
    @Setting(value = "Use alpn on postgres connections", type = BOOLEAN)
    public static final String SETTINGS_KEY_USE_ALPN = "pg-use-alpn";
    @Setting(value = "Use quick-ack on postgres connections", type = BOOLEAN)
    public static final String SETTINGS_KEY_QUICK_ACK = "pg-quick-ack";
    @Setting(value = "Disable the nagle algorithm on postgres connections", type = BOOLEAN)
    public static final String SETTINGS_KEY_TCP_NODELAY = "pg-tcp-nodelay";
    @Setting(value = "Use TCP keep-alives on postgres connections", type = BOOLEAN)
    public static final String SETTINGS_KEY_TCP_KEEP_ALIVE
            = "pg-tcp-keep-alive";
    @Setting(value = "Use tcp-fast-open on postgres connections", type = BOOLEAN)
    public static final String SETTINGS_KEY_TCP_FAST_OPEN = "pg-tcp-fast-open";
    @Setting(value = "Number of reconnection attempts when connecting to postgres", type = INTEGER)
    public static final String SETTINGS_KEY_RECONNECT_ATTEMPTS
            = "pg-tcp-fast-open";
    @Setting(value = "Instruct the vertx postgres driver to log activity", type = BOOLEAN)
    public static final String SETTINGS_KEY_LOG_ACTIVITY = "pg-log-activity";
    @Setting(value = "Mark the postgres connection pool as shared", type = BOOLEAN)
    public static final String SETTINGS_KEY_SHARED_POOL = "pg-pool-shared";
    @Setting(value = "Number of milliseconds between passes of pruning disused postgres "
            + "connections from the pool", type = INTEGER)
    public static final String SETTINGS_KEY_POOL_CLEANER_PERIOD_MILLIS = "pg-pool-cleaner-millis";
    @Setting("Name of the postgres pool (used by its internal logging)")
    public static final String SETTINGS_KEY_POOL_NAME = "pg-pool-name";
    @Setting(value = "Event loop thread count for the postgres pool (irrelevant if using a shared Vertx instance)", type = INTEGER, tier = SECONDARY)
    public static final String SETTINGS_KEY_POOL_EVENT_LOOP_SIZE = "pg-pool-event-loop-size";
    @Setting(value = "Idle time in seconds for pruning postgres connections from the pool", type = INTEGER, tier = SECONDARY)
    public static final String SETTINGS_KEY_POOL_IDLE_TIMEOUT_SECONDS = "pg-pool-event-idle-timeout-seconds";
    public static final String SETTINGS_KEY_POOL_CONNECTION_TIMEOUT_SECONDS = "pg-pool-connection-idle-timeout-seconds";
    /**
     * @deprecated Removed in the vertx pg client library. Does nothing
     */
    @Deprecated
    public static final String SETTINGS_KEY_POOLED_BUFFERS = "pg-pooled-buffers";
    @Setting(value = "If true, trust any SSL certificate offered by a postgres server", type = BOOLEAN, tier = TERTIARY)
    public static final String SETTINGS_KEY_TRUST_ALL = "pg-trust-all";

    static final int DEFAULT_POOL_CLEANER_MILLIS = 30000;
    static final int DEFAULT_CONNECT_TIMEOUT = 30000;
    static final int DEFAULT_RECONNECT_ATTEMPTS = 20;
    static final boolean DEFAULT_TCP_CORK = false;
    static final boolean DEFAULT_USE_ALPN = false;
    static final boolean DEFAULT_TCP_NO_DELAY = true;
    static final boolean DEFAULT_TCP_QUICK_ACK = true;
    static final boolean DEFAULT_TCP_KEEP_ALIVE = true;
    static final boolean DEFAULT_TCP_FAST_OPEN = true;
    static final boolean DEFAULT_LOG_ACTIVITY = true;
    static final boolean DEFAULT_TRUST_ALL = false;
    static final int DEFAULT_MAX_POOL_SIZE = 12;
    static final int DEFAULT_MAX_WAIT_QUEUE_SIZE = 100;
    static final boolean DEFAULT_CACHE_PREPARED_STATEMENTS = true;
    static final String DEFAULT_PG_URI
            = "postgres://postgres@postgres.timboudreau.org:5432/sensors";
    private boolean useProvider;

    public static SettingsBuilder populateDefaults(SettingsBuilder b) {
        return b.add(SETTINGS_KEY_PG_URI, DEFAULT_PG_URI)
                .add(SETTINGS_KEY_MAX_POOL_SIZE, DEFAULT_MAX_POOL_SIZE)
                .add(SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE,
                        DEFAULT_MAX_WAIT_QUEUE_SIZE)
                .add(SETTINGS_KEY_CACHE_PREPARED_STATEMENTS,
                        DEFAULT_CACHE_PREPARED_STATEMENTS);
    }

    /**
     * If called, expect a <code>Provider&lt;Vertx&gt;</code> already to be
     * bound at configuration time, and use that to obtain the
     * <code>Vertx</code> instance used by the driver; otherwise, use the
     * default obtained from  <code>Vertx.vertx()</code>. Alternately,
     * implementing and binding <code>VertxProvider</code> directly overrides
     * the default behavior.
     *
     * @return this
     */
    public PostgresAsyncModule getVertxFromProvider() {
        useProvider = true;
        return this;
    }

    @Override
    protected void configure() {
        bind(PgConnectOptions.class).toProvider(PgConnectOptionsProvider.class);
        bind(PoolOptions.class).toProvider(PoolOptionsProvider.class);
        bind(PgPool.class).toProvider(PoolProvider.class);
        if (useProvider) {
            bind(VertxProvider.class).to(VertxProviderProvider.class);
        }
    }

}
