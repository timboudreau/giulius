/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.maven.merge.configuration;

import com.telenav.cactus.maven.xml.AbstractXMLUpdater;
import com.telenav.cactus.maven.xml.XMLElementRemoval;
import com.telenav.cactus.maven.xml.XMLFile;
import com.telenav.cactus.maven.xml.XMLReplacer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.graph.Dependency;

/**
 *
 * @author Tim Boudreau
 */
public class PomRewriter {

    private final MavenProject project;
    private final Set<Dependency> removed;
    private final ArtifactFactory artifactFactory;
    private final Path outputJar;

    PomRewriter(MavenProject project, Set<Dependency> removed, ArtifactFactory artifactFactory, Path outputJar) {
        this.project = project;
        this.removed = removed;
        this.artifactFactory = artifactFactory;
        this.outputJar = outputJar;
    }

    Path rewrite() throws Exception {
        Path outFile;
        String pomFileName = project.getArtifactId() + "-" + project.getVersion() + ".pom";
        if (outputJar.getParent() == null) {
            outFile = Paths.get(pomFileName);
        } else {
            outFile = outputJar.getParent().resolve(pomFileName);
        }
        Files.copy(project.getFile().toPath(), outFile, StandardCopyOption.REPLACE_EXISTING);

        DF xf = new DF(outFile);
        List<AbstractXMLUpdater> removals = new ArrayList<>();
        for (Dependency dep : removed) {
            // XXX need a better query
            removals.add(new XMLElementRemoval(xf,
                    "/project/dependencies/dependency[./artifactId[text()=\""
                    + dep.getArtifact().getArtifactId()
                    + "\"]]"));
        }
        return xf.inContext(doc -> {
            AbstractXMLUpdater.applyAll(removals, false, msg -> {
                System.out.println(msg);
            });
            XMLReplacer.writeXML(doc, outFile);
            return outFile;
        });
    }

    static class DF extends XMLFile {

        public DF(Path path) {
            super(path);
        }

        @Override
        public String toString() {
            return path().toString();
        }
    }
}
