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
package com.mastfrog.giulius.tests;

import com.mastfrog.util.Streams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.SocketFactory;

/**
 * Can check Network availability.  Caches the result in a tmp file tied
 * to the host checked, and has a timeout, so actual network connections
 * are only performed if the last known result is older than a timeout
 * (default 1 minute, 30 seconds).
 *
 * @author Tim Boudreau
 */
public class NetworkCheck {

    private final String host;
    private static final long TWO_MINUTES = 60 * 1000 * 2;//Duration TWO_MINUTES = Duration.minutes(1).add(Duration.seconds(30));
    private File networkTestResult;
    private AtomicReference<Boolean> lastResult = new AtomicReference<Boolean>();
    private static final long TIMEOUT = 30000; //Duration TIMEOUT = Duration.THIRTY_SECONDS;
    private final boolean useHttp;

    NetworkCheck(String host, boolean useHttp) {
        this.host = host;
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        assert tmp.exists() && tmp.isDirectory();
        networkTestResult = new File(tmp, host + "." + getClass().getSimpleName());
        this.useHttp = useHttp;
    }

    NetworkCheck() {
        this("google.com", true);
    }

    public synchronized void reset() {
        lastResult.set(null);
        if (networkTestResult.exists()) {
            for (int i = 0; i < 10 && !networkTestResult.delete() && networkTestResult.exists(); i++) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    public synchronized boolean isNetworkAvailable() {
        boolean result;
        try {
            if (shouldRecheck()) {
                result = performNetworkCheckAndSaveResult();
                lastResult.set(result);
                return result;
            } else {
                Boolean b = lastResult.get();
                if (b != null) {
                    result = b.booleanValue();
                } else {
                    result = readFromFile();
                    lastResult.set(result);
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            result = false;
        }
        return result;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("NetworkCheck {");
        sb.append(host);
        sb.append(" lastResult=").append(lastResult);
        sb.append(" shouldRecheck? ").append(shouldRecheck());
        if (networkTestResult.exists()) {
            InputStream in = null;
            try {
                in = new FileInputStream(networkTestResult);
                String s = Streams.readString(in);
                sb.append(" FileContents=").append(s);
            } catch (IOException e) {
                Logger.getLogger(NetworkCheck.class.getName()).log(Level.FINEST, null, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ex) {
                        Logger.getLogger(NetworkCheck.class.getName()).log(Level.FINEST, null, ex);
                    }
                }
            }
        }
        return sb.toString();
    }

    private boolean readFromFile() throws IOException {
        boolean result;
        if (!networkTestResult.exists()) {
            return performNetworkCheckAndSaveResult();
        } else {
            FileInputStream in = new FileInputStream(networkTestResult);
            try {
                int val = in.read();
                result = val == (byte) 'o';
            } finally {
                in.close();
            }
        }
        return result;
    }

    private void writeToFile(boolean value) throws IOException {
        if (!networkTestResult.exists()) {
            for (int i = 0; i < 10 && !networkTestResult.createNewFile(); i++) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
        FileOutputStream out = new FileOutputStream(networkTestResult);
        try {
            out.write(value ? "ok".getBytes("US-ASCII") : "fail".getBytes("US-ASCII"));
        } finally {
            out.flush();
            out.close();
        }
    }

    private boolean performNetworkCheckAndSaveResult() throws IOException {
        boolean result;
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (!useHttp) {
                result = addr.isReachable((int) TIMEOUT);
            } else {
                Socket socket = SocketFactory.getDefault().createSocket(addr, 80);
                OutputStream out = socket.getOutputStream();
                out.close();
                result = true;
            }
        } catch (IOException ioe) {
            Logger.getLogger(NetworkCheck.class.getName()).log(Level.FINE, null, ioe);
            result = false;
        }
        writeToFile(result);
        return result;
    }

    private boolean shouldRecheck() {
        long lastActualNetworkCheck;
        if (networkTestResult.exists()) {
            lastActualNetworkCheck = networkTestResult.lastModified();
        } else {
            lastActualNetworkCheck = -1;
        }
//        boolean needRecheck = TWO_MINUTES.ago().isBefore(lastActualNetworkCheck);
        boolean needRecheck = System.currentTimeMillis() - TWO_MINUTES < lastActualNetworkCheck;
        if (needRecheck) {
            lastResult.set(null);
        }
        return needRecheck;
    }
}
