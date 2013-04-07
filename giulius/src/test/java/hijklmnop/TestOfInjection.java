package hijklmnop;

import abcdefg.ThingThatNeedsThingThatNeedsThing;
import com.mastfrog.giulius.Dependencies;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author tim
 */
public class TestOfInjection {

    @Test
    public void test() throws IOException {
        Dependencies deps = Dependencies.builder().addDefaultSettings().build();

        System.out.println("\n\n\n*************************************************\n\n\n");
        
        
        ThingThatNeedsThingThatNeedsThing tt = deps.getInstance(ThingThatNeedsThingThatNeedsThing.class);
        assertEquals("wucky", tt.toString());

    }
}
