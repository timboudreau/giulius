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

import com.mastfrog.util.Checks;
import com.mastfrog.util.Streams;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Contains the definition of one table in the database which should be created
 * if not present the first time the database is connected to.
 *
 * @author Tim Boudreau
 */
public final class TableDefinition {

    private final String name;
    private final String definitionResource;
    private Class<?> relativeTo;

    /**
     * Create a table definition
     *
     * @param name The name of the table in the database
     * @param definitionResource A file on the classpath relative to the passed
     * Class, such that it can be loaded with Class.getResourceAsStream()
     * @param relativeTo The class to load relative to
     */
    public TableDefinition(String name, String definitionResource, Class<?> relativeTo) {
        Checks.notNull("name", name);
        Checks.notNull("definitionResource", definitionResource);
        Checks.notNull("relativeTo", relativeTo);
        this.relativeTo = relativeTo;
        this.name = name;
        this.definitionResource = definitionResource;
    }

    public String name() {
        return name;
    }

    /**
     * Drop the table in question. Use with care.
     *
     * @param connection A database connection
     * @throws SQLException If something goes wrong
     */
    public void drop(Connection connection) throws SQLException {
        Checks.notNull("connection", connection);
        String dropStatement = "drop table if exists " + name() + ";";
        try (Statement st = connection.createStatement()) {
            boolean result = st.execute(dropStatement);
        }
    }

    /**
     * Create the table, running the associated SQL. Will attempt to look up a
     * version of the resource specific to the database name as returned by
     * metadata.getDatabaseProductName().toLowerCase() - so if the resource is
     * named foo.sql and the database is mysql, will first try to look up
     * foo_mysql.sql and failing that use foo.sql.
     *
     * @param metadata The database metadata
     * @param connection The connection
     * @throws SQLException If something goes wrong
     */
    public void createIfNotPresent(DatabaseMetaData metadata, Connection connection) throws SQLException {
        Checks.notNull("metadata", metadata);
        Checks.notNull("connection", connection);
        boolean present;
        try (ResultSet tables = metadata.getTables(null, null, name, null)) {
            present = tables.next();
        }
        if (!present) {
            for (String line : definition(metadata.getDatabaseProductName())) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(line);
                }
            }
        }
    }

    private String withDbName(String dbName) {
        dbName = dbName.toLowerCase();
        int ix = definitionResource.lastIndexOf(".");
        if (ix < 0) {
            return null;
        }
        String prefix = definitionResource.substring(0, ix);
        String suffix = definitionResource.substring(ix + 1);
        String result = prefix + "_" + dbName + "." + suffix;
        return result;
    }

    String[] definition(String dbName) {
        InputStream in = relativeTo.getResourceAsStream(withDbName(dbName));
        if (in == null) {
            in = relativeTo.getResourceAsStream(definitionResource);
        }
        if (in == null) {
            throw new Error("No definition for table " + name + " at " 
                    + definitionResource + " relative to " + relativeTo.getName());
        }
        return Streams.readSql(in);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TableDefinition && ((TableDefinition) o).name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
