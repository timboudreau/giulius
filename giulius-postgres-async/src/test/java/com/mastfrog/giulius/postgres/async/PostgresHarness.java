/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
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
package com.mastfrog.giulius.postgres.async;

import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.net.PortFinder;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.streams.ContinuousLineStream;
import com.mastfrog.util.strings.Strings;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Starts postgres in a temporary directory for testing and shuts it down and
 * cleans it up afterwards.
 *
 * @author Tim Boudreau
 */
public final class PostgresHarness {

    public static final PortFinder AVAILABLE_PORTS = new PortFinder();
    private static final String[] DEFAULT_SEARCH_PATH
            = {"/usr/bin", "/usr/local/bin", "/opt/local/bin", "/bin", "/sbin", "/usr/sbin", "/opt/bin"};
    private static final String PREFIX = "pg-";
    private static final String PSQL_BINARY = System.getProperty("psql", find("psql").toString());
    private static final String INITDB_BINARY = System.getProperty("initdb", find("initdb").toString());
    private static final String POSTGRES_BINARY = System.getProperty("postgres", find("postgres").toString());
    private final AtomicInteger sqlruns = new AtomicInteger(1);
    private final AtomicBoolean started = new AtomicBoolean();
    private Path dir;
    private Path dest;
    private Process process;
    private int port = -1;
    private static boolean warned;

    private PostgresHarness(Path dir) {
        this.dir = dir;
    }

