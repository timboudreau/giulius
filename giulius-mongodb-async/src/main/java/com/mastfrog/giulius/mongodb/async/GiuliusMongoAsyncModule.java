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

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.Checks;
import com.mastfrog.util.ConfigurationError;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoCollection;
import com.mongodb.async.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import static java.util.Arrays.asList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.bson.Document;
import org.bson.codecs.BsonValueCodecProvider;
import org.bson.codecs.Codec;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.ValueCodecProvider;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import org.bson.codecs.configuration.CodecRegistry;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;

/**
 * Supports MongoDB Guice bindings, allowing &#064;Named to be used to inject
 * collections, and similar. Binds the following
 * <ul>
 * <li>MongoCLientSettings (optional - you can provide your own) - the default
 * one is initialized from settings properties, using key names defined as
 * constant string fields on this class</li>
 * <li>CodecRegistry (used by MongoClientSettings if you don't provide your
 * own)</li>
 * <li>MongoClient</li>
 * <li>MongoDatabase</li>
 * <li>MongoCollection - bind multiple collections and inject them using
 * &#064;Named</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public class GiuliusMongoAsyncModule extends AbstractModule implements MongoAsyncConfig<GiuliusMongoAsyncModule> {

    private MongoClientSettings settings;
    private volatile boolean done;
    private final Set<CollectionBinding<?>> bindings = new HashSet<>();
    private final Set<Class<? extends MongoAsyncInitializer>> initializers = new HashSet<>();

    private final Set<CodecProvider> codecProviders = new HashSet<>();
    private final Set<Codec<?>> codecs = new HashSet<>();
    private final Set<Class<? extends Codec<?>>> codecTypes = new HashSet<>();
    private final Set<Class<? extends CodecProvider>> codecProviderTypes = new HashSet<>();

    /**
     * Add a codec to decode objects from BSON.
     *
     * @param prov The provider
     * @return this
     */
    @Override
    public GiuliusMongoAsyncModule withCodecProvider(CodecProvider prov) {
        checkDone();
        checkSettings();
        codecProviders.add(prov);
        return this;
    }

    private void checkSettings() {
        if (settings != null) {
            throw new ConfigurationError("You are providing your own "
                    + "MongoClientSettings - set up its codec registry directly"
                    + "in that case");
        }
    }

    /**
     * Add a codec to decode objects from BSON.
     *
     * @param prov The codec
     * @return this
     */
    @Override
    public GiuliusMongoAsyncModule withCodec(Codec<?> prov) {
        checkDone();
        checkSettings();
        codecs.add(prov);
        return this;
    }

    /**
     * Add a codec to decode objects from BSON, by type. The passed
     * CodecProvider will be instantiated by Guice and may contain injected
     * elements.
     *
     * @param prov The provider
     * @return this
     */
    @Override
    public GiuliusMongoAsyncModule withCodecProvider(Class<? extends CodecProvider> prov) {
        checkDone();
        checkSettings();
        codecProviderTypes.add(prov);
        return this;
    }

    /**
     * Add a codec to decode objects from BSON, by type. The passed Codec will
     * be instantiated by Guice and may contain injected elements.
     *
     * @param prov The provider
     * @return this
     */
    @Override
    public GiuliusMongoAsyncModule withCodec(Class<? extends Codec<?>> prov) {
        checkDone();
        checkSettings();
        codecTypes.add(prov);
        return this;
    }

    private Class<? extends DynamicCodecs> dynCodecs;

    public GiuliusMongoAsyncModule withDynamicCodecs(Class<? extends DynamicCodecs> codecs) {
        checkDone();
        if (dynCodecs != null) {
            throw new ConfigurationError("Dynamic codecs already set");
        }
        this.dynCodecs = codecs;
        return this;
    }

    static class DefaultFallbackCodecs implements DynamicCodecs {

        @Override
        public <T> Codec<T> createCodec(Class<T> type, CodecConfigurationException ex) {
            throw ex;
        }
    }

    @Singleton
    private class CodecRegistryImpl implements CodecRegistry {

        private final Provider<Dependencies> deps;
        private CodecRegistry registry;
        private final Provider<DynamicCodecs> fallback;

        CodecRegistryImpl(Provider<Dependencies> deps, Provider<DynamicCodecs> fallback) {
            this.deps = deps;
            this.fallback = fallback;
        }

        private CodecRegistry get() {
            if (registry != null) {
                return registry;
            }
            Dependencies deps = this.deps.get();
            List<CodecProvider> providers = new LinkedList<>(GiuliusMongoAsyncModule.this.codecProviders);
            List<Codec<?>> codecs = new LinkedList<>(GiuliusMongoAsyncModule.this.codecs);
            for (Class<? extends CodecProvider> c : codecProviderTypes) {
                providers.add(deps.getInstance(c));
            }
            for (Class<? extends Codec<?>> c : codecTypes) {
                codecs.add(deps.getInstance(c));
            }
            int total = providers.size() + codecs.size();
            if (total == 0) {
                return DEFAULT_CODEC_REGISTRY;
            }
            List<CodecRegistry> all = new LinkedList<>();
            if (!codecs.isEmpty()) {
                CodecRegistry forProviders = CodecRegistries.fromCodecs(codecs);
                all.add(forProviders);
            }
            if (!providers.isEmpty()) {
                CodecRegistry forCodecs = CodecRegistries.fromProviders(providers);
                all.add(forCodecs);
            }
            all.add(DEFAULT_CODEC_REGISTRY);
            return registry = CodecRegistries.fromRegistries(all);
        }

        @Override
        public <T> Codec<T> get(Class<T> type) {
            try {
                return get().get(type);
            } catch (CodecConfigurationException ex) {
                return fallback.get().createCodec(type, ex);
            }
        }
    }
    // XXX when 3.1.0 is stable, replace with MongoClients.getDefaultCodecRegistry()
    private static final CodecRegistry DEFAULT_CODEC_REGISTRY
            = fromProviders(asList(new ValueCodecProvider(),
                    new DocumentCodecProvider(),
                    new BsonValueCodecProvider()));

    private void checkDone() {
        if (done) {
            throw new ConfigurationError("Cannot configure module after the injector has been initialized");
        }
    }

    /**
     * Use the passed client settings instead of deriving them from settings
     * key/value pairs.
     *
     * @param settings The settings
     * @return this
     */
    @Override
    public GiuliusMongoAsyncModule withClientSettings(MongoClientSettings settings) {
        checkDone();
        this.settings = settings;
        return this;
    }

    /**
     * Bind the collection with the passed name to &#064;Named DBCollection of
     * the same name, so it is injectable as
     * <code>&#064;Named(&quot;someBinding&quot;) MongoCollection&lt;MyType&gt;</code>.
     * where <code>MyType</code> is tha passed type.
     *
     * @param bindingName The binding and collection name
     * @return this
     */
    public <T> GiuliusMongoAsyncModule bindCollection(String bindingName, Class<T> type) {
        return bindCollection(bindingName, bindingName, type);
    }

    /**
     * Bind a collection so it is injectable as
     * <code>&#064;Named(&quot;someBinding&quot;) MongoCollection&lt;Document&gt;</code>.
     *
     * @param bindingName The name of the binding <i>and</i> the collection in
     * question
     * @return this
     */
    public GiuliusMongoAsyncModule bindCollection(String bindingName) {
        bindCollection(bindingName, bindingName);
        return this;
    }

    /**
     * Add an initializer which will be called when collections are created, and
     * before and after the <code>MongoClient</code> is initialized. This can
     * also be accomplished by simply binding the initializer as an eager
     * singleton, if the module in question does not have direct access to this
     * one.
     *
     * @param initializerType The type of initializer
     * @return this
     */
    public GiuliusMongoAsyncModule withInitializer(Class<? extends MongoAsyncInitializer> initializerType) {
        checkDone();
        initializers.add(initializerType);
        return this;
    }

    /**
     * Bind the collection with the passed name to &#064;Named DBCollection wth
     * the passed binding name
     *
     * @param bindingName The binding used in &#064;Named
     * @param collectionName The collection name
     * @return this
     */
    @Override
    public GiuliusMongoAsyncModule bindCollection(String bindingName, String collectionName) {
        checkDone();
        bindings.add(new CollectionBinding<Document>(collectionName, bindingName, null, Document.class));
        return this;
    }

    /**
     * Bind the collection with the passed name to &#064;Named DBCollection of
     * the same name, so it is injectable as
     * <code>&#064;Named(&quot;someBinding&quot;) MongoCollection&lt;MyType&gt;</code>.
     * where <code>MyType</code> is tha passed type.
     *
     * @param bindingName The name of the binding that will appear in
     * &#064;Named annotations
     * @param collectionName The name of the collection to be created/used in
     * MongoDB, which may differ from the binding name
     * @return this
     */
    @Override
    public <T> GiuliusMongoAsyncModule bindCollection(String bindingName, String collectionName, Class<T> type) {
        checkDone();
        bindings.add(new CollectionBinding<T>(collectionName, bindingName, null, type));
        return this;
    }

    @Override
    protected void configure() {
        for (Class<? extends MongoAsyncInitializer> itype : this.initializers) {
            bind(itype).asEagerSingleton();;
        }
        if (settings != null) {
            bind(MongoClientSettings.class).toInstance(settings);
        } else {
            bind(MongoClientSettings.class).toProvider(MongoClientSettingsProvider.class).asEagerSingleton();
        }
        if (this.dynCodecs != null) {
            bind(DynamicCodecs.class).to(this.dynCodecs);
        }
        bind(CodecRegistry.class).toInstance(new CodecRegistryImpl(binder().getProvider(Dependencies.class), binder().getProvider(DynamicCodecs.class)));
        // Bind this as an eager singleton so that the client shutdown hook runs before the
        // MongoHarness shutdown hook shuts down the server - otherwise, can get exceptions thrown
        // during shutdown
        bind(MongoClient.class).toProvider(AsyncMongoClientProvider.class);
        bind(MongoDatabase.class).toProvider(MongoDatabaseProvider.class).in(Scopes.SINGLETON);
        for (CollectionBinding<?> binding : bindings) {
            binding.bind(binder());
        }
    }

    private static final class CollectionBinding<T> {

        private final String collection;
        private final String bindingName;
        private final CreateCollectionOptions opts;
        private Class<T> type;

        public CollectionBinding(String collection, String bindingName, CreateCollectionOptions opts, Class<T> type) {
            Checks.notNull("collection", collection);
            this.collection = collection;
            this.bindingName = bindingName == null ? collection : bindingName;
            this.opts = opts != null ? opts : new CreateCollectionOptions();
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof CollectionBinding && ((CollectionBinding) obj).collection.equals(collection);
        }

        @Override
        public int hashCode() {
            return collection.hashCode() * 37;
        }

        @Override
        public String toString() {
            return collection + ":" + bindingName + " with " + opts;
        }

        @SuppressWarnings("unchecked")
        void bind(Binder binder) {
            Provider<MongoDatabase> dbProvider = binder.getProvider(MongoDatabase.class);
            Provider<KnownCollections> knownProvider = binder.getProvider(KnownCollections.class);
            Provider<MongoAsyncInitializer.Registry> inits = binder.getProvider(MongoAsyncInitializer.Registry.class);
            MongoTypedCollectionProvider<Document> docProvider = new MongoTypedCollectionProvider<>(dbProvider, collection, Document.class, knownProvider, opts, inits);
            binder.bind(MONGO_DOCUMENT_COLLECTION).annotatedWith(Names.named(bindingName)).toProvider(docProvider);
            if (type != Document.class) {
                MongoTypedCollectionProvider<T> typedProvider = new MongoTypedCollectionProvider<T>(dbProvider, collection, type, knownProvider, opts, inits);
                Type t = new FakeType<>(type);
                Key<MongoCollection<T>> key = (Key<MongoCollection<T>>) Key.get(t, Names.named(bindingName));
                binder.bind(key).toProvider(typedProvider);
            }
        }
    }

    /**
     * TypeLiteral for MongoCollection parameterized on BSON Document.
     */
    public static final TypeLiteral<MongoCollection<Document>> MONGO_DOCUMENT_COLLECTION = new TL();

    static class TL extends TypeLiteral<MongoCollection<Document>> {

    }

    static class FakeType<T> implements ParameterizedType {

        private final Class<T> genericType;

        public FakeType(Class<T> genericType) {
            this.genericType = genericType;
        }

        public String getTypeName() {
            return MongoCollection.class.getName();
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{genericType};
        }

        public Type getRawType() {
            return MongoCollection.class;
        }

        public Type getOwnerType() {
            return null;
        }
    }
}
