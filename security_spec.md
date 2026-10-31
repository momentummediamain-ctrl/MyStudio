# PersonaForge Studio — Security Specification & Threat Model (`security_spec.md`)

## 1. Data & Security Invariants

1. **Tenant Isolation Invariant**: Every collection resides under `/users/{userId}/...` and requires `request.auth != null && request.auth.uid == userId`. Furthermore, every document's `userId` field must equal `request.auth.uid` on creation, remain immutable on update, and be verified in `allow list` (`resource.data.userId == request.auth.uid`).
2. **Metadata-Only Media Invariant (No Binary in Firestore)**: `referenceStorageUri`, `sourceReferenceUri`, and `storageUri` fields are strictly bounded to `<= 512` characters and must start with `gs://`, `file://`, `content://`, or `vault://`—rejecting raw base64/binary payloads (`data:image/...` or megabyte strings).
3. **Traceable Provenance & Cost Invariant**: Every `GenerationJob` and `MediaAsset` must record `idempotencyKey`, `modelId`, `estimatedCostMicros`/`costMicros` (`>= 0`), and `sourceReferenceUri`.
4. **Two-Step Publish Approval Binding Invariant**: `PublishApproval` records bind `(assetId, destinationPlatform, scheduledEpochMillis, caption)` via a deterministic 64-character hex `bindingHash`. Rejected (`REJECTED`), expired (`EXPIRED`), or already consumed (`CONSUMED`) approvals are terminal and cannot transition back to `APPROVED`.
5. **Append-Only Audit Trail Invariant**: `AuditLogEntry` documents under `/users/{userId}/auditLogs/{logId}` allow `create` and `read`, but strictly forbid `update` and `delete`.

## 2. The "Dirty Dozen" Adversarial Payloads

1. **Unauthenticated Read/Write**: Caller with `request.auth == null` attempting to list `/users/alice_123/personas`.
2. **Cross-Tenant IDOR Read/Write**: Authenticated `bob_456` attempting to read or mutate `/users/alice_123/assets/asset_1`.
3. **Identity Spoofing on Create**: Authenticated `alice_123` writing to `/users/alice_123/personas/p1` with `userId: "bob_456"`.
4. **Ghost Field / Shadow Update**: Authenticated owner sending an undeclared field `isAdminBypass: true` on `Persona` update.
5. **Binary Media Blob Injection**: Authenticated owner attempting to store a 50KB base64 image string inside `MediaAsset.storageUri`.
6. **Immutable Field Mutation**: Authenticated owner attempting to alter `createdAt` or `userId` on an existing `MediaAsset`.
7. **Invalid Enum Injection**: Authenticated owner setting `reviewStatus: "AUTO_BYPASS"` on `MediaAsset`.
8. **Negative Cost / Quota Manipulation**: Authenticated owner creating a `GenerationJob` with `estimatedCostMicros: -5000`.
9. **Approval Replay / Terminal State Reversal**: Attempting to update a `PublishApproval` whose existing status is `REJECTED` or `CONSUMED` back to `APPROVED`.
10. **Audit Log Tampering**: Authenticated owner attempting to `update` or `delete` an `AuditLogEntry`.
11. **Unfiltered Collection Scraping**: Querying `collection("personas")` without aligning with tenant ownership.
12. **Document ID Poisoning**: Creating a document with an invalid or oversized ID (`> 128` chars).
