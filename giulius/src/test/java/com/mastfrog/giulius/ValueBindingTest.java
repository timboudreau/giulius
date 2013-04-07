/*                       BSD LICENSE NOTICE
 * Copyright (c) 2010-2012, Tim Boudreau, All Rights Reserved
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: 
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution. 
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
