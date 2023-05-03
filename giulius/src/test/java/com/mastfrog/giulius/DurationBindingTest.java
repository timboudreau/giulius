/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
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
package com.mastfrog.giulius;

import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.time.TimeUtil;
import java.io.IOException;
import java.time.Duration;
import static java.util.EnumSet.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class DurationBindingTest {

    static final Duration A = Duration.ofDays(1);
    static final String a1 = A.toString();
    static final String a2 = TimeUtil.format(A, true);
    static final String a3 = TimeUtil.format(A, false);
    static final String a4 = Long.toString(A.toMillis());

    static final Duration B = Duration.ofMinutes(23).plus(Duration.ofSeconds(32)).plus(Duration.ofMillis(5));
    static final String b1 = B.toString();
    static final String b2 = TimeUtil.format(B, true);
    static final String b3 = TimeUtil.format(B, false);
    static final String b4 = Long.toString(B.toMillis());

    static final Duration Z = Duration.ZERO;
    static final String z1 = Z.toString();
    static final String z2 = TimeUtil.format(Z, true);
    static final String z3 = TimeUtil.format(Z, false);
    static final String z4 = Long.toString(Z.toMillis());

    @Test
    public void testDurationBinding() throws IOException {
        Settings settings = Settings.builder()
                .add("a1", a1)
                .add("a2", a2)
                .add("a3", a3)
                .add("a4", a4)
                .add("b1", b1)
                .add("b2", b2)
                .add("b3", b3)
                .add("b4", b4)
                .add("z1", z1)
                .add("z2", z2)
                .add("z3", z3)
                .add("z4", z4)
                .build();

        Dependencies deps = new Dependencies(settings, allOf(SettingsBindings.class));
        testOne(A, "a", deps, settings);
        testOne(B, "b", deps, settings);
        testOne(Z, "z", deps, settings);
    }

    private void testOne(Duration expect, String name, Dependencies deps, Settings settings) {
        for (int i = 1; i <= 4; i++) {
            String lkp = name + i;
            assertNotNull(lkp, settings.getString(lkp));
            Named anno = Names.named(lkp);
            Key<Duration> k = Key.get(Duration.class, anno);
            Duration d = deps.getInstance(k);
            assertEquals(lkp + "", expect, d);
        }
    }
}
