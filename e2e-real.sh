#!/usr/bin/env bash
set -euo pipefail

ADAPTER=http://localhost:8081
F=https://localhost:8443/fineract-provider/api/v1
AUTH="mifos:password"
TH="Fineract-Platform-TenantId: default"
TS=$(date +%s)
PASS=0; FAIL=0; TOTAL=0

rt() {
  TOTAL=$((TOTAL+1))
  if [ "$2" = "true" ]; then
    echo "  ✅ $1"
    PASS=$((PASS+1))
  else
    echo "  ❌ $1  [got: ${3:-}]"
    FAIL=$((FAIL+1))
  fi
}

fineract_get() {
  curl -sk -u $AUTH -H "$TH" "$F$1"
}

fineract_post() {
  curl -sk -u $AUTH -H "$TH" -H "Content-Type: application/json" -X POST "$F$1" -d "$2"
}

balance() {
  fineract_get "/savingsaccounts/$1" | python3 -c "import json,sys; print(json.load(sys.stdin)['summary']['accountBalance'])"
}

adapter_post_xml() {
  curl -s -X POST "$ADAPTER$1" -H "Content-Type: application/xml" -H "Idempotency-Key: $2" -d @"$3"
}

adapter_post_json() {
  curl -s -X POST "$ADAPTER$1" -H "Content-Type: application/json" -H "Idempotency-Key: $2" -d "$3"
}

echo ""
echo "═══════════════════════════════════════════════════════════════════"
echo "  REAL E2E TESTS — LIVE FINERACT + KAFKA — ZERO MOCKS"
echo "═══════════════════════════════════════════════════════════════════"
echo "  Adapter: $ADAPTER"
echo "  Fineract: $F"
echo "  Timestamp: $TS"
echo ""

echo "── 0. INFRASTRUCTURE CHECK ─────────────────────────────────────"
ADAPTER_OK=$(curl -s -o /dev/null -w "%{http_code}" $ADAPTER/actuator/health)
rt "Adapter healthy" "$([ "$ADAPTER_OK" = "200" ] && echo true || echo false)" "$ADAPTER_OK"

FINERACT_OK=$(curl -sk -o /dev/null -w "%{http_code}" -u $AUTH -H "$TH" $F/offices)
rt "Fineract API accessible" "$([ "$FINERACT_OK" = "200" ] && echo true || echo false)" "$FINERACT_OK"

echo ""
echo "── 1. CREDIT TRANSFER (pain.001 → Fineract accounttransfer) ───"
BAL1_BEFORE=$(balance 1)
BAL2_BEFORE=$(balance 2)
echo "  Before: Debtor=$BAL1_BEFORE  Creditor=$BAL2_BEFORE"

PAIN001=$(python3 -c "import json; print(json.dumps(open('src/test/resources/sample-pain001.xml').read()))")
R=$(adapter_post_json "/api/v1/payments" "e2e-p001-$TS" "{\"xmlMessage\": $PAIN001, \"idempotencyKey\": \"e2e-p001-$TS\"}")
P1_STATUS=$(echo "$R" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['status'])" 2>/dev/null || echo "ERROR")
P1_TXN=$(echo "$R" | python3 -c "import json,sys; print(json.load(sys.stdin)[0].get('fineractTransactionId',''))" 2>/dev/null || echo "")
echo "  Status=$P1_STATUS  FineractTxn=$P1_TXN"

BAL1_AFTER=$(balance 1)
BAL2_AFTER=$(balance 2)
echo "  After:  Debtor=$BAL1_AFTER  Creditor=$BAL2_AFTER"

rt "pain.001 COMPLETED" "$([ "$P1_STATUS" = "COMPLETED" ] && echo true || echo false)" "$P1_STATUS"
rt "Fineract txn ID returned" "$([ -n "$P1_TXN" ] && [ "$P1_TXN" != "null" ] && echo true || echo false)" "$P1_TXN"
rt "Debtor balance -1000" "$(python3 -c "print('true' if $BAL1_AFTER == $BAL1_BEFORE - 1000 else 'false')")" "$BAL1_AFTER"
rt "Creditor balance +1000" "$(python3 -c "print('true' if $BAL2_AFTER == $BAL2_BEFORE + 1000 else 'false')")" "$BAL2_AFTER"

