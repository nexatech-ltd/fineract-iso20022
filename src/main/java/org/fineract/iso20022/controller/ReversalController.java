package org.fineract.iso20022.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.service.PaymentService;
import org.springframework.http.HttpStatus;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reversals, Returns & Cancellations", description = "ISO 20022 reversal, return, and cancellation endpoints")
public class ReversalController {

    private final PaymentService paymentService;

    @PostMapping(value = "/reversals", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process pain.007 payment reversal")
    public ResponseEntity<List<PaymentStatusResponse>> processReversal(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml)
                .idempotencyKey(idempotencyKey)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(request));
    }

    @PostMapping(value = "/returns", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process pacs.004 payment return")
    public ResponseEntity<List<PaymentStatusResponse>> processReturn(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml)
                .idempotencyKey(idempotencyKey)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(request));
    }

    @PostMapping(value = "/cancellations", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process camt.056 payment cancellation request")
    public ResponseEntity<List<PaymentStatusResponse>> processCancellation(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml)
                .idempotencyKey(idempotencyKey)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.processPayment(request));
    }
}
