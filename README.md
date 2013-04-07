Giulius ("julius") is a collection of several projects for loading configuration files, binding them using 
Guice and writing boilerplate-free JUnit tests of Guice code.

  * A toolkit for loading Properties files (or URLs) and mapping them with Guice's ``@Named``
     * Inject configuration values using ``@Named("foo")``
     * Provide default values using ``@Defaults("foo=bar")`` - processed into files in ``META-INF/settings``
     * Override default values in files named ``/etc/defaults.properties``, ``~/defaults.properties``, ``./defaults.properties`` (you can choose the file name)
     * Specify the name of the properties file to look in using ``@Namespace`` on the file or package
     * Defaults can also be loaded from a remote URL
     * Optionally specify polling interval for reloading configuration from files or URLs
  * The `resources` project, which provides a similar model for loading ad-hoc files, for legacy projects that need to inject the contents of files from disk from a variety of folders, without making too many assumptions about layout on disk
  * A Maven plugin for merging properties files generated from ``@Defaults``, for building single-JAR applications
  * A Guicified JUnit test runner which allows tests to be written very simply
     * Specify ``@RunWith(GuiceRunner.class)`` on the test class
     * Specify Guice modules using ``@TestWith ( ModuleA.class, ModuleB.class )``
     * Configuration defaults loaded from ``$PACKAGE/$TEST_NAME.properties`` or can be specified in annotations
     * Write normal JUnit test methods, but with arguments:

         @Test
         public void guiceTest ( InjectedThing thing ) { ... }

The idea is to make it easy to specify machine-specific configuration for an application in vanilla properties files.

