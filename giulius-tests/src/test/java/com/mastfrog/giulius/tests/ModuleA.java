package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
class ModuleA extends AbstractModule {

    @Override
    protected void configure() {
        bind (String.class).toInstance("A!");
        bind (new TypeLiteral<List<String>>(){}).toInstance(Arrays.asList("a1", "a2", "a3"));
    }
}
