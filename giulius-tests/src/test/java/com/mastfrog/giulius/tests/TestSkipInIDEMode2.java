package com.mastfrog.giulius.tests;

import org.junit.Test;
import org.junit.BeforeClass;
import static org.junit.Assert.*;

@SkipWhenRunInIDE
public class TestSkipInIDEMode2 extends GuiceTest {
    @BeforeClass
    public static void setUpClass() {
        System.setProperty("in.ide", "true");
    }

    @TestWith
    public void testStuff() {
        fail("This test should not actually be invoked");
    }

    @Test
    public void testAlso() {
        fail("Standard test runner tests should also not be run");
    }
}
