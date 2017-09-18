/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
import com.google.inject.Inject;
import com.google.inject.Scopes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class ShutdownHookRegistryTest {

    @Test
    public void testShutdownHooksAreRunInReverseOrder() throws IOException {
        Dependencies deps = new Dependencies(new M());
        ThingOne one = deps.getInstance(ThingOne.class);
        ThingTwo two = deps.getInstance(ThingTwo.class);
        ThingThree three = deps.getInstance(ThingThree.class);
        ThingThree three2 = deps.getInstance(ThingThree.class);
        assertSame(three, three2);
        deps.shutdown();

        assertTrue(one.hookRan);
        assertTrue(two.hookRan);
        assertTrue(three.hookRan);

        assertEquals(3, order.size());

        assertEquals(order, Arrays.asList(3,2,1));
    }

    static class M extends AbstractModule {

        @Override
        protected void configure() {
            bind(ThingOne.class).in(Scopes.SINGLETON);
            bind(ThingTwo.class).in(Scopes.SINGLETON);
            bind(ThingThree.class).in(Scopes.SINGLETON);
        }

    }

    private static final List<Integer> order = new ArrayList<>();
    public static final class ThingOne implements Runnable {

        private boolean hookRan;

        @Inject
        ThingOne(ShutdownHookRegistry reg) {
            reg.add(this);
        }

        @Override
        public void run() {
            hookRan = true;
            order.add(1);
        }
    }

    public static final class ThingTwo implements Runnable {

        private boolean hookRan;

        @Inject
        ThingTwo(ShutdownHookRegistry reg) {
            reg.add(this);
        }

        @Override
        public void run() {
            hookRan = true;
            order.add(2);
        }
    }

    public static final class ThingThree implements Runnable {
        boolean hookRan;
        @Inject
        ThingThree(ShutdownHookRegistry reg) {
            reg.add(this);
        }

        @Override
        public void run() {
            hookRan = true;
            order.add(3);
        }
    }

}
