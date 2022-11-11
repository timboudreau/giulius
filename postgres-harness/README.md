Postgres Harness
================

Launches PostgresSQL quickly over a temporary directory, and optionally populate it
by executing one or more SQL files.

While principally useful as test harness for launching, shutting down and cleaning up
a database inside a unit test, it can also be handy to have an easy way to launch
a pristine database, run some code against it and similar during schema-development -
so this project also emits an executable *fat-jar* attached with the maven classifier `app`.

By default, the harness locates an unused random server port on the local machine, and
(in cli-mode) logs it; when used programmatically simply call `jdbcUrl(databaseName)` to
get a working JDBC URL.

Postgres is launched against a new directory every time - this harness is *really* for things
that want a brand new database, optionally pre-populated by running some SQL against it
to put it in a known state.

And since the port is randomly computed and tested for availability, running many tests
concurrently, each of which is talking to its own postgres instance does not cause any
issues with tests interfering with each other.

Postgres is launched with arguments to minimize memory consumption and maximize performance,
so tests complete quickly (for example, `fsync` is off - tests do not care about durability)
and concurrent postgres processes do not burden the machine running them.  In practice,
on a modern laptop, it is ready to use in < 200ms.

Usage in Unit Tests
-------------------

Add a dependency:

```xml
<dependency>
  <groupId>com.mastfrog</groupId>
  <artifactId>postgres-harness</artifactId>
  <version>2.9.1</version> <!-- CHECK FOR THE LATEST VERSION IN THE POM NEXT TO THIS README -->
</dependency>
```

If you want the standalone application version, add `<classifier>app</classifier>`


And, for example, in a JUnit-5 test:

```java
public class SomeTest {
  private static final String DB_NAME = "someTestDb";
  private PostgresHarness harness;
  private String jdbcUrl;

  @BeforeEach
  public void launchDatabase() throws Exception {
    harness = new PostgresHarness();
    // this will block until the database is up and running
    harness.start();

    // create a database if needed
    harness.initDb(DB_NAME);

    // populate the database
    harness.runSql("create table foo (id : bigserial not null primary key);")
    // or alternately,
    harness.runSql(Paths.get("/path/to/some/file.sql"));
    jdbcUrl = harness.jdbcUrl(DB_NAME);
  }

  @AfterEach
  public void cleanupDatabase() throws Exception {
    if (harness != null) { // no exception launching
      // passing true here will wait for db shutdown and then delete the
      // database directory - if you want to get fancy, you can have JUnit
      // inject a TestInfo, detect if the test failed, and if so, leave the
      // database behind for forensic analysis
      harness.shutdown(true);
    }
  }

  @Test
  public void testIt() throws Exception {
    Connection conn = new YourFavoriteConnectionPoolOrORM().connect(jdbcUrl);
    // read, write, test...
  }
}
```

CLI Usage
---------

For postgres-harness as a command-line application, simply run the `app` artifact
of this project with `java -jar`.  It can be passed a database name, and zero or more
files or directories to run SQL in.

If passing directories, the directories will be *listed* (not walked) and any `.sql`
files found will be run in the order they are discovered.  Since order is usually
important, if you want to use multiple files and a directory path, name the sql files
so that they will lexically sort them in the order you want them run - i.e.
`00-init.sql`, `01-create-tables.sql` and so forth.

The simplest way to get the CLI JAR is to temporarily add `<classifier>app</classifier>`
to a maven project, build it once so maven downloads it, and then it will be under
`~/.m2/repository/com/mastfrog/postgres-harness` in your home directory - look for,
e.g. `postgres-harness-2.9.1-app.jar` and just run it with java-jar, or copy it
somewhere convenient, or set up a shell script to run it, as you wish.

The following help is also available by running the jar with `--help`:

```
postgres-harness - quickly start and initialize postgres in an empty temporary directory.

By default, finds a random unused server port to use, and logs it along with the database URL.

Usage
-----

java -jar postgres-harness.jar DB_NAME [/path/to/file-1.sql .. path/to/file-n.sql]

Options
-------

	--port NNNN explicitly set the port to run postgres on
	--cleanup delete the database folder on normal VM exit (including CTRL-C)
        --help show this help
```
