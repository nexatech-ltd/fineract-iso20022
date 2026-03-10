package org.fineract.iso20022.unit.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.nio.charset.StandardCharsets;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.util.IsoMessageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class IsoMessageValidatorTest {

    private IsoMessageValidator validator;

    @BeforeEach
    void setUp() {
        validator = new IsoMessageValidator();
    }

    @Test
    void shouldParseValidPain001() {
        String xml = loadSampleXml("sample-pain001.xml");
        AbstractMX mx = validator.parseAndValidate(xml);

        assertThat(mx).isNotNull();
        String messageType = validator.extractMessageType(mx);
        assertThat(messageType).startsWith("pain.001");
    }

    @Test
    void shouldParseValidPacs008() {
        String xml = loadSampleXml("sample-pacs008.xml");
        AbstractMX mx = validator.parseAndValidate(xml);

        assertThat(mx).isNotNull();
        String messageType = validator.extractMessageType(mx);
        assertThat(messageType).startsWith("pacs.008");
    }

    @Test
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> validator.parseAndValidate(null))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void shouldRejectEmptyMessage() {
        assertThatThrownBy(() -> validator.parseAndValidate(""))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("null or empty");
    }

    @Test
    void shouldRejectNonXmlMessage() {
        assertThatThrownBy(() -> validator.parseAndValidate("not xml at all"))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("valid XML");
    }

    @Test
    void shouldValidateSupportedMessageTypes() {
        validator.validateSupported("pain.001.001.11");
        validator.validateSupported("pacs.008.001.10");
        validator.validateSupported("camt.053.001.10");
    }

    @Test
    void shouldRejectUnsupportedMessageType() {
        assertThatThrownBy(() -> validator.validateSupported("setr.001.001.01"))
                .isInstanceOf(MessageParsingException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void shouldValidateBusinessRules() {
        String xml = loadSampleXml("sample-pain001.xml");
        AbstractMX mx = validator.parseAndValidate(xml);

        var errors = validator.validateBusinessRules(mx);
        assertThat(errors).isEmpty();
    }

    private String loadSampleXml(String filename) {
        try (var is = getClass().getClassLoader().getResourceAsStream(filename)) {
            assert is != null;
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
