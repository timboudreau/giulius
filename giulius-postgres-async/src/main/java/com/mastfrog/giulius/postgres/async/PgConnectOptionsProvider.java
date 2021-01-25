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

import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_CACHE_PREPARED_STATEMENTS;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_CONNECT_TIMEOUT;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_LOG_ACTIVITY;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_RECONNECT_ATTEMPTS;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_TCP_CORK;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_TCP_FAST_OPEN;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_TCP_KEEP_ALIVE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_TCP_NO_DELAY;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_TCP_QUICK_ACK;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_TRUST_ALL;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_USE_ALPN;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_CACHE_PREPARED_STATEMENTS;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_CONNECT_TIMEOUT;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_LOG_ACTIVITY;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_PG_URI;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_QUICK_ACK;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_RECONNECT_ATTEMPTS;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_TCP_CORK;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_TCP_FAST_OPEN;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_TCP_KEEP_ALIVE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_TCP_NODELAY;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_TRUST_ALL;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_USE_ALPN;
import com.mastfrog.settings.Settings;
import io.vertx.pgclient.PgConnectOptions;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
final class PgConnectOptionsProvider implements Provider<PgConnectOptions> {

    private final Settings settings;

    @Inject
    PgConnectOptionsProvider(Settings settings) {
        this.settings = settings;
    }

    @Override
    public PgConnectOptions get() {
        PgConnectOptions opts = PgConnectOptions.fromUri(
                settings.getString(SETTINGS_KEY_PG_URI))
                .setCachePreparedStatements(settings.getBoolean(SETTINGS_KEY_CACHE_PREPARED_STATEMENTS, DEFAULT_CACHE_PREPARED_STATEMENTS))
                .setConnectTimeout(settings.getInt(SETTINGS_KEY_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT))
                .setTcpCork(settings.getBoolean(SETTINGS_KEY_TCP_CORK, DEFAULT_TCP_CORK))
                .setUseAlpn(settings.getBoolean(SETTINGS_KEY_USE_ALPN, DEFAULT_USE_ALPN))
                .setTcpQuickAck(settings.getBoolean(SETTINGS_KEY_QUICK_ACK, DEFAULT_TCP_QUICK_ACK))
                .setTcpNoDelay(settings.getBoolean(SETTINGS_KEY_TCP_NODELAY, DEFAULT_TCP_NO_DELAY))
                .setTcpKeepAlive(settings.getBoolean(SETTINGS_KEY_TCP_KEEP_ALIVE, DEFAULT_TCP_KEEP_ALIVE))
                .setTcpFastOpen(settings.getBoolean(SETTINGS_KEY_TCP_FAST_OPEN, DEFAULT_TCP_FAST_OPEN))
                .setReconnectAttempts(settings.getInt(SETTINGS_KEY_RECONNECT_ATTEMPTS, DEFAULT_RECONNECT_ATTEMPTS))
                .setLogActivity(settings.getBoolean(SETTINGS_KEY_LOG_ACTIVITY, DEFAULT_LOG_ACTIVITY))
                .setTrustAll(settings.getBoolean(SETTINGS_KEY_TRUST_ALL, DEFAULT_TRUST_ALL));
        return opts;
    }

}
