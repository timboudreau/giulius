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
package com.mastfrog.giulius.thread.util;

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.thread.ExecutorServiceBuilder;
import com.mastfrog.giulius.thread.ThreadModule;
import com.mastfrog.settings.Settings;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ExecutorService;

/**
 * Manually test that the reflection code that only works on jdks greater than
 * our target works correctliy, since that cannot be done in a JDK 8 build.
 *
 * @author Tim Boudreau
 */
public class X {

    public static void main(String[] args) throws IOException {

        System.out.println("JVER " + System.getProperty("java.version"));

        UncaughtExceptionHandler ueh = (Thread t, Throwable e) -> {
            System.out.println("UNCAUGHT ON " + t);
            e.printStackTrace();
        };

        ThreadModule mod = new ThreadModule();
        mod.builder("fj").forkJoin()
                .withDefaultThreadCount(10)
                .shutdownCoordination(ExecutorServiceBuilder.ShutdownBatch.LATE)
                .withDefaultThreadCount(5).bind();
        mod.builder("ws")
                .workStealing()
                .withDefaultThreadCount(13)
                .shutdownCoordination(ExecutorServiceBuilder.ShutdownBatch.DEFAULT)
                .bind();

        Settings settings = Settings
                .builder("blah")
                .add("shutdownHookExecutorWait", 10)
                .add("fj.corePoolSize", 3)
                .parseCommandLineArguments(args)
                .logging()
                .build();

        Dependencies deps = new Dependencies(settings, mod, binder -> binder.bind(UncaughtExceptionHandler.class).toInstance(ueh));

        Key<ExecutorService> key = Key.get(ExecutorService.class, Names.named("fj"));
        Key<ExecutorService> k2 = Key.get(ExecutorService.class, Names.named("ws"));

        ExecutorService a = deps.getInstance(key);
        ExecutorService b = deps.getInstance(k2);

        System.out.println("A: " + a);
        System.out.println("B: " + b);
    }
}
