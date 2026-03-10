package org.fineract.iso20022.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fineract.iso20022.mapper.Pain012Mapper;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.service.DirectDebitService;
import org.fineract.iso20022.service.PaymentService;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mandates")
@RequiredArgsConstructor
@Validated
@Tag(name = "Mandate Management (ISO 20022)", description = "ISO 20022 mandate lifecycle management (pain.009/010/012)")
public class MandateManagementController {

    private final PaymentService paymentService;
    private final DirectDebitService directDebitService;
    private final Pain012Mapper pain012Mapper;

    @PostMapping(value = "/initiate", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process pain.009 mandate initiation request")
    public ResponseEntity<List<PaymentStatusResponse>> initiateMandate(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml).idempotencyKey(idempotencyKey).build();
        return ResponseEntity.ok(paymentService.processPayment(request));
    }

    @PostMapping(value = "/amend", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process pain.010 mandate amendment request")
    public ResponseEntity<List<PaymentStatusResponse>> amendMandate(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml).idempotencyKey(idempotencyKey).build();
        return ResponseEntity.ok(paymentService.processPayment(request));
    }

    @GetMapping(value = "/{mandateId}/acceptance-report", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Generate pain.012 mandate acceptance report")
    public ResponseEntity<String> getMandateAcceptanceReport(
            @PathVariable @Size(max = 64) String mandateId,
            @RequestParam(defaultValue = "true") boolean accepted,
            @RequestParam(required = false) String reason) {
        directDebitService.getMandate(mandateId);
        String reportXml = pain012Mapper.buildAcceptanceReport(null, mandateId, accepted, reason);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(reportXml);
    }
}
