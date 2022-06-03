package com.mastfrog.giulius.mongo;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple bindings for MongoDB
 *
 * @author Tim Boudreau
 */
public final class GiuliusMongoModule extends AbstractModule implements MongoConfigModule {

    public static final String MONGO_HOST = "mongoHost";
    public static final String MONGO_PORT = "mongoPort";
    public static final String DATABASE_NAME = "_dbName";
    public static final String SETTINGS_KEY_MONGO_USER = "mongo.user";
    public static final String SETTINGS_KEY_MONGO_SSL = "mongo.ssl";
    public static final String SETTINGS_KEY_MONGO_SSL_INVALID_HOSTNAMES_ALLOWED = "mongo.ssl.allow.invalid.hostnames";
    public static final String SETTINGS_KEY_MONGO_PASSWORD = "mongo.password";
    public static final String SETTINGS_KEY_MAX_WAIT_MILLIS = "mongo.max.wait.millis";
    public static final int DEFAULT_MAX_WAIT_MILLIS = 20000;
    public static final String SETTINGS_KEY_MAX_CONNECTIONS = "mongo.max.connections";
    public static final int DEFAULT_MAX_CONNECTIONS = 1500;

    
    private boolean configured;
    private final Map<String, String> collectionForName = new HashMap<>();
    private final String databaseName;
    private final Set<Class<? extends MongoInitializer>> initializers = new HashSet<>();

    /**
     * Create a new module, attempting to find the main class name and use that
     * as the database name.
     */
    public GiuliusMongoModule() {
        this(getMainClassName());
    }

    /**
     * Create a new module, and use the specified database name
     *
     * @param databaseName
     */
    public GiuliusMongoModule(String databaseName) {
        this.databaseName = databaseName;
    }

    public GiuliusMongoModule addInitializer(Class<? extends MongoInitializer> type) {
        initializers.add(type);
        return this;
    }

    private static String getMainClassName() {
        Exception e = new Exception();
        StackTraceElement[] els = e.getStackTrace();
        String className = els[els.length - 1].getClassName();
        if (className.contains(".")) {
            int ix = className.lastIndexOf(".");
            if (ix < className.length() - 1) {
                className = className.substring(ix + 1);
            }
        }
        System.out.println("Using MongoDB database " + className.toLowerCase());
        return className.toLowerCase();
    }

    /**
     * Bind a collection so it can be injected using &#064;Named, using the same
     * name in code and as a collection name
     *
     * @param bindingName The name that will be used in code
     * @return this
     */
    public final GiuliusMongoModule bindCollection(String bindingName) {
        return bindCollection(bindingName, bindingName);
    }

    /**
     * Bind a collection so it can be injected using &#064;Named
     *
     * @param bindingName The name that will be used in code
     * @param collectionName The name of the actual collection
     * @return this
     */
    public final GiuliusMongoModule bindCollection(String bindingName, String collectionName) {
        if (configured) {
            throw new ConfigurationError("Cannot add bindings after application is started");
        }
        collectionForName.put(bindingName, collectionName);
        return this;
    }

    public final String getDatabaseName() {
        return databaseName;
    }

    @Override
    protected void configure() {
        configured = true;
        bind(String.class).annotatedWith(Names.named(DATABASE_NAME)).toInstance(databaseName);
        // We want to bail during startup if we can't contact the
        // database, so use eager singleton to ensure we'll be
        bind(MongoClient.class).toProvider(MongoClientProvider.class);
        bind(DB.class).toProvider(DatabaseProvider.class);
        bind(MongoInitializer.Registry.class).toInstance(new MongoInitializer.Registry());

        for (Class<? extends MongoInitializer> c : initializers) {
            bind(c).asEagerSingleton();
        }

        for (Map.Entry<String, String> e : collectionForName.entrySet()) {
            CollectionProvider prov = new CollectionProvider(binder().getProvider(DB.class),
                    e.getValue(), binder().getProvider(MongoInitializer.Registry.class));
            bind(DBCollection.class).annotatedWith(Names.named(e.getKey())).toProvider(
                    prov);
        }
    }
}
