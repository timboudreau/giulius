/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau
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
package com.mastfrog.giulius.mongodb.reactive;

import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_DATABASE_NAME;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_CONNECTION_POOL_MAINTENANCE_FREQUENCY_MILLIS;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_CONNECTION_POOL_MAINTENANCE_INITIAL_DELAY_MILLIS;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_CONNECTION_LIFETIME_MILLIS;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_IDLE_TIME_MILLIS;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_SIZE;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_WAIT_TIME_MILLIS;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_CONNECTION_POOL_MIN_SIZE;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_DEFAULT_WRITE_CONCERN;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_HOST;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_PASSWORD;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_PORT;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_READ_PREFERENCE;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_SSL;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_SSL_INVALID_HOSTNAMES_ALLOWED;
import static com.mastfrog.giulius.mongodb.reactive.MongoAsyncConfig.SETTINGS_KEY_MONGO_USER;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.SslSettings;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistry;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
final class MongoClientSettingsProvider implements Provider<MongoClientSettings> {

    private final MongoClientSettings clientSettings;

    @Inject
    @SuppressWarnings("deprecation")
    MongoClientSettingsProvider(Settings settings, CodecRegistry registry, @Named(SETTINGS_KEY_DATABASE_NAME) String db, @Named(SETTINGS_KEY_MONGO_PORT) int mongoPort) {
        ReadPreference pref = ReadPreferences.find(settings.getString(SETTINGS_KEY_MONGO_READ_PREFERENCE)).get();

        ConnectionPoolSettings.Builder cp = ConnectionPoolSettings.builder();
        Integer maxSize = settings.getInt(SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_SIZE);
        if (maxSize != null) {
            cp.maxSize(maxSize);
        }
        Integer minSize = settings.getInt(SETTINGS_KEY_MONGO_CONNECTION_POOL_MIN_SIZE);
        if (minSize != null) {
            cp.minSize(minSize);
        }
        Long maxWaitTimeMS = settings.getLong(SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_WAIT_TIME_MILLIS);
        if (maxWaitTimeMS != null) {
            cp.maxWaitTime(maxWaitTimeMS, TimeUnit.MILLISECONDS);
        }
        Long maxConnectionLifeTimeMS = settings.getLong(SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_CONNECTION_LIFETIME_MILLIS);
        if (maxConnectionLifeTimeMS != null) {
            cp.maxConnectionLifeTime(maxConnectionLifeTimeMS, TimeUnit.MILLISECONDS);
        }
        Long maxConnectionIdleTimeMS = settings.getLong(SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_IDLE_TIME_MILLIS);
        if (maxConnectionIdleTimeMS != null) {
            cp.maxConnectionIdleTime(maxConnectionIdleTimeMS, TimeUnit.MILLISECONDS);
        }
        Long maintenanceFrequencyMS = settings.getLong(SETTINGS_KEY_MONGO_CONNECTION_POOL_MAINTENANCE_FREQUENCY_MILLIS);
        if (maintenanceFrequencyMS != null) {
            cp.maintenanceFrequency(maintenanceFrequencyMS, TimeUnit.MILLISECONDS);
        }
        Long maintenanceInitialDelayMS = settings.getLong(SETTINGS_KEY_MONGO_CONNECTION_POOL_MAINTENANCE_INITIAL_DELAY_MILLIS);
        if (maintenanceInitialDelayMS != null) {
            cp.maintenanceInitialDelay(maintenanceInitialDelayMS, TimeUnit.MILLISECONDS);
        }
        WriteConcern wc = findWriteConcern(settings.getString(SETTINGS_KEY_MONGO_DEFAULT_WRITE_CONCERN));

        boolean sslEnabled = settings.getBoolean(SETTINGS_KEY_MONGO_SSL, false);
        SslSettings ssl = SslSettings.builder().enabled(sslEnabled).invalidHostNameAllowed(settings.getBoolean(SETTINGS_KEY_MONGO_SSL_INVALID_HOSTNAMES_ALLOWED, true)).build();

        String mongoHost = settings.getString(SETTINGS_KEY_MONGO_HOST, "localhost");

        ServerAddress addr = new ServerAddress(mongoHost, mongoPort);

        ClusterSettings cluster = ClusterSettings.builder().hosts(Arrays.asList(addr)).mode(ClusterConnectionMode.SINGLE).build();

        String mongoUser = settings.getString(SETTINGS_KEY_MONGO_USER);
        String mongoPassword = settings.getString(SETTINGS_KEY_MONGO_PASSWORD);
        if ((mongoUser == null) != (mongoPassword == null)) {
            throw new ConfigurationError("Either both " + SETTINGS_KEY_MONGO_USER + " and "
                    + SETTINGS_KEY_MONGO_PASSWORD + " must be set, or neither may be.");
        }
        MongoCredential credential = null;
        if (mongoUser != null) {
            credential = MongoCredential.createCredential(mongoUser, db, mongoPassword.toCharArray());
        }

        MongoClientSettings.Builder sb = MongoClientSettings.builder()
                .readPreference(pref)
                .codecRegistry(registry)
                .applyToClusterSettings(bldr -> {
                    bldr.applySettings(cluster);
                })
                .applyToSslSettings(bldr -> {
                    bldr.applySettings(ssl);
                })
                .applyToConnectionPoolSettings(bldr -> {
                    bldr.applySettings(cp.build());
                })
                .writeConcern(wc);
        
        String appName = settings.getString("application.name");
        if (appName != null) {
            sb.applicationName(appName);
        }

        if (credential != null) {
            sb.credential(credential);
        }
        clientSettings = sb.build();
    }

    private WriteConcern findWriteConcern(String setting) {
        if (setting == null) {
            return WriteConcern.ACKNOWLEDGED;
        }
        return WriteConcern.valueOf(setting.toUpperCase());
    }

    @Override
    public MongoClientSettings get() {
        return clientSettings;
    }

}
