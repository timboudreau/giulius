package com.mastfrog.giulius.jdbc;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;
import com.mastfrog.giulius.ShutdownHookRegistry;
import com.mastfrog.util.Exceptions;
import java.sql.SQLException;

/**
 * Provides a BoneCP connection pool
 *
 * @author Tim Boudreau
 */
@Singleton
class ConnectionPoolProvider implements Provider<BoneCP>, Runnable {

    private volatile BoneCP instance;
    private final Provider<BoneCPConfig> config;

    @Inject
    public ConnectionPoolProvider(Provider<BoneCPConfig> config, ShutdownHookRegistry reg) {
        this.config = config;
        reg.add(this);
    }

    @Override
    public BoneCP get() {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    try {
                        BoneCP cp = new BoneCP(config.get());
                        instance = cp;
                    } catch (SQLException ex) {
                        Exceptions.chuck(ex);
                    }
                }
            }
        }
        return instance;
    }

    @Override
    public void run() {
        synchronized(this) {
            if (instance != null) {
                instance.shutdown();
            }
        }
    }
}
