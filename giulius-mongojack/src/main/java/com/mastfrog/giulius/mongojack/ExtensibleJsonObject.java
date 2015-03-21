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
 * ad-hoc properties.
 *
 * @author Tim Boudreau
 */
public class ExtensibleJsonObject implements Iterable<Map.Entry<String, Object>> {

    @JsonIgnore
    public final Map<String, Object> metadata = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Object> metadata() {
        return metadata;
    }

    @JsonAnySetter
    public void put(String key, Object value) {
        if (propertyNames().contains(key)) {
            throw new IllegalArgumentException("Cannot replace property '" + key
                    + "' with ad-hoc value '" + value + "'");
        }
        metadata.put(key, value);
    }

    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return metadata.entrySet().iterator();
    }

    public <T> T get(String key, Class<T> type) {
        return type.cast(metadata.get(key));
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

    protected Set<String> propertyNames() {
        Set<String> cached = cache.get(getClass());
        if (cached != null) {
            return cached;
        }
        Set<String> result = new HashSet<>();
        for (Constructor c : getClass().getConstructors()) {
            int pc = c.getParameterCount();
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
