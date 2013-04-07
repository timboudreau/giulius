package com.mastfrog.giulius.tests.beforeafter;

import com.google.inject.AbstractModule;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.OnInjection;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.giulius.tests.beforeafter.ExampleTest.M;
import java.util.Stack;

@RunWith(GuiceRunner.class)
@TestWith({ M.class })
public class ExampleTest {
    static class L {
        public void info(String s) {
            System.out.println(s);
        }
    }
    static L log = new L();

    static volatile Object a;
    volatile Object b;
    volatile Object c;
    
    static Stack<Object> s = new Stack<Object>();
    
    static class M extends AbstractModule {

        @Override
        protected void configure() {
            //do nothing
        }
        
    }

    // private ProfileAPIService service;

    @BeforeClass
    public static void setupDatabase() throws Exception {
        log.info("--------------- Starting One Time Only Initialization ---------------");
        a = new Object();
        log.info("--------------- Ending One Time Only Initialization ---------------");
        Thread.dumpStack();
    }

    @Before
    public void start() {
        log.info("--------------- Starting test ---------------");
        b = new Object();
        s.push(b);
        Thread.dumpStack();

        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
    }

    @OnInjection
    public void initialize() {
        log.info("--------------- Initializing profile service. ---------------");
        c = new Object();

        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
    }

    @After
    public void cleanUp() {
        System.out.println("!!!!! Cleanup!");
        Thread.dumpStack();
        System.out.flush();
        
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertSame(b, s.pop());
//        b = null;

        log.info("--------------- Cleaning up profile service. ---------------");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("--------------- Ending clean up profile service. ---------------");
    }

    @Test
    public void testAddFolder(User user) {
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertEquals(b, s.peek());
        
        log.info("========== Starting testAddFolder ==========");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("========== Ending testAddFolder   ==========");
    }

    /*
    @Test
    public void testAddSavedQueryFolder(User user) {
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertEquals(b, s.peek());
        
        log.info("========== Starting testAddSavedQueryFolder ==========");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("========== Ending testAddSavedQueryFolder  =========");
    }

    @Test
    public void testDeleteFolder(User user) {
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertEquals(b, s.peek());
        
        log.info("========== Starting testDeleteFolder       ==========");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("========== Ending testDeleteFolder         =========");
    }

    @Test
    public void testUpdateFolder(User user) {
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertEquals(b, s.peek());
        
        log.info("========== Starting testUpdateFolder       ==========");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("========== Ending testUpdateFolder         =========");
    }

    @Test
    public void testFolderDocuments(User user, ObjectMapper mapper) {
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertEquals(b, s.peek());
        
        log.info("========== Starting testFolderDocuments    ==========");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("========== Ending testFolderDocuments      =========");
    }

    @Test
    public void testSavedQueryDocuments(User user, ObjectMapper mapper) {
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertEquals(b, s.peek());
        
        log.info("========== Starting testSavedQueryDocuments ========");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("========== Ending testSavedQueryDocuments  =========");
    }

    @Test
    public void testEnablingAlert(User user) {
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertEquals(b, s.peek());
        
        log.info("========== Starting testEnablingAlert   ===========");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("========== Ending testEnablingAlert  =============");
    }

    @Test
    public void testLastUpdateTime(User user, ObjectMapper mapper) {
        assertNotNull(b);
        assertFalse(s.isEmpty());
        assertEquals(b, s.peek());
        
        log.info("======== Starting testLastUpdateTime =======");
        log.info("A = " + a);
        log.info("B = " + b);
        log.info("C = " + c);
        log.info("======== Ending testLastUpdateTime   =======");
    }
    */
}
