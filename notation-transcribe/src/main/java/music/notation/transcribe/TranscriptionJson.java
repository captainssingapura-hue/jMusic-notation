package music.notation.transcribe;

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
 * JSON for requests and results. {@link Duration}s are {@code "num/den"}.
 * Unknown request fields are an error, so a misspelt parameter cannot
 * silently fall back to its default.
 */
public final class TranscriptionJson {

    private static final ObjectMapper MAPPER = build();

    private TranscriptionJson() {}

    public static TranscriptionRequest readRequest(Path file) {
        try {
            return MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), TranscriptionRequest.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read request " + file + ": " + e.getMessage(), e);
        }
    }

    public static TranscriptionRequest requestFromJson(String json) {
        try {
            return MAPPER.readValue(json, TranscriptionRequest.class);
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

    public static void write(Path file, Object value) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, toJson(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file + ": " + e.getMessage(), e);
        }
    }

    private static ObjectMapper build() {
        ObjectMapper m = new ObjectMapper();
        m.enable(SerializationFeature.INDENT_OUTPUT);
        m.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        SimpleModule module = new SimpleModule("notation-transcribe");
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
