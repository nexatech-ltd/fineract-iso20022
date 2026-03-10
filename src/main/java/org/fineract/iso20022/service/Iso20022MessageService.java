package org.fineract.iso20022.service;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.MessageParsingException;
import org.fineract.iso20022.mapper.*;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.fineract.iso20022.model.enums.Iso20022MessageType;
import org.fineract.iso20022.util.IsoMessageValidator;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Iso20022MessageService {

    private final IsoMessageValidator validator;
    private final Pain001Mapper pain001Mapper;
    private final Pain007Mapper pain007Mapper;
    private final Pain008Mapper pain008Mapper;
    private final Pain009Mapper pain009Mapper;
    private final Pain010Mapper pain010Mapper;
    private final Pacs008Mapper pacs008Mapper;
    private final Pacs003Mapper pacs003Mapper;
    private final Pacs004Mapper pacs004Mapper;
    private final Pacs009Mapper pacs009Mapper;
    private final Pacs028Mapper pacs028Mapper;
    private final Acmt007Mapper acmt007Mapper;
    private final Acmt008Mapper acmt008Mapper;
    private final Acmt019Mapper acmt019Mapper;
    private final Camt056Mapper camt056Mapper;
    private final Camt060Mapper camt060Mapper;

    public record ParsedMessage(
            AbstractMX mx,
            String messageTypeId,
            Iso20022MessageType messageType,
            List<InternalPaymentInstruction> instructions
    ) {}

    public ParsedMessage parseInbound(String xml) {
        AbstractMX mx = validator.parseAndValidate(xml);
        String messageTypeId = validator.extractMessageType(mx);
        validator.validateSupported(messageTypeId);

        Iso20022MessageType messageType = Iso20022MessageType.fromCode(messageTypeId);
        List<InternalPaymentInstruction> instructions;

        switch (messageType) {
            case PAIN_001 -> instructions = pain001Mapper.toPaymentInstructions(mx);
            case PAIN_007 -> instructions = pain007Mapper.toPaymentInstructions(mx);
            case PAIN_008 -> instructions = pain008Mapper.toPaymentInstructions(mx);
            case PACS_008 -> instructions = pacs008Mapper.toPaymentInstructions(mx);
            case PACS_003 -> instructions = pacs003Mapper.toPaymentInstructions(mx);
            case PACS_004 -> instructions = pacs004Mapper.toPaymentInstructions(mx);
            case PACS_009 -> instructions = pacs009Mapper.toPaymentInstructions(mx);
            case PACS_028 -> instructions = pacs028Mapper.toPaymentInstructions(mx);
            case PAIN_009 -> instructions = pain009Mapper.toPaymentInstructions(mx);
            case PAIN_010 -> instructions = pain010Mapper.toPaymentInstructions(mx);
            case ACMT_007 -> instructions = acmt007Mapper.toPaymentInstructions(mx);
            case ACMT_008 -> instructions = acmt008Mapper.toPaymentInstructions(mx);
            case ACMT_019 -> instructions = acmt019Mapper.toPaymentInstructions(mx);
            case CAMT_056 -> instructions = camt056Mapper.toPaymentInstructions(mx);
            case CAMT_060 -> instructions = camt060Mapper.toPaymentInstructions(mx);
            default -> throw new MessageParsingException(
                    "Message type " + messageTypeId + " is not an inbound type");
        }

        List<String> validationErrors = validator.validateBusinessRules(mx);
        if (!validationErrors.isEmpty()) {
            throw new MessageParsingException(
                    "Business validation failed: " + String.join("; ", validationErrors));
        }

        log.info("Parsed inbound {} message with {} instructions", messageTypeId, instructions.size());
        return new ParsedMessage(mx, messageTypeId, messageType, instructions);
    }
}
