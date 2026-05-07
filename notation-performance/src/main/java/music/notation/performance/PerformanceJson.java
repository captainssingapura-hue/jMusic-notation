package music.notation.performance;

import music.notation.expressivity.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bidirectional bridge between {@link Performance} and a JSON text representation.
 * Round-trip parity: {@code fromJson(toJson(p)).equals(p)}.
 *
 * <p>Together with {@link MidiCodec}, gives
 * {@code byte[] (MIDI) ⇄ Performance ⇄ String (JSON)} — the data-layer's two
 * external faces.</p>
 *
 * <p>Built on Jackson; sealed {@link ConcreteNote} is discriminated by a
 * {@code type} field; {@link TrackId} keys serialise to bare strings (and as
 * value, also serialise to a bare string rather than a wrapper object).</p>
 */
public final class PerformanceJson {

    private static final ObjectMapper MAPPER = buildMapper();
    private static final ObjectWriter PRETTY = MAPPER.writerWithDefaultPrettyPrinter();

    private PerformanceJson() {}

    public static String toJson(Performance p) {
        try {
            return PRETTY.writeValueAsString(p);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("toJson failed", e);
        }
    }

    public static Performance fromJson(String json) {
        try {
            return MAPPER.readValue(json, Performance.class);
        } catch (IOException e) {
            throw new IllegalStateException("fromJson failed", e);
        }
    }

    public static byte[] toJsonBytes(Performance p) {
        return toJson(p).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Pretty-print any value through the same configured mapper — exposed for
     * consumers that want to split a {@link Performance} across multiple files
     * (per-track / per-tempo) while keeping {@link TrackId} formatting and the
     * sealed-{@link ConcreteNote} discriminator consistent.
     */
    public static String toJsonAny(Object value) {
        try {
            return PRETTY.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("toJsonAny failed", e);
        }
    }

    /**
     * Inverse of {@link #toJsonAny(Object)} — parse any value through the same
     * configured mapper. Useful for split-file readers that load
     * {@link Track}, {@link TempoTrack}, or custom meta records one at a time.
     */
    public static <T> T fromJsonAny(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "fromJsonAny failed for " + type.getSimpleName(), e);
        }
    }

    public static Performance fromJsonBytes(byte[] bytes) {
        return fromJson(new String(bytes, StandardCharsets.UTF_8));
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper();
        m.enable(SerializationFeature.INDENT_OUTPUT);

        SimpleModule module = new SimpleModule("notation-performance");

        // TrackId as VALUE: serialise as bare string "name", deserialise from bare string.
        module.addSerializer(TrackId.class, new JsonSerializer<TrackId>() {
            @Override
            public void serialize(TrackId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.name());
            }
        });
        module.addDeserializer(TrackId.class, new StdScalarDeserializer<TrackId>(TrackId.class) {
            @Override
            public TrackId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return new TrackId(p.getValueAsString());
            }
        });

        // TrackId as KEY: emit name as field name, parse field name back to TrackId.
        module.addKeySerializer(TrackId.class, new JsonSerializer<TrackId>() {
            @Override
            public void serialize(TrackId value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeFieldName(value.name());
            }
        });
        module.addKeyDeserializer(TrackId.class, new KeyDeserializer() {
            @Override
            public Object deserializeKey(String key, DeserializationContext ctxt) {
                return new TrackId(key);
            }
        });

        m.registerModule(module);
        return m;
    }
}
