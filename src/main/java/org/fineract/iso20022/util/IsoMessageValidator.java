package org.fineract.iso20022.util;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.security.SecureXmlParser;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IsoMessageValidator {

    private static final Set<String> SUPPORTED_MESSAGE_TYPES = Set.of(
            "pain.001", "pain.002", "pain.007", "pain.008", "pain.009", "pain.010",
            "pain.012", "pain.014",
            "pacs.002", "pacs.003", "pacs.004", "pacs.008", "pacs.009", "pacs.028",
            "acmt.007", "acmt.008", "acmt.010", "acmt.019",
            "camt.029", "camt.052", "camt.053", "camt.054", "camt.056", "camt.060");

    public AbstractMX parseAndValidate(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new MessageParsingException("XML message cannot be null or empty");
        }

        String trimmed = SecureXmlParser.sanitize(xml.trim());
        if (!trimmed.startsWith("<?xml") && !trimmed.startsWith("<Document")
                && !trimmed.startsWith("<Envelope") && !trimmed.startsWith("<AppHdr")) {
            throw new MessageParsingException("Input does not appear to be a valid XML document");
        }

        try {
            AbstractMX mx = AbstractMX.parse(trimmed);
            if (mx == null) {
                throw new MessageParsingException("Failed to parse ISO 20022 message: unknown format");
            }
            return mx;
        } catch (MessageParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageParsingException("Failed to parse ISO 20022 XML: " + e.getMessage(), e);
        }
    }

    public String extractMessageType(AbstractMX mx) {
        if (mx.getMxId() == null) {
            throw new MessageParsingException("Cannot determine message type: MxId is null");
        }
        return mx.getMxId().id();
    }

    public void validateSupported(String messageTypeId) {
        boolean supported = SUPPORTED_MESSAGE_TYPES.stream()
                .anyMatch(messageTypeId::startsWith);
        if (!supported) {
            throw new MessageParsingException("Unsupported ISO 20022 message type: " + messageTypeId
                    + ". Supported: " + SUPPORTED_MESSAGE_TYPES);
        }
    }

    public List<String> validateBusinessRules(AbstractMX mx) {
        List<String> errors = new ArrayList<>();
        String xml = mx.message();

        if (!xml.contains("<MsgId>") && !xml.contains("MsgId>")) {
            errors.add("Message ID (MsgId) is required");
        }

        return errors;
    }
}
