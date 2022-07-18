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

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
final class ArtifactHandlerImpl implements ArtifactHandler {

    private final String classifier;
    private final String dir;

    ArtifactHandlerImpl(MavenProject prj, String classifier) {
        this.classifier = classifier;
        dir = prj.getBasedir().toPath().resolve("target").toAbsolutePath().toString();
    }

    @Override
    public String getExtension() {
        return "jar";
    }

    @Override
    public String getDirectory() {
        return dir;
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    @Override
    public String getPackaging() {
        return getExtension();
    }

    @Override
    public boolean isIncludesDependencies() {
        return true;
    }

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public boolean isAddedToClasspath() {
        return false;
    }

}
