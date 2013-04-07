package abcdefg;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.mastfrog.guicy.annotations.Defaults;
import com.mastfrog.guicy.annotations.Namespace;
import java.util.Objects;

/**
 *
 * @author tim
 */
@Namespace("abcdefg")
@Defaults(value={"queeze=wucky"}, namespace=@Namespace("abcdefg"))
public class NamespacedThing {
    private final String stuff;
    
    @Inject
    public NamespacedThing(@Named("queeze") String stuff) {
        assert stuff != null;
        this.stuff = stuff;
    }
    
    @Override
    public String toString() {
        return stuff;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(this.stuff);
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
        final NamespacedThing other = (NamespacedThing) obj;
        if (!Objects.equals(this.stuff, other.stuff)) {
            return false;
        }
        return true;
    }
}
