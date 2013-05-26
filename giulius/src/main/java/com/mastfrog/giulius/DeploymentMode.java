package com.mastfrog.giulius;

/**
 * Determine if we are in development mode or production mode.  This corresponds
 * to Guice's STAGE (minus tool mode);  Guice 3.1 no longer allows binding
 * Stage directly, so this class replaces requesting injection of that.
 *
 * @author Tim Boudreau
 */
public enum DeploymentMode {
    PRODUCTION,
    DEVELOPMENT;
    
    public boolean isProduction() {
        return this == PRODUCTION;
    }
}