# Verify in Fineract directly
FINERACT_TXN_COUNT=$(fineract_get "/savingsaccounts/1?associations=transactions" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('transactions',[])))")
rt "Fineract has real transactions" "$([ "$FINERACT_TXN_COUNT" -gt 1 ] && echo true || echo false)" "$FINERACT_TXN_COUNT"

echo ""
echo "── 2. INTERBANK TRANSFER (pacs.008 → Fineract) ─────────────────"
BAL1_BEFORE=$BAL1_AFTER
BAL2_BEFORE=$BAL2_AFTER

R=$(adapter_post_xml "/api/v1/payments/xml" "e2e-p008-$TS" "src/test/resources/sample-pacs008.xml")
P2_STATUS=$(echo "$R" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['status'])" 2>/dev/null || echo "ERROR")
P2_TXN=$(echo "$R" | python3 -c "import json,sys; print(json.load(sys.stdin)[0].get('fineractTransactionId',''))" 2>/dev/null || echo "")
echo "  Status=$P2_STATUS  FineractTxn=$P2_TXN"

BAL1_AFTER=$(balance 1)
BAL2_AFTER=$(balance 2)
echo "  After:  Debtor=$BAL1_AFTER  Creditor=$BAL2_AFTER"

rt "pacs.008 COMPLETED" "$([ "$P2_STATUS" = "COMPLETED" ] && echo true || echo false)" "$P2_STATUS"
rt "pacs.008 Fineract txn" "$([ -n "$P2_TXN" ] && [ "$P2_TXN" != "null" ] && echo true || echo false)" "$P2_TXN"
rt "pacs.008 balance changed" "$(python3 -c "print('true' if $BAL1_AFTER != $BAL1_BEFORE or $BAL2_AFTER != $BAL2_BEFORE else 'false')")"

echo ""
echo "── 3. STATEMENT camt.053 (from Fineract real txns) ──────────────"
STMT=$(curl -s -w "\nHTTP:%{http_code}" "$ADAPTER/api/v1/statements/1?fromDate=2024-01-01&toDate=2026-12-31")
SCODE=$(echo "$STMT" | grep "^HTTP:" | cut -d: -f2)
SBODY=$(echo "$STMT" | grep -v "^HTTP:")

rt "camt.053 HTTP 200" "$([ "$SCODE" = "200" ] && echo true || echo false)" "$SCODE"
rt "Has BkToCstmrStmt" "$(echo "$SBODY" | grep -q "BkToCstmrStmt" && echo true || echo false)"
rt "Has balance (Bal)" "$(echo "$SBODY" | grep -q "Bal>" && echo true || echo false)"
rt "Has entries (Ntry)" "$(echo "$SBODY" | grep -q "Ntry>" && echo true || echo false)"

echo ""
echo "── 4. INTRADAY camt.052 (live Fineract data) ───────────────────"
INTRA=$(curl -s -w "\nHTTP:%{http_code}" "$ADAPTER/api/v1/statements/1/intraday")
ICODE=$(echo "$INTRA" | grep "^HTTP:" | cut -d: -f2)
IBODY=$(echo "$INTRA" | grep -v "^HTTP:")

rt "camt.052 HTTP 200" "$([ "$ICODE" = "200" ] && echo true || echo false)" "$ICODE"
rt "Has BkToCstmrAcctRpt" "$(echo "$IBODY" | grep -q "BkToCstmrAcctRpt" && echo true || echo false)"

echo ""
echo "── 5. NOTIFICATION camt.054 ─────────────────────────────────────"
NOTIF=$(curl -s -w "\nHTTP:%{http_code}" "$ADAPTER/api/v1/statements/notification/MSG-TEST-001?credit=true")
NCODE=$(echo "$NOTIF" | grep "^HTTP:" | cut -d: -f2)
NBODY=$(echo "$NOTIF" | grep -v "^HTTP:")

