package com.mastfrog.giulius.tests;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class BeforeAfter {

    static int beforeCallCount;
    static int beforeClassCallCount;
    static int afterCallCount;
    static int afterClassCallCount;

    @BeforeClass
    public static void beforeClass1() {
        beforeClassCallCount++;
    }

    @AfterClass
    public static void afterClass1() {
        afterClassCallCount++;
    }

    @BeforeClass
    public static void beforeClass2() {
        beforeClassCallCount++;
    }

    @AfterClass
    public static void afterClass2() {
        afterClassCallCount++;
    }

    @Before
    public void before1() {
        beforeCallCount++;
    }

    @After
    public void after1() {
        afterCallCount++;
    }

    @Before
    public void before2() {
        beforeCallCount++;
    }

    @After
    public void after2() {
        afterCallCount++;
    }
}
