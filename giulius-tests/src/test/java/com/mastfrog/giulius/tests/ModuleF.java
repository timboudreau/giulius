package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;

/**
 *
 * @author Tim Boudreau
 */
public class ModuleF extends AbstractModule {

    @Override
    protected void configure() {
        bind (Integer.class).toInstance(47);
    }

}
