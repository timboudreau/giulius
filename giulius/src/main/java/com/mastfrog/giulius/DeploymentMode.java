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

    /**
     * Detect if running inside a test.  Uses system properties
     * <code>unit.test</code> (set by giulius-tests), <code>forkNumber</code>
     * and <code>surefire.forkNumber</code> (Maven common practices) to detect
     * it.
     * <p>
     * This is needed for certain things, such as the MongoDB test harness, where
     * an index build can be scheduled as the process is shutting down, causing
     * a panic if files are already deleted.
     *
     * @return True if, according to the above criteria, the JVM is running a
     * unit test.
     */
    public boolean inUnitTest() {
        String prop = System.getProperty("unit.test", System.getProperty("forkNumber", System.getProperty("surefire.forkNumber")));
        if ( prop != null && !"false".equals(prop)) {
            return true;
        }
        // Could inspect the stack, but if this were called frequently, that could be
        // a major performance hit
        return false;
    }
}
