package com.mastfrog.giulius.mongodb.async;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.giulius.Ordered;
import com.mastfrog.giulius.ShutdownHookRegistry;
import static com.mastfrog.giulius.mongodb.async.GiuliusMongoAsyncModule.SETTINGS_KEY_DATABASE_NAME;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.connection.ClusterConnectionMode;
import com.mongodb.connection.ClusterSettings;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Starts a local mongodb over java.io.tmpdir and cleans it up on shutdown; uses
 * a random, available port. Simply request this be injected in your test, and
 * use MongoHarness.Module, to have the db started for you and automatically
 * cleaned up.
 * <p/>
 * Test <code>failed()</code> if you want to detect if you're running on a
 * machine where mongodb is not installed.
 *
 * @author Tim Boudreau
 */
@Singleton
public class MongoHarness {

    private final int port;
    private final Init mongo;
    private static int count = 1;
    /*
    Try to connect too soon and you get a crash: https://jira.mongodb.org/browse/SERVER-23441
     */
    private static final int CONNECT_WAIT_MILLIS = 500;

    @Inject
    MongoHarness(@Named("mongoPort") int port, Init mongo) throws IOException, InterruptedException {
        this.port = port;
        this.mongo = mongo;
    }

    @Singleton
    @Ordered(Integer.MIN_VALUE)
    static class Init extends MongoAsyncInitializer implements Runnable {

        private final File mongoDir;
        private Process mongo;
        private int port;

        @SuppressWarnings("LeakingThisInConstructor")
        @Inject
        public Init(MongoAsyncInitializer.Registry registry, ShutdownHookRegistry shutdownHooks) {
            super(registry);
            shutdownHooks.add(this);
            mongoDir = createMongoDir();
        }

        @Override
        public MongoClientSettings onBeforeCreateMongoClient(MongoClientSettings settings) {
            System.out.println("Init.onBeforeCreateMongoClient");
            ClusterSettings origClusterSettings = settings.getClusterSettings();
            List<ServerAddress> hosts = origClusterSettings.getHosts();
            ServerAddress addr = hosts.iterator().next();
            if (!"localhost".equals(addr.getHost()) || hosts.size() > 1) {
                addr = new ServerAddress("localhost", addr.getPort());
                ClusterSettings newClusterSettings = ClusterSettings.builder().hosts(Arrays.asList(addr)).mode(ClusterConnectionMode.SINGLE).build();
                settings = MongoClientSettings.builder(settings).clusterSettings(newClusterSettings).build();
            }
            try {
                this.port = addr.getPort();
                mongo = startMongoDB(port);
            } catch (IOException | InterruptedException ex) {
                Exceptions.chuck(ex);
            }
            return settings;
        }

