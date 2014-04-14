package com.mastfrog.giulius.jdbc;

import com.google.inject.ImplementedBy;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Use this to create tables if they do not exist.  Will be called the first
 * time a connection is created.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(JdbcInitializerImpl.class)
public interface JdbcInitializer {

    Connection onFirstConnect(Connection connection) throws SQLException, IOException;
}
