/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.settings;

import com.mastfrog.util.Checks;
import java.util.Timer;
import java.util.TimerTask;

/**
 * System-wide refresh intervals
 *
 * @author Tim Boudreau
 */
public enum SettingsRefreshInterval implements RefreshInterval {
    CLASSPATH(60 * 60 * 1000),
    SYSTEM_PROPERTIES(10 * 60 * 1000),
    FILES(10 * 60 * 1000),
    URLS(5 * 60 * 1000);
    private volatile int interval;

    SettingsRefreshInterval(int initialValue) {
        interval = initialValue;
    }

    SettingsRefreshInterval() {
        this(0);
    }

    @Override
    public int getMilliseconds() {
        timer.purge();
        return interval;
    }

    @Override
    public synchronized void add(TimerTask task) {
        long interval = getMilliseconds();
        timer.scheduleAtFixedRate(task, interval, interval);
        timer.purge();
    }

    @Override
    public void setMilliseconds(int millis) {
        Checks.nonNegative("millis", millis);
        if (interval != millis) {
            interval = millis;
            modCount++;
        }
    }

    private static volatile int modCount;

    static final Timer timer = new Timer("Settings refresh", true);
}
