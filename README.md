Giulius
-------

Giulius ("julius") is a collection of several projects for loading configuration files, binding them using Guice and writing boilerplate-free JUnit tests of Guice code.  Read the 
<a href="https://timboudreau.com/builds/job/giulius/lastSuccessfulBuild/artifact/giulius/target/site/apidocs/com/mastfrog/giulius/package-summary.html">javadoc here</a>;  or [scroll down for the tutorial](#introduction--tutorial)

Builds and a Maven repository containing this project can be <a href="https://timboudreau.com/builds/"> found on timboudreau.com</a>.

### Features

  * A toolkit for loading Properties files (or URLs) and mapping them with Guice's ``@Named``
     * Inject configuration values using ``@Named("foo")``
     * Provide default values using ``@Defaults("foo=bar")`` - processed into files in ``META-INF/settings`` so the system can always function without a configuration file
     * Override default values in files named ``/etc/defaults.properties``, ``~/defaults.properties``, ``./defaults.properties`` (you can choose the file name or even have multiple files)
     * Specify the name of the properties file to look in using ``@Namespace`` on the file or package
     * Defaults can also be loaded from a remote URL
     * Optionally specify polling interval for reloading configuration from files or URLs
  * A Maven plugin for merging properties files generated from ``@Defaults``, for building single-JAR applications

The idea is to make it easy to specify machine-specific configuration for an application in vanilla properties files, and optionally layer up those files to have machine-specific, user-specific and process-specific settings.

## Usage
--------

Add the Maven repository <a href="https://timboudreau.com/builds/">here</a> into your build
file, and add a dependency 

```xml
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>giulius</artifactId>
            <version>1.3.4</version> <!-- check what the latest version is! -->
        </dependency>
```

Now, say you have a class which needs a value someone might want to change injected into it - annotate it with ``@Defaults`` and a default value that can be overridden.  You can inject
these as numbers or strings:

```java
    @Defaults("port=8080")
    public class MyServer {
       private final int port;
       @Inject
       public MyServer (@Named("port") int port) {
          this.port = port;
       }
    }
```

Then ensure defaults are injected.  The easy way is to wrapper the injector in a [Dependencies](https://timboudreau.com/builds/job/giulius/lastSuccessfulBuild/artifact/giulius/target/site/apidocs/com/mastfrog/giulius/Dependencies.html), which offers a few additional useful features (if you don't control injector creation, you can create a Dependencies and call ``createBindings()`` to get a Guice module you can include).

```java
     Dependencies deps = Dependencies.builder().addDefaultSettings().build();
     MyServer server = deps.getInstance(MyServer.class);
```

If you then create a file named ``defaults.properties`` in the process' working directory or
your home directory, and add

    port=8234

Then the value from the annotation will be ignored and this one will be used.  There are many options for affecting what and how and from where settings are loaded - ``defaults.properties``
is just a default value.


## Projects

The following projects are here:

  * giulius - The core framework that binds ``@Named`` to the contents of properties files, provides builders for Guice injectors and a few other features
  * giulius-settings - A mini-framework for loading properties from an ordered set of properties files or URLs with properties-format data at the end of it
  * giulius-tests - The JUnit runner that eliminates boilerplate in setting up unit tests of injected objects
  * maven-merge-configuration - Merges all ``.properties`` files in ``META-INF/settings`` on the compile classpath into the current project's classes output directory.  Giulius' annotation processor writes files there, but if you want to build a marged executable JAR file, something needs to merge the defaults from all such files.


### Credits

Many of the concepts represented here were inspired by (and in some cases, named identically to) mini-frameworks [Eelco Hillenius](https://github.com/chillenious) did when we worked together a few years ago, which were too nice to live without, leading me to build similar things from scratch.

[Greg Bollella](https://www.facebook.com/greg.bollella), of Real-Time Java fame, gets credit for suggesting the name.

### License

MIT License - do what thou wilt, give credit where it's due

Introduction & Tutorial
=======================

The purpose of these libraries is to make you more productive and to enable
software to focus on what it's there to do, by removing the need for boilerplate.
In the spirit of standing on the shoulders of giants, most of the heavy lifting
is done by Google's dependency-injection library, [Guice](http://code.google.com/p/google-guice/).

What kinds of boilerplate are we eliminating?
---------------------------------------------

1. *Configuration loading/parsing* - Code should be focused on what it's there to
do, but frequently it's necessary to load settings.  This takes the form of a block
of code, in an otherwise sane class, which parses strings into java primitives, and
sometimes constructs some sort of object out of the result.

2. *Startup initialiation* - "plumbing" code that wires things together and 
intializes things.

3. *Test initialization* - tests usually need to start an environment very similar
to that of an application, yet different.  Once we've addressed startup initialization,
it's easy to apply that to tests too.

On the Value of Tests
---------------------

I realized after the first draft of this that I was writing as if tests were a
good in and of themselves, and perhaps this needs some justification.

If tests are written after the fact, it is usually seen as a cost, and possibly
not a good use of time.  If it is done as part of development, it allows you to
build a system bit by bit, based on parts which you can literally prove work.
This means that you find more bugs sooner, and also that you know if some later
work is affecting another part of the system in an unanticipated way.  I can't
count the number of times in developing these libraries that having a huge test
suite has saved me from committing code I thought should work but which had
subtle problems.

Code is an investment of time and work.  Tests make it possible to 
keep that investment.  Code which is covered by tests
is hard to break with future changes because (with Maven) 
you know that something is broken as soon as you try to compile.

Tests also form an invaluable source of real code that demonstrates how to use
any API.  If you can guarantee that any feature worth using has tests, then you
always know where to go for examples of how to use that feature.

In the end, the value of tests is easier to experience than to put into words:
Once you have the experience of doing doing development where you can simply be
sure that all the parts you're building on work as advertised, it's hard to 
go back.

Getting Started
===============

Assuming you're using [Maven](http://maven.apache.org/), starting to use these
libraries is as simple as declaring a dependency on them:

        <dependency>
            <groupId>com.mastfrog</groupId>
            <artifactId>giulius-settings</artifactId>
            <version>1.3.6-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.mastfrog</groupId>
            <artifactId>giulius</artifactId>
            <version>1.3.6-SNAPSHOT</version>
        </dependency>
        <dependency>
            <groupId>com.mastfrog</groupId>
            <artifactId>giulius-tests</artifactId>
            <version>1.3.6-SNAPSHOT</version>
            <scope>test</scope>
        </dependency>

For the test code in this document, it's probably simplest to depend on all 
three.  Check what the latest version is - it was ``1.3.6-SNAPSHOT`` when
this document was last updated on 7 May 2013.

Using Settings
==============

Any non-trivial application has some configuration.  For simple programs that
are run from the command-line, it is always preferable for these to be able to
be passed in as arguments to the process.

But libraries need settings too, and they need to come from somewhere.  The
Settings library solves that problem.

The main interface to settings is a class called `Settings`.  It is a no-muss,
no-fuss interface to key/value pairs.

```java
    public interface Settings extends Iterable<String> {
        Integer getInt(String name);
        int getInt(String name, int defaultValue);
        Long getLong(String name);
        long getLong(String name, long defaultValue);
        String getString(String name);
        String getString(String name, String defaultValue);
        Boolean getBoolean(String name);
        boolean getBoolean(String name, boolean defaultValue);
        Double getDouble(String name);
        double getDouble(String name, double defaultValue);
        Set<String> allKeys();
        Properties toProperties();
    }
```

There are plenty of similar settings interfaces (which can in fact be adaptered
to ``Settings`` very easily);  the key distinction about Settings is that it
does not have mutator methods.  That doesn't mean the contents of a `Settings` can't
change, just that if you want that to happen, the code that does that is probably
not the code that also consumes the settings - and if it is, that indicates a 
design problem.

Note that ``Settings`` does not do magic:  If you have a setting stored as "foo"
and you call `Settings.getInt` on it, you will get a `NumberFormatException`.
Generally these are fail-fast when used with Guice, meaning that if the settings
are unusable, an `Error` will be thrown early in startup with a meaningful
message.

Typically you don't implement ``Settings``.  You get a ``Settings`` using 
a ``SettingsBuilder``.  That class has ways to add properties files, properties
objects, other settings objects, and even properties-format text from a 
remote URL.  You can also add environment variables and system properties to the
set of available settings;  and you can customize values and put them in there
during initialization.

Here's a simple example:

```java
    Settings settings = new SettingsBuilder().addSystemProperties().build();
    String home = settings.getString("user.home");
```

This isn't that exciting, since we've just traded a call to `System.getProperty("user.home")`
for a call to `Settings.getString()`.  So what else can you do with Settings?

Layered Settings
----------------

``SettingsBuilder`` uses a [fluent interface](http://en.wikipedia.org/wiki/Fluent_interface) to
allow you to stack up multiple sources of settings.  When you call `Settings.get*`,
the last one you added is consulted first, followed by the next-to-last, and so forth.
You can try this out with some `Properties` objects:

```java
    Properties a = new Properties();
    a.setProperty("hello", "Hello world");
    Properties b = new Properties();
    b.setProperty("hello", "Goodbye world");

    Settings settings = new SettingsBuilder().add(a).add(b).build();
    System.out.println(settings.getString("hello"));
```

The result of this code will be the output

    Goodbye world

Reading Settings
----------------

There wouldn't be much point to settings if they had to be constructed programmatically,
or you had to manually read them from files, so typically you don't pass 
settings into a `SettingsBuilder`, you *tell it where it should load them from*.
There are several overloaded `add()` methods on `SettingsBuilder` for doing that:

1. `String` adds a string-based path on the classpath (it should not start with a `/`).
This tells Settings to look for a file with that name in *every* JAR on the classpath
and merge them together (if one overrides another, whichever one comes later in
the classpath order wins).

2. `File` adds a file (if it exists), causing it to be read as a Java `.properties` file
using `Properties.load()`.  Optionally, you can specify an interval for how often the file
should be reloaded (so you can have configuration that is dynamic - only do this if you actually need that).

3. `InputStream` reads any input stream you pass in as a properties file.

4. `URL` tries to load a URL as a properties file.  As with File, it can be passed
an interval after which it should be reloaded.

5. `key, value` allows you to add in a single key/value pair

In addition, there are several methods which will automatically add all of a 
standard set of key/value pairs:

 - `addEnv()` adds the contents of all environment variables visible to the process as key/value pairs in Settings
 - `addSystemProperties()` adds all system properties
 - `addGeneratedDefaultsFromClasspath()` adds all properties files on the classpath in the location `com/mastfrog/generated-defaults.properties` (*if* the settings builder is using the default namespace - see below)
 - `addDefaultsFromClasspath()` adds all properties files on the classpath in the location `com/mastfrog/defaults.properties`, and any file named `defaults.properties` in the user's home directory (*if* the settings builder is using the default namespace - see below)

Finally, there is simply `SettingsBuilder.createDefault()` which gets you a standard stack of settings which combine system properties, environment variables, defaults files from the classpath and home directories:

        File homeDefaults = new File(new File(System.getProperty("user.home")), "defaults.properties");
        SettingsBuilder b = new SettingsBuilder()
                .addEnv()
                .addSystemProperties()
                .addGeneratedDefaultsFromClasspath()
                .addDefaultsFromClasspath();
        if (homeDefaults.exists()) {
            b = b.add(homeDefaults);
        }

So, you take a ``SettingsBuilder`` and build up a Settings object which merges together whatever sources of
settings you want;  then you pass that into Giulius ``Dependencies`` to get Guice to bind the values in
the settings.

Using Annotations to Specify Settings
-------------------------------------

One of the usual problems with key/value pair settings is that a program is written
to expect a setting always to exist, but does not handle the case that it is 
simply not there.  Another typical problem is having a huge file full of settings,
and having no idea which ones are still used or what classes consume them.

What if code which contained a setting actually defined the settings it needs
and provided a definition of a reasonable default?

We handle this with the annotation `@Defaults`, like this:

    @Defaults("port=8080","baseurl=http://localhost")
    public class Server { ...

This keeps the definition of the setting with the code that uses it;  and additionally, it
gives us access to settings at compile-time, so they can be sanity checked,
documented or whatever we want.

Using Namespaces with Settings
------------------------------

For legacy code, it may not be enough to provide one blob of keys and values - 
it may have been written to look in a specific place, and that mechanism may need to 
continue working for backward compatibility.

For that purpose, `SettingsBuilder.forNamespace` exists.  What this does is change
the default locations it will look in for settings.  So if you call

    Settings s = SettingsBuilder.forNamespace("log-service").createDefault().build();

then you get a settings which still contains environment variables and system
properties, but looks on the classpath in `com/mastfrog/generated-log-service.properties`, `com/mastfrog/log-service.properties` 
and `~/log-service.properties` for default values.  To specify values for these
namespaces using the `@Defaults` annotation, do it like this:

    @Defaults(value={"port=8080","baseurl=http://localhost"}, namespace=@Namespace("log-service"))
    public class Server { ...

Mutability
----------

You may have noticed that the `Settings` interface has no setters - this is
intentional, as it is almost always a bad idea for code which *consumes* settings
also to be able to modify them.

However, for legacy code, you can call `SettingsBuilder.createMutable()` to get
a Settings object which can be mutated.  All such settings are in-memory only, and
disappear on VM shutdown.  

This library is about making settings available to an application, with limited
support for settings that are reloaded periodically.  Full-blown support for creating
and writing files full of settings has different requirements and is an orthagonal
concern.


Giulius
=====
[Guice](http://code.google.com/p/google-guice/) is a dependency injection (DI) tool.
Depending on whom you talk to, you can get many answers about the purpose of
dependency injection, such as

 - It eliminates the need to call constructors
 - It adds clarity to code because you can see, in any objects constructor arguments,
all of the objects which it will ever touch
 - It eliminates the threat of ever seeing *uninitialized objects*
 - It means that systems can be decoupled - something that uses a log service 
doesn't need to know anything about the implementation of it or how to start it

A common pattern in non-DI code is to use static variables for singleton objects.
Typically there is a static getter which initializes the service, so that the exact
details of how it is initialized are encapsulated;  usually there is a synchronized
block so that two callers on different threads do not initialize two instances
at the same time.

```java
    public class FooService {
        private static FooService INSTANCE;
        public static FooService get() {
            synchronized (FooService.class);
               if (INSTANCE == null) {
                   //...read some files, make an instance here
               }
            }
        }
        public void doSomething() { ... }
    }
```

This works, but it has some problems:

 - There is only one way ``FooService`` can get initialized, and that has to be the
same way it is initialized in production. So if ``FooService`` needs a message queue and
a database, then any unit test, no matter how trivial, will need those things
running.  The typical result is that any use of ``FooService`` discourages test writing
for all code that calls it - since it makes the code untestable without heroic efforts.  If
``FooService`` is the heart of the system, it will discourage having tests throughout the whole thing.
 - Synchronizing on every call is not very nice - it will limit paralellism (you could use `volatile` and [double-checked locking](http://en.wikipedia.org/wiki/Double-checked_locking) to eliminate that)
 - It ties every caller directly to the implementation type - the author is forever limited to making FooService be better, and locked out of making a better ``FooService``
 - It gets initialized on a first-come, first-served basis - so it is not at all obvious 
who initializes it.  And if it is misconfigured, the application may be running for a long
time before it shows any sign that something is terribly wrong.
 - We are really stuck with one instance per JVM, without resorting to 
classloader tricks.  A lot of the time might seem be okay, but it severely limits
the ability to test code (easy to write a test that depends on state left behind
in some global service by a previous test, and spend hours trying to debug why
a test started failing after an unrelated change).  It also limits future flexibility - 
there might be good reason to run instantiate two totally independent instances in
the same VM, and not have them be able to interfere with, or even see each other.

The typical fix for this problem is to [separate interface and implementation](http://c2.com/cgi/wiki?SeparateInterfacesFromImplementation).  That looks like this:

```java
    public interface FooService {
        void doSomething();
    }

    public class FooServiceImpl implements FooService { ... }
```

This gets a lot farther - code that cares about ``FooService`` doesn't need to know
what implements ``FooService`` - it can simply care that there is one.  This helps
a lot - it is now possible to write tests against a toy implementation of 
FooService.  So the codebase is becoming testable.

There's just one problem:  *Somebody still has to know about FooServiceImpl*.
So you code that wants a FooService still has to call `new FooServiceImpl()`.
We haven't solved anything at all!

At this point somebody aware they have a problem makes their first cut at
something like dependency injection:

```java
    public abstract class FooService {
        private static FooService INSTANCE;
        public static synchronized FooService getInstance() {
            Class<?> type;
            if (INSTANCE == null) {
                Class<?> type = Class.forName(System.getProperty("fooService"));
                if (type == null) { type = DefaultFooService.class; }
                INSTANCE = (FooService) type.newInstance();
            }
            return INSTANCE;
        }
    }
```

Now something or other can set what class to use for `FooService`, and code
that uses it doesn't have to know where it comes from.  Something still has
to set the system property, but at least we can hide `FooServiceImpl` from
being a dependency of everything that uses `FooService`.
            
What if we could initialize all the things like `FooService` on startup, and
code just did not have to worry about where it comes from, if it is initialized,
or calling some getter that might or might not initialize `FooService`?

What if we could find out immediately on startup if `FooService` could not possibly
run, and abort startup in time to tell a person what is happening?

That is the problem (along with the others listed above) which Guice solves.

Using Guice
-----------

Guice introduces an invisible phase of application startup, during which services
which are needed at runtime are loaded and instantiated.  It provides a well-defined
place to glue together interface and implementation, so that it's guaranteed that
there is only *one thing* which needs to know about both.

There are first-class concepts to know about in Guice:

 - `Injector` - a thing which you can ask for instances of classes you need, in
the very simple form injector.getInstance(FooService.class);  Guice will take care
of creating whatever objects are needed to construct an instance.
 - `Module` - a Guice module specifies *bindings* - what implementation to use
for what interface.  This takes a form such as

```java
    bind(FooService.class).to(FooServiceImpl.class);
```

So there still is something which knows about both - but it is tucked away in
a Guice module which is seen at startup and never again.  If FooServiceImpl
takes some objects in its constructor, Guice will create those if it can.  This
looks like this

```java
    final class FooServiceImpl implements FooService {
        private final int port;
        private final LogService logService;
        @Inject
        public FooServiceImpl(@Named("port") int port, LogService logService) {
            this.port = port;
            this.logService = logService;
        }
        ...
    }
```

Here we are doing a few things:

 - Marking our constructor with `@Inject` to tell Guice it should try to create
all of the objects it needs
 - Using Guice's `@Named` annotation to say "I want an integer named 'port'"
 - Asking for an instance of LogService as well (Guice needs to be able to create it or know where it is too)

An important thing to remember about Guice is exactly that:  Guice is all
about building a graph of objects on startup.  You *can* ask it for objects at
runtime, but usually you shouldn't.  It's about setting up your dependencies.
If you ask Guice to make objects for you after startup, it will work, but this
is more the [service locator](http://en.wikipedia.org/wiki/Service_locator_pattern)
pattern, *and* it's no longer clear from your constructor what your class uses.
If you are writing a framework in its own right, you may legitimately need to
do that;  in application code, it's an indication that something is being done wrong.

You can think of Guice's `Injector` as the repository of all those things which
used to be held in static variables.

Guice Meets Settings = Giulius
==============================

In the last example above, you may have noticed use of the `@Named` annotation.
Where did that come from?

While the "keys" Guice uses to look up objects are (typically) `Class` objects,
it is routine to need more than one of something.  One of the ways you can solve
that is to give names to specific instances of things.

That can be helpful for all sorts of things:

```java
    FooService (@Named("requests") Logger requestLogger, @Named("debug") Logger debugLogger) {...}
```

but its primary use is for small bits of configuration - the kind of things people
typically read from properties files - like the "port" number from the earlier 
example.

Without a library to help, this is fairly cumbersome, though:

```java
    bind(Integer.class).annotatedWith(Names.named("port")).toInstance(8080);
```

and it means some piece of code hard-codes a value that very likely needs to be
configured on a per-machine basis.

That's where Giulius comes in.  It provides a way to bind all of your key/value
pairs to `@Named` based on the contents of `Settings`.

A class called `Dependencies` wraps Guice's injector and initialization, and
automatically binds `@Named` appropriately.  It is a thin layer on top of
Guice - it just adds one module which does property bindings, and a few other
useful application lifecycle utilities.

To use it, use the `Dependencies` class:

```java
    Settings settings = SettingsBuilder.createDefault().build();
    Dependencies deps = new Dependencies(s, moduleA, moduleB, moduleC);
    //Typically you ask Guice for a single entry-point class;  this bootstraps the rest of the system
    FooService service = deps.getInstance(FooService.class);
```

So, the only difference between this and vanilla Guice usage is that we're using
a Dependencies object instead of talking directly to Guice's injector.  Dependencies
adds just two things on top of vanilla Guice:

 - Binds ``Settings`` key/value pairs to `@Named` and `@Value` automatically
 - Provides for lifecycle management with `ShutdownHookRegistry` - this can be used
to de-initialize things that would other be memory leaks when the system is logically
finished with running, whether or not the VM is actually shutting down (for example,
this allows us to *completely* clean up the environment after running one test,
so that it does not affect the next one, without needing a new VM)

Namespaces
----------

Above we mentioned that `SettingsBuilder` can specify "namespaces" - logically
independent settings objects which load from different places.

We make this very easy to use with Dependencies, as follows:  In a large application, 
often a subsystem 
deals with specific configuration just for it.  And particularly with legacy code,
it may already have configuration loading code that expects that configuration to
be isolated from the rest of the system.  You *could* just take all such configuration,
aim it at the same properties file and hope it works - but there can be name collisions,
and it is probably a better idea to evolve toward that than have it be an all-or-nothing
proposition.

This is handled using the `@Namespace` annotation.  *You can annotate either a 
class or an entire package with `@Namespace`* to change where all uses of 
`@Named` get their values from - to aim it at a specific set of key-value pairs
which might even get its data, initially, from the same file you've already used.
`Settings.toProperties()` makes it possible to use properties-related code 
unmodified.

Migrating code like this can be accomplished in several phases, and at each
phase you have code that works:

1. Change the code to take an injected Settings object in its constructor instead of reading
a file;  use `SettingsBuilder.add(File)` to create a `Settings` over the same
file you've always used;  use `Settings.toProperties()` initially to leave the
legacy code unmodified
2. Change the code to use a `Settings` object directly
3. Change the code (if practical) to use `@Named` for settings
4. If there are commonly used values or reasonable defaults, move them into code
using `@Defaults` and delete them from the configuration file
5. (If desired) Carefully merge the namespaced settings into the global default
namespace, ensuring no collisions, and delete the `@Namespaced` annotation 


Writing Tests with Giulius-Tests
================================

It is fairly simple to write standard unit tests with Guice - just use your
`setUp()` method to do the incantation described above.  It just gets repetitive
to put this boilerplate in every test.

So we have a framework for that.  The tests.guice framework allows you to use
JUnit pretty much the way you are used to, with a simple difference: *your test
methods can take arguments*.

There are three annotations to be concerned with:
 - `@RunWith(GuiceRunner.class)` - annotate your test class with this to tell 
JUnit that you want to use our GuiceRunner to invoke your tests instead of the
standard mechanism
 - `@TestWith(ModuleA.class, ModuleB.class)` - you can annotate your test class,
*or individual test methods* or both, to tell GuiceRunner what modules you need
instantiated to set up all the bindings needed to make the objects that are
arguments to your test method.  You can substitute this for the standard JUnit
`@Test` annotation (if you annotated the class with `@TestWith`) or you can
annotate the class and just use `@ Test` normally.  You can also use it on both
the class and the method if you want some modules used for all test methods, and
other modules just used for some tests.

Modules instantiated with `@TestWith` may either have a no-argument constructor,
or a single argument of `Settings`.

So, our tests look pretty similar to ordinary JUnit tests:

```java
    @TestWith(EncryptionModule.class)
    public class EncryptorTest extends GuiceTest {
        @Test
        public void testPublicKeyEncryption(PublicKeyEncryptor enc) {
            assertNotNull(enc);
            String toEncrypt = "This string will be encrypted, if this goes right";
            String encrypted = enc.encryptWithPublicKey(toEncrypt);
            assertNotNull(encrypted);
            System.out.println("Got " + encrypted);

            String decrypted = enc.decryptWithPrivateKey(encrypted);
            assertEquals(toEncrypt, decrypted);
        }
    }
```

except that all of the objects and configuration needed to generate key pairs
or read them from settings are neatly hidden behind Guice - and our test code
gets to focus on what really matters - the thing it is there to test!

If your test requires some custom ``Settings``, this is simple - put a properties
file with the same name as the text, next to the test on the classpath and it
will be loaded before when your tests are run.

``@TestWith`` can be applied to either the _test class_ or or the _test method_
or both.  And, of course, your test classes can use ``@Inject`` - though in this
author's opinion, parameter injection is preferable.

If you have a method you want to run immediately after injection, you can annotate
it with ``@OnInjection``.

#### Advanced Testing Features

Sometimes you have more than one implementation of a subsystem which needs to be
tested, but you want to run more-or-less the same tests on several implementations.
Giulius-Tests makes it easy to run the same test multiple times with different
Guice modules - annotate your test

```java
        @TestWith(value = {TestHarness.Module.class},
        iterate = {
            ResourcesApp.ClasspathResourcesModule.class,
            ResourcesApp.FileResourcesModule.class,
            ResourcesApp.MergedResourcesModule.class
        })
```

[This test](https://github.com/timboudreau/acteur/blob/master/acteur-resources/src/test/java/com/mastfrog/acteur/resources/StaticResourcesTest.java) is a working example of how to do that.

In fact, if you're slightly insane, you can annotate both the test class and the
test method with different ``iterate=`` values, and wind up testing all possible
combinations of the sets of modules specified in both annotations (while probably
not useful in the real world, there is a test for the test framework that proves that
it will work correctly if you do it).





