/*
 * Copyright 2018, Mighty Learning Objects.  All rights reserved.
 */
package com.mastfrog.giulius.mongodb.async;

import com.mastfrog.util.thread.FactoryThreadLocal;
import java.util.Date;
import java.util.function.IntSupplier;
import org.bson.types.ObjectId;

/**
 * Makes it easier to compare test results by having test fixtures consistently have the same IDs whenever tests are
 * run.
 *
 * @author Tim Boudreau
 */
public final class PredictableObjectIds {

    static int count = 1;
    static short pcount = 1;
    static int ccount = 1;
    @SuppressWarnings("StaticNonFinalUsedInInitialization")
    static final FactoryThreadLocal<Integer> loc = new FactoryThreadLocal<>( () -> {
        return count++;
    } );
    static final FactoryThreadLocal<Short> proc = new FactoryThreadLocal<>( () -> {
        return pcount++;
    } );
    static final FactoryThreadLocal<IntSupplier> ct = new FactoryThreadLocal<>( () -> {
        return new IntSupplier() {
            private int ints = 0;

            @Override
            public int getAsInt() {
                return ints++;
            }
        };
    } );
    private static final Date DATE = new Date( 1516264455073L );

    public static ObjectId nextId() {
        return new ObjectId( DATE, loc.get(), proc.get(), ct.get().getAsInt() );
    }

    PredictableObjectIds() {
    }
}
