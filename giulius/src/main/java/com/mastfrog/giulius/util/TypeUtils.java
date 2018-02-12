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
 */package com.mastfrog.giulius.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 *
 * @author Tim Boudreau
 */
public final class TypeUtils {

    public static <S, T> ParameterizedType generifiedType(Class<S> outerType, Class<T> typeParameter) {
        return new OneGenericFakeType<>( outerType, typeParameter );
    }

    public static <S, T, U> ParameterizedType generifiedType(Class<S> outerType, Class<T> typeParameter,
                                                             Class<U> typeParameter2) {
        return new TwoGenericFakeType<>( outerType, typeParameter, typeParameter2 );
    }

    public static <S, T, U, V> ParameterizedType generifiedType(Class<S> outerType, Class<T> typeParameter,
                                                                Class<U> typeParameter2, Class<V> typeParameter3) {
        return new ThreeGenericFakeType<>( outerType, typeParameter, typeParameter2, typeParameter3 );
    }

    private static final class OneGenericFakeType<R, T> implements ParameterizedType {

        private final Class<T> genericType;
        private final Class<R> topType;

        public OneGenericFakeType(Class<R> topType, Class<T> genericType) {
            this.topType = topType;
            this.genericType = genericType;
        }

        public String getTypeName() {
            return topType.getName();
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{genericType};
        }

        public Type getRawType() {
            return topType;
        }

        public Type getOwnerType() {
            return null;
        }

        public String toString() {
            return topType.getSimpleName() + '<' + genericType.getSimpleName() + '>';
        }
    }

    private static final class TwoGenericFakeType<R, T, U> implements ParameterizedType {

        private final Class<T> genericType;
        private final Class<R> topType;
        private final Class<U> otherGenericType;

        public TwoGenericFakeType(Class<R> topType, Class<T> genericType, Class<U> otherGenericType) {
            this.topType = topType;
            this.genericType = genericType;
            this.otherGenericType = otherGenericType;
        }

        public String getTypeName() {
            return topType.getName();
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{genericType, otherGenericType};
        }

        public Type getRawType() {
            return topType;
        }

        public Type getOwnerType() {
            return null;
        }

        public String toString() {
            return topType.getSimpleName() + '<' + genericType.getSimpleName() + ", " + otherGenericType.getSimpleName()
                   + '>';
        }
    }

    private static final class ThreeGenericFakeType<R, T, U, V> implements ParameterizedType {

        private final Class<T> genericType;
        private final Class<R> topType;
        private final Class<U> otherGenericType;
        private final Class<V> otherOtherGenericType;

        public ThreeGenericFakeType(Class<R> topType, Class<T> genericType, Class<U> otherGenericType,
                                    Class<V> otherOtherGenericType) {
            this.topType = topType;
            this.genericType = genericType;
            this.otherGenericType = otherGenericType;
            this.otherOtherGenericType = otherOtherGenericType;
        }

        public String getTypeName() {
            return topType.getName();
        }

        public Type[] getActualTypeArguments() {
            return new Type[]{genericType, otherGenericType, otherOtherGenericType};
        }

        public Type getRawType() {
            return topType;
        }

        public Type getOwnerType() {
            return null;
        }

        public String toString() {
            return topType.getSimpleName() + '<' + genericType.getSimpleName() + ", " + otherGenericType.getSimpleName()
                   + ", " + otherOtherGenericType.getSimpleName() + '>';
        }
    }

    private TypeUtils() {

    }
}
