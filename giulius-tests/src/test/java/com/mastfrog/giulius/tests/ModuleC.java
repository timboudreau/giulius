package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public class ModuleC extends AbstractModule {

    @Override
    protected void configure() {
        bind (StringBuilder.class).toInstance(new StringBuilder("Injected by C"));
        bind (new TypeLiteral<List<Integer>>(){}).toInstance(Arrays.asList(1, 2, 3, 4));
    }

}
