// Generated by com.dv.sourcetreetool.impl.App
open module com.mastfrog.smart.jar.merge {
    exports com.mastfrog.jarmerge;
    exports com.mastfrog.jarmerge.builtin;
    exports com.mastfrog.jarmerge.spi;
    exports com.mastfrog.jarmerge.support;

    requires java.xml;

    // Inferred from source scan

    // Sibling com.mastfrog/util-fileformat-3.0.0-dev
    requires com.mastfrog.fileformat;

    // Inferred from source scan
    requires com.mastfrog.function;

    // Sibling com.mastfrog/giulius-settings-3.0.0-dev
    requires com.mastfrog.giulius.settings;

    // Inferred from source scan
    requires com.mastfrog.preconditions;

    // Inferred from source scan
    requires com.mastfrog.streams;

    // Inferred from source scan

    // Sibling com.mastfrog/util-strings-3.0.0-dev
    requires com.mastfrog.strings;

    // derived from com.fasterxml.jackson.core/jackson-databind-0.0.0-? in com/fasterxml/jackson/core/jackson-databind/2.9.9.3/jackson-databind-2.9.9.3.pom
    requires com.fasterxml.jackson.databind;
}
