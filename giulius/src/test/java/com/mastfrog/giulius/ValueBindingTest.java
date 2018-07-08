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
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Properties;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ValueBindingTest {
    private static final class M extends AbstractModule {
        private final Properties propsA = new Properties();
        private final Properties propsB = new Properties();
        private final Properties defaults = new Properties();

        M() {
            propsA.setProperty("a", "a");
            propsB.setProperty("a", "b");
            defaults.setProperty("a", "nothing");
        }
        private final ThreadLocal<TypeLiteral<?>> type = new ThreadLocal<>();

        @Override
        public void configure() {
            MyNamedImpl stringInjectionsOfA = new MyNamedImpl("a"); //in real life we'd iterate all the keys
            bind(String.class).annotatedWith(stringInjectionsOfA).toProvider(new PropsProvider(stringInjectionsOfA.value()));
            bindListener(Matchers.any(), new TypeListener() {
                @Override
                public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                    M.this.type.set(type);
                }
            });
        }

        private Class<?> getTheType() {
            return type.get().getRawType();
        }

        public Properties getProperties() {
            Class<?> injectingInto = getTheType();
            MyNamespace ns = injectingInto.getAnnotation(MyNamespace.class);
            Properties props = defaults; //fallback
            if (ns != null) {
                switch (ns.value()) {
                    case "one":
                        return propsA;
                    case "two":
                        return propsB;
                }
            }
            return props;
        }
        class PropsProvider extends DefaultBindingTargetVisitor<Object, Object> implements Provider<String> {
            private final String key;

            public PropsProvider(String key) {
                this.key = key;
            }

            @Override
            public String get() {
                return getProperties().getProperty(key);
            }
        }
    }
    @Target(ElementType.PARAMETER)
    @BindingAnnotation
    @Retention(RetentionPolicy.RUNTIME)
    @interface MyNamed {
        String value();
    }
    private static class MyNamedImpl implements MyNamed {
        private final String value;

        public MyNamedImpl(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return MyNamed.class;
        }

        public boolean equals(Object o) {
            return o instanceof MyNamed && value().equals(((MyNamed) o).value());
        }

        public int hashCode() {
            return (127 * "value".hashCode()) ^ value.hashCode();
        }
    }
    @Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.FIELD, ElementType.LOCAL_VARIABLE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface MyNamespace {
        String value();
    }
    @MyNamespace("one")
    static class InNamespaceOne {
        private final String value;

        @Inject
        public InNamespaceOne(@MyNamed("a") String value) {
            this.value = value;
        }
    }
    @MyNamespace("two")
    static class InNamespaceTwo {
        private final String value;

        @Inject
        public InNamespaceTwo(@MyNamed("a") String value) {
            this.value = value;
        }
    }
    static class NotNamespaced {
        private final String value;

        @Inject
        public NotNamespaced(@MyNamed("a") String value) {
            this.value = value;
        }
    }

    @Test
    public void test() {
        Injector inj = Guice.createInjector(new M());
        InNamespaceOne a = inj.getInstance(InNamespaceOne.class);
        InNamespaceTwo b = inj.getInstance(InNamespaceTwo.class);
        NotNamespaced c = inj.getInstance(NotNamespaced.class);
        assertEquals("a", a.value);
        assertEquals("b", b.value);
        assertEquals("nothing", c.value);
    }
}
