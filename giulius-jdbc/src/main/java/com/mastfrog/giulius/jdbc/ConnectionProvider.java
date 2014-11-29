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
import com.jolbox.bonecp.BoneCP;
import com.mastfrog.util.Exceptions;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Provides a JDBC connection
 *
 * @author Tim Boudreau
 */
@Singleton
class ConnectionProvider implements Provider<Connection> {

    private final Provider<BoneCP> connectionPool;
    private final ConnectionConfigurer configurer;
    private final JdbcInitializer init;
    private final Provider<Connection> delegate;

    @Inject
    public ConnectionProvider(Provider<BoneCP> connectionPool, ConnectionConfigurer configurer, JdbcInitializer init) {
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

        private volatile boolean first = true;

        @Override
        public synchronized Connection get() {
            try {
                BoneCP pool = connectionPool.get();
                Connection result = pool.getConnection();
                result = configurer.onProvideConnection(result);
                if (first) {
                    synchronized (this) {
                        if (first) {
                            first = false;
                            result = init.onFirstConnect(result);
                        }
                    }
                }
                return result;
            } catch (SQLException | IOException ex) {
                return Exceptions.chuck(ex);
            }
        }
    }
}
