open module com.mastfrog.injection.reflection.indexer {
// Generated by com.dv.sourcetreetool.impl.App

    exports com.mastfrog.graal.annotation;
    exports com.mastfrog.graal.injection.processor;

    // derived from com.fasterxml.jackson.core/jackson-databind-2.13.2.2 in com/fasterxml/jackson/core/jackson-databind/2.13.2.2/jackson-databind-2.13.2.2.pom
//    requires transitive com.fasterxml.jackson.databind;

    // derived from com.google.inject/guice-5.1.0 in com/google/inject/guice/5.1.0/guice-5.1.0.pom
//    requires com.google.guice;
    requires transitive com.mastfrog.annotation.processors;
    requires transitive com.mastfrog.annotation.tools;
    requires transitive com.mastfrog.util.collections;
    requires transitive com.mastfrog.util.fileformat;
    requires transitive com.mastfrog.util.strings;
    requires java.compiler;

    // derived from javax.inject/javax.inject-1 in javax/inject/javax.inject/1/javax.inject-1.pom
//    requires javax.inject;
}
