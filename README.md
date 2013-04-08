Giulius
-------

Giulius ("julius") is a collection of several projects for loading configuration files, binding them using Guice and writing boilerplate-free JUnit tests of Guice code.  Read the 
<a href="https://timboudreau.com/builds/job/giulius/lastSuccessfulBuild/artifact/giulius/target/site/apidocs/com/mastfrog/giulius/package-summary.html">javadoc here</a>.

Builds and a Maven repository containing this project can be <a href="https://timboudreau.com/builds/"> found on timboudreau.com</a>.

### Features

  * A toolkit for loading Properties files (or URLs) and mapping them with Guice's ``@Named``
     * Inject configuration values using ``@Named("foo")``
     * Provide default values using ``@Defaults("foo=bar")`` - processed into files in ``META-INF/settings`` so the system can always function without a configuration file
     * Override default values in files named ``/etc/defaults.properties``, ``~/defaults.properties``, ``./defaults.properties`` (you can choose the file name or even have multiple files)
     * Specify the name of the properties file to look in using ``@Namespace`` on the file or package
     * Defaults can also be loaded from a remote URL
     * Optionally specify polling interval for reloading configuration from files or URLs
  * The `resources` project, which provides a similar model for loading ad-hoc files, for legacy projects that need to inject the contents of files from disk from a variety of folders, without making too many assumptions about layout on disk
  * A Maven plugin for merging properties files generated from ``@Defaults``, for building single-JAR applications

The idea is to make it easy to specify machine-specific configuration for an application in vanilla properties files, and optionally layer up those files to have machine-specific, user-specific and process-specific settings.

## Usage
--------

Add the Maven repository <a href="https://timboudreau.com/builds/">here</a> into your build
file, and add a dependency 

        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>giulius</artifactId>
            <version>1.3.4</version> <!-- check what the latest version is! -->
        </dependency>

Now, say you have a class which needs a value someone might want to change injected into it - annotate it with ``@Defaults`` and a default value that can be overridden.  You can inject
these as numbers or strings:

    @Defaults("port=8080")
    public class MyServer {
       private final int port;
       @Inject
       public MyServer (@Named("port") int port) {
          this.port = port;
       }
    }

Then ensure defaults are injected.  The easy way is to wrapper the injector in a [Dependencies](https://timboudreau.com/builds/job/giulius/lastSuccessfulBuild/artifact/giulius/target/site/apidocs/com/mastfrog/giulius/Dependencies.html), which offers a few additional useful features (if you don't control injector creation, you can create a Dependencies and call ``createBindings()`` to get a Guice module you can include).

     Dependencies deps = Dependencies.builder().addDefaultSettings().build();
     MyServer server = deps.getInstance(MyServer.class);

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

MIT License


