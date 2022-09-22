/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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
package com.mastfrog.mongodb.init;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.mastfrog.mongodb.migration.Migration;
import com.mastfrog.mongodb.migration.MigrationBuilder;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A guice module for attaching collection and index initialization on startup.
 *
 * @author Tim Boudreau
 */
public class MongoInitModule extends AbstractModule {

    private CollectionsInfo info;
    private final Set<Migration> migrations = new LinkedHashSet<>();

    public MigrationBuilder<MongoInitModule> addMigration(String name, int toVersion) {
        return new MigrationBuilder<>(name, toVersion, (m) -> {
            migrations.add(m);
            return MongoInitModule.this;
        }
        );
    }

    /**
     * Get a builder which can be used to define collections and their
     * characteristics. This method can be used exactly once per module
     * instance.
     *
     * @return A builder
     */
    public CollectionsInfoBuilder<MongoInitModule> withCollections() {
        if (info != null) {
            throw new IllegalStateException("Collection info already configured");
        }
        return new CollectionsInfoBuilder<>(this, (ifo) -> {
            MongoInitModule.this.info = ifo;
        });
    }

    @Override
    protected void configure() {
        if (info != null) {
            bind(CollectionsInfo.class).toInstance(info);
            bind(InitCollectionsInitializer.class).asEagerSingleton();
        }
        bind(new TypeLiteral<Set<Migration>>() {
        }).toInstance(new LinkedHashSet<>(migrations));
        if (!migrations.isEmpty()) {
            bind(InitMigrationsInitializer.class).asEagerSingleton();
        }
    }
}
