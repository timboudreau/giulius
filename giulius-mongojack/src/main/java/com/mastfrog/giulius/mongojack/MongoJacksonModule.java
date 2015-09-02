package com.mastfrog.giulius.mongojack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.acteur.mongo.GiuliusMongoModule;
import com.mastfrog.acteur.mongo.MongoConfigModule;
import com.mastfrog.acteur.mongo.MongoInitializer;
import com.mongodb.DBCollection;
import java.util.LinkedList;
import java.util.List;
import org.mongojack.JacksonDBCollection;

/**
 * Wraps the Giulius mongo module with support for MongoJack which uses Jackson
 * to serialize/deserialize.
 *
 * @author Tim Boudreau
 */
public class MongoJacksonModule extends AbstractModule {

    private final List<Entry<?, ?>> entries = new LinkedList<>();

    private final MongoConfigModule mongo;

    public MongoJacksonModule(String name) {
        this(new GiuliusMongoModule(name));
    }

    public MongoJacksonModule(MongoConfigModule module) {
        mongo = module;
    }

    public MongoJacksonModule addInitializer(Class<? extends MongoInitializer> type) {
        mongo.addInitializer(type);
        return this;
    }

    public final <T, R> MongoJacksonModule bindCollection(String bindingName, TypeLiteral<JacksonDBCollection<T, R>> tl, Class<T> left, Class<R> right) {
        bindCollection(bindingName, bindingName, tl, left, right);
        return this;
    }

    public final <T, R> MongoJacksonModule bindCollection(String bindingName, String collectionName, TypeLiteral<JacksonDBCollection<T, R>> tl, Class<T> left, Class<R> right) {
        mongo.bindCollection(bindingName, collectionName);
        entries.add(new Entry<>(bindingName, tl, left, right));
        return this;
    }

    public MongoJacksonModule bindCollection(String bindingName) {
        mongo.bindCollection(bindingName);
        return this;
    }

    public MongoJacksonModule bindCollection(String bindingName, String collectionName) {
        mongo.bindCollection(bindingName, collectionName);
        return this;
    }

    public final String getDatabaseName() {
        return mongo.getDatabaseName();
    }

    @Override
    protected void configure() {
        install(mongo);
        Binder binder = binder();
        for (Entry<?, ?> e : entries) {
            e.bind(binder);
        }
        entries.clear();
    }

    private static final class Entry<T, R> {

        private final String bindingName;
        private final TypeLiteral<JacksonDBCollection<T, R>> tl;
        private final Class<T> left;
        private final Class<R> right;

        public Entry(String bindingName, TypeLiteral<JacksonDBCollection<T, R>> tl, Class<T> left, Class<R> right) {
            this.bindingName = bindingName;
            this.tl = tl;
            this.left = left;
            this.right = right;
        }

        void bind(Binder binder) {
            Named anno = Names.named(bindingName);
            Provider<DBCollection> collectionProvider = binder.getProvider(Key.get(DBCollection.class, anno));
            Provider<JacksonDBCollection<T, R>> result = new JacksonDBCollectionProvider<>(collectionProvider, left, right, binder.getProvider(ObjectMapper.class));
            binder.bind(tl).annotatedWith(anno).toProvider(result);
        }
    }

    private static final class JacksonDBCollectionProvider<T, R> implements Provider<JacksonDBCollection<T, R>> {

        private final Provider<DBCollection> dbCollection;
        private final Class<T> left;
        private final Class<R> right;
        private final Provider<ObjectMapper> mapper;
        private ObjectMapper mapperInstance;

        JacksonDBCollectionProvider(Provider<DBCollection> dbCollection, Class<T> left, Class<R> right, Provider<ObjectMapper> mapper) {
            this.dbCollection = dbCollection;
            this.left = left;
            this.right = right;
            this.mapper = mapper;
        }

        @Override
        public JacksonDBCollection<T, R> get() {
            DBCollection coll = dbCollection.get();
            // Ensure we don't pollute the globally bound object mapper
            ObjectMapper m = mapperInstance == null ? mapperInstance = mapper.get().copy() : mapperInstance;
            return JacksonDBCollection.wrap(coll, left, right, m);
        }

        public String toString() {
            return "JacksonDbCollection<" + left.getName() + "," + right.getName() + ">";
        }
    }
}