    public static boolean binariesExist() {
        for (String file : new String[]{PSQL_BINARY, INITDB_BINARY, POSTGRES_BINARY}) {
            Path path = Paths.get(file);
            if (!Files.exists(path) || !Files.isExecutable(path)) {
                if (!warned) {
                    warned = true;
                    System.err.println("Could not find an executable binary named '" + file
                            + "' in any of " + Strings.join(',', DEFAULT_SEARCH_PATH) + " - "
                            + "tests using " + PostgresHarness.class.getName() + " will "
                            + "(or should) be skipped.  If it is present but in an unusual "
                            + "location, set the system properties 'psql', 'initdb' and 'postgres' "
                            + "to the absolute paths to the binaries you want to use.");
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Create a new postgres harness which starts an empty database in a
     * temporary directory.
     */
    public PostgresHarness() throws IOException {
        this(FileUtils.newTempDir(PREFIX));
    }

    static Path find(String cmd) {
        cmd = Paths.get(cmd).getFileName().toString();
        Set<String> searched = new HashSet<>();
        String systemPath = System.getenv("PATH");
        if (systemPath != null) {
            for (CharSequence path : Strings.splitUniqueNoEmpty(File.pathSeparatorChar, systemPath)) {
                String p = path.toString();
                Path dir = Paths.get(p);
                Path file = dir.resolve(cmd);
                if (Files.exists(file) && file.toFile().canExecute()) {
                    return file;
                }
                searched.add(p);
            }
        }
        for (String path : DEFAULT_SEARCH_PATH) {
            if (!searched.contains(path)) {
                Path dir = Paths.get(path);
                Path file = dir.resolve(cmd);
                if (Files.exists(file) && file.toFile().canExecute()) {
                    return file;
                }
                searched.add(path);
            }
        }
        return Paths.get(cmd);
    }

    public static void main(String[] args) throws Exception {
        PostgresHarness harn = new PostgresHarness();
        harn.start();
        harn.initDatabase("foo", "create table bar(id varchar(20));");
        Thread.sleep(2500);
        harn.shutdown(true);
    }

    /**
     * Get the log file as a string.
     *
     * @return The log file or the empty string if none exists.
     * @throws IOException If something goes wrong
     */
    public String log() throws IOException {
        Path path = dir.resolve("postgres.out");
        if (Files.exists(path)) {
            return Strings.join('\n', Files.readAllLines(path));
        }
        return "";
    }

    /**
     * Shut down the database.
     *
     * @param cleanup If true, delete the database files.
     *
     * @throws IOException If something goes wrong
     */
    public void shutdown(boolean cleanup) throws IOException, InterruptedException, Exception {
        shutdown();
        if (cls != null) {
            cls.close();
        }
        if (cleanup) {
            waitForLoggedWords("database system is shut down");
            cleanup();
        }
    }

    private void cleanup() throws IOException {
        FileUtils.deltree(dir);
    }

    /**
     * Get the folder the database is created in.
     *
     * @return A folder which may or may not exist
     */
    public Path databaseRoot() {
        return dir;
    }

    /**
     * Shut down the database, leaving its files.
     */
    public void shutdown() {
        if (started.compareAndSet(true, false)) {
            synchronized (this) {
                if (process != null && process.isAlive()) {
//                    System.out.println("Shutting down postgres " + process.info() + " " + process);
                    process.destroy();
                    process = null;
                }
            }
        }
    }

    /**
     * return dir;
     *
     * Get a JDBC url for the managed database (must be started) and the given
     * database name (does not create the database).
     *
     * @param dbname The database name
     * @return A JDBC url
     * @throws IOException If something goes wrong
     */
    public String jdbcUrl(String dbname) throws IOException {
        if (!started.get()) {
            throw new IOException("Stopped, failed or not started");
        }
        synchronized (this) {
            if (process == null) {
                throw new IOException("No process to talk to");
            }
        }
        return "jdbc:postgresql://localhost:" + port + "/"
                + dbname + "?user=" + System.getProperty("user.name") + "&ssl=false";
    }

    /**
     * Create a postgres URI for use with psql for the managed database and the
     * passed database name.
     *
     * @param dbname The database name
     * @return A uri
     * @throws IOException If something goes wrong
     */
    public String dbUrl(String dbname) throws IOException {
        if (!started.get()) {
            throw new IOException("Stopped, failed or not started");
        }
        synchronized (this) {
            if (process == null) {
                throw new IOException("No process to talk to");
            }
        }
        return "postgres://" + System.getProperty("user.name") + "@localhost:" + port + "/" + dbname;
    }

    /**
     * Create a database with the passed name.
     *
     * @param database A database name
     * @return A <i>postgres</i> URI
     * @throws IOException If something goes wrong
     * @throws InterruptedException If something goes wrong
     * @throws ExecutionException If something goes wrong
     */
    public String initDatabase(String database) throws IOException, InterruptedException, ExecutionException {
        return initDatabase(database, (Path) null);
    }

    /**
     * Create a database with the passed name and initialize it by running the
     * passed SQL.
     *
     * @param database The database name
     * @param initSql Some SQL or null
     * @return A <i>postgres</i> (not JDBC) uri
     * @throws IOException If something goes wrong
     * @throws InterruptedException If something goes wrong
     * @throws ExecutionException If something goes wrong
     */
    public String initDatabase(String database, String initSql) throws IOException, InterruptedException, ExecutionException {
        if (initSql == null) {
            initDatabase(database, (Path) null);
        }
        Path initFile = dir.resolve("init.sql");
        FileUtils.writeUtf8(initFile, initSql);
        return initDatabase(database, initFile);
    }

    /**
     * Create a database with the passed name and initialize it by running the
     * passed SQL file.
     *
     * @param database The database name
     * @param initSql Some SQL or null
     * @return A <i>postgres</i> (not JDBC) uri
     * @throws IOException If something goes wrong
     * @throws InterruptedException If something goes wrong
     * @throws ExecutionException If something goes wrong
     */
    public String initDatabase(String database, Path sqlFile) throws IOException, InterruptedException, ExecutionException {
        checkRunning();
        if (sqlFile != null && !Files.exists(sqlFile)) {
            throw new IOException(sqlFile + " does not exist");
        }
        String[] createDbCommand = {PSQL_BINARY, "-a", "-b", "-h", "localhost", "-p", Integer.toString(port), "postgres", "-c", "create database " + database + ";"};
        String result = run(createDbCommand);
        if (!"CREATE DATABASE".equals(result.trim())) {
            throw new IOException("Output of '" + Strings.join(' ', createDbCommand) + "' should be 'CREATE DATABASE' not '" + result + "'");
        }
        if (sqlFile != null) {
            runSql(database, sqlFile);
        }
        return dbUrl(database);
    }

    private void checkRunning() throws IOException {
        if (!started.get()) {
            throw new IOException("Stopped, failed or not started");
        }
        synchronized (this) {
            if (process == null) {
                throw new IOException("No process to talk to");
            }
        }
    }

    /**
     * Run some sql against the database by invoking psql. The sql sent is
     * written to a file in the harness folder.
     *
     * @param database The database - must exist
     * @param sql The sql
     * @return The output of psql (e.g. "CREATE TABLE" for creating a table)
     * @throws IOException If something goes wrong
     * @throws InterruptedException If something goes wrong
     * @throws ExecutionException If something goes wrong
     */
    public String runSql(String database, String sql) throws IOException, InterruptedException, ExecutionException {
        Checks.notNull("database", database);
        Checks.notNull("sql", sql);
        checkRunning();
        Path sqlFile = newFile(database + "-runsql-" + sqlruns.incrementAndGet(), "sql");
        FileUtils.writeUtf8(sqlFile, sql);
        return runSql(database, sqlFile);
    }

    /**
     * Run an sql file against the database by invoking psql.
     *
     * @param database The database - must exist
     * @param sql The sql file
     * @return The output of psql (e.g. "CREATE TABLE" for creating a table)
     * @throws IOException If something goes wrong
     * @throws InterruptedException If something goes wrong
     * @throws ExecutionException If something goes wrong
     */
    public String runSql(String database, Path sql) throws IOException, InterruptedException, ExecutionException {
        Checks.notNull("database", database);
        Checks.readable("sql", sql);
        return run(PSQL_BINARY, "-a", "-b", "-h", "localhost", "-p", Integer.toString(port), database, "-f", sql.toString());
    }

    /**
     * Start the database, blocking until it is available.
     *
     * @throws IOException If something goes wrong
     * @throws InterruptedException If something goes wrong
     * @throws ExecutionException If something goes wrong
     */
    public PostgresHarness start() throws IOException, InterruptedException, ExecutionException {
        if (started.compareAndSet(false, true)) {
            dest = initDb();
            port = createConfFile(dest);
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
            synchronized (this) {
                process = startDb(dest, port);
                onExit(process).handle((p, thrown) -> {
                    if (thrown != null) {
                        thrown.printStackTrace();
                    }
                    return null;
                });
            }
            try {
//                Thread.sleep(20);
                waitForLoggedWords("database system is ready to accept connections");
            } catch (InterruptedException ex) {
                Logger.getLogger(PostgresHarness.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return this;
    }

    private Process startDb(Path dest, int port) throws IOException {
        List<String> cmd = Arrays.asList(POSTGRES_BINARY,
                "-D", dest.toString(),
                "-h", "localhost",
                "-i", "-p", Integer.toString(port),
                "-N", "5",
                "-d", "2",
                "-B", "128",
                "-F",
                "-S", "256",
                "-n"
        );

        File output = dir.resolve("postgres.out").toFile();
        output.createNewFile();
        Redirect redirect = ProcessBuilder.Redirect.appendTo(output);

//        System.out.println("RUN " + Strings.join(' ', cmd));
        return new ProcessBuilder(cmd)
                .redirectError(redirect)
                .redirectOutput(redirect)
                .directory(dir.toFile()).start();
    }

    volatile ContinuousLineStream cls;

    private static String removeWhitespace(CharSequence s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Block the calling thread until the words (ignoring case and whitespace)
     * in the passed string are logged to the database output, to ensure tests
     * of the database's contents do not run before the data is written and
     * flushed. The <i>first</i> call to this is historical, and will scan the
     * log file up to its current tail; subsequent calls are not.
     *
     * @param seq A sequence of words the database is expected to log.
     * @return true if the sequence is found
     * @throws IOException If something goes wrong
     * @throws InterruptedException If sleep is interrupted
     */
    public boolean waitForLoggedWords(String seq) throws IOException, InterruptedException {
        String noWhitespace = removeWhitespace(seq).toLowerCase();
        return pollOutput(10000, line -> {
            String compare = removeWhitespace(line).toLowerCase();
            return compare.contains(noWhitespace);
        });
    }

    /**
     * Block the calling thread until a line is logged by the database which
     * matches the passed predicate. The <i>first</i> call to this is
     * historical, and will scan the log file up to its current tail; subsequent
     * calls are not.
     *
     * @param maxMillis The maximum milliseconds to poll for before returning
     * false if no matching line has been logged
     * @param forLine A predicate which is called for each line that is logged
     */
    public boolean pollOutput(long maxMillis, Predicate<CharSequence> forLine) throws IOException, InterruptedException {
        ContinuousLineStream clsLocal = this.cls;
        if (clsLocal == null) {
            synchronized (this) {
                clsLocal = this.cls;
                if (clsLocal == null) {
                    File output = dir.resolve("postgres.out").toFile();
                    this.cls = clsLocal = ContinuousLineStream.of(output, 1024);
                }
            }
        }
        long start = System.currentTimeMillis();
        if (clsLocal != null) {
            for (;;) {
                if (!started.get() || Thread.interrupted() || System.currentTimeMillis() - start > maxMillis) {
                    return false;
                }
                if (clsLocal.hasMoreLines()) {
                    CharSequence line = clsLocal.nextLine();
                    if (forLine.test(line)) {
                        return true;
                    }
                } else {
                    Thread.sleep(50);
                }
            }
        }
        return false;
    }

    private Path initDb() throws IOException, InterruptedException, ExecutionException {
        Files.createDirectories(dir);
        String ver = psqlVersion();
        dest = dir.resolve(ver);
        Files.createDirectories(dir);
        run(INITDB_BINARY, "--nosync", "-D", dest.toString(), "-E", "UNICODE", "-A", "trust");
        return dest;
    }

    private int createConfFile(Path dest) throws IOException {
        int port = AVAILABLE_PORTS.findAvailableServerPort();
        List<String> conf = Arrays.asList(
                "port = " + port,
                "unix_socket_directories = '" + dir + "'",
                "listen_addresses = 'localhost'",
                "superuser_reserved_connections = 1",
                "max_wal_senders = 3",
                "shared_buffers = 12MB",
                "fsync = off",
                "synchronous_commit = off",
                "full_page_writes = off",
                "log_min_duration_statement = 0",
                "log_connections = on",
                "log_disconnections = on",
                "log_directory = '" + dir + "'"
        );
        Path confFile = dest.resolve("postgresql.conf");
        if (Files.exists(confFile)) {
            Files.move(confFile, confFile.getParent().resolve("postgresql.conf.generated"));
        }
        Files.write(confFile, conf, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        return port;
    }

    private static final Pattern VERSION_PATTERN = Pattern.compile(".*?(\\d+.*?\\d+)$");

    private String psqlVersion() throws IOException, InterruptedException, ExecutionException {
        String result = run(PSQL_BINARY, "-V");
        if (result == null) {
            throw new IOException("Failed running 'psql -V'");
        }
        Matcher m = VERSION_PATTERN.matcher(result);
        if (m.find()) {
            return m.group(1);
        }
        throw new IOException("Could not find version in string '" + result + "'");
    }

    private String pathName(String path) {
        return Paths.get(path).getFileName().toString();
    }

    synchronized Path newFile(String name, String ext) throws IOException {
        int ix = 0;
        Path p = dir.resolve(name + "." + ext);
        while (Files.exists(p)) {
            p = dir.resolve(name + "-" + ++ix + "." + ext);
        }
        Files.createFile(p);
        return p;
    }

    private String run(String... command) throws IOException, InterruptedException, ExecutionException {
        Checks.notEmptyOrNull("command", command);
        return runNamed(pathName(command[0]), command);
    }

    static boolean onExitMethodMissing;
    static Method onExitMethod;

    static synchronized Method onExitMethod() {
        if (onExitMethod != null) {
            return onExitMethod;
        }
        if (!onExitMethodMissing) {
            try {
                return onExitMethod = Process.class.getMethod("onExit");
            } catch (NoSuchMethodException | SecurityException ex) {
                onExitMethodMissing = true;
            }
        }
        return null;
    }

    /**
     * While still supporting JDK 8, we cannot use Process.onExit() except
     * reflectively. The implementation is less than efficient for JDK 8 but
     * should do the job.
     *
     * @param proc A process
     * @return A completable future
     */
    @SuppressWarnings("unchecked")
    CompletableFuture<Process> onExit(Process proc) {
        Method onExit = onExitMethod();
        if (onExit != null) {
            try {
                return (CompletableFuture<Process>) onExit.invoke(proc);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                return Exceptions.chuck(ex);
            }
        }
        CompletableFuture<Process> result = new CompletableFuture<>();
        Runnable busywait = () -> {
            for (;;) {
                if (proc.isAlive()) {
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ex) {
                        result.completeExceptionally(ex);
                    }
                } else {
                    result.complete(proc);
                    return;
                }
            }
        };
        Thread waiter = new Thread(busywait, "JDK8-Process-Waiter: " + proc);
        waiter.setDaemon(true);
        waiter.setPriority(Thread.currentThread().getPriority() - 1);
        waiter.setUncaughtExceptionHandler((thr, ex) -> {
            ex.printStackTrace();
        });
        waiter.start();
        return result;
    }

    private String runNamed(String name, String... command) throws IOException, InterruptedException, ExecutionException {
        Path outPath = dir.resolve(name + ".out");
        Path errPath = dir.resolve(name + ".err");
        int ix = 1;
        while (Files.exists(outPath)) {
            outPath = dir.resolve(name + "-" + ix++ + ".out");
            errPath = dir.resolve(name + "-" + ix++ + ".err");
        }
        Path finalOutPath = outPath;
        Path finalErrPath = errPath;
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectOutput(finalOutPath.toFile())
                .redirectError(finalErrPath.toFile());

        Process proc = pb.start();
        Throwable[] t = new Throwable[1];
        String result = onExit(proc).handle((p, thrown) -> {
            int exitCode = p.exitValue();
            if (exitCode != 0) {
                IOException exitValueException = new IOException(name + " process exited with " + exitCode);
                if (thrown != null) {
                    thrown.addSuppressed(exitValueException);
                } else {
                    thrown = exitValueException;
                }
            }
            t[0] = thrown;
            if (Files.exists(finalOutPath)) {
                try {
                    return FileUtils.readUTF8String(finalOutPath);
                } catch (IOException ex) {
                    if (t[0] != null) {
                        t[0].addSuppressed(ex);
                    } else {
                        t[0] = ex;
                    }
                }
            }
            return null;
        }).get();
        if (t[0] != null) {
            throw new IOException("Execution of " + name + " - " + Strings.join(' ', command) + " failed", t[0]);
        }
        return result;
    }
}
