package com.mastfrog.cluster;

import com.google.inject.ImplementedBy;

/**
 * Info about the currently running application, suitable for use in a shared
 * cluster log, or with performance monitors and similar UDP-blurt-to-the-universe 
 * things, to identify the machine, process and application.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(ApplicationInfoImpl.class)
public interface ApplicationInfo {

    /**
     * If set to true, do not attempt to write out a file with the process
     * unique ID - suitable for things like unit tests
     */
    public static final String SYSTEM_PROPERTY_NO_WRITES = "appInfo.nowrite";

    /**
     * The name of the application, as set by system property or by attempting
     * to locate the main class name.
     *
     * @return The name
     */
    public String applicationName();

    /**
     * A random string that uniquely identifies this installation of this
     * application. By default, this is written to a file named "." +
     * applicationName() in the home directory of the running user the first
     * time it is called.
     *
     * @return
     */
    public String installationIdentifier();

    /**
     * A probably unique string generated for this process
     *
     * @return
     */
    public String processIdentifier();

    /**
     * A concatenation of applicationName, installationIdentifier and
     * processIdentifier delimited by :
     *
     * @return An id for this process
     */
    public String uniqueIdentifier();
}
