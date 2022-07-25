/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
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
package com.mastfrog.jarmerge.spi;

import com.mastfrog.jarmerge.JarMerge;
import java.util.Collection;
import java.util.function.Function;

/**
 * Allows a Jar filter which changes class names to share the transforms it
 * performs with other things which write lists of class names.
 *
 * @author Tim Boudreau
 */
public interface ClassNameRewriter extends Function<String, String> {

    static ClassNameRewriter NONE = in -> in;

    static ClassNameRewriter coalesce(Collection<? extends JarFilter<?>> filters) {
        ClassNameRewriter result = null;
        for (JarFilter<?> jf : filters) {
            ClassNameRewriter cnr = jf.as(ClassNameRewriter.class);
            if (cnr != null) {
                if (result == null) {
                    result = cnr;
                } else {
                    result = result.andThen(cnr);
                }
            }
        }
        if (result == null) {
            return NONE;
        }
        return result;
    }

    /**
     * Gets the instance from the thread-local JarMerge, if any, and if not,
     * returns NONE.
     *
     * @return A rewriter, never null
     */
    static ClassNameRewriter get() {
        JarMerge g = JarMerge.get();
        if (g != null) {
            return g.rewriter();
        }
        return NONE;
    }

    default ClassNameRewriter andThen(ClassNameRewriter next) {
        return in -> {
            String res = ClassNameRewriter.this.apply(in);
            return next.apply(res);
        };
    }
}
