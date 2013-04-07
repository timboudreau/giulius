package abcdefg;

import com.google.inject.Inject;
import java.util.Objects;

/**
 *
 * @author tim
 */
public class ThingThatNeedsThing {
    private final NamespacedThing thing;
    
    @Inject
    public ThingThatNeedsThing(NamespacedThing thing) {
        this.thing = thing;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + Objects.hashCode(this.thing);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ThingThatNeedsThing other = (ThingThatNeedsThing) obj;
        if (!Objects.equals(this.thing, other.thing)) {
            return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
        return thing.toString();
    }
}
