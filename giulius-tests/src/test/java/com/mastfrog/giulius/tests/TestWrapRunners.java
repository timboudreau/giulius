package com.mastfrog.giulius.tests;

import com.mastfrog.giulius.tests.TestWrapRunners.ThingModule;
import com.google.inject.AbstractModule;
import com.mastfrog.giulius.tests.TestWrapRunners.GR;
import com.mastfrog.giulius.Dependencies;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
@RunWith (GR.class)
@TestWith(ThingModule.class)
public class TestWrapRunners {

    @Test
    @MyAnno(name="Hello", value="Test World")
    public void testWrappedRunner() {
        assertEquals ("Test World", System.getProperty("Hello"));
    }

    @MyAnno(name="Goodbye", value="World")
    @Test
    public void testModulesAreUsed(Thing thing) {
        assertNotNull (thing);
    }

    static abstract class Thing {

        Thing () {

        }
    }

    static final class ThingModule extends AbstractModule {

        @Override
        protected void configure() {
            bind (Thing.class).toInstance(new Thing(){});
        }

    }

    public static class GR extends GuiceRunner {
        public GR (Class<?> tc) throws InitializationError {
            super (tc);
            registerRunWrapper(new RW());
        }
    }

    private static final class RW extends RunWrapper {
        RW() {
            super (MyAnno.class);
        }

        @Override
        protected void invokeTest(Statement base, Object target, FrameworkMethod method, Dependencies dependencies) throws Throwable {
            System.out.println("Invoke test " + method);
            MyAnno anno = method.getAnnotation(MyAnno.class);
            System.out.println("anno is " + anno);
            assert anno != null;
            System.setProperty(anno.name(), anno.value());
            base.evaluate();
        }
    }
}
