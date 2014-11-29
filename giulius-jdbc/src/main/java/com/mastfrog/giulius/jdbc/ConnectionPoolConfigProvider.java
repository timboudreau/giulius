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
import com.jolbox.bonecp.BoneCPConfig;
import static com.mastfrog.giulius.jdbc.JdbcModule.ACQUIRE_RETRY_ATTEMPTS;
import static com.mastfrog.giulius.jdbc.JdbcModule.CLOSE_CONNECTION_WATCH;
import static com.mastfrog.giulius.jdbc.JdbcModule.CLOSE_OPEN_STATEMENTS;
import static com.mastfrog.giulius.jdbc.JdbcModule.CONNECTION_TIMEOUT_MINUTES;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_ACQUIRE_RETRY_ATTEMPTS;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_CLOSE_CONNECTION_WATCH;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_CLOSE_OPEN_STATEMENTS;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_JDBC_URL;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_JDBC_USER;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_MAX_CONNECTIONS_PER_PARTITION;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_MIN_CONNECTIONS_PER_PARTITION;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_PARTITION_COUNT;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_READ_ONLY;
import static com.mastfrog.giulius.jdbc.JdbcModule.IDLE_MAX_AGE_SECONDS;
import static com.mastfrog.giulius.jdbc.JdbcModule.JDBC_PASSWORD;
import static com.mastfrog.giulius.jdbc.JdbcModule.JDBC_URL;
import static com.mastfrog.giulius.jdbc.JdbcModule.JDBC_USER;
import static com.mastfrog.giulius.jdbc.JdbcModule.MAX_CONNECTIONS_PER_PARTITION;
import static com.mastfrog.giulius.jdbc.JdbcModule.MAX_CONNECTION_AGE_MINUTES;
import static com.mastfrog.giulius.jdbc.JdbcModule.MIN_CONNECTIONS_PER_PARTITION;
import static com.mastfrog.giulius.jdbc.JdbcModule.PARTITION_COUNT;
import static com.mastfrog.giulius.jdbc.JdbcModule.READ_ONLY;
import com.mastfrog.settings.Settings;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Tim Boudreau
 */
class ConnectionPoolConfigProvider implements Provider<BoneCPConfig> {

    private final Settings settings;

    private final ConnectionPoolPostConfig postConfig;

    @Inject
    public ConnectionPoolConfigProvider(Settings settings, ConnectionPoolPostConfig postConfig) {
        this.settings = settings;
        this.postConfig = postConfig;
    }

    @Override
    public BoneCPConfig get() {
        BoneCPConfig config = new BoneCPConfig();
        config.setJdbcUrl(settings.getString(JDBC_URL, DEFAULT_JDBC_URL));
        config.setPartitionCount(settings.getInt(PARTITION_COUNT, DEFAULT_PARTITION_COUNT));
        config.setMinConnectionsPerPartition(settings.getInt(MIN_CONNECTIONS_PER_PARTITION, DEFAULT_MIN_CONNECTIONS_PER_PARTITION));
        config.setMaxConnectionsPerPartition(settings.getInt(MAX_CONNECTIONS_PER_PARTITION, DEFAULT_MAX_CONNECTIONS_PER_PARTITION));
        config.setCloseOpenStatements(settings.getBoolean(CLOSE_OPEN_STATEMENTS, DEFAULT_CLOSE_OPEN_STATEMENTS));
        config.setCloseConnectionWatch(settings.getBoolean(CLOSE_CONNECTION_WATCH, DEFAULT_CLOSE_CONNECTION_WATCH));

        config.setDefaultReadOnly(settings.getBoolean(READ_ONLY, DEFAULT_READ_ONLY));
        config.setAcquireRetryAttempts(settings.getInt(ACQUIRE_RETRY_ATTEMPTS, DEFAULT_ACQUIRE_RETRY_ATTEMPTS));
        Long timeout = settings.getLong(CONNECTION_TIMEOUT_MINUTES);
        if (timeout != null) {
            config.setConnectionTimeout(timeout, TimeUnit.MINUTES);
        }
        Long maxConnectionAgeInMinutes = settings.getLong(MAX_CONNECTION_AGE_MINUTES);
        if (maxConnectionAgeInMinutes != null) {
            config.setMaxConnectionAgeInSeconds(maxConnectionAgeInMinutes * 60);
        }
        Long idleMaxAge = settings.getLong(IDLE_MAX_AGE_SECONDS);
        if (idleMaxAge != null) {
            config.setIdleMaxAgeInSeconds(idleMaxAge);
        }
        String u = settings.getString(JDBC_USER, DEFAULT_JDBC_USER);
        String p = settings.getString(JDBC_PASSWORD);
        if (u != null && !u.trim().isEmpty()) {
            config.setUsername(u);
        }
        if (p != null && !p.trim().isEmpty()) {
            config.setPassword(p);
        }
        return postConfig.onConfigure(config);
    }
}
