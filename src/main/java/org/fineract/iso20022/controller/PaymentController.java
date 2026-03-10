package org.fineract.iso20022.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "ISO 20022 payment processing endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Process ISO 20022 payment message",
            description = "Accepts pain.001 or pacs.008 XML and processes payment via Fineract")
    @ApiResponse(responseCode = "201", description = "Payment processed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid message format")
    @ApiResponse(responseCode = "409", description = "Duplicate request")
    @ApiResponse(responseCode = "502", description = "Fineract API error")
    public ResponseEntity<List<PaymentStatusResponse>> processPayment(
            @Valid @RequestBody PaymentInitiationRequest request) {
        List<PaymentStatusResponse> responses = paymentService.processPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @PostMapping(value = "/xml", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process raw ISO 20022 XML",
            description = "Accepts raw ISO 20022 XML directly in the request body")
    public ResponseEntity<List<PaymentStatusResponse>> processRawXml(
            @RequestBody String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml)
                .idempotencyKey(idempotencyKey)
                .build();
        List<PaymentStatusResponse> responses = paymentService.processPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @GetMapping("/status/{messageId}")
    @Operation(summary = "Get payment status by message ID")
    @ApiResponse(responseCode = "200", description = "Status found")
    @ApiResponse(responseCode = "404", description = "Payment not found")
    public ResponseEntity<PaymentStatusResponse> getStatus(
            @Parameter(description = "Original message ID") @PathVariable String messageId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(messageId));
    }

    @GetMapping("/status/e2e/{endToEndId}")
    @Operation(summary = "Get payment status by end-to-end ID")
    public ResponseEntity<PaymentStatusResponse> getStatusByEndToEndId(
            @Parameter(description = "End-to-end ID") @PathVariable String endToEndId) {
        return ResponseEntity.ok(paymentService.getPaymentStatusByEndToEndId(endToEndId));
    }

    @GetMapping("/status/{messageId}/xml")
    @Operation(summary = "Get pacs.002 status report XML")
    @ApiResponse(responseCode = "200", description = "Status XML",
            content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE))
    public ResponseEntity<String> getStatusXml(@PathVariable String messageId) {
        PaymentStatusResponse status = paymentService.getPaymentStatus(messageId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(status.getStatusXml());
    }
}
