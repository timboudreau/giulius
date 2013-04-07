/*
 *               BSD LICENSE NOTICE
 * Copyright (c) 2010-2012, Tim Boudreau
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

import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.Ordered;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.util.Set;
import com.google.inject.Module;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.mastfrog.settings.SettingsBuilder;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tim
 */
public class BindingTest {
    ThingOne one;
    ThingTwo two;
    ThingThree three;
    ThingOne oneA;
    ThingTwo twoA;
    ThingThree threeA;

    @Before
    public void setUp() throws IOException {
        Dependencies d = new Dependencies(new SettingsBuilder().addDefaultsFromClasspath().build(), new M());

        one = d.getInstance(ThingOne.class);
        oneA = d.getInstance(ThingOne.class);
        two = d.getInstance(ThingTwo.class);
        twoA = d.getInstance(ThingTwo.class);
        three = d.getInstance(ThingThree.class);
        threeA = d.getInstance(ThingThree.class);
    }

    @Test
    public void test() {
        assertNotNull (one);
        assertSame (one.i, oneA.i);
        assertSame (two.i, twoA.i);
        assertSame (three.i, two.i);
        assertSame (three.i, twoA.i);
        assertSame (threeA.i, twoA.i);
    }

    @Test
    public void testMultibind() throws Throwable {
        com.mastfrog.settings.Settings s= SettingsBuilder.createDefault().build();
        Module a = new AbstractModule() {
            @Override
            protected void configure() {
                Multibinder<I> m = Multibinder.newSetBinder(binder(), I.class);
                m.addBinding().to(A.class);
                m.addBinding().to(B.class);
            }
        };
        Module b = new AbstractModule() {
            @Override
            protected void configure() {
                Multibinder<I> m = Multibinder.newSetBinder(binder(), I.class);
                m.addBinding().to(C.class);
            }
        };
        Dependencies d = new Dependencies(s, a, b);
        Set<I> all = d.getInstance(Key.get(new TypeLiteral<Set<I>>(){}));
        assertNotNull (all);
        assertEquals (3, all.size());
    }

    static int instancesA;
    static int instancesB;
    public static interface I {

    }

    @Ordered(1)
    public static class A implements I {
        int ct = instancesA++;
    }

    @Ordered(2)
    public static class B implements I {
        int ct = instancesB++;
    }

    @Ordered(3)
    public static class C implements I {

    }


    static final class M extends AbstractModule {
        @Override
        protected void configure() {
            bind (I.class).to(A.class).in(Scopes.SINGLETON);
            bind(B.class).in(Scopes.SINGLETON);
            bind (I.class).annotatedWith(Anno.class).to(B.class).in(Scopes.SINGLETON);
            bind (I.class).annotatedWith(Nothing.class).to(B.class).in(Scopes.SINGLETON);
        }
    }

    static final class ThingOne {
        @Inject
        public I i;
        @Override
        public String toString() {
            return super.toString() + " [" + i + "]";
        }
    }

    static final class ThingTwo {
        @Inject
        public @Anno I i;

        @Override
        public String toString() {
            return super.toString() + " [" + i + "]";
        }
    }

    static final class ThingThree {
        @Inject
        public @Nothing I i;

        @Override
        public String toString() {
            return super.toString() + " [" + i + "]";
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PACKAGE, ElementType.METHOD})
    @BindingAnnotation
    static @interface Nothing {

    }
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.PACKAGE, ElementType.METHOD})
    @BindingAnnotation
    static @interface Anno {

    }
}
