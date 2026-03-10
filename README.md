# Fineract ISO 20022 Adapter

A comprehensive ISO 20022 message adapter for [Apache Fineract](https://fineract.apache.org/). Supports **24 ISO 20022 message types** across payments, direct debits, mandates, reversals, returns, cancellations, account management, account statements, and notifications. Integrates with Fineract via REST API and Kafka external events (JSON + Avro).

## Supported ISO 20022 Message Types (24 types)

### Payments & Transfers (Inbound — 10 types)

| Message | Type | Description | Fineract Operation |
|---------|------|-------------|-------------------|
| **pain.001.001.11** | Customer Credit Transfer Initiation | Customer payment instructions | Account transfer / deposit |
| **pain.007.001.11** | Customer Payment Reversal | Reversal of a previous payment | Undo transaction |
| **pain.008.001.11** | Customer Direct Debit Initiation | Direct debit collection order | Withdrawal + standing instruction |
| **pacs.003.001.11** | FI to FI Customer Direct Debit | Interbank direct debit | Withdrawal from debtor account |
| **pacs.004.001.14** | Payment Return | Return of a previously settled payment | Undo original transaction |
| **pacs.008.001.10** | FI to FI Customer Credit Transfer | Interbank credit transfer | Account transfer / deposit |
| **pacs.009.001.12** | Financial Institution Credit Transfer | FI-level institution transfer | Internal account transfer |
| **pacs.028.001.06** | FI to FI Payment Status Request | Payment status inquiry | Look up payment status |
| **camt.056.001.11** | FI to FI Payment Cancellation Request | Request to cancel a payment | Undo + investigation |
| **camt.060.001.07** | Account Reporting Request | Request for account report | Generate camt.052/053 |

### Mandate Management (3 types)

| Message | Type | Description | Fineract Operation |
|---------|------|-------------|-------------------|
| **pain.009.001.08** | Mandate Initiation Request | Create a new direct debit mandate | Create standing instruction |
| **pain.010.001.08** | Mandate Amendment Request | Amend an existing mandate | Update standing instruction |
| **pain.012.001.04** | Mandate Acceptance Report (Outbound) | Accept/reject mandate request | — |

### Account Management (4 types)

| Message | Type | Description | Fineract Operation |
|---------|------|-------------|-------------------|
| **acmt.007.001.05** | Account Opening Request | Open a new bank account | Create, approve & activate savings account |
| **acmt.008.001.05** | Account Opening Amendment Request | Modify an existing account | Update savings account |
| **acmt.010.001.04** | Account Request Acknowledgement (Outbound) | Confirm account operation | — |
| **acmt.019.001.04** | Account Closing Request | Close an account | Close savings account |

### Outbound Messages (7 types)

| Message | Type | Description |
|---------|------|-------------|
| **pain.002** | Customer Payment Status Report | Response to pain.001 |
| **pain.014.001.11** | Creditor Payment Activation Request Status Report | Response to pain.008 direct debit |
| **pacs.002.001.12** | FI to FI Payment Status Report | Status report for all processed payments |
| **camt.029.001.13** | Resolution of Investigation | Response to camt.056 cancellation request |
| **camt.052.001.13** | Bank to Customer Account Report (Intraday) | Intraday account report |
| **camt.053.001.10** | Bank to Customer Statement | End-of-day account statement |
| **camt.054.001.10** | Bank to Customer Debit/Credit Notification | Real-time transaction notification |

## Architecture

```
┌──────────────────┐      ┌──────────────────────┐      ┌─────────────────┐
│  External Systems │─────▶│  ISO 20022 Adapter    │─────▶│  Apache Fineract │
│  (Banks, PSPs)    │◀─────│  (Spring Boot)        │◀─────│  (Core Banking)  │
└──────────────────┘      └──────────┬───────────┘      └─────────────────┘
                                     │
                          ┌──────────┼───────────┐
                          │          │           │
                    ┌─────▼───┐ ┌────▼────┐ ┌───▼────────┐
                    │  MySQL  │ │Dragonfly│ │   Kafka    │
                    │ (Audit) │ │ (Cache) │ │ (Messaging)│
                    └─────────┘ └─────────┘ └────────────┘
```

### Fineract Integration

#### REST API Integration

| ISO 20022 Operation | Fineract API Endpoint |
|---------------------|----------------------|
| Credit Transfer (pain.001/pacs.008) | `POST /accounttransfers` or `POST /savingsaccounts/{id}/transactions?command=deposit` |
| Payment Reversal (pain.007) | `POST /savingsaccounts/{id}/transactions/{txnId}?command=undo` |
| Direct Debit (pain.008/pacs.003) | `POST /standinginstructions` + `POST /savingsaccounts/{id}/transactions?command=withdrawal` |
| Mandate (pain.009/010) | `POST /standinginstructions` / `DELETE /standinginstructions/{id}` |
| Payment Return (pacs.004) | `POST /savingsaccounts/{id}/transactions/{txnId}?command=undo` |
| Cancellation (camt.056) | `POST /savingsaccounts/{id}/transactions/{txnId}?command=undo` + investigation |
| Account Opening (acmt.007) | `POST /savingsaccounts` + approve + activate |
| Account Modification (acmt.008) | `PUT /savingsaccounts/{id}` |
| Account Closing (acmt.019) | `POST /savingsaccounts/{id}?command=close` |
| Loan Disbursement | `POST /loans/{id}/transactions?command=disburse` |
| Loan Repayment | `POST /loans/{id}/transactions?command=repayment` |
| Account Statement | `GET /savingsaccounts/{id}/transactions` |
| Loan Statement | `GET /loans/{id}?associations=transactions` |
| Batch Operations | `POST /batches` |

#### Kafka Integration (Fineract External Events)

| Feature | Details |
|---------|---------|
| Fineract Events Topic | `fineract-external-events` |
| Format Support | **JSON** and **Avro** (Fineract's native BinaryMessageEncoder format) |
| Avro Schemas | 30+ Fineract Avro schemas included (MessageV1 envelope, SavingsAccountTransaction, LoanTransaction, etc.) |
| Event Processing | SavingsAccountTransaction, LoanTransaction, Account change events |
| Cache Refresh | Automatic cache eviction on account/transaction events |
| Auto Notifications | camt.054 auto-generated for every Fineract transaction event |

### Message Flow

1. **Inbound**: External system sends ISO 20022 XML via REST API or Kafka topic
2. **Validation**: XML is parsed and validated using the Prowide ISO 20022 library
3. **Routing**: Message is routed by operation type (credit transfer, direct debit, mandate, reversal, return, cancellation, loan, account management, status request, account report)
4. **Account Resolution**: IBAN/BIC resolved to Fineract account ID via cache → DB → Fineract API
5. **Execution**: Corresponding Fineract REST API calls are made
6. **Response**: A pacs.002 status report (or camt.029/acmt.010/pain.012 depending on type) is generated
7. **Audit**: All messages and state transitions are logged to MySQL
8. **Notification**: camt.054 notifications are auto-generated from Fineract Kafka events (JSON or Avro)

## Tech Stack

| Technology | Version | Purpose |
|-----------|---------|---------|
| Java | 21 | Runtime |
| Spring Boot | 3.4.5 | Application framework |
| Gradle | 8.12 | Build tool |
| MySQL | 8.0 | Audit trail, message persistence, account mapping, mandates |
| DragonflyDB | Latest | Redis-compatible cache (idempotency, account resolution) |
| Apache Kafka | 3.x | Async message ingestion, status publishing, Fineract events |
| Apache Avro | 1.12.0 | Fineract external event deserialization (Avro format) |
| Prowide ISO 20022 | SRU2025-10.3.4 | ISO 20022 message parsing and building |
| Apache Fineract | Latest | Core banking platform |
| Testcontainers | 1.20.6 | Integration testing |

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 21 (for local development)

### Run with Docker Compose

```bash
docker compose up -d
docker compose ps
docker compose logs -f adapter
docker compose down
```

The adapter will be available at `http://localhost:8081`.
Fineract will be available at `https://localhost:8443`.

### Local Development

```bash
./gradlew build
./gradlew unitTest        # 47 unit tests
./gradlew e2eTest         # E2E tests (requires Docker)
./gradlew bootRun
```

## API Endpoints

### Swagger UI

Available at: `http://localhost:8081/swagger-ui/index.html`

### Payment Processing

```bash
# Process ISO 20022 payment (JSON wrapper - all message types)
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"xmlMessage": "...", "idempotencyKey": "unique-key-123"}'

# Process raw ISO 20022 XML
curl -X POST http://localhost:8081/api/v1/payments/xml \
  -H "Content-Type: application/xml" \
  -H "Idempotency-Key: unique-key-456" \
  -d @message.xml

# Get payment status
curl http://localhost:8081/api/v1/payments/status/{messageId}
curl http://localhost:8081/api/v1/payments/status/e2e/{endToEndId}
curl http://localhost:8081/api/v1/payments/status/{messageId}/xml
```

### Direct Debits & Mandates

```bash
# Process pain.008 direct debit
curl -X POST http://localhost:8081/api/v1/direct-debits \
  -H "Content-Type: application/xml" \
  -d @pain008.xml

# List mandates (REST API)
curl http://localhost:8081/api/v1/mandates
curl http://localhost:8081/api/v1/mandates?status=ACTIVE

# Get / revoke mandate
curl http://localhost:8081/api/v1/mandates/{mandateId}
curl -X DELETE http://localhost:8081/api/v1/mandates/{mandateId}
```

### Mandate Management (ISO 20022)

```bash
# pain.009 - Mandate Initiation
curl -X POST http://localhost:8081/api/v1/mandates/initiate \
  -H "Content-Type: application/xml" -d @pain009.xml

# pain.010 - Mandate Amendment
curl -X POST http://localhost:8081/api/v1/mandates/amend \
  -H "Content-Type: application/xml" -d @pain010.xml

# pain.012 - Mandate Acceptance Report
curl http://localhost:8081/api/v1/mandates/{mandateId}/acceptance-report?accepted=true
curl http://localhost:8081/api/v1/mandates/{mandateId}/acceptance-report?accepted=false&reason=Insufficient+funds
```

### Account Management (ISO 20022)

```bash
# acmt.007 - Open Account
curl -X POST http://localhost:8081/api/v1/accounts/open \
  -H "Content-Type: application/xml" -d @acmt007.xml

# acmt.008 - Modify Account
curl -X POST http://localhost:8081/api/v1/accounts/modify \
  -H "Content-Type: application/xml" -d @acmt008.xml

# acmt.019 - Close Account
curl -X POST http://localhost:8081/api/v1/accounts/close \
  -H "Content-Type: application/xml" -d @acmt019.xml
```

### Reversals, Returns & Cancellations

```bash
# Process pain.007 reversal
curl -X POST http://localhost:8081/api/v1/reversals \
  -H "Content-Type: application/xml" -d @pain007.xml

# Process pacs.004 return
curl -X POST http://localhost:8081/api/v1/returns \
  -H "Content-Type: application/xml" -d @pacs004.xml

# Process camt.056 cancellation
curl -X POST http://localhost:8081/api/v1/cancellations \
  -H "Content-Type: application/xml" -d @camt056.xml
```

### Account Statements & Reports

```bash
# camt.053 savings account statement
curl "http://localhost:8081/api/v1/statements/{accountId}?fromDate=2025-01-01&toDate=2025-01-31"

# camt.052 intraday report
curl http://localhost:8081/api/v1/statements/{accountId}/intraday

# camt.053 loan statement
curl "http://localhost:8081/api/v1/statements/loan/{loanId}?fromDate=2025-01-01&toDate=2025-06-30"

# camt.054 notification
curl "http://localhost:8081/api/v1/statements/notification/{paymentMessageId}?credit=true"
```

### Message History

```bash
curl "http://localhost:8081/api/v1/messages?page=0&size=20"
curl http://localhost:8081/api/v1/messages/{messageId}
curl http://localhost:8081/api/v1/messages/{messageId}/audit
curl http://localhost:8081/api/v1/messages/status/COMPLETED
curl http://localhost:8081/api/v1/messages/account/{accountId}
```

## Kafka Topics

| Topic | Purpose |
|-------|---------|
| `iso20022.inbound` | Incoming ISO 20022 XML messages (all inbound types) |
| `iso20022.outbound` | Outbound ISO 20022 messages (camt.053, camt.054, camt.029, etc.) |
| `iso20022.status` | Payment status reports (pacs.002) |
| `iso20022.dlq` | Dead letter queue for failed messages |
| `fineract-external-events` | Fineract Kafka events (auto camt.054 generation, cache refresh) |

## Configuration

```yaml
fineract:
  api:
    base-url: http://localhost:8443/fineract-provider/api/v1
    username: mifos
    password: password
    tenant-id: default

iso20022:
  kafka:
    inbound-topic: iso20022.inbound
    outbound-topic: iso20022.outbound
    status-topic: iso20022.status
    dlq-topic: iso20022.dlq
  cache:
    idempotency-ttl: 86400
    account-cache-ttl: 3600
  fineract-events:
    enabled: false          # Enable to consume Fineract Kafka events
    topic: fineract-external-events
    format: json            # json or avro (Fineract native Avro encoding)
```

Environment variable overrides: `FINERACT_API_BASE_URL`, `SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `ISO20022_FINERACT_EVENTS_ENABLED`, `ISO20022_FINERACT_EVENTS_FORMAT`.

## Database Schema

### Tables

| Table | Purpose |
|-------|---------|
| `payment_messages` | All processed ISO 20022 messages with status, amounts, accounts, operation type |
| `message_audit_log` | Immutable audit trail of every state transition |
| `account_mapping` | IBAN/BIC to Fineract account ID mapping with caching |
| `direct_debit_mandates` | Direct debit mandate lifecycle (create, activate, suspend, cancel) |
| `payment_investigations` | camt.056 cancellation request tracking and resolution |

## Testing

### Unit Tests (53 tests)

```bash
./gradlew unitTest
```

Covers: all 18 mapper parsers/builders (pain, pacs, camt, acmt), service layer logic, XML validation, idempotency.

### End-to-End Tests

```bash
./gradlew e2eTest
```

Uses Testcontainers (MySQL, Redis, Kafka). Covers: full REST API flows, idempotency, error handling, OpenAPI, actuator.

## Project Structure

```
src/main/java/org/fineract/iso20022/
├── FineractIso20022Application.java
├── config/          # Spring configuration (Kafka, Redis, WebClient, OpenAPI, Async)
├── controller/      # REST endpoints (Payment, Statement, DirectDebit, Reversal, AccountManagement, MandateManagement, Message)
├── exception/       # Custom exceptions and global error handler
├── kafka/           # Kafka producer, consumer, Fineract event consumer (JSON + Avro), Avro deserializer
├── mapper/          # 18 ISO 20022 mappers (Pain001/007/008/009/010/012/014, Pacs002/003/004/008/009/028, Acmt007/008/010/019, Camt029/052/053/054/056/060)
├── model/
│   ├── dto/         # InternalPaymentInstruction, request/response DTOs
│   ├── entity/      # PaymentMessage, AccountMapping, DirectDebitMandate, PaymentInvestigation
│   └── enums/       # Iso20022MessageType (24), OperationType, MandateStatus, InvestigationStatus
├── repository/      # Spring Data JPA repositories
├── service/         # PaymentService, DirectDebitService, ReversalService, StatementService, AccountManagementService, AccountResolutionService, FineractClientService
└── util/            # ID generator, message validator
```

## Author

**[NexaTech Consulting (PTY) LTD](https://nexatech.dev)**
17 Dock Road, Watershed, Cape Town, Western Cape, South Africa, 8002

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.
