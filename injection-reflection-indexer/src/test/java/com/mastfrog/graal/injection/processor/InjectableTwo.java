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

import com.google.inject.Inject;
import java.util.Properties;
import com.mastfrog.graal.annotation.Expose;
import com.mastfrog.graal.annotation.ExposeAllMethods;
import com.mastfrog.graal.annotation.ExposeMany;

/**
 *
 * @author Tim Boudreau
 */
@Expose(type = "java.lang.Integer", methods = {@Expose.MethodInfo(name="parseInt", parameterTypes = {"java.lang.String"})},
        fields={"hash"}
)
@ExposeAllMethods({Integer.class, Byte.class})
@ExposeMany({@Expose(type="java.lang.String", fields="*")})
public class InjectableTwo {

    @Inject
    public InjectableTwo(Properties props, InjectableOne one) {
    }
}
