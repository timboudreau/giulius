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
import com.mastfrog.settings.Settings;
import io.vertx.sqlclient.PoolOptions;
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
        return new PoolOptions().setMaxSize(settings.getInt(SETTINGS_KEY_MAX_POOL_SIZE,
                DEFAULT_MAX_POOL_SIZE)).setMaxWaitQueueSize(settings.getInt(
                        SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE, DEFAULT_MAX_WAIT_QUEUE_SIZE));
    }
}
