/*
 * The MIT License
 *
 * Copyright 2021 Mastfrog Technologies.
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
package com.mastfrog.jarmerge;

import java.text.MessageFormat;

/**
 *
 * @author Tim Boudreau
 */
public interface MergeLog {

    default MergeLog log(String template, Object... components) {
        return log(MessageFormat.format(template, components));
    }

    default MergeLog debug(String template, Object... components) {
        return debug(MessageFormat.format(template, components));
    }

    default MergeLog warn(String template, Object... components) {
        return warn(MessageFormat.format(template, components));
    }

    default MergeLog error(String template, Object... components) {
        return error(MessageFormat.format(template, components));
    }

    MergeLog log(String msg);

    MergeLog debug(String msg);

    MergeLog warn(String msg);

    MergeLog error(String msg);

    static MergeLog stdout(String name, Phase phase, JarMerge settings) {
        return new StdoutMergeLog(name, phase, settings.verbose);
    }
}
