package com.mastfrog.giulius.jdbc;

import com.google.inject.ImplementedBy;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Sets connection properties before the connection is injected - the default
 * implementation will, for postgres, ensure that scrollable cursors can be used.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultConnectionConfigurer.class)
public interface ConnectionConfigurer {
    Connection onProvideConnection(Connection connection) throws SQLException;
}
