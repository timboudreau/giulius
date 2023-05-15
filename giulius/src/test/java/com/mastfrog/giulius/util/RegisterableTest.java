/*
 * The MIT License
 *
 * Copyright 2023 Mastfrog Technologies.
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
package com.mastfrog.giulius.util;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.giulius.Ordered;
import com.mastfrog.giulius.util.RegisterableTest.ThingRegisterable.ThingRegistry;
import com.mastfrog.giulius.util.Registerable.*;
import java.util.ArrayList;
import static java.util.Arrays.asList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Tim Boudreau
 */
public class RegisterableTest {

    private ThingRegistry reg;
    private ThingRegistry secondReg;

    @Test
    public void testRegistry() {
        assertNotNull(reg);

        List<String> regOrder = reg.registrationOrder();
        assertEquals(asList("two", "four", "one", "three"), regOrder);
        List<String> returnOrder = strings();
        assertEquals(asList("one", "two", "three", "four"), returnOrder);
    }

    @Test
    public void testSeparateInjectorsDoNotSeeEachOthersContents() {
        assertEquals(asList("five", "six"), moreStrings());
    }

    private List<Thing> things() {
        List<Thing> t = new ArrayList<>();
        for (ThingRegisterable tr : reg) {
            t.add(tr);
        }
        return t;
    }

    private List<Thing> moreThings() {
        List<Thing> t = new ArrayList<>();
        for (ThingRegisterable tr : secondReg) {
            t.add(tr);
        }
        return t;
    }

    private List<String> strings() {
        List<String> result = new ArrayList<>();
        for (Thing t : things()) {
            result.add(t.thing());
        }
        return result;
    }

    private List<String> moreStrings() {
        List<String> result = new ArrayList<>();
        for (Thing t : moreThings()) {
            result.add(t.thing());
        }
        return result;
    }

    @Before
    public void before() {
        reg = Guice.createInjector(new Things()).getInstance(ThingRegistry.class);
        secondReg = Guice.createInjector(new MoreThings()).getInstance(ThingRegistry.class);
    }

    static abstract class ThingRegisterable extends Registerable<ThingRegisterable, ThingRegistry> implements Thing {

        @Inject
        ThingRegisterable(ThingRegistry registry) {
            super(registry);
        }

        @Singleton
        static class ThingRegistry extends AbstractRegistry<ThingRegisterable, ThingRegistry> {

            private final List<String> registrationOrder = new ArrayList<>();

            public ThingRegistry() {
                super(true);
            }

            @Override
            protected <S extends ThingRegisterable> S validate(S obj) {
                registrationOrder.add(obj.thing());
                return obj;
            }

            List<String> registrationOrder() {
                return registrationOrder;
            }
        }
    }

    interface Thing {

        String thing();
    }

    static class Things extends AbstractModule {

        @Override
        protected void configure() {
            // Bind in a different order
            bind(ThingTwo.class).asEagerSingleton();
            bind(ThingFour.class).asEagerSingleton();
            bind(ThingOne.class).asEagerSingleton();
            bind(ThingThree.class).asEagerSingleton();
        }
    }

    static class MoreThings extends AbstractModule {

        @Override
        protected void configure() {
            // Bind in a different order
            bind(ThingSix.class).asEagerSingleton();
            bind(ThingFive.class).asEagerSingleton();
        }
    }

    @Ordered(400)
    static class ThingFour extends ThingRegisterable {

        @Inject
        ThingFour(ThingRegistry registry) {
            super(registry);
        }

        @Override
        public String thing() {
            return "four";
        }
    }

    @Ordered(100)
    static class ThingOne extends ThingRegisterable {

        @Inject
        ThingOne(ThingRegistry registry) {
            super(registry);
        }

        @Override
        public String thing() {
            return "one";
        }

    }

    @Ordered(200)
    static class ThingTwo extends ThingRegisterable {

        @Inject
        ThingTwo(ThingRegistry registry) {
            super(registry);
        }

        @Override
        public String thing() {
            return "two";
        }
    }

    @Ordered(300)
    static class ThingThree extends ThingRegisterable {

        @Inject
        ThingThree(ThingRegistry registry) {
            super(registry);
        }

        @Override
        public String thing() {
            return "three";
        }
    }

    @Ordered(500)
    static class ThingFive extends ThingRegisterable {

        @Inject
        ThingFive(ThingRegistry registry) {
            super(registry);
        }

        @Override
        public String thing() {
            return "five";
        }
    }

    @Ordered(600)
    static class ThingSix extends ThingRegisterable {

        @Inject
        ThingSix(ThingRegistry registry) {
            super(registry);
        }

        @Override
        public String thing() {
            return "six";
        }
    }

}
