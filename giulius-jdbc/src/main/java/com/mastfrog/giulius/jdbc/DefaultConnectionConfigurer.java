package com.mastfrog.giulius.jdbc;

import com.google.inject.Inject;
import static com.mastfrog.giulius.jdbc.JdbcModule.HINT_SCROLLABLE_CURSORS;
import com.mastfrog.settings.Settings;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 *
 * @author Tim Boudreau
 */
final class DefaultConnectionConfigurer implements ConnectionConfigurer {

    private final Settings settings;

    @Inject
    public DefaultConnectionConfigurer(Settings settings) {
        this.settings = settings;
    }

    @Override
    public Connection onProvideConnection(Connection connection) throws SQLException {
        if (settings.getBoolean(HINT_SCROLLABLE_CURSORS, false)) {
            connection.setAutoCommit(false);
            connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
            connection.setReadOnly(true);
        }
        return connection;
    }
}
