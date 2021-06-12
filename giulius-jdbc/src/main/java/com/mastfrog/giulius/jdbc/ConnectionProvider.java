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
import com.mastfrog.util.preconditions.Exceptions;
import com.zaxxer.hikari.pool.HikariPool;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides a JDBC connection
 *
 * @author Tim Boudreau
 */
@Singleton
class ConnectionProvider implements Provider<Connection> {

    private final Provider<HikariPool> connectionPool;
    private final ConnectionConfigurer configurer;
    private final JdbcInitializer init;
    private final Provider<Connection> delegate;

    @Inject
    public ConnectionProvider(Provider<HikariPool> connectionPool, ConnectionConfigurer configurer, JdbcInitializer init) {
        this.connectionPool = connectionPool;
        this.configurer = configurer;
        this.init = init;
        if (init instanceof JdbcInitializerImpl) {
            delegate = new NoLockDelegate();
        } else {
            delegate = new LockDelegate();
        }
    }

    @Override
    public Connection get() {
        return delegate.get();
    }

    private class NoLockDelegate implements Provider<Connection> {

        @Override
        public Connection get() {
            try {
                return configurer.onProvideConnection(connectionPool.get().getConnection());
            } catch (SQLException ex) {
                return Exceptions.chuck(ex);
            }
        }
    }

    private class LockDelegate implements Provider<Connection> {

        private final ConnectionInterceptor icept;

        LockDelegate() {
            icept = init instanceof JdbcInitializerImpl ? new None() : new Switching(init);
        }

        @Override
        public Connection get() {
            try {
                HikariPool pool = connectionPool.get();
                Connection result = pool.getConnection();
                result = configurer.onProvideConnection(result);
                return icept.intercept(result);
            } catch (SQLException | IOException ex) {
                return Exceptions.chuck(ex);
            }
        }
    }

    interface ConnectionInterceptor {

        Connection intercept(Connection c) throws SQLException, IOException;
    }

    private static class None implements ConnectionInterceptor {

        @Override
        public Connection intercept(Connection c) throws SQLException, IOException {
            return c;
        }
    }

    private static final class Switching implements ConnectionInterceptor {

        private final AtomicReference<ConnectionInterceptor> ref = new AtomicReference<>();

        Switching(JdbcInitializer init) {
            // Allows us to block all threads requesting a connection until the
            // connection initializer has completed, but on exit replaces itself
            // with a non-locking instance so every request for a connection ever
            // does not bottleneck on a lock that' sonly needed initially
            ConnectionInterceptor locking = new Locking(init, ref);
            ref.set(locking);
        }

        @Override
        public Connection intercept(Connection c) throws SQLException, IOException {
            return ref.get().intercept(c);
        }
    }

    private static final class Locking implements ConnectionInterceptor {

        private final JdbcInitializer init;
        private boolean initialized;
        private final AtomicReference<ConnectionInterceptor> ref;

        public Locking(JdbcInitializer init, AtomicReference<ConnectionInterceptor> ref) {
            this.init = init;
            this.ref = ref;
        }

        @Override
        public synchronized Connection intercept(Connection c) throws SQLException, IOException {
            if (!initialized) {
                initialized = true;
                try {
                    return init.onFirstConnect(c);
                } finally {
                    ref.set(new None());
                }
            }
            return c;
        }
    }
}
