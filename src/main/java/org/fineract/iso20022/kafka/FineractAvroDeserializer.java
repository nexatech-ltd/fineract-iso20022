package org.fineract.iso20022.kafka;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Slf4j
@Component
public class FineractAvroDeserializer {

    private static final byte[] AVRO_SINGLE_OBJECT_MARKER = {(byte) 0xC3, 0x01};
    private static final int HEADER_SIZE = 10; // 2 marker + 8 fingerprint

    private static final String BIGDECIMAL_PLACEHOLDER = "\"bigdecimal\"";
    private static final String BIGDECIMAL_REPLACEMENT = 
            "{\"logicalType\":\"decimal\",\"precision\":27,\"scale\":8,\"type\":\"bytes\"}";

    private Schema messageV1Schema;
    private final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();

    public record FineractExternalEvent(
            long id, String source, String type, String category,
            String createdAt, String businessDate, String tenantId,
            String idempotencyKey, String dataschema, GenericRecord data) {}

    @PostConstruct
    public void init() {
        try {
            messageV1Schema = loadSchema("avro/fineract/MessageV1.avsc");
            loadAllSchemas();
            log.info("Fineract Avro schemas loaded: {} schemas cached", schemaCache.size());
        } catch (Exception e) {
            log.warn("Could not pre-load Fineract Avro schemas: {}. Avro deserialization will not work.", e.getMessage());
        }
    }

    private void loadAllSchemas() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:avro/fineract/*.avsc");
            for (Resource resource : resources) {
                try {
                    String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    content = content.replace(BIGDECIMAL_PLACEHOLDER, BIGDECIMAL_REPLACEMENT);
                    Schema schema = new Schema.Parser().parse(content);
                    String fullName = schema.getNamespace() + "." + schema.getName();
                    schemaCache.put(fullName, schema);
                } catch (Exception e) {
                    log.debug("Skipped schema {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not scan avro schemas: {}", e.getMessage());
        }
    }

    public FineractExternalEvent deserialize(byte[] avroBytes) {
        try {
            GenericRecord envelope = decodeRecord(avroBytes, messageV1Schema);
            
            long id = (long) envelope.get("id");
            String source = str(envelope.get("source"));
            String type = str(envelope.get("type"));
            String category = str(envelope.get("category"));
            String createdAt = str(envelope.get("createdAt"));
            String businessDate = str(envelope.get("businessDate"));
            String tenantId = str(envelope.get("tenantId"));
            String idempotencyKey = str(envelope.get("idempotencyKey"));
            String dataschema = str(envelope.get("dataschema"));
            
            GenericRecord innerData = null;
            Object dataObj = envelope.get("data");
            if (dataObj instanceof ByteBuffer dataBytes) {
                Schema innerSchema = schemaCache.get(dataschema);
                if (innerSchema != null) {
                    byte[] innerBytes = new byte[dataBytes.remaining()];
                    dataBytes.get(innerBytes);
                    innerData = decodeRecord(innerBytes, innerSchema);
                } else {
                    log.debug("No schema found for dataschema: {}", dataschema);
                }
            }
            
            return new FineractExternalEvent(id, source, type, category, 
                    createdAt, businessDate, tenantId, idempotencyKey, dataschema, innerData);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize Fineract Avro message", e);
        }
    }

    private GenericRecord decodeRecord(byte[] bytes, Schema schema) throws IOException {
        byte[] payload = stripSingleObjectHeader(bytes);
        GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);
        return reader.read(null, decoder);
    }

    private byte[] stripSingleObjectHeader(byte[] bytes) {
        if (bytes.length > HEADER_SIZE 
                && bytes[0] == AVRO_SINGLE_OBJECT_MARKER[0] 
                && bytes[1] == AVRO_SINGLE_OBJECT_MARKER[1]) {
            byte[] stripped = new byte[bytes.length - HEADER_SIZE];
            System.arraycopy(bytes, HEADER_SIZE, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }

    private Schema loadSchema(String classpath) throws IOException {
        try (InputStream is = new ClassPathResource(classpath).getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            content = content.replace(BIGDECIMAL_PLACEHOLDER, BIGDECIMAL_REPLACEMENT);
            return new Schema.Parser().parse(content);
        }
    }

    private String str(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    public static BigDecimal extractBigDecimal(GenericRecord record, String field) {
        Object val = record.get(field);
        if (val instanceof ByteBuffer bb) {
            byte[] bytes = new byte[bb.remaining()];
            bb.get(bytes);
            return new BigDecimal(new java.math.BigInteger(bytes), 8);
        }
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return null;
    }

    public static String extractString(GenericRecord record, String field) {
        Object val = record.get(field);
        return val != null ? val.toString() : null;
    }

    public static Long extractLong(GenericRecord record, String field) {
        Object val = record.get(field);
        if (val instanceof Number n) return n.longValue();
        return null;
    }

    public static Boolean extractBoolean(GenericRecord record, String field) {
        Object val = record.get(field);
        if (val instanceof Boolean b) return b;
        return null;
    }

    public static GenericRecord extractRecord(GenericRecord record, String field) {
        Object val = record.get(field);
        if (val instanceof GenericRecord gr) return gr;
        return null;
    }
}