rt "camt.054 HTTP 200" "$([ "$NCODE" = "200" ] && echo true || echo false)" "$NCODE"
rt "Has Ntfctn element" "$(echo "$NBODY" | grep -q "Ntfctn\|BkToCstmrDbtCdtNtfctn" && echo true || echo false)"

echo ""
echo "── 6. IDEMPOTENCY (duplicate pain.001 → 409) ───────────────────"
R2_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ADAPTER/api/v1/payments" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: e2e-p001-$TS" \
  -d "{\"xmlMessage\": $PAIN001, \"idempotencyKey\": \"e2e-p001-$TS\"}")
rt "Duplicate returns 409" "$([ "$R2_CODE" = "409" ] && echo true || echo false)" "$R2_CODE"

echo ""
echo "── 7. MANDATE LIFECYCLE (pain.009 → pain.012) ──────────────────"
MR=$(adapter_post_xml "/api/v1/mandates/initiate" "e2e-m009-$TS" "src/test/resources/sample-pain009.xml")
MS=$(echo "$MR" | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['status'])" 2>/dev/null || echo "ERROR")
rt "pain.009 mandate COMPLETED" "$([ "$MS" = "COMPLETED" ] && echo true || echo false)" "$MS"

ML=$(curl -s "$ADAPTER/api/v1/mandates")
rt "Mandate in list" "$(echo "$ML" | grep -q "MNDT-2025-001" && echo true || echo false)"

AR_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$ADAPTER/api/v1/mandates/MNDT-2025-001/acceptance-report?accepted=true")
rt "pain.012 acceptance 200" "$([ "$AR_CODE" = "200" ] && echo true || echo false)" "$AR_CODE"

echo ""
echo "── 8. PAYMENT STATUS (pacs.002) ─────────────────────────────────"
ST=$(curl -s "$ADAPTER/api/v1/payments/status/MSG-TEST-001")
ST_S=$(echo "$ST" | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])" 2>/dev/null || echo "ERROR")
rt "Status query works" "$([ "$ST_S" = "COMPLETED" ] && echo true || echo false)" "$ST_S"

ST_XML=$(curl -s -w "\nHTTP:%{http_code}" "$ADAPTER/api/v1/payments/status/MSG-TEST-001/xml")
SX_CODE=$(echo "$ST_XML" | grep "^HTTP:" | cut -d: -f2)
SX_BODY=$(echo "$ST_XML" | grep -v "^HTTP:")
rt "pacs.002 XML returned" "$([ "$SX_CODE" = "200" ] && echo true || echo false)" "$SX_CODE"
rt "pacs.002 has FIToFIPmtStsRpt" "$(echo "$SX_BODY" | grep -q "FIToFIPmtStsRpt" && echo true || echo false)"

echo ""
echo "── 9. KAFKA TOPICS & MESSAGES ───────────────────────────────────"
TOPICS=$(docker exec iso20022-kafka kafka-topics --bootstrap-server localhost:29092 --list 2>&1)
rt "iso20022.outbound topic" "$(echo "$TOPICS" | grep -q "iso20022.outbound" && echo true || echo false)"
rt "iso20022.status topic" "$(echo "$TOPICS" | grep -q "iso20022.status" && echo true || echo false)"
rt "iso20022.inbound topic" "$(echo "$TOPICS" | grep -q "iso20022.inbound" && echo true || echo false)"

OUTBOUND_COUNT=$(docker exec iso20022-kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic iso20022.outbound --from-beginning --timeout-ms 5000 2>/dev/null | wc -l | tr -d ' ')
echo "  Kafka outbound messages: $OUTBOUND_COUNT"
rt "Kafka outbound has messages" "$([ "$OUTBOUND_COUNT" -gt 0 ] && echo true || echo false)" "$OUTBOUND_COUNT"

STATUS_COUNT=$(docker exec iso20022-kafka kafka-console-consumer --bootstrap-server localhost:29092 --topic iso20022.status --from-beginning --timeout-ms 5000 2>/dev/null | wc -l | tr -d ' ')
echo "  Kafka status messages: $STATUS_COUNT"
rt "Kafka status has messages" "$([ "$STATUS_COUNT" -gt 0 ] && echo true || echo false)" "$STATUS_COUNT"

