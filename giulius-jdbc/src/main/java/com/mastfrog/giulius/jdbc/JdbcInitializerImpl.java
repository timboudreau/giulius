package com.mastfrog.giulius.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

final class JdbcInitializerImpl implements JdbcInitializer {

    @Override
    public Connection onFirstConnect(Connection connection) throws SQLException {
        //do nothing
        return connection;
    }
}
