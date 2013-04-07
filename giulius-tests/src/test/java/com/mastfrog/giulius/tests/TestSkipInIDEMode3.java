package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;
import org.junit.BeforeClass;
import static org.junit.Assert.*;

@SkipWhenRunInIDE
public class TestSkipInIDEMode3 extends GuiceTest {
    @BeforeClass
    public static void setUpClass() {
        System.setProperty("in.ide", "true");
    }

    @TestWith
    public void testStuff(String s) {
        assertEquals ("A", s);
    }

    static class A extends AbstractModule {
        @Override
        protected void configure() {
            bind(String.class).toInstance("A");
        }
    }

    @SkipWhenRunInIDE
    static class B extends AbstractModule {
        @Override
        protected void configure() {
            bind(String.class).toInstance("B");
        }
    }
}
