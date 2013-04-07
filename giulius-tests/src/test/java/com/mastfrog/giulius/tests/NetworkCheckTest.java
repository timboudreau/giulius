package com.mastfrog.giulius.tests;

import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
public class NetworkCheckTest {
    @Test
    public void testCheck() throws Exception {
        NetworkCheck check = new NetworkCheck("127.0.0.1", false);
        boolean result = check.isNetworkAvailable();
        assertTrue (result);
        System.out.println("Check " + check + ": " + result);
        
        assertEquals (result, check.isNetworkAvailable());
        
        check = new NetworkCheck("169.254.0.15", false); //Link Local - should always fail
        System.out.println("Check " + check + ": " + result);
        result = check.isNetworkAvailable();
        assertFalse (result);
        
        NetworkCheck google = new NetworkCheck("google.com", true);
        result = google.isNetworkAvailable();
        System.out.println("Result for " + google + ": " + result);
    }
    
    @Test
    @SkipWhenNetworkUnavailable
    public void testSkipWhenNetworkUnavailableAnnotation() {
        NetworkCheck check = TestMethodRunner.NETWORK_CHECK;
        System.out.println("testSkipWhenNetworkUnavailableAnnotation running");
        assertTrue (check.isNetworkAvailable());
        System.out.println("  result " + check);
    }
}
