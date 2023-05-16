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
 */
package com.mastfrog.giulius.mongodb.reactive;

import java.util.Date;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import org.bson.types.ObjectId;

/**
 * Makes it easier to compare test results by having test fixtures consistently
 * have the same IDs whenever tests are run.
 *
 * @author Tim Boudreau
 */
public final class PredictableObjectIds {

    static final ThreadLocal<IntSupplier> ct = ThreadLocal.withInitial(IS::new);
    private static final Date DATE = new Date(1516264455073L);

    public static ObjectId nextId() {
        return new ObjectId(DATE, ct.get().getAsInt());
    }

    PredictableObjectIds() {
    }

    private static final class IS implements IntSupplier, Supplier<Integer> {

        private int ints = 0;

        @Override
        public int getAsInt() {
            return ints++;
        }

        @Override
        public Integer get() {
            return getAsInt();
        }
    }
}
