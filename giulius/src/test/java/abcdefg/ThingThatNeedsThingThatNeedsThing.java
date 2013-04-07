package abcdefg;

import com.google.inject.Inject;
import java.util.Objects;

/**
 *
 * @author tim
 */
public class ThingThatNeedsThingThatNeedsThing {

    private final ThingThatNeedsThing thing;

    @Inject
    public ThingThatNeedsThingThatNeedsThing(ThingThatNeedsThing thing) {
        this.thing = thing;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Objects.hashCode(this.thing);
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
        final ThingThatNeedsThingThatNeedsThing other = (ThingThatNeedsThingThatNeedsThing) obj;
        if (!Objects.equals(this.thing, other.thing)) {
            return false;
        }
        return true;
    }

    public String toString() {
        return thing.toString();
    }
}
