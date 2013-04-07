package com.mastfrog.giulius.tests;
import com.google.inject.Inject;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
public class TestMethodInjection {
    
    @TestWith({ModuleA.class, ModuleC.class})
    public void testAInjection(String val) {
        assertNotNull (val);
        assertEquals ("A!", val);
    }

    @TestWith({ModuleB.class, ModuleC.class})
    public void testBInjection(String val) {
        assertNotNull (val);
        assertEquals ("B!", val);
    }

    @TestWith({ModuleA.class, ModuleC.class})
    public void testTwoArguments(String val, StringBuilder sb) {
        assertNotNull (sb);
        assertEquals ("Injected by C", sb.toString());
        assertEquals ("A!", val);
    }
    
    @TestWith({ModuleA.class, ModuleC.class})
    public void testGenericsA(List<Integer> ints, List<String> strings) {
        assertNotNull (ints);
        assertNotNull (strings);
        assertEquals (Arrays.asList(1, 2, 3, 4), ints);
        assertEquals (Arrays.asList("a1", "a2", "a3"), strings);
    }

    @TestWith({ModuleB.class, ModuleC.class})
    public void testGenericsB(List<Integer> ints, List<String> strings) {
        assertNotNull (ints);
        assertNotNull (strings);
        assertEquals (Arrays.asList(1, 2, 3, 4), ints);
        assertEquals (Arrays.asList("b1", "b2", "b3"), strings);
    }

    @Inject(optional=true)
    void setMember (String member) {
        this.member = member;
    }

    String member = null;
    @Test
    public void plainOldTest() {
        //Weird that Guice injects null strings with ""
        assertEquals ("Should be empty string: '" + member + "'", "", member);
    }
}
