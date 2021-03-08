/*
 * The MIT License
 *
 * Copyright 2021 Mastfrog Technologies.
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
package com.mastfrog.jarmerge.support;

import com.mastfrog.jarmerge.MergeLog;
import com.mastfrog.jarmerge.spi.Coalescer;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/**
 *
 * @author Tim Boudreau
 */
public abstract class AbstractCoalescer implements Coalescer {

    protected final String name;
    protected FileTime creation;
    protected FileTime modification;
    protected FileTime access;
    private final boolean zeroDates;

    public AbstractCoalescer(String name, boolean zeroDates) {
        this.name = name;
        this.zeroDates = zeroDates;
    }

    @Override
    public final String path() {
        return name;
    }

    protected boolean canWrite(MergeLog log) {
        return true;
    }

    private FileTime oldest(FileTime a, FileTime b) {
        if (a == null && b != null) {
            return b;
        } else if (a != null && b == null) {
            return b;
        } else if (a != null && b != null) {
            if (a.toInstant().isAfter(b.toInstant())) {
                return b;
            } else {
                return a;
            }
        } else {
            return null;
        }
    }

    private FileTime newest(FileTime a, FileTime b) {
        if (a == null && b != null) {
            return b;
        } else if (a != null && b == null) {
            return b;
        } else if (a != null && b != null) {
            if (a.toInstant().isBefore(b.toInstant())) {
                return b;
            } else {
                return a;
            }
        } else {
            return null;
        }
    }

    private Runnable updateFileTimes(JarEntry en) {
        FileTime newCreation = en.getCreationTime();
        FileTime newModification = en.getLastModifiedTime();
        FileTime newAccess = en.getLastAccessTime();
        return () -> {
            this.creation = oldest(this.creation, newCreation);
            this.modification = newest(this.modification, newModification);
            this.access = newest(this.access, newAccess);
        };
    }

    private static final FileTime EPOCH = FileTime.from(Instant.EPOCH);

    private void setFileTimes(JarEntry nue) {
        if (zeroDates) {
            nue.setCreationTime(EPOCH);
            nue.setLastModifiedTime(EPOCH);
            nue.setLastAccessTime(EPOCH);
        } else {
            if (creation != null) {
                nue.setCreationTime(creation);
            }
            if (modification != null) {
                nue.setLastModifiedTime(modification);
            }
            if (access != null) {
                nue.setLastAccessTime(access);
            }
        }
    }

    @Override
    public final void add(Path jar, JarEntry entry, JarFile file, MergeLog log) throws Exception {
        Runnable updateFileTimes = updateFileTimes(entry);
        boolean success;
        try (InputStream in = file.getInputStream(entry)) {
            success = read(jar, entry, file, in, log);
        }
        if (success) {
            updateFileTimes.run();
        }
    }

    @Override
    public final void writeCoalesced(JarOutputStream out, MergeLog log) throws Exception {
        if (canWrite(log)) {
            JarEntry nue = new JarEntry(path());
            setFileTimes(nue);
            out.putNextEntry(nue);
            write(nue, out, log);
        }
    }

    protected abstract boolean read(Path jar, JarEntry entry, JarFile file, InputStream in,
            MergeLog log) throws Exception;

    protected abstract void write(JarEntry entry, JarOutputStream out,
            MergeLog log) throws Exception;

    /**
     * For logging, include additional info in toString().
     *
     * @return Some details
     */
    protected String details() {
        return "";
    }

    @Override
    public String toString() {
        String det = details();
        if (!det.isEmpty() && det.charAt(0) != ' ') {
            det = ' ' + det;
        }
        return getClass().getSimpleName() + "(" + name + det + ")";
    }

//    @Override
//    public int hashCode() {
//        int hash = 7;
//        hash = 71 * hash + Objects.hashCode(this.name);
//        return hash;
//    }
//
//    @Override
//    public boolean equals(Object obj) {
//        if (this == obj) {
//            return true;
//        }
//        if (obj == null) {
//            return false;
//        }
//        if (getClass() != obj.getClass()) {
//            return false;
//        }
//        final Concatenator other = (Concatenator) obj;
//        if (!Objects.equals(this.name, other.name)) {
//            return false;
//        }
//        return true;
//    }
}
