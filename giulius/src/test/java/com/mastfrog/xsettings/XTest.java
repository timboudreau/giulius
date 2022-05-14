/*
 * The MIT License
 *
 * Copyright 2010-2018 Tim Boudreau.
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
package com.mastfrog.xsettings;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author tim
 */
public class XTest {

    @Test
    public void test() {
        MM mm = new MM();
        Injector inj = Guice.createInjector(mm);
        IFace i = inj.getInstance(IFace.class);
        assertNotNull(i);
    }

    public static class MM extends AbstractModule {

        private final ThreadLocal<TypeLiteral<?>> loc = new ThreadLocal<>();

        @Override
        @SuppressWarnings("unchecked")
        protected void configure() {
            bind(IFace.class).to(Implementation.class);

            bind(Settings.class).toProvider(new P());

            Matcher<? super TypeLiteral<?>> m;


            m = Matchers.only(TypeLiteral.get(IFace.class));



            m = new Matcher() {
                @Override
                public boolean matches(Object t) {
                    if (t instanceof TypeLiteral) {
                    }
                    return true;
                }

                @Override
                public Matcher and(Matcher other) {
                    return this;
                }

                @Override
                public Matcher or(Matcher other) {
                    return this;
                }
            };


            binder().bindListener(m, new TypeListener() {
                @Override
                public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                    if (type.getRawType() == Stage.class) {
                        return;
                    }
                    if (type.getRawType() == P.class) {
                        return;
                    }
                    loc.set(type);
                }
            });
        }

        Namespace findNamespace(TypeLiteral<?> l) {
            return findNamespace(l, new HashSet<TypeLiteral<?>>());
        }

        Namespace findNamespace(TypeLiteral<?> l, Set<TypeLiteral<?>> seen) {
            Namespace ns = l.getRawType().getAnnotation(Namespace.class);
            if (ns == null) {
                Package pk = l.getRawType().getPackage();
                ns = pk.getAnnotation(Namespace.class);
            }
            if (ns == null) {
                Class<?> sup = l.getRawType().getSuperclass();
                if (sup != Object.class) {
                    TypeLiteral supType = TypeLiteral.get(sup);
                    if (!seen.contains(supType)) {
                        ns = findNamespace(supType, seen);
                    }
                }
                if (ns == null) {
                    for (Class<?> iface : l.getRawType().getInterfaces()) {
                        TypeLiteral<?> tl = TypeLiteral.get(iface);
                        if (!seen.contains(tl)) {
                            ns = findNamespace(tl, seen);
                        }
                        if (ns != null) {
                            break;
                        }
                    }
                }
            }
            return ns;
        }

        class P implements Provider<Settings> {

            @Override
            public Settings get() {
                TypeLiteral<?> l = loc.get();
                Namespace ns = null;
                if (loc.get() != null) {
                    ns = findNamespace(loc.get());
                }
                try {
                    String namespace = ns == null ? "default" : ns.value();
                    Settings s = new SettingsBuilder(namespace).add("bar", namespace.equals("foo") ? "true" : "false").build();
                    return s;
                } catch (IOException ex) {
                    throw new Error(ex);
                }
            }
        }
    }

    @Namespace("foo")
    public interface IFace {

        public Boolean getFoo();
    }

    @Namespace("foo")
    public static class Implementation implements IFace {

        private final Settings settings;

        @Inject
        public Implementation(Settings settings) {
            this.settings = settings;
        }

        @Override
        public Boolean getFoo() {
            return settings.getBoolean("bar");
        }
    }
}
