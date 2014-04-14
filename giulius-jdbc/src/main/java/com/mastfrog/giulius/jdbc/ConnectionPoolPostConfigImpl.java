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
