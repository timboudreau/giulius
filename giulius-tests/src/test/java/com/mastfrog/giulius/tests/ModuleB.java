package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public class ModuleB extends AbstractModule {
    @Override
    protected void configure() {
        bind (String.class).toInstance("B!");
        bind (new TypeLiteral<List<String>>(){}).toInstance(Arrays.asList("b1", "b2", "b3"));
    }
}
