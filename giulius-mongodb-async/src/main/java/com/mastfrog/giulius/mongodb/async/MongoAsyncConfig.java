/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.giulius.mongodb.async;

import com.mongodb.async.client.MongoClientSettings;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;

/**
 * Implemented by GiuliusMongoAsyncModule and other classes that delegate
 * to it.
 *
 * @author Tim Boudreau
 */
public interface MongoAsyncConfig<SpecificType extends MongoAsyncConfig> {

    String SETTINGS_KEY_DATABASE_NAME = "_dbName";
    String SETTINGS_KEY_MAX_CONNECTIONS = "mongo.max.connections";
    String SETTINGS_KEY_MAX_WAIT_MILLIS = "mongo.max.wait.millis";
    String SETTINGS_KEY_MONGO_CONNECTION_POOL_MAINTENANCE_FREQUENCY_MILLIS = "mongo.connection.pool.maintenance.frequency.millis";
    String SETTINGS_KEY_MONGO_CONNECTION_POOL_MAINTENANCE_INITIAL_DELAY_MILLIS = "mongo.connection.pool.maintenance.initial.delay.millis";
    String SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_CONNECTION_LIFETIME_MILLIS = "mongo.connection.pool.max.connection.lifetime.millis";
    String SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_IDLE_TIME_MILLIS = "mongo.connection.pool.max.idle.time.millis";
    String SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_SIZE = "mongo.connection.pool.size.max";
    String SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_WAIT_QUEUE_SIZE = "mongo.connection.pool.max.wait.queue.size";
    String SETTINGS_KEY_MONGO_CONNECTION_POOL_MAX_WAIT_TIME_MILLIS = "mongo.connection.pool.max.wait.time.millis";
    String SETTINGS_KEY_MONGO_CONNECTION_POOL_MIN_SIZE = "mongo.connection.pool.size.min";
    String SETTINGS_KEY_MONGO_DEFAULT_WRITE_CONCERN = "mongo.write.concern";
    String SETTINGS_KEY_MONGO_HOST = "mongoHost";
    String SETTINGS_KEY_MONGO_PASSWORD = "mongo.password";
    String SETTINGS_KEY_MONGO_PORT = "mongoPort";
    String SETTINGS_KEY_MONGO_READ_PREFERENCE = "mongo.readPreference";
    String SETTINGS_KEY_MONGO_SSL = "mongo.ssl";
    String SETTINGS_KEY_MONGO_SSL_INVALID_HOSTNAMES_ALLOWED = "mongo.ssl.allow.invalid.hostnames";
    String SETTINGS_KEY_MONGO_USER = "mongo.user";

    /**
     * Bind the collection with the passed name to &#064;Named DBCollection wth
     * the passed binding name
     *
     * @param bindingName The binding used in &#064;Named
     * @param collectionName The collection name
     * @return this
     */
    SpecificType bindCollection(String bindingName, String collectionName);

    <T> SpecificType bindCollection(String bindingName, String collectionName, Class<T> type);

    SpecificType withClientSettings(MongoClientSettings settings);

    SpecificType withCodec(Codec<?> prov);

    SpecificType withCodec(Class<? extends Codec<?>> prov);

    SpecificType withCodecProvider(CodecProvider prov);

    SpecificType withCodecProvider(Class<? extends CodecProvider> prov);

    <T> SpecificType bindCollection(String bindingName, Class<T> type);

    SpecificType bindCollection(String bindingName);

    SpecificType withInitializer(Class<? extends MongoAsyncInitializer> initializerType);
    
    SpecificType withDynamicCodecs(Class<? extends DynamicCodecs> codecs);
}
