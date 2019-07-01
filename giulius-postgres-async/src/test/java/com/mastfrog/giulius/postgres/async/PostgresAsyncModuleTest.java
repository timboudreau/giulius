/*
 * The MIT License
 *
 * Copyright 2019 Mastfrog Technologies.
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
package com.mastfrog.giulius.postgres.async;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.giulius.Dependencies;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_MAX_POOL_SIZE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE;
import static com.mastfrog.giulius.postgres.async.PostgresAsyncModule.SETTINGS_KEY_PG_URI;
import com.mastfrog.settings.Settings;
import static com.mastfrog.util.collections.CollectionUtils.setOf;
import static com.mastfrog.util.preconditions.Checks.notNull;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.streams.Streams;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.RowSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Tim Boudreau
 */
public class PostgresAsyncModuleTest {

    @Test
    public void testSomeMethod() throws InterruptedException {
        PgPool p = deps.getInstance(PgPool.class);
        CountDownLatch latch = new CountDownLatch(1);
        Set<String> names = new HashSet<>();
        ErrorContext ctx = ctx();
        ctx.handle(p::getConnection, conn -> {
            ctx.handle("select * from things", conn::preparedQuery, (RowSet rowSet) -> {
                rowSet.forEach(row -> {
                    String name = row.getString("name");
                    assertNotNull(name);
                    names.add(name);
                    if (names.size() == 2) {
                        latch.countDown();
                    }
                });
            });
        });
        latch.await(10, TimeUnit.SECONDS);
        assertEquals(2, names.size(), names::toString);
        assertEquals(setOf("skiddoo", "meaning"), names);
    }

    private PostgresHarness harn;
    private ThrowingRunnable onShutdown;
    private String pgUri;
    private Dependencies deps;
    private EH eh;

    ErrorContext ctx() {
        return eh.context((r, t) -> {
//            Exceptions.chuck(t);
            return false;
        });
    }

    @AfterEach
    public void shutdown() throws Exception {
        onShutdown.run();
        eh.rethrow();
    }

    @BeforeEach
    public void setup() throws Throwable {
        eh = new EH();
        onShutdown = ThrowingRunnable.composable(true);
        harn = new PostgresHarness();
        harn.start();
//        harn.waitForLoggedWords("database system is ready to accept connections");
        pgUri = harn.initDatabase("mw", loadInitSql());
        Settings settings = Settings.builder()
                .add(SETTINGS_KEY_PG_URI, pgUri)
                .add(SETTINGS_KEY_MAX_POOL_SIZE, 20)
                .add(SETTINGS_KEY_MAX_WAIT_QUEUE_SIZE, 50)
                .build();
        deps = new Dependencies(settings, new PostgresAsyncModule());
        onShutdown.andAlways(deps::shutdown);
        onShutdown.andAlways(() -> harn.shutdown(false));
    }

    static String loadInitSql() throws IOException {
        return notNull("init.sql", Streams.readResourceAsUTF8(PostgresAsyncModuleTest.class, "init.sql"));
    }

    static final class EH implements PgErrorHandler {

        Throwable thrown;

        void rethrow() {
            if (thrown != null) {
                Exceptions.chuck(thrown);
            }
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (thrown == null) {
                thrown = e;
            } else {
                thrown.addSuppressed(e);
            }
        }
    }
}