echo ""
echo "── 10. KAFKA INBOUND (send via Kafka → process) ─────────────────"
KAFKA_XML=$(python3 -c "
xml = open('src/test/resources/sample-pacs009.xml').read()
xml = xml.replace('PACS009-TEST-001', 'KAFKA-INBOUND-$TS')
print(xml.replace('\"','\\\\\"'))" 2>/dev/null)
docker exec iso20022-kafka bash -c "echo '$KAFKA_XML' | kafka-console-producer --bootstrap-server localhost:29092 --topic iso20022.inbound" 2>/dev/null
sleep 3
KAFKA_MSG=$(curl -s "$ADAPTER/api/v1/payments/status/KAFKA-INBOUND-$TS" 2>/dev/null)
KAFKA_STATUS=$(echo "$KAFKA_MSG" | python3 -c "import json,sys; print(json.load(sys.stdin).get('status','NOT_FOUND'))" 2>/dev/null || echo "NOT_FOUND")
echo "  Kafka inbound message status: $KAFKA_STATUS"
rt "Kafka inbound processed" "$([ "$KAFKA_STATUS" != "NOT_FOUND" ] && echo true || echo false)" "$KAFKA_STATUS"

echo ""
echo "── 11. AUDIT TRAIL ──────────────────────────────────────────────"
AUDIT=$(curl -s "$ADAPTER/api/v1/messages/MSG-TEST-001/audit")
AUDIT_COUNT=$(echo "$AUDIT" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "0")
AUDIT_ACTIONS=$(echo "$AUDIT" | python3 -c "
import json,sys
data = json.load(sys.stdin)
print(','.join([e['action'] for e in data]))" 2>/dev/null || echo "")
echo "  MSG-TEST-001 audit: $AUDIT_COUNT entries [$AUDIT_ACTIONS]"
rt "Has RECEIVED action" "$(echo "$AUDIT_ACTIONS" | grep -q "RECEIVED" && echo true || echo false)"
rt "Has VALIDATED action" "$(echo "$AUDIT_ACTIONS" | grep -q "VALIDATED" && echo true || echo false)"
rt "Has COMPLETED action" "$(echo "$AUDIT_ACTIONS" | grep -q "COMPLETED" && echo true || echo false)"

SYS_AUDIT=$(curl -s "$ADAPTER/api/v1/messages/system-audit?page=0&size=1")
SYS_COUNT=$(echo "$SYS_AUDIT" | python3 -c "import json,sys; print(json.load(sys.stdin)['totalElements'])" 2>/dev/null || echo "0")
echo "  System audit events: $SYS_COUNT"
rt "System audit has events" "$([ "$SYS_COUNT" -gt 0 ] && echo true || echo false)" "$SYS_COUNT"

echo ""
echo "── 12. FINERACT FINAL STATE ─────────────────────────────────────"
echo "  Fineract accounts:"
fineract_get "/savingsaccounts/1" | python3 -c "import json,sys; d=json.load(sys.stdin); print(f'    Debtor (DE89..3000):  {d[\"summary\"][\"accountBalance\"]} EUR — {d[\"status\"][\"value\"]}')"
fineract_get "/savingsaccounts/2" | python3 -c "import json,sys; d=json.load(sys.stdin); print(f'    Creditor (FR76..0189): {d[\"summary\"][\"accountBalance\"]} EUR — {d[\"status\"][\"value\"]}')"

TOTAL_TXNS=$(fineract_get "/savingsaccounts/1?associations=transactions" | python3 -c "import json,sys; print(len(json.load(sys.stdin).get('transactions',[])))")
echo "  Total Fineract transactions on account 1: $TOTAL_TXNS"
rt "Multiple real Fineract txns" "$([ "$TOTAL_TXNS" -gt 2 ] && echo true || echo false)" "$TOTAL_TXNS"

echo ""
echo "═══════════════════════════════════════════════════════════════════"
printf "  RESULTS: %d / %d passed" "$PASS" "$TOTAL"
if [ "$FAIL" -gt 0 ]; then
  printf "  (%d FAILED)" "$FAIL"
fi
echo ""
echo "═══════════════════════════════════════════════════════════════════"
exit $FAIL
