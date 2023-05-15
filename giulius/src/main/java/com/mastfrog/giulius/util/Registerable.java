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

import com.google.inject.Singleton;
import com.mastfrog.giulius.Ordered;
import com.mastfrog.giulius.util.Registerable.Registry;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.ConfigurationError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * Basic abstraction for a simple pattern that eliminates the need for
 * complicated solutions like Guice multibindings, for creating registries of
 * objects of a type when multiple should be bound. Simply implement a concrete
 * type of Registry, and an abstract subtype of Registerable, and bind
 * implementations as eager singletons.
 *
 * @author Tim Boudreau
 */
public abstract class Registerable<T extends Registerable<T, R>, R extends Registry<T, R>> {

    protected Registerable(R registry) {
        registry.register(cast());
    }

    @SuppressWarnings("unchecked")
    protected final T cast() {
        return (T) this;
    }

    /**
     * A registry of objects which become registered on creation, which will be
     * bound as eager singletons to ensure they get discovered on startup.
     * <p>
     * <b>Important:</b> Subclasses <i>must</i> have the
     * <code>&#064;Singleton</code> annotation or an error will be thrown on
     * creation. Guice annotations are not heritable, and the registry pattern
     * only works if the registry is a singleton - otherwise each registered
     * object will register itself in a registry created for its constructor
     * which is immediately garbage-collected.
     * </p>
     *
     * @param <T> The registerable type
     * @param <R> This type
     */
    protected static abstract class Registry<T extends Registerable<T, R>, R extends Registry<T, R>> {

        protected Registry() {
            Singleton sing = getClass().getAnnotation(Singleton.class);
            if (sing == null) {
                throw new ConfigurationError(getClass().getName()
                        + " is not annotated with the @Singleton annotation. "
                        + "Singleton-ness is not heritable, and a registry will "
                        + "not work if not bound as a singleton.");
            }
        }

        /**
         * Register an object to this registry.
         *
         * @param <S> The type
         * @param obj The object being registered
         */
        protected abstract <S extends T> void register(S obj);
    }

    /**
     * Simple implementation of Registry over a map or a tree set depending on
     * whether the sorted constructor parameter is true. If sorted, will used
     * the &#064Ordered annotation on the types of objects registered to
     * determine the traversal order.
     * <p>
     * <b>Important:</b> Subclasses <i>must</i> have the
     * <code>&#064;Singleton</code> annotation or an error will be thrown on
     * creation. Guice annotations are not heritable, and the registry pattern
     * only works if the registry is a singleton - otherwise each registered
     * object will register itself in a registry created for its constructor
     * which is immediately garbage-collected.
     * </p>
     *
     * @param <T> The registerable type
     * @param <R> This type
     */
    protected static abstract class AbstractRegistry<T extends Registerable<T, R>, R extends Registry<T, R>>
            extends Registry<T, R> implements Iterable<T> {

        private final Collection<T> items;
        protected boolean used;

        protected AbstractRegistry(boolean sorted) {
            if (!sorted) {
                items = new ArrayList<>();
            } else {
                items = new TreeSet<>(new Ordered.OrderedObjectComparator());
            }
        }

        public synchronized boolean isEmpty() {
            return items.isEmpty();
        }

        /**
         * Perform any sanity checks of a to-be-registered object here.
         *
         * @param <S> The specific registeree type
         * @param obj The registeree
         * @return the registeree
         */
        protected <S extends T> S validate(S obj) {
            return obj;
        }

        @Override
        protected synchronized final <S extends T> void register(S obj) {
            if (used) {
                throw new IllegalStateException("Cannot register objects after the "
                        + getClass().getSimpleName() + " has had its contents read.");
            }
            items.add(notNull("obj", validate(obj)));
        }

        @Override
        public synchronized Iterator<T> iterator() {
            used = true;
            return new ArrayList<>(items).iterator();
        }
    }
}
