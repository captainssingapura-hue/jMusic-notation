package music.notation.guitar;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import music.notation.duration.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON for the request and the arrangement. {@link Duration}s are
 * written as {@code "num/den"} — the same convention as the
 * performance JSON. Unknown request fields are an error, so a typo in a
 * parameter name cannot silently fall back to a default.
 */
public final class ArrangementJson {

    private static final ObjectMapper MAPPER = build();

    private ArrangementJson() {}

    public static ArrangementRequest readRequest(Path file) {
        try {
            return MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), ArrangementRequest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read request " + file + ": " + e.getMessage(), e);
        }
    }

    public static ArrangementRequest requestFromJson(String json) {
        try {
            return MAPPER.readValue(json, ArrangementRequest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("bad request JSON: " + e.getMessage(), e);
        }
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Arrangement arrangementFromJson(String json) {
        try {
            return MAPPER.readValue(json, Arrangement.class);
        } catch (IOException e) {
            throw new UncheckedIOException("bad arrangement JSON: " + e.getMessage(), e);
        }
    }

    public static void write(Path file, Object value) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(file, toJson(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file + ": " + e.getMessage(), e);
        }
    }

    private static ObjectMapper build() {
        ObjectMapper m = new ObjectMapper();
        m.enable(SerializationFeature.INDENT_OUTPUT);
        m.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SimpleModule module = new SimpleModule("notation-guitar");
        module.addSerializer(Duration.class, new JsonSerializer<Duration>() {
            @Override
            public void serialize(Duration value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.numerator() + "/" + value.denominator());
            }
        });
        module.addDeserializer(Duration.class, new StdScalarDeserializer<Duration>(Duration.class) {
            @Override
            public Duration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String s = p.getValueAsString();
                int slash = s == null ? -1 : s.indexOf('/');
                if (slash < 0) throw new IOException("Duration must be 'num/den': got '" + s + "'");
                try {
                    return Duration.of(Long.parseLong(s.substring(0, slash).trim()), Long.parseLong(s.substring(slash + 1).trim()));
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid Duration '" + s + "'", e);
                }
            }
        });
        m.registerModule(module);
        return m;
    }
}
