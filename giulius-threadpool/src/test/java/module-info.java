// Generated by com.dv.sourcetreetool.impl.App
open module com.mastfrog.giulius.threadpool {
    exports com.mastfrog.giulius.thread;
    exports com.mastfrog.giulius.thread.util;

    requires com.google.guice;
    
    // Sibling com.mastfrog/giulius-3.0.0-dev
    // Transitive detected by source scan
    requires com.mastfrog.giulius;

    // Sibling com.mastfrog/injection-reflection-indexer-3.0.0-dev:compile
    requires static com.mastfrog.injection.reflection.indexer;

    // Inferred from source scan
    requires com.mastfrog.preconditions;
    
    requires com.mastfrog.giulius.settings;

    // Inferred from source scan
    requires com.mastfrog.strings;
    requires java.logging;

    // Inferred from test-source-scan
    requires junit;

}
