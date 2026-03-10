package org.fineract.iso20022.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.mapper.Acmt007Mapper;
import org.fineract.iso20022.mapper.Acmt008Mapper;
import org.fineract.iso20022.mapper.Acmt019Mapper;
import org.fineract.iso20022.mapper.Camt056Mapper;
import org.fineract.iso20022.mapper.Camt060Mapper;
import org.fineract.iso20022.mapper.Pacs003Mapper;
import org.fineract.iso20022.mapper.Pacs004Mapper;
import org.fineract.iso20022.mapper.Pacs008Mapper;
import org.fineract.iso20022.mapper.Pacs009Mapper;
import org.fineract.iso20022.mapper.Pacs028Mapper;
import org.fineract.iso20022.mapper.Pain001Mapper;
import org.fineract.iso20022.mapper.Pain007Mapper;
import org.fineract.iso20022.mapper.Pain008Mapper;
import org.fineract.iso20022.mapper.Pain009Mapper;
import org.fineract.iso20022.mapper.Pain010Mapper;
import org.fineract.iso20022.model.enums.Iso20022MessageType;
import org.fineract.iso20022.service.Iso20022MessageService;
import org.fineract.iso20022.service.Iso20022MessageService.ParsedMessage;
import org.fineract.iso20022.util.IsoMessageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class Iso20022MessageServiceTest {

    private Iso20022MessageService service;

    @BeforeEach
    void setUp() {
        service = new Iso20022MessageService(
                new IsoMessageValidator(),
                new Pain001Mapper(),
                new Pain007Mapper(),
                new Pain008Mapper(),
                new Pain009Mapper(),
                new Pain010Mapper(),
                new Pacs008Mapper(),
                new Pacs003Mapper(),
                new Pacs004Mapper(),
                new Pacs009Mapper(),
                new Pacs028Mapper(),
                new Acmt007Mapper(),
                new Acmt008Mapper(),
                new Acmt019Mapper(),
                new Camt056Mapper(),
                new Camt060Mapper());
    }

    @Test
    void shouldParseInboundPain001() {
        String xml = loadSampleXml("sample-pain001.xml");

        ParsedMessage result = service.parseInbound(xml);

        assertThat(result.messageType()).isEqualTo(Iso20022MessageType.PAIN_001);
        assertThat(result.messageTypeId()).startsWith("pain.001");
        assertThat(result.instructions()).hasSize(1);
        assertThat(result.instructions().getFirst().getDebtorName()).isEqualTo("John Doe");
    }

    @Test
    void shouldParseInboundPacs008() {
        String xml = loadSampleXml("sample-pacs008.xml");

        ParsedMessage result = service.parseInbound(xml);

        assertThat(result.messageType()).isEqualTo(Iso20022MessageType.PACS_008);
        assertThat(result.messageTypeId()).startsWith("pacs.008");
        assertThat(result.instructions()).hasSize(1);
        assertThat(result.instructions().getFirst().getDebtorName()).isEqualTo("Alpha Corp");
    }

    @Test
    void shouldThrowOnInvalidXml() {
        assertThatThrownBy(() -> service.parseInbound("invalid xml"))
                .isInstanceOf(MessageParsingException.class);
    }

    @Test
    void shouldThrowOnNullXml() {
        assertThatThrownBy(() -> service.parseInbound(null))
                .isInstanceOf(MessageParsingException.class);
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
