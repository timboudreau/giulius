/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Support for ordering things.  Can be used effectively with Guice
 * Multibinder to force an order onto a Set of injected items.
 *
 * @author Tim Boudreau
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Ordered {

    int value();

    public static final class OrderedTypeComparator implements Comparator<Class<?>> {
        private static final Set<String> WARNED_CLASSES = new HashSet<>();
        private void warn(Class<?> type) {
            boolean asserts = false;
            assert asserts = true;
            if (!asserts) {
                return;
            }
            if (!WARNED_CLASSES.contains(type.getName())) {
                Logger.getLogger(OrderedTypeComparator.class.getName()).log(
                        Level.WARNING, "{0} does not specify the "
                        + "@Ordered annotation", type.getName());
                WARNED_CLASSES.add(type.getName());
            }
        }

        @Override
        public int compare(Class<?> o1, Class<?> o2) {
            Ordered pos1 = o1.getAnnotation(Ordered.class);
            Ordered pos2 = o2.getAnnotation(Ordered.class);
            if (pos1 == null && pos2 != null) {
                warn(o1);
                return 1;
            }
            if (pos1 != null && pos2 == null) {
                warn(o2);
                return -1;
            }
            if (pos1 == null) {
                warn(o1);
                warn(o2);
                return 0;
            }
            int p1 = pos1.value();
            int p2 = pos2.value();
            return p1 == p2 ? 0 : p1 > p2 ? 1 : -1;
        }
    }

    public static final class OrderedObjectComparator implements Comparator<Object> {
        private final OrderedTypeComparator typeComparator = new OrderedTypeComparator();

        @Override
        public int compare(Object a, Object b) {
            if (a == null && b != null) {
                return 1;
            } else if (a != null && b == null) {
                return -1;
            } else if (a == null && b == null) {
                return 0;
            }
            return typeComparator.compare(a.getClass(), b.getClass());
        }
    }
}
