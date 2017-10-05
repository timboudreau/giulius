/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.cluster;

import com.google.inject.Singleton;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import com.mastfrog.util.Strings;
import com.mastfrog.util.UniqueIDs;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
class ApplicationInfoImpl implements ApplicationInfo {

    private static final Logger logger = Logger.getLogger(ApplicationInfo.class.getName());
    public static final String PROCESS_GUID = "processGuid";
    public static final String INSTALLATION_GUID = "installationGuid";
    public static final String APPLICATION_NAME = "applicationName";
    public static final String COMBINED_ID = "combinedId";
    private static final String SGUID = "sguid";
    private static final String PROCESS_NAME = "processName";

    @Override
    public String applicationName() {
        return findMainClassName();
    }

    @Override
    public String installationIdentifier() {
        return findInstallationGuid();
    }

    @Override
    public String processIdentifier() {
        return findProcessId();
    }

    @Override
    public String uniqueIdentifier() {
        return findInstallationGuid() + ':' + findProcessId() + ':' + findMainClassName();
    }

    @Override
    public String toString() {
        return uniqueIdentifier();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ApplicationInfoImpl && o.toString().equals(toString());
    }

    @Override
    public int hashCode() {
        return uniqueIdentifier().hashCode();
    }

    private static String findProcessId() {
        String result = System.getProperty(PROCESS_GUID);
        if (result == null) {
            result = Strings.shuffleAndExtract(ThreadLocalRandom.current(), uids().newId(), 4);
            System.setProperty(PROCESS_GUID, result);
        }
        return result;
    }

    private static final String loadingClassName;

    static {
        @SuppressWarnings({"ThrowableInstanceNotThrown", "ThrowableInstanceNeverThrown"})
        Exception e = new Exception();
        StackTraceElement el = e.getStackTrace()[e.getStackTrace().length - 1];
        String[] nm = el.getClassName().split("\\.");
        loadingClassName = nm[nm.length - 1];
    }

    private static String findMainClassName() {
        String processName = System.getProperty(PROCESS_NAME);
        if (processName == null) {
            processName = loadingClassName;
//            String s = System.getProperty("sun.java.command");
//            if (s == null) {
//                s = "unknown.Application";
//            } else {
//                if (s.startsWith("jar ")) {
//                    s = s.substring(4).split(" ")[0];
//                    String[] slashes = s.split("/");
//                    s = slashes[slashes.length - 1];
//                }
//            }
//            int ix = s.lastIndexOf('.');
//            if (ix > 0 && ix < s.length() - 2) {
//                s = s.substring(ix + 1, s.length());
//            }
//            processName = s;
            System.setProperty(PROCESS_NAME, processName);
        }
        return processName;
    }

    private static String findInstallationGuid() {
        return findInstallationGuid(findMainClassName());
    }

    static UniqueIDs uids;

    private static UniqueIDs uids() {
        if (uids == null) {
            boolean write = Boolean.valueOf(System.getProperty(SYSTEM_PROPERTY_NO_WRITES));
            if (!write) {
                return UniqueIDs.noFile();
            }
            File f = new File(System.getProperty("user.home"));
            File uidsFile = new File(f, ".uids");
            try {
                uids = new UniqueIDs(uidsFile);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
                uids = UniqueIDs.noFile();
            }
        }
        return uids;
    }

    private static String findInstallationGuid(String name) {
        String result = System.getProperty(SGUID);
        if (result == null) {
            File f = new File(System.getProperty("user.home"));
            File nue = new File(f, "." + name);
            result = null;
            if (nue.exists() && nue.isFile()) {
                try (final FileInputStream in = new FileInputStream(nue)) {
                    result = Streams.readString(in).trim();
                    System.setProperty(SGUID, result);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
            if (result == null) {
                try {
                    result = Strings.shuffleAndExtract(ThreadLocalRandom.current(), uids().newId(), 7);
                    System.setProperty(SGUID, result);
                    boolean write = Boolean.valueOf(System.getProperty(SYSTEM_PROPERTY_NO_WRITES));
                    if (write && f.createNewFile()) {
                        try (final FileOutputStream o = new FileOutputStream(nue)) {
                            o.write(result.getBytes(Charset.forName("US-ASCII")));
                        } catch (IOException ex) {
                            logger.log(Level.SEVERE, null, ex);
                        }
                    }
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        }
        return result;
    }

}
