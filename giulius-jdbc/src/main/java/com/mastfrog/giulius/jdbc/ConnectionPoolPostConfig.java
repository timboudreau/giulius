package com.mastfrog.giulius.jdbc;

import com.google.inject.ImplementedBy;
import com.jolbox.bonecp.BoneCPConfig;

/**
 * Configures additional properties of the BoneCPConfig before the pool is
 * created. The default implementation will look for postgres-specific
 * parameters and set them.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(ConnectionPoolPostConfigImpl.class)
public interface ConnectionPoolPostConfig {

    BoneCPConfig onConfigure(BoneCPConfig config);
}
