package org.fineract.iso20022.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fineract.iso20022.model.dto.MandateSummary;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.model.enums.MandateStatus;
import org.fineract.iso20022.service.DirectDebitService;
import org.fineract.iso20022.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Direct Debits & Mandates", description = "ISO 20022 direct debit and mandate management")
public class DirectDebitController {

    private final PaymentService paymentService;
    private final DirectDebitService directDebitService;

    @PostMapping(value = "/direct-debits", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process pain.008 direct debit initiation")
    public ResponseEntity<List<PaymentStatusResponse>> processDirectDebit(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml)
                .idempotencyKey(idempotencyKey)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(request));
    }

    @GetMapping("/mandates")
    @Operation(summary = "List direct debit mandates")
    public ResponseEntity<List<MandateSummary>> listMandates(
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) MandateStatus status) {
        return ResponseEntity.ok(directDebitService.listMandates(status).stream()
                .map(MandateSummary::fromEntity).toList());
    }

    @GetMapping("/mandates/{mandateId}")
    @Operation(summary = "Get mandate details")
    public ResponseEntity<MandateSummary> getMandate(
            @PathVariable @Size(max = 64) String mandateId) {
        return ResponseEntity.ok(MandateSummary.fromEntity(directDebitService.getMandate(mandateId)));
    }

    @DeleteMapping("/mandates/{mandateId}")
    @Operation(summary = "Revoke a direct debit mandate")
    public ResponseEntity<Void> revokeMandate(
            @PathVariable @Size(max = 64) String mandateId) {
        directDebitService.revokeMandate(mandateId);
        return ResponseEntity.noContent().build();
    }
}
