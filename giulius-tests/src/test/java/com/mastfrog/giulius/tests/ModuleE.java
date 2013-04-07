/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;

/**
 *
 * @author Tim Boudreau
 */
public class ModuleE extends AbstractModule {

    @Override
    protected void configure() {
        bind (Integer.class).toInstance(23);
    }

}
