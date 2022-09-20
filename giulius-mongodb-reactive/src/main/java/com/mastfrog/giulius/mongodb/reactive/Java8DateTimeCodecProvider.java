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

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntMap;
import com.mastfrog.util.time.TimeUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import org.bson.BsonReader;
import org.bson.BsonTimestamp;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class Java8DateTimeCodecProvider implements CodecProvider {

    private static final ZonedDateTimeCodec ZONED_DATE_TIME = new ZonedDateTimeCodec();
    private static final OffsetDateTimeCodec OFFSET_DATE_TIME = new OffsetDateTimeCodec();
    private static final LocalDateTimeCodec LOCAL_DATE_TIME = new LocalDateTimeCodec();
    private static final InstantCodec INSTANT = new InstantCodec();
    private static final DurationCodec DURATION = new DurationCodec();

    private final List<CodecKey<?>> l = new ArrayList<>(5);
    private final IntMap<CodecKey<?>> imp = CollectionUtils.intMap(6);

    public Java8DateTimeCodecProvider() {
        l.add(new CodecKey<>(ZonedDateTime.class, ZONED_DATE_TIME));
        l.add(new CodecKey<>(LocalDateTime.class, LOCAL_DATE_TIME));
        l.add(new CodecKey<>(OffsetDateTime.class, OFFSET_DATE_TIME));
        l.add(new CodecKey<>(Instant.class, INSTANT));
        l.add(new CodecKey<>(Duration.class, DURATION));
        for (CodecKey<?> k : l) {
            imp.put(k.type.hashCode(), k);
        }
    }

    public <T> Codec<T> createCodec(Class<T> type, CodecConfigurationException ex) {
        CodecKey<T> result = match(type);
        return result == null ? null : result.codec;
    }

    public static void installCodecs(MongoAsyncConfig<?> config) {
        config.withCodec(ZONED_DATE_TIME);
        config.withCodec(OFFSET_DATE_TIME);
        config.withCodec(LOCAL_DATE_TIME);
        config.withCodec(INSTANT);
        config.withCodec(DURATION);
    }

    private <J> CodecKey<J> match(Class<?> type) {
        CodecKey<?> kk = imp.get(type.hashCode());
        CodecKey<J> kk1 = kk == null ? null : kk.match(type);
        if (kk1 != null) {
            return kk1;
        }
        for (CodecKey<?> k : l) {
            CodecKey<J> matched = k.match(type);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    static final class CodecKey<T> {

        private final Class<T> type;
        private final Codec<T> codec;

        public CodecKey(Class<T> type, Codec<T> codec) {
            this.type = type;
            this.codec = codec;
        }

        @SuppressWarnings("unchecked")
        public <J> CodecKey<J> match(Class<?> clazz) {
            if (clazz == type || clazz.isAssignableFrom(type)) {
                return (CodecKey<J>) this;
            }
            return null;
        }

        public String toString() {
            return type.getSimpleName();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Codec<T> get(Class<T> type, CodecRegistry cr) {
        CodecKey<T> key = match(type);
        return key == null ? null : key.codec;
    }

    static final class ZonedDateTimeCodec implements Codec<ZonedDateTime> {

        @Override
        public void encode(BsonWriter writer, ZonedDateTime t, EncoderContext ec) {
            writer.writeDateTime(TimeUtil.toUnixTimestamp(t));
        }

        @Override
        public Class<ZonedDateTime> getEncoderClass() {
            return ZonedDateTime.class;
        }

        @Override
        public ZonedDateTime decode(BsonReader reader, DecoderContext dc) {
            checkNull(reader);
            switch (reader.getCurrentBsonType()) {
                case TIMESTAMP:
                    BsonTimestamp ts = reader.readTimestamp();
                    return TimeUtil.fromUnixTimestamp(ts.getValue() * 1000);
                case DATE_TIME:
                case INT64:
                    return TimeUtil.fromUnixTimestamp(reader.readDateTime());
                case STRING:
                    String s = reader.readString();
                    return TimeUtil.fromIsoFormat(s);
            }
            return null;
        }
    }

    static final class LocalDateTimeCodec implements Codec<LocalDateTime> {

        @Override
        public void encode(BsonWriter writer, LocalDateTime t, EncoderContext ec) {
            writer.writeDateTime(TimeUtil.toUnixTimestampSystemDefault(t));
        }

        @Override
        public Class<LocalDateTime> getEncoderClass() {
            return LocalDateTime.class;
        }

        @Override
        public LocalDateTime decode(BsonReader reader, DecoderContext dc) {
            checkNull(reader);
            switch (reader.getCurrentBsonType()) {
                case TIMESTAMP:
                    BsonTimestamp ts = reader.readTimestamp();
                    return TimeUtil.localFromUnixTimestamp(ts.getValue() * 1000);
                case DATE_TIME:
                case INT64:
                    return TimeUtil.localFromUnixTimestamp(reader.readDateTime());
                case STRING:
                    return TimeUtil.localFromIsoFormat(reader.readString());
            }
            return null;
        }
    }

    static final class OffsetDateTimeCodec implements Codec<OffsetDateTime> {

        @Override
        public void encode(BsonWriter writer, OffsetDateTime t, EncoderContext ec) {
            writer.writeDateTime(TimeUtil.toUnixTimestamp(t));
        }

        @Override
        public Class<OffsetDateTime> getEncoderClass() {
            return OffsetDateTime.class;
        }

        @Override
        public OffsetDateTime decode(BsonReader reader, DecoderContext dc) {
            checkNull(reader);
            switch (reader.getCurrentBsonType()) {
                case TIMESTAMP:
                    BsonTimestamp ts = reader.readTimestamp();
                    return TimeUtil.offsetFromUnixTimestamp(ts.getValue() * 1000);
                case DATE_TIME:
                case INT64:
                    return TimeUtil.offsetFromUnixTimestamp(reader.readDateTime());
                case STRING:
                    return TimeUtil.offsetFromIsoFormat(reader.readString());
            }
            return null;
        }
    }

    static final class InstantCodec implements Codec<Instant> {

        @Override
        public void encode(BsonWriter writer, Instant t, EncoderContext ec) {
            writer.writeDateTime(t.toEpochMilli());
        }

        @Override
        public Class<Instant> getEncoderClass() {
            return Instant.class;
        }

        @Override
        public Instant decode(BsonReader reader, DecoderContext dc) {
            checkNull(reader);
            switch (reader.getCurrentBsonType()) {
                case TIMESTAMP:
                    BsonTimestamp ts = reader.readTimestamp();
                    return Instant.ofEpochMilli(ts.getValue() * 1000);
                case DATE_TIME:
                case INT64:
                    return Instant.ofEpochMilli(reader.readInt64());
                case STRING:
                    return TimeUtil.instantFromIsoFormat(reader.readString());
            }
            return Instant.ofEpochMilli(reader.readDateTime());
        }
    }

    static void checkNull(BsonReader reader) {
        if (null == reader.getCurrentBsonType()) {
            throw new IllegalArgumentException("Can't read a duration from " + reader.getCurrentBsonType()
                    + " for " + reader.getCurrentName());
        }
    }

    static final class DurationCodec implements Codec<Duration> {

        @Override
        public void encode(BsonWriter writer, Duration t, EncoderContext ec) {
            writer.writeInt64(TimeUtil.millis(t));
        }

        @Override
        public Class<Duration> getEncoderClass() {
            return Duration.class;
        }

        @Override
        public Duration decode(BsonReader reader, DecoderContext dc) {
            checkNull(reader);
            switch (reader.getCurrentBsonType()) {
                case INT32:
                    return TimeUtil.millis(reader.readInt32());
                case INT64:
                    return TimeUtil.millis(reader.readInt64());
                case STRING:
                    return TimeUtil.parse(reader.readString());
                default:
                    throw new IllegalArgumentException("Can't read a duration from " + reader.getCurrentBsonType()
                            + " for " + reader.getCurrentName());
            }
        }
    }
}
