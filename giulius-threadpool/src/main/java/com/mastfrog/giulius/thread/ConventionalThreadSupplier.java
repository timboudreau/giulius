/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
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
package com.mastfrog.giulius.thread;

import com.mastfrog.settings.Settings;

/**
 * Used to allow users of ThreadModule to customize thread creation for
 * conventional (non fork-join) thread pools - for example, using Netty's
 * FastThreadLocalThread instead of the default.
 *
 * @author Tim Boudreau
 */
public interface ConventionalThreadSupplier {

    /**
     * Create a new thread.
     *
     * @param group The thread group
     * @param run The runnable it should invoke
     * @param settings The settings, which can be used to derive customizations
     * @param stackSize The stack size - if &lt;= 0, use the JVM's default
     * @param bindingName The name of this binding (used to look up
     * bindingName.stackSize in settings)
     * @param threadName The name to give to the thread
     * @return
     */
    Thread newThread(ThreadGroup group, Runnable run, Settings settings, int stackSize, String bindingName, String threadName);

    default int findStackSize(Settings settings, int passedValue, String bindingName) {
        return settings.getInt(bindingName + ".stackSize", passedValue);
    }

    ConventionalThreadSupplier DEFAULT = new ConventionalThreadSupplier() {
        public Thread newThread(ThreadGroup group, Runnable run, Settings settings, int stackSize, String bindingName, String threadName) {
            int stack = this.findStackSize(settings, stackSize, bindingName);
            if (stack <= 0) {
                return new Thread(group, run, threadName);
            } else {
                return new Thread(group, run, threadName, stack);
            }
        }
    };
}
