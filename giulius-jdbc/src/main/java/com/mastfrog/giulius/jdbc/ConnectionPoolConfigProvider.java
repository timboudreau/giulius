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
import static com.mastfrog.giulius.jdbc.JdbcModule.CATALOG;
import static com.mastfrog.giulius.jdbc.JdbcModule.CONNECTION_TIMEOUT_MINUTES;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_JDBC_URL;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_JDBC_USER;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_MIN_CONNECTIONS_PER_PARTITION;
import static com.mastfrog.giulius.jdbc.JdbcModule.DEFAULT_READ_ONLY;
import static com.mastfrog.giulius.jdbc.JdbcModule.IDLE_MAX_AGE_SECONDS;
import static com.mastfrog.giulius.jdbc.JdbcModule.JDBC_CONNECTION_TEST_QUERY;
import static com.mastfrog.giulius.jdbc.JdbcModule.JDBC_PASSWORD;
import static com.mastfrog.giulius.jdbc.JdbcModule.JDBC_URL;
import static com.mastfrog.giulius.jdbc.JdbcModule.JDBC_USER;
import static com.mastfrog.giulius.jdbc.JdbcModule.MAX_CONNECTIONS_PER_PARTITION;
import static com.mastfrog.giulius.jdbc.JdbcModule.MAX_CONNECTION_AGE_MINUTES;
import static com.mastfrog.giulius.jdbc.JdbcModule.MIN_CONNECTIONS_PER_PARTITION;
import static com.mastfrog.giulius.jdbc.JdbcModule.POOL_MAX_CONNECTIONS;
import static com.mastfrog.giulius.jdbc.JdbcModule.POOL_MIN_CONNECTIONS;
import static com.mastfrog.giulius.jdbc.JdbcModule.READ_ONLY;
import static com.mastfrog.giulius.jdbc.JdbcModule.defaultMaxConnectionsPerPartition;
import com.mastfrog.settings.Settings;
import com.zaxxer.hikari.HikariConfig;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Tim Boudreau
 */
class ConnectionPoolConfigProvider implements Provider<HikariConfig> {

    private final Settings settings;

    private final ConnectionPoolPostConfig postConfig;

    @Inject
    public ConnectionPoolConfigProvider(Settings settings, ConnectionPoolPostConfig postConfig) {
        this.settings = settings;
        this.postConfig = postConfig;
    }

    @Override
    @SuppressWarnings("deprecation")
    public HikariConfig get() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(settings.getString(JDBC_URL, DEFAULT_JDBC_URL));
        config.setMinimumIdle(settings.getInt(MIN_CONNECTIONS_PER_PARTITION,
                settings.getInt(POOL_MIN_CONNECTIONS, DEFAULT_MIN_CONNECTIONS_PER_PARTITION)));
        config.setMaximumPoolSize(settings.getInt(MAX_CONNECTIONS_PER_PARTITION,
                settings.getInt(POOL_MAX_CONNECTIONS, defaultMaxConnectionsPerPartition())));
        config.setReadOnly(settings.getBoolean(READ_ONLY, DEFAULT_READ_ONLY));
        String cat = settings.getString(CATALOG);
        if (cat != null) {
            config.setCatalog(cat);
        }
        Long timeout = settings.getLong(CONNECTION_TIMEOUT_MINUTES);
        if (timeout != null) {
            config.setConnectionTimeout(TimeUnit.MILLISECONDS.convert(timeout, TimeUnit.MINUTES));
        }
        Long maxConnectionAgeInMinutes = settings.getLong(MAX_CONNECTION_AGE_MINUTES);
        if (maxConnectionAgeInMinutes != null) {
            config.setMaxLifetime(TimeUnit.MILLISECONDS.convert(maxConnectionAgeInMinutes, TimeUnit.MINUTES));
        }
        Long idleMaxAge = settings.getLong(IDLE_MAX_AGE_SECONDS);
        if (idleMaxAge != null) {
            config.setIdleTimeout(idleMaxAge * 1000);
        }
        String u = settings.getString(JDBC_USER, DEFAULT_JDBC_USER);
        String p = settings.getString(JDBC_PASSWORD);
        if (u != null && !u.trim().isEmpty()) {
            config.setUsername(u);
        }
        if (p != null && !p.trim().isEmpty()) {
            config.setPassword(p);
        }
        String c = settings.getString(JDBC_CONNECTION_TEST_QUERY);
        if (c != null) {
            config.setConnectionTestQuery(c);
        }
        return postConfig.onConfigure(config);
    }
}
