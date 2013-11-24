package com.mastfrog.cluster;

import com.mastfrog.util.GUIDFactory;
import com.mastfrog.util.Streams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Tim Boudreau
 */
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
            result = GUIDFactory.get().newGUID(1, 4);
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
        loadingClassName = nm[nm.length-1];
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
                    result = GUIDFactory.get().newGUID(1, 7);
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
