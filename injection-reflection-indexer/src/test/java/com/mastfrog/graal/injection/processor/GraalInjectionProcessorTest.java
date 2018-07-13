/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.graal.injection.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import static com.mastfrog.util.collections.CollectionUtils.map;
import com.mastfrog.util.collections.StringObjectMap;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class GraalInjectionProcessorTest {

    String expectJSON = "[{\"name\":\"com.mastfrog.graal.injection.processor.InjectableOne\",\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[\"java.lang.String\"]}],\"fields\":[{\"name\":\"foo\"}]},{\"name\":\"com.mastfrog.graal.injection.processor.InjectableTwo\",\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[\"java.util.Properties\",\"com.mastfrog.graal.injection.processor.InjectableOne\"]}]},{\"name\":\"com.mastfrog.graal.injection.processor.SomeBean\",\"methods\":[{\"name\":\"<init>\",\"parameterTypes\":[\"java.lang.String\",\"int\"]}],\"fields\":[{\"name\":\"age\"},{\"name\":\"foo\"}]},{\"name\":\"java.lang.String\",\"fields\":[{\"name\":\"CASE_INSENSITIVE_ORDER\"},{\"name\":\"COMPACT_STRINGS\"},{\"name\":\"LATIN1\"},{\"name\":\"UTF16\"},{\"name\":\"coder\"},{\"name\":\"hash\"},{\"name\":\"serialPersistentFields\"},{\"name\":\"serialVersionUID\"},{\"name\":\"value\"}]}]";

    @Test
    @SuppressWarnings("unchecked")
    public void testGeneratedReflectionIndex() throws IOException {
        StringObjectMap[] expect = new ObjectMapper().readValue(expectJSON, StringObjectMap[].class);
        for (StringObjectMap m : expect) {
            if (String.class.getName().equals(m.get("name"))) {
                List<Map<String,Object>> flds = (List<Map<String,Object>>) m.get("fields");
                flds.clear();
                List<Field> fieldList = Arrays.asList(String.class.getDeclaredFields());
                Collections.sort(fieldList, (a, b) -> {
                    return a.getName().compareTo(b.getName());
                });
                for (Field f : fieldList) {
                    flds.add(map("name").finallyTo(f.getName()));
                }
            }
        }
        InputStream in = GraalInjectionProcessorTest.class.getResourceAsStream("/META-INF/injection/reflective.json");
        assertNotNull("/META-INF/injection/reflective.json was not generated", in);
        StringObjectMap[] got = new ObjectMapper().readValue(in, StringObjectMap[].class);
//        assertEquals(setOf(expect), setOf(got));
    }

}
