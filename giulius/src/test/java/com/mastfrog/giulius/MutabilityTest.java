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
package com.mastfrog.giulius;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Stage;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.Enumeration;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
public class MutabilityTest {

    @Test
    public void test() {
        M m = new M();
        Injector i = Guice.createInjector(Stage.PRODUCTION, m);

        T t = i.getInstance(T.class);
        assertEquals("This is a", t.a);

        m.p.setProperty("a", "Changed!");
        T t2 = i.getInstance(T.class);
        assertEquals("Changed!", t2.a);

        R r = i.getInstance(R.class);
        assertEquals(23, r.skidoo);
    }

    static class M extends AbstractModule {

        final Properties p = new Properties();

        M() {
            p.setProperty("a", "This is a");
            p.setProperty("b", "This is b");
            p.setProperty("skidoo", "" + 23);
        }

        @Override
        protected void configure() {
            bindProperties(binder(), p);
        }
    }

    public static void bindProperties(Binder binder, Properties properties) {
        binder = binder.skipSources(Names.class);
        // use enumeration to include the default properties
        for (Enumeration<?> e = properties.propertyNames(); e.hasMoreElements();) {
            String propertyName = (String) e.nextElement();
            Named n = Names.named(propertyName);
            PropertyProvider p = new PropertyProvider(propertyName, properties);
            binder.bind(Key.get(String.class, n)).toProvider(p);
            binder.bind(Key.get(Integer.class, n)).toProvider(new IntProvider(p));
            binder.bind(Key.get(Boolean.class, n)).toProvider(new BooleanProvider(p));
        }
    }

    private static class IntProvider implements Provider<Integer> {
        private final Provider<String> p;
        IntProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Integer get() {
            String s = p.get();
            return s == null ? null : Integer.parseInt(p.get());
        }
    }

    private static class BooleanProvider implements Provider<Boolean> {
        private final Provider<String> p;
        BooleanProvider(Provider<String> p) {
            this.p = p;
        }

        @Override
        public Boolean get() {
            String s = p.get();
            return s == null ? false : Boolean.valueOf(s);
        }
    }

    private static class PropertyProvider implements Provider<String> {
        private final String key;
        private final Properties props;
        PropertyProvider(String key, Properties props) {
            this.key = key;
            this.props = props;
        }

        @Override
        public String get() {
            return props.getProperty(key);
        }
    }

    static class T {

        private final String a;
        private final String b;

        @Inject
         T(@Named("a") String a, @Named("a") String b) {
            this.a = a;
            this.b = b;
        }
    }

    static class Q {

        private final String c;

        Q(@Named("c") String c) {
            this.c = c;
        }
    }

    static class R {

        final int skidoo;

        @Inject
         R(@Named("skidoo") int skidoo) {
            this.skidoo = skidoo;
        }
    }
}