        void handleOutput(ProcessBuilder pb, String suffix) {
            if (suffix == null) {
                suffix = "";
            }
            if (Boolean.getBoolean("acteur.debug")) {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
            if (Boolean.getBoolean("mongo.tmplog")) {
                String tmq = System.getProperty("testMethodQname");
                if (tmq == null) {
                    tmq = MongoHarness.class.getSimpleName();
                }
                tmq += "-" + System.currentTimeMillis() + "-" + suffix;
                File tmp = new File(System.getProperty("java.io.tmpdir"));
                File err = new File(tmp, tmq + ".err");
                File out = new File(tmp, tmq + ".err");
                pb.redirectError(err);
                pb.redirectOutput(out);
            } else {
                System.err.println("Discarding mongodb output.  Set system property acteur.debug to true to inherit "
                        + "it, or mongo.tmplog to true to write it to a file in /tmp");
                pb.redirectError(new File("/dev/null"));
                pb.redirectOutput(new File("/dev/null"));
                // JDK 9:
//                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
//                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            }
        }

        public void stop() {
            if (mongo != null) {
                String[] cmd = new String[]{"mongod", "--dbpath",
                    mongoDir.getAbsolutePath(), "--shutdown", "--port", "" + port};
                ProcessBuilder pb = new ProcessBuilder().command(cmd);
                handleOutput(pb, "mongodb-shutdown");
                try {
                    Process shutdown = pb.start();
                    System.err.println("Try graceful mongodb shutdown " + Arrays.toString(cmd));
                    boolean exited = false;
                    for (int i = 0; i < 19000; i++) {
                        try {
                            int exit = shutdown.exitValue();
                            System.err.println("Shutdown mongodb call exited with " + exit);
                            break;
                        } catch (IllegalThreadStateException ex) {
//                            System.out.println("no exit code yet, sleeping");
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException ex1) {
                                Exceptions.printStackTrace(ex1);
                            }
                        }
                    }
                    System.err.println("Wait for mongodb exit");
                    for (int i = 0; i < 10000; i++) {
                        try {
                            int code = mongo.exitValue();
                            System.err.println("Mongo server exit code " + code);
                            exited = true;
                            break;
                        } catch (IllegalThreadStateException ex) {
//                            System.out.println("Not exited yet; sleep 100ms");
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException ex1) {
                                Exceptions.printStackTrace(ex1);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (!exited && i > 30) {
                            System.err.println("Mongodb has not exited; kill it");
                            mongo.destroy();
                        }
                    }
                } catch (IOException ex) {
                    Exceptions.chuck(ex);
                    mongo = null;
                }
                mongo = null;
            }
        }

        public void start() throws IOException, InterruptedException {
            if (mongo != null) {
                throw new IllegalStateException("MongoDB already started");
            }
            mongo = startMongoDB(port);
        }

        @Override
        public void run() {
            try {
                stop();
            } finally {
                if (mongoDir != null && mongoDir.exists()) {
                    cleanup(mongoDir);
                }
            }
        }

        private File createMongoDir() {
            File tmp = new File(System.getProperty("java.io.tmpdir"));
            String fname = System.getProperty("testMethodQname");
            if (fname == null) {
                fname = "mongo-" + System.currentTimeMillis() + "-" + count++;
            } else {
                fname += "-" + System.currentTimeMillis() + "-" + count++;
            }
            File mongoDir = new File(tmp, fname);
            if (!mongoDir.mkdirs()) {
                throw new AssertionError("Could not create " + mongoDir);
            }
            return mongoDir;
        }

        private volatile boolean failed;

        public boolean failed() {
            return failed;
        }

        Process startMongoDB(int port) throws IOException, InterruptedException {
            Checks.nonZero("port", port);
            Checks.nonNegative("port", port);
            System.err.println("Starting mongodb on port " + port + " with data dir " + mongoDir);
            ProcessBuilder pb = new ProcessBuilder().command("mongod", "--dbpath",
                    mongoDir.getAbsolutePath(), "--nojournal", "--smallfiles", "-nssize", "1",
                    "--noprealloc", "--slowms", "5", "--port", "" + port,
                    "--maxConns", "50", "--nohttpinterface", "--syncdelay", "0", "--oplogSize", "1",
                    "--diaglog", "0");
            System.err.println(pb.command());
            handleOutput(pb, "mongodb");

            // XXX instead of sleep, loop trying to connect?
            Process result = pb.start();
            Thread.sleep(CONNECT_WAIT_MILLIS);
            for (int i = 0;; i++) {
                try {
                    Socket s = new Socket("localhost", port);
                    s.close();
                    Thread.sleep(CONNECT_WAIT_MILLIS);;
                    break;
                } catch (ConnectException e) {
                    if (i > 1750) {
                        throw new IOException("Could not connect to mongodb "
                                + "after " + i + " attempts.  Assuming it's dead.");
                    }
                    Thread.sleep(i > 1700 ? 400 : i > 1500 ? 250 : i > 1000 ? 125 : 50);
                }
            }
            return result;
        }
    }

    private static void cleanup(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                cleanup(f);
                f.delete();
            }
        }
        for (File f : dir.listFiles()) {
            if (f.isFile()) {
                f.delete();
            }
        }
        dir.delete();
    }

    /**
     * Determine if starting MongoDB failed (the process exited with non-zero a
     * few seconds after launch). Use this to allow tests to pass when building
     * on a machine which does not have mongodb installed.
     *
     * @return True if mongodb was started and failed for some reason (details
     * will be on system.out)
     */
    public boolean failed() {
        return mongo.failed();
    }

    /**
     * Stop mongodb. This is done automatically on system shutdown - only call
     * this if you want to test the behavior of something when the database is
     * <i>not</i> there for some reason.
     */
    public void stop() {
        mongo.stop();
    }

    /**
     * Start mongodb, if stop has been called. Otehrwise, it is automatically
     * started for you.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    public void start() throws IOException, InterruptedException {
        mongo.start();
    }

    /**
     * Get the randomly selected available port we wnat to use
     *
     * @return a port
     */
    public int port() {
        return port;
    }

    /**
     * Use this module in a test to automatically start MongoDB the first time
     * something requests a class related to it for injection. Automatically
     * finds an unused, non-standard port. Inject MongoHarness and call its
     * <code>port()</code> method if you need the port.
     */
    public static class Module extends AbstractModule {

        @Override
        protected void configure() {
            bind(String.class).annotatedWith(Names.named(GiuliusMongoAsyncModule.SETTINGS_KEY_MONGO_PORT)).toInstance("" + findPort());
            bind(String.class).annotatedWith(Names.named(GiuliusMongoAsyncModule.SETTINGS_KEY_MONGO_HOST)).toInstance("localhost");
            bind(String.class).annotatedWith(Names.named(SETTINGS_KEY_DATABASE_NAME)).toInstance("_testDb");
            bind(Init.class).asEagerSingleton();
        }

        private int findPort() {
            Random r = new Random(System.currentTimeMillis());
            int port;
            do {
                // Make sure we're out of the way of a running mongo instance,
                // both the mongo port and the http port
                int startPort = 28002;
                port = r.nextInt(65536 - startPort) + startPort;
            } while (!available(port));
            return port;
        }

        private boolean available(int port) {
            try (ServerSocket ss = new ServerSocket(port)) {
                ss.setReuseAddress(true);
                try (DatagramSocket ds = new DatagramSocket(port)) {
                    ds.setReuseAddress(true);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
    }
}
