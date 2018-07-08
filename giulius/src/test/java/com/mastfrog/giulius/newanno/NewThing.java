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

package com.mastfrog.giulius.newanno;

import com.google.inject.Inject;
import com.mastfrog.giulius.annotations.Namespace;
import com.mastfrog.giulius.annotations.SettingsDefaults;
import com.mastfrog.giulius.annotations.SettingsDefaults.KV;
import com.mastfrog.giulius.annotations.Value;
import com.mastfrog.settings.Settings;

/**
 *
 * @author Tim Boudreau
 */
@SettingsDefaults(namespace="noobie", value={
    @KV(name="whupty", value="woo"),
    @KV(name="snacks", value="23"),
    @KV(name="glorst", value="wagglum's large\tantelope")
})
@Namespace("noobie")
public class NewThing {

    public final String whupty;

    public final String glorst;

    @Inject
    public NewThing(@Value(value="whupty", namespace=@Namespace("noobie")) String whupty, Settings settings) {
        this.whupty = whupty;
        this.glorst = settings.getString("glorst");
    }
}
