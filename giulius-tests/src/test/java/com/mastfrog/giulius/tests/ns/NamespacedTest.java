package com.mastfrog.giulius.tests.ns;

import com.google.inject.BindingAnnotation;
import com.mastfrog.giulius.tests.GuiceRunner;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author tim
 */
@RunWith(GuiceRunner.class)
public class NamespacedTest {
    
    @Test
    public void test(@Nullable WildcardOptions opts) {
        assertNotNull(opts);
        assertEquals (2, opts.getWildcardMinPrefix());
        assertEquals (15, opts.getMaxWildcardPerTerm());
    }
    
    @BindingAnnotation
    public @interface Nullable {
        
    }
}
