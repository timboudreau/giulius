open module com.mastfrog.giulius.annotation.processors {
// Generated by com.dv.sourcetreetool.impl.App

    provides javax.annotation.processing.Processor with
            com.mastfrog.giulius.annotation.processors.DefaultsAnnotationProcessor,
            com.mastfrog.giulius.annotation.processors.NamespaceAnnotationProcessor;
    
    exports com.mastfrog.giulius.annotation.processors;
    requires transitive com.mastfrog.annotation.processors;
    requires transitive com.mastfrog.annotation.tools;
    requires transitive com.mastfrog.util.fileformat;
    requires java.compiler;

    
}
