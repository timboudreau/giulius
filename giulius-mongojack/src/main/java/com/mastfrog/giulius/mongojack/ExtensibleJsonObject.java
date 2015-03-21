package com.mastfrog.giulius.mongojack;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Base class for objects that have some typed fields but can also support
 * ad-hoc properties it does not define.  This is used in order to write
 * POJO classes which can be serialized/deserialized either from the web or from
 * MongoDB which are allowed to contain properties not explicitly defined, and
 * have a way of retaining them.
 * <p/>
 * Implementations should use final fields and a constructor annotated with
 * &#064;JsonCreator whose arguments use &#064;JsonProperty to identify them - 
 * these annotations are also used to ensure the catch-all setter cannot be
 * used to override property names or cause duplicate keys in the resulting JSON.
 *
 * @author Tim Boudreau
 */
public abstract class ExtensibleJsonObject implements Iterable<Map.Entry<String, Object>> {

    @JsonIgnore
    private final Map<String, Object> metadata = new HashMap<>();

    /**
     * Gets properties that are not explicitly defined but which are present.
     * @return The additional properties
     */
    @JsonAnyGetter
    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Setter for Jackson to use with ad-hoc properties.
     * @param key The property name
     * @param value The vaue
     */
    @JsonAnySetter
    public void put(String key, Object value) {
        if (propertyNames().contains(key)) {
            throw new IllegalArgumentException("Cannot replace property '" + key
                    + "' with ad-hoc value '" + value + "'");
        }
        metadata.put(key, value);
    }

    /**
     * Iterate non-standard key/value pairs.
     * @return An iterator
     */
    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return metadata.entrySet().iterator();
    }

    /**
     * Get a property not defined on the child class but which was
     * present at deserialization.
     * @param <T> The type
     * @param key The name of the property
     * @param type The type
     * @return A property or null
     */
    public <T> T get(String key, Class<T> type) {
        return metadata.containsKey(key) ? type.cast(metadata.get(key)) : null;
    }

    @Override
    public int hashCode() {
        return 7 * metadata.hashCode() + getClass().getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null) {
            return false;
        }
        if (o.getClass() == getClass()) {
            ExtensibleJsonObject obj = (ExtensibleJsonObject) o;
            return obj.metadata.equals(metadata);
        }
        return false;
    }

    /**
     * Return a set of those property names which *are* defined on this
     * class, and therefore should not be used with the catch-all setter.
     * The default implementation looks at the constructor arguments for
     * &#064;JsonProperty annotations returns the set of all such property
     * names.
     * 
     * @return The set of property names this subclass defines.
     */
    protected Set<String> propertyNames() {
        Set<String> cached = cache.get(getClass());
        if (cached != null) {
            return cached;
        }
        Set<String> result = new HashSet<>();
        for (Constructor c : getClass().getConstructors()) {
//            int pc = c.getParameterCount(); // JDK 8
            int pc = c.getParameters().length;
            Annotation[][] annos = c.getParameterAnnotations();
            for (int i = 0; i < pc; i++) {
                Annotation[] curr = annos[i];
                for (Annotation a : curr) {
                    if (a instanceof JsonProperty) {
                        JsonProperty p = (JsonProperty) a;
                        result.add(p.value());
                    }
                }
            }
        }
        cache.put(getClass(), result);
        return result;
    }

    private static final Map<Class<?>, Set<String>> cache = new HashMap<>();
}
