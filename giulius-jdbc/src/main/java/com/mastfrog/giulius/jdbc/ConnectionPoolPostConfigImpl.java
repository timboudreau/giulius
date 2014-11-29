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
import com.jolbox.bonecp.BoneCPConfig;
import static com.mastfrog.giulius.jdbc.JdbcModule.POSTGRES_KEEP_ALIVE;
import static com.mastfrog.giulius.jdbc.JdbcModule.POSTGRES_LOGIN_TIMEOUT;
import static com.mastfrog.giulius.jdbc.JdbcModule.POSTGRES_LOG_UNCLOSED_CONNECTIONS;
import static com.mastfrog.giulius.jdbc.JdbcModule.POSTGRES_RECEIVE_BUFFER_SIZE;
import static com.mastfrog.giulius.jdbc.JdbcModule.POSTGRES_SEND_BUFFER_SIZE;
import static com.mastfrog.giulius.jdbc.JdbcModule.POSTGRES_SOCKET_TIMEOUT;
import com.mastfrog.settings.Settings;
import java.util.Properties;

final class ConnectionPoolPostConfigImpl implements ConnectionPoolPostConfig {

    private final Settings settings;

    @Inject
    public ConnectionPoolPostConfigImpl(Settings settings) {
        this.settings = settings;
    }

    @Override
    public BoneCPConfig onConfigure(BoneCPConfig config) {
        String url = config.getJdbcUrl();
        // http://jdbc.postgresql.org/documentation/head/connect.html
        if (url.contains("postgres")) {
            Properties props = new Properties();
            Boolean logUnclosedConnections = settings.getBoolean(POSTGRES_LOG_UNCLOSED_CONNECTIONS);
            Integer loginTimeout = settings.getInt(POSTGRES_LOGIN_TIMEOUT);
            Integer socketTimeout = settings.getInt(POSTGRES_SOCKET_TIMEOUT);
            Integer sendBufferSize = settings.getInt(POSTGRES_SEND_BUFFER_SIZE);
            Integer receiveBufferSize = settings.getInt(POSTGRES_RECEIVE_BUFFER_SIZE);
            Boolean keepAlive = settings.getBoolean(POSTGRES_KEEP_ALIVE);

            putIfNotNull(props, "sendBufferSize", sendBufferSize);
            putIfNotNull(props, "recvBufferSize", receiveBufferSize);
            putIfNotNull(props, "tcpKeepAlive", keepAlive);
            putIfNotNull(props, "loginTimeout", loginTimeout);
            putIfNotNull(props, "socketTimeout", socketTimeout);
            putIfNotNull(props, "logUnclosedConnections", logUnclosedConnections);
            if (!props.isEmpty()) {
                config.setDriverProperties(props);
            }
        }
        return config;
    }

    void putIfNotNull(Properties props, String key, Object value) {
        if (value != null) {
            props.setProperty(key, value.toString());
        }
    }

}
