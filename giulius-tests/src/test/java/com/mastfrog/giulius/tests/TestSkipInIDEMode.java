package com.mastfrog.giulius.tests;

import org.junit.BeforeClass;
import static org.junit.Assert.*;

public class TestSkipInIDEMode extends GuiceTest {
    @BeforeClass
    public static void setUpClass() {
        System.setProperty("in.ide", "true");
    }

    @TestWith
    @SkipWhenRunInIDE
    public void testStuff() {
        fail("This test should not actually be invoked");
    }
}
