package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

@IgnoreExtraProperties
data class Persona(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val handle: String = "",
    val status: String = "DRAFT", // DRAFT | ACTIVE | ARCHIVED
    val profileVersion: Long = 1L,
    val facialIdentityDescriptor: String = "",
    val referenceStorageUri: String = "", // vault:// or gs:// (never binary data)
    val referenceSha256: String = "",
    val consentSubjectName: String = "",
    val consentVerified: Boolean = false,
    val retentionPolicyDays: Long = 365L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GenerationJob(
    @DocumentId val id: String = "",
    val userId: String = "",
    val idempotencyKey: String = "",
    val personaId: String = "",
    val personaVersion: Long = 1L,
    val jobType: String = "IMAGE_DRAFT", // IMAGE_DRAFT | VIDEO_STORYBOARD | VIDEO_SYNTHESIS
    val provider: String = "FIREBASE_AI_LOGIC", // FIREBASE_AI_LOGIC | FAL_AI | REPLICATE
    val modelId: String = "gemini-nano-banana-2.1",
    val prompt: String = "",
    val emotion: String = "",
    val stylePreset: String = "",
    val sourceReferenceUri: String = "",
    val estimatedCostMicros: Long = 40000L,
    val actualCostMicros: Long = 40000L,
    val status: String = "QUEUED", // QUEUED | PROCESSING | COMPLETED | FAILED_RETRYABLE | FAILED_FINAL
    val retryCount: Long = 0L,
    val maxRetries: Long = 3L,
    val outputAssetId: String = "",
    val errorMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MediaAsset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val personaId: String = "",
    val personaName: String = "",
    val personaVersion: Long = 1L,
    val jobId: String = "",
    val idempotencyKey: String = "",
    val mediaType: String = "IMAGE", // IMAGE | VIDEO_STORYBOARD | VIDEO_CLIP
    val storageUri: String = "", // vault:// or gs:// (never binary data)
    val sha256Hash: String = "",
    val emotion: String = "",
    val stylePreset: String = "",
    val modelProvider: String = "FIREBASE_AI_LOGIC",
    val modelId: String = "gemini-nano-banana-2.1",
    val costMicros: Long = 40000L,
    val sourceReferenceUri: String = "",
    val versionNumber: Long = 1L,
    val reviewStatus: String = "DRAFT", // DRAFT | APPROVED | REJECTED
    val reviewerNotes: String = "",
    val storyboardSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ScheduledPost(
    @DocumentId val id: String = "",
    val userId: String = "",
    val assetId: String = "",
    val personaId: String = "",
    val personaName: String = "",
    val destinationPlatform: String = "",
    val caption: String = "",
    val scheduledEpochMillis: Long = 0L,
    val status: String = "DRAFT", // DRAFT | PENDING_APPROVAL | APPROVED | PUBLISHED | REJECTED | EXPIRED
    val activeApprovalId: String = "",
    val bindingHash: String = "",
    val lastPublishIdempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PublishApproval(
    @DocumentId val id: String = "",
    val userId: String = "",
    val scheduleId: String = "",
    val assetId: String = "",
    val destinationPlatform: String = "",
    val scheduledEpochMillis: Long = 0L,
    val caption: String = "",
    val bindingHash: String = "",
    val requestedByRole: String = "OWNER_ANDROID", // OWNER_ANDROID | CHATGPT_MCP
    val status: String = "PENDING", // PENDING | APPROVED | REJECTED | EXPIRED | CONSUMED
    val decisionNotes: String = "",
    val expiresAtEpochMillis: Long = 0L,
    val consumedByIdempotencyKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AnalyticsEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val assetId: String = "",
    val personaId: String = "",
    val platformName: String = "",
    val sourceProvenance: String = "VERIFIED_PLATFORM", // VERIFIED_PLATFORM | IMPORTED_DATASET | MANUAL_ENTRY
    val impressions: Long = 0L,
    val engagements: Long = 0L,
    val conversions: Long = 0L,
    val revenueMicros: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class QuotaBudgetConfig(
    @DocumentId val id: String = "config",
    val userId: String = "",
    val monthlyBudgetLimitMicros: Long = 25000000L, // $25.00
    val currentMonthSpendMicros: Long = 0L,
    val dailyGenerationLimit: Long = 50L,
    val todayGenerationCount: Long = 0L,
    val alertThresholdPercent: Long = 80L,
    val falAiEnabled: Boolean = false,
    val replicateEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLogEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actorRole: String = "OWNER_ANDROID", // OWNER_ANDROID | CHATGPT_MCP
    val action: String = "",
    val targetCollection: String = "",
    val targetId: String = "",
    val idempotencyKey: String = "",
    val outcome: String = "ALLOWED", // ALLOWED | DENIED | ERROR
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

object SecurityHashUtil {
    private val ALLOWED_PROVIDER_HOSTS = setOf(
        "queue.fal.run",
        "fal.run",
        "api.replicate.com",
        "firebasevertexai.googleapis.com"
    )

    fun sha256Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    /**
     * Deterministic binding hash tying exact (assetId, destinationPlatform, scheduledEpochMillis, caption).
     * Matches `computePublishBindingHash` in the Node.js/TypeScript MCP Server.
     */
    fun computePublishBindingHash(
        assetId: String,
        destinationPlatform: String,
        scheduledEpochMillis: Long,
        caption: String
    ): String {
        val canonical = "${assetId.trim()}|${destinationPlatform.trim()}|$scheduledEpochMillis|${caption.trim()}"
        return sha256Hex(canonical)
    }

    /**
     * Validates that a storage URI is metadata-only and never contains raw binary/base64 data.
     */
    fun requireMetadataOnlyUri(uri: String) {
        require(uri.length in 7..512) {
            "Storage URI must be between 7 and 512 characters."
        }
        require(!uri.startsWith("data:") && !uri.contains(";base64,")) {
            "Binary/Base64 blobs are strictly prohibited in Firestore documents."
        }
        require(
            uri.startsWith("gs://") ||
                uri.startsWith("vault://") ||
                uri.startsWith("file://") ||
                uri.startsWith("content://")
        ) {
            "Storage URI must use gs://, vault://, file://, or content:// scheme."
        }
    }

    /**
     * SSRF Protection for external provider endpoints.
     */
    fun validateProviderUrl(rawUrl: String): String {
        val uri = java.net.URI(rawUrl.trim())
        require(uri.scheme?.lowercase() == "https") {
            "SSRF Blocked: Only HTTPS provider endpoints are permitted."
        }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("SSRF Blocked: Missing host")
        require(
            host != "localhost" &&
                host != "127.0.0.1" &&
                !host.startsWith("10.") &&
                !host.startsWith("192.168.") &&
                !host.startsWith("169.254.") &&
                !host.endsWith(".internal")
        ) {
            "SSRF Blocked: Private/loopback/metadata host '$host' is forbidden."
        }
        require(ALLOWED_PROVIDER_HOSTS.contains(host)) {
            "SSRF Blocked: Host '$host' is not in the verified provider allowlist."
        }
        return uri.toString()
    }
}
