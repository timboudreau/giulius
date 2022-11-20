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

import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_MAX_POOL_SIZE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.DEFAULT_MAX_WAIT_QUEUE_SIZE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_MAX_POOL_SIZE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_POOL_CLEANER_PERIOD_MILLIS;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_POOL_CONNECTION_TIMEOUT_SECONDS;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_POOL_EVENT_LOOP_SIZE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_POOL_IDLE_TIMEOUT_SECONDS;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_POOL_NAME;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_SHARED_POOL;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.preconditions.ConfigurationError;
import io.vertx.sqlclient.PoolOptions;
import static java.util.concurrent.TimeUnit.SECONDS;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 *
 * @author Tim Boudreau
 */
final class PoolOptionsProvider implements Provider<PoolOptions> {

    private final Settings settings;

    @Inject
    PoolOptionsProvider(Settings settings) {
        this.settings = settings;
    }

    @Override
    public PoolOptions get() {
        PoolOptions opts = new PoolOptions()
                .setMaxSize(settings.getInt(SETTINGS_KEY_MAX_POOL_SIZE, DEFAULT_MAX_POOL_SIZE))
                .setMaxWaitQueueSize(settings.getInt(SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE, DEFAULT_MAX_WAIT_QUEUE_SIZE))
                .setShared(settings.getBoolean(SETTINGS_KEY_SHARED_POOL, true));
        Integer cleanerPeriod = settings.getInt(SETTINGS_KEY_POOL_CLEANER_PERIOD_MILLIS);
        if (cleanerPeriod != null) {
            opts = opts.setPoolCleanerPeriod(cleanerPeriod);
        }
        String name = settings.getString(SETTINGS_KEY_POOL_NAME);
        if (name != null) {
            opts = opts.setName(name);
        }
        Integer eventLoopSize = settings.getInt(SETTINGS_KEY_POOL_EVENT_LOOP_SIZE);
        if (eventLoopSize != null) {
            if (eventLoopSize <= 0) {
                throw new ConfigurationError("Event loop size too small: " + eventLoopSize);
            }
            opts = opts.setEventLoopSize(eventLoopSize);
        }
        Integer idleTimeout = settings.getInt(SETTINGS_KEY_POOL_IDLE_TIMEOUT_SECONDS);
        if (idleTimeout != null) {
            opts = opts.setIdleTimeout(idleTimeout)
                    .setIdleTimeoutUnit(SECONDS);
        }
        Integer connectTimeout = settings.getInt(SETTINGS_KEY_POOL_CONNECTION_TIMEOUT_SECONDS);
        if (connectTimeout != null) {
            opts = opts.setConnectionTimeout(connectTimeout)
                    .setConnectionTimeoutUnit(SECONDS);
        }

        return opts;
    }
}
