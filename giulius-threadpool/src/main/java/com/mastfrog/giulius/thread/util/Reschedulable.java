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
package com.mastfrog.giulius.thread.util;

import java.time.Duration;
import java.util.concurrent.Delayed;

/**
 * A wrapper for a job - a runnable or callable - which is submitted to
 * a background thread pool to run after some delay, and can be repeatedly
 * resubmitted.  Various policy options are available for how rescheduling
 * happens in the presence of multiple calls to touch() are available from the
 * various factory methods on {@link Resettables}.
 * <p>
 * A Reschedulable is <i>not</i> scheduled just because it was created - you
 * must call <code>touch()</code> on it to enqueue it;  subsequent calls to
 * touch() may further delay it or not depending on policy;  a maximum delay
 * for running can be set, based either on the time of the first call to touch()
 * since a run or post-creation, or the time of the preceding run (if any).
 *
 * @author Tim Boudreau
 */
public interface Reschedulable extends Delayed {

    /**
     * (Possibly) update the delay before this Reschedulable is run, and
     * enqueue it to run if it is not already.
     * <p>
     * What this method does depends on the policy the Reschedulable was created
     * with - it may push execution further into the future, or make no change
     * if the job is already enqueued.
     * <p>
     * If this Reschedulable was created with a maximum elapsed interval,
     * then it will run no further in the future than the difference between
     * the time of the last run (or creation) and now.
     */
    void touch();

    /**
     * (Possibly) update the delay before this Reschedulable is run, and
     * enqueue it to run if it is not already.  The passed delay parameter
     * changes the timing of the <i>next run only</i>, after which calls to
     * the no-argument touch() will use the original delay.  Subsequent calls
     * to thhe no-argument touch() method will not change the delay of the next
     * run from the one set here;  a subsequent call to this method will.
     * <p>
     * What this method does depends on the policy the Reschedulable was created
     * with - it may push execution further into the future, or make no change
     * if the job is already enqueued.
     * <p>
     * If this Reschedulable was created with a maximum elapsed interval,
     * then it will run no further in the future than the difference between
     * the time of the last run (or creation) and now.
     */
    void touch(Duration temporaryDelay);

    /**
     * Cancel <i>any current pending run</i> of this Reschedulable.  This
     * does not prevent future calls to touch() from causing it to be scheduled
     * again.
     */
    void cancel();

}
