package com.mastfrog.giulius.tests;

import com.google.inject.AbstractModule;
import com.mastfrog.giulius.tests.BeforeAfterTest.MMMM;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(MMMM.class)
public class BeforeAfterTest extends BeforeAfter {
    
    static class MMMM extends AbstractModule {

        @Override
        protected void configure() {
            bind(String.class).toInstance("HELLO");
        }
        
    }

    @Test
    public void test(String s) throws Exception {
        assertNotNull(s);
        assertEquals("HELLO", s);
        assertEquals(2, beforeCallCount);
        assertEquals(2, beforeClassCallCount);
    }

    @After
    public void afterItAll() {
        afterCallCount++;
    }

    @AfterClass
    public static void afterClass3() {
        assertEquals(3, afterCallCount);
    }
}
