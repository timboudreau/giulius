/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
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
package com.mastfrog.giulius.mongodb.reactive;

import com.mongodb.ReadPreference;

/**
 *
 * @author Tim Boudreau
 */
enum ReadPreferences {

    PRIMARY,
    PRIMARY_PREFERRED,
    SECONDARY,
    SECONDARY_PREFERRED,
    NEAREST;
    
    public static ReadPreferences find(String name) {
        if (name == null) {
            return NEAREST;
        }
        for (ReadPreferences p : values()) {
            if (p.name().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    public ReadPreference get() {
        switch (this) {
            case NEAREST:
                return ReadPreference.nearest();
            case PRIMARY:
                return ReadPreference.primary();
            case PRIMARY_PREFERRED:
                return ReadPreference.primaryPreferred();
            case SECONDARY:
                return ReadPreference.secondary();
            case SECONDARY_PREFERRED:
                return ReadPreference.secondaryPreferred();
            default:
                throw new AssertionError(this);
        }
    }
}
