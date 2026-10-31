package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AnalyticsEntry
import com.example.data.model.AuditLogEntry
import com.example.data.model.GenerationJob
import com.example.data.model.MediaAsset
import com.example.data.model.Persona
import com.example.data.model.PublishApproval
import com.example.data.model.QuotaBudgetConfig
import com.example.data.model.ScheduledPost
import com.example.data.model.SecurityHashUtil
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PersonaForgeRepository(
    private val db: FirebaseFirestore
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- Real-Time Observation Flows (Aligned with Firestore Security Rules) ---

    fun observePersonas(userId: String): Flow<List<Persona>> {
        val path = "users/$userId/personas"
        return db.collection("users").document(userId).collection("personas")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Persona::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeJobs(userId: String): Flow<List<GenerationJob>> {
        val path = "users/$userId/jobs"
        return db.collection("users").document(userId).collection("jobs")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(GenerationJob::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAssets(userId: String): Flow<List<MediaAsset>> {
        val path = "users/$userId/assets"
        return db.collection("users").document(userId).collection("assets")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MediaAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeSchedules(userId: String): Flow<List<ScheduledPost>> {
        val path = "users/$userId/schedules"
        return db.collection("users").document(userId).collection("schedules")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ScheduledPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.scheduledEpochMillis }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeApprovals(userId: String): Flow<List<PublishApproval>> {
        val path = "users/$userId/approvals"
        return db.collection("users").document(userId).collection("approvals")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(PublishApproval::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAnalytics(userId: String): Flow<List<AnalyticsEntry>> {
        val path = "users/$userId/analytics"
        return db.collection("users").document(userId).collection("analytics")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AnalyticsEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeQuotaConfig(userId: String): Flow<QuotaBudgetConfig?> {
        val path = "users/$userId/quotas/config"
        return db.collection("users").document(userId).collection("quotas").document("config")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(QuotaBudgetConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    fun observeAuditLogs(userId: String): Flow<List<AuditLogEntry>> {
        val path = "users/$userId/auditLogs"
        return db.collection("users").document(userId).collection("auditLogs")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AuditLogEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    // --- Direct Queries & Cross-User Isolation Verification ---

    suspend fun getPersonaById(targetUserId: String, personaId: String): Result<Persona> = runCatching {
        val docRef = db.collection("users").document(targetUserId).collection("personas").document(personaId)
        try {
            val snap = docRef.get().await()
            snap.toObject(Persona::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: throw NoSuchElementException("Persona $personaId not found")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun listPersonas(targetUserId: String): Result<List<Persona>> = runCatching {
        val colRef = db.collection("users").document(targetUserId).collection("personas")
        try {
            val snap = colRef.whereEqualTo("userId", targetUserId).get().await()
            snap.toObjects(Persona::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    // --- Mutations (Persona Vault) ---

    suspend fun createPersonaDraft(
        name: String,
        handle: String,
        facialIdentityDescriptor: String,
        referenceStorageUri: String,
        referenceSha256: String,
        consentSubjectName: String,
        consentVerified: Boolean,
        retentionPolicyDays: Long = 365L,
        status: String = "ACTIVE",
        actorRole: String = "OWNER_ANDROID",
        idempotencyKey: String = "idem_${UUID.randomUUID()}"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        SecurityHashUtil.requireMetadataOnlyUri(referenceStorageUri)
        require(consentVerified) { "Explicit subject consent verification is required." }

        val personaId = "persona_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("personas").document(personaId)
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim(),
            "handle" to if (handle.startsWith("@")) handle.trim() else "@${handle.trim()}",
            "status" to status,
            "profileVersion" to 1L,
            "facialIdentityDescriptor" to facialIdentityDescriptor.trim(),
            "referenceStorageUri" to referenceStorageUri.trim(),
            "referenceSha256" to referenceSha256.trim(),
            "consentSubjectName" to consentSubjectName.trim(),
            "consentVerified" to consentVerified,
            "retentionPolicyDays" to retentionPolicyDays,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            writeAuditLog(
                actorRole = actorRole,
                action = "create_persona_draft",
                targetCollection = "personas",
                targetId = personaId,
                idempotencyKey = idempotencyKey,
                outcome = "ALLOWED",
                details = "Created persona ${name.trim()} (v1)"
            )
            personaId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updatePersonaProfile(
        persona: Persona,
        newName: String,
        newDescriptor: String,
        newStatus: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(persona.id.isNotBlank()) { "Persona ID must not be blank" }
        requireNotNull(persona.createdAt) { "Persona createdAt must not be null" }

        val nextVersion = persona.profileVersion + 1L
        val docRef = db.collection("users").document(uid).collection("personas").document(persona.id)
        try {
            docRef.set(
                persona.copy(
                    name = newName.trim(),
                    facialIdentityDescriptor = newDescriptor.trim(),
                    status = newStatus,
                    profileVersion = nextVersion,
                    updatedAt = null
                )
            ).await()
            writeAuditLog(
                actorRole = "OWNER_ANDROID",
                action = "update_persona",
                targetCollection = "personas",
                targetId = persona.id,
                idempotencyKey = "upd_${persona.id}_v$nextVersion",
                outcome = "ALLOWED",
                details = "Incremented persona profileVersion to v$nextVersion"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deletePersona(personaId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("personas").document(personaId)
        try {
            docRef.delete().await()
            writeAuditLog(
                actorRole = "OWNER_ANDROID",
                action = "delete_persona",
                targetCollection = "personas",
                targetId = personaId,
                idempotencyKey = "del_$personaId",
                outcome = "ALLOWED",
                details = "Purged persona metadata and private vault reference per retention policy"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Quota & Billing Controls ---

    suspend fun ensureOrGetQuotaConfig(): QuotaBudgetConfig {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("quotas").document("config")
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(QuotaBudgetConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: QuotaBudgetConfig(userId = uid)
            } else {
                val initPayload = mapOf(
                    "userId" to uid,
                    "monthlyBudgetLimitMicros" to 25000000L,
                    "currentMonthSpendMicros" to 0L,
                    "dailyGenerationLimit" to 50L,
                    "todayGenerationCount" to 0L,
                    "alertThresholdPercent" to 80L,
                    "falAiEnabled" to false,
                    "replicateEnabled" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(initPayload).await()
                QuotaBudgetConfig(userId = uid)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateQuotaSettings(
        existingConfig: QuotaBudgetConfig,
        monthlyBudgetLimitMicros: Long,
        dailyGenerationLimit: Long,
        falAiEnabled: Boolean,
        replicateEnabled: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("quotas").document("config")
        val current = if (existingConfig.createdAt != null) existingConfig else {
            ensureOrGetQuotaConfig()
            val refreshed = docRef.get().await()
                .toObject(QuotaBudgetConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            checkNotNull(refreshed)
        }
        try {
            docRef.set(
                current.copy(
                    monthlyBudgetLimitMicros = monthlyBudgetLimitMicros,
                    dailyGenerationLimit = dailyGenerationLimit,
                    falAiEnabled = falAiEnabled,
                    replicateEnabled = replicateEnabled,
                    updatedAt = null
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Durable Background Generation Jobs & Idempotent Asset Recording ---

    suspend fun enqueueAndRecordGeneration(
        idempotencyKey: String,
        persona: Persona,
        jobType: String,
        provider: String,
        modelId: String,
        prompt: String,
        emotion: String,
        stylePreset: String,
        estimatedCostMicros: Long,
        outputStorageUri: String,
        outputSha256Hash: String,
        storyboardSummary: String = "",
        actorRole: String = "OWNER_ANDROID"
    ): Result<MediaAsset> = runCatching {
        val uid = requireUserId()
        require(idempotencyKey.length >= 4) { "idempotencyKey must be at least 4 characters" }
        require(persona.consentVerified) { "Persona consent must be verified before generation" }
        SecurityHashUtil.requireMetadataOnlyUri(persona.referenceStorageUri)
        SecurityHashUtil.requireMetadataOnlyUri(outputStorageUri)

        // 1. Idempotency check: if an asset with this idempotencyKey already exists, return it without charging twice!
        val assetsCol = db.collection("users").document(uid).collection("assets")
        val existingQuery = assetsCol
            .whereEqualTo("userId", uid)
            .whereEqualTo("idempotencyKey", idempotencyKey)
            .get()
            .await()
        val existingAsset = existingQuery
            .toObjects(MediaAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .firstOrNull()
        if (existingAsset != null) {
            return@runCatching existingAsset
        }

        // 2. Enforce provider & budget quotas
        val quotaDocRef = db.collection("users").document(uid).collection("quotas").document("config")
        ensureOrGetQuotaConfig()
        val quotaSnap = quotaDocRef.get().await()
        val quota = checkNotNull(
            quotaSnap.toObject(QuotaBudgetConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        )
        if (provider == "FAL_AI" && !quota.falAiEnabled) {
            throw IllegalStateException("fal.ai adapter is disabled by default until configured and tested.")
        }
        if (provider == "REPLICATE" && !quota.replicateEnabled) {
            throw IllegalStateException("Replicate adapter is disabled by default until configured and tested.")
        }
        if (quota.todayGenerationCount >= quota.dailyGenerationLimit) {
            throw IllegalStateException("Daily generation quota (${quota.dailyGenerationLimit}) reached.")
        }
        if (quota.currentMonthSpendMicros + estimatedCostMicros > quota.monthlyBudgetLimitMicros) {
            throw IllegalStateException("Monthly budget cap reached. Generation blocked by cost control gate.")
        }

        val jobId = "job_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val assetId = "asset_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val jobRef = db.collection("users").document(uid).collection("jobs").document(jobId)
        val assetRef = assetsCol.document(assetId)

        val jobPayload = mapOf(
            "userId" to uid,
            "idempotencyKey" to idempotencyKey,
            "personaId" to persona.id,
            "personaVersion" to persona.profileVersion,
            "jobType" to jobType,
            "provider" to provider,
            "modelId" to modelId,
            "prompt" to prompt.trim(),
            "emotion" to emotion.trim(),
            "stylePreset" to stylePreset.trim(),
            "sourceReferenceUri" to persona.referenceStorageUri,
            "estimatedCostMicros" to estimatedCostMicros,
            "actualCostMicros" to estimatedCostMicros,
            "status" to "COMPLETED",
            "retryCount" to 0L,
            "maxRetries" to 3L,
            "outputAssetId" to assetId,
            "errorMessage" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val mediaType = when (jobType) {
            "VIDEO_STORYBOARD" -> "VIDEO_STORYBOARD"
            "VIDEO_SYNTHESIS" -> "VIDEO_CLIP"
            else -> "IMAGE"
        }

        val assetPayload = mapOf(
            "userId" to uid,
            "personaId" to persona.id,
            "personaName" to persona.name,
            "personaVersion" to persona.profileVersion,
            "jobId" to jobId,
            "idempotencyKey" to idempotencyKey,
            "mediaType" to mediaType,
            "storageUri" to outputStorageUri,
            "sha256Hash" to outputSha256Hash,
            "emotion" to emotion.trim(),
            "stylePreset" to stylePreset.trim(),
            "modelProvider" to provider,
            "modelId" to modelId,
            "costMicros" to estimatedCostMicros,
            "sourceReferenceUri" to persona.referenceStorageUri,
            "versionNumber" to 1L,
            "reviewStatus" to "DRAFT",
            "reviewerNotes" to "",
            "storyboardSummary" to storyboardSummary.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            jobRef.set(jobPayload).await()
            assetRef.set(assetPayload).await()
            quotaDocRef.set(
                quota.copy(
                    currentMonthSpendMicros = quota.currentMonthSpendMicros + estimatedCostMicros,
                    todayGenerationCount = quota.todayGenerationCount + 1L,
                    updatedAt = null
                )
            ).await()

            writeAuditLog(
                actorRole = actorRole,
                action = "generate_image_draft",
                targetCollection = "assets",
                targetId = assetId,
                idempotencyKey = idempotencyKey,
                outcome = "ALLOWED",
                details = "Generated $mediaType via $provider ($modelId), cost=${estimatedCostMicros / 10000.0} cents"
            )

            val createdSnap = assetRef.get().await()
            checkNotNull(
                createdSnap.toObject(MediaAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, assetRef.path)
            throw e
        }
    }

    suspend fun reviewAsset(
        asset: MediaAsset,
        reviewStatus: String,
        reviewerNotes: String,
        actorRole: String = "OWNER_ANDROID"
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(reviewStatus in listOf("APPROVED", "REJECTED")) { "Invalid reviewStatus: $reviewStatus" }
        requireNotNull(asset.createdAt) { "Asset createdAt must not be null" }

        val docRef = db.collection("users").document(uid).collection("assets").document(asset.id)
        try {
            docRef.set(
                asset.copy(
                    reviewStatus = reviewStatus,
                    reviewerNotes = reviewerNotes.trim(),
                    updatedAt = null
                )
            ).await()
            writeAuditLog(
                actorRole = actorRole,
                action = "review_asset",
                targetCollection = "assets",
                targetId = asset.id,
                idempotencyKey = "rev_${asset.id}_$reviewStatus",
                outcome = "ALLOWED",
                details = "Marked asset ${asset.id} as $reviewStatus"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- Scheduler & Two-Step Cryptographically Bound Publish Approvals ---

    suspend fun prepareContentSchedule(
        asset: MediaAsset,
        destinationPlatform: String,
        scheduledEpochMillis: Long,
        caption: String,
        actorRole: String = "OWNER_ANDROID"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(asset.reviewStatus == "APPROVED") {
            "Only APPROVED assets can be scheduled for publication (current status: ${asset.reviewStatus})."
        }

        val scheduleId = "sched_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("schedules").document(scheduleId)
        val payload = mapOf(
            "userId" to uid,
            "assetId" to asset.id,
            "personaId" to asset.personaId,
            "personaName" to asset.personaName,
            "destinationPlatform" to destinationPlatform.trim(),
            "caption" to caption.trim(),
            "scheduledEpochMillis" to scheduledEpochMillis,
            "status" to "DRAFT",
            "activeApprovalId" to "",
            "bindingHash" to "",
            "lastPublishIdempotencyKey" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            writeAuditLog(
                actorRole = actorRole,
                action = "prepare_content_schedule",
                targetCollection = "schedules",
                targetId = scheduleId,
                idempotencyKey = "prep_$scheduleId",
                outcome = "ALLOWED",
                details = "Prepared draft schedule for ${destinationPlatform.trim()}"
            )
            scheduleId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun requestPublishApproval(
        schedule: ScheduledPost,
        requestedByRole: String = "CHATGPT_MCP"
    ): Result<PublishApproval> = runCatching {
        val uid = requireUserId()
        requireNotNull(schedule.createdAt) { "Schedule createdAt must not be null" }

        val bindingHash = SecurityHashUtil.computePublishBindingHash(
            assetId = schedule.assetId,
            destinationPlatform = schedule.destinationPlatform,
            scheduledEpochMillis = schedule.scheduledEpochMillis,
            caption = schedule.caption
        )
        val approvalId = "appr_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val expiresAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L

        val approvalRef = db.collection("users").document(uid).collection("approvals").document(approvalId)
        val scheduleRef = db.collection("users").document(uid).collection("schedules").document(schedule.id)

        val approvalPayload = mapOf(
            "userId" to uid,
            "scheduleId" to schedule.id,
            "assetId" to schedule.assetId,
            "destinationPlatform" to schedule.destinationPlatform,
            "scheduledEpochMillis" to schedule.scheduledEpochMillis,
            "caption" to schedule.caption,
            "bindingHash" to bindingHash,
            "requestedByRole" to requestedByRole,
            "status" to "PENDING",
            "decisionNotes" to "Pending explicit owner verification",
            "expiresAtEpochMillis" to expiresAt,
            "consumedByIdempotencyKey" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            approvalRef.set(approvalPayload).await()
            scheduleRef.set(
                schedule.copy(
                    status = "PENDING_APPROVAL",
                    activeApprovalId = approvalId,
                    bindingHash = bindingHash,
                    updatedAt = null
                )
            ).await()
            writeAuditLog(
                actorRole = requestedByRole,
                action = "request_publish_approval",
                targetCollection = "approvals",
                targetId = approvalId,
                idempotencyKey = "req_$approvalId",
                outcome = "ALLOWED",
                details = "Bound approval request to hash ${bindingHash.take(12)}..."
            )
            val snap = approvalRef.get().await()
            checkNotNull(
                snap.toObject(PublishApproval::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, approvalRef.path)
            throw e
        }
    }

    suspend fun decidePublishApprovalByOwner(
        approval: PublishApproval,
        decision: String, // APPROVED | REJECTED
        decisionNotes: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(decision in listOf("APPROVED", "REJECTED")) { "Decision must be APPROVED or REJECTED" }
        require(approval.status == "PENDING") {
            "Approval in terminal/non-pending state (${approval.status}) cannot be modified."
        }
        requireNotNull(approval.createdAt) { "Approval createdAt must not be null" }

        val approvalRef = db.collection("users").document(uid).collection("approvals").document(approval.id)
        val scheduleRef = db.collection("users").document(uid).collection("schedules").document(approval.scheduleId)

        try {
            approvalRef.set(
                approval.copy(
                    status = decision,
                    decisionNotes = decisionNotes.trim(),
                    updatedAt = null
                )
            ).await()

            val schedSnap = scheduleRef.get().await()
            val sched = schedSnap.toObject(ScheduledPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (sched != null && sched.createdAt != null) {
                scheduleRef.set(
                    sched.copy(
                        status = decision,
                        updatedAt = null
                    )
                ).await()
            }

            writeAuditLog(
                actorRole = "OWNER_ANDROID",
                action = "owner_${decision.lowercase()}_publish",
                targetCollection = "approvals",
                targetId = approval.id,
                idempotencyKey = "dec_${approval.id}_$decision",
                outcome = "ALLOWED",
                details = "Owner marked publish request $decision"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, approvalRef.path)
            throw e
        }
    }

    suspend fun publishApprovedPost(
        idempotencyKey: String,
        approvalId: String,
        scheduleId: String,
        assetId: String,
        destinationPlatform: String,
        scheduledEpochMillis: Long,
        caption: String,
        actorRole: String = "CHATGPT_MCP"
    ): Result<ScheduledPost> = runCatching {
        val uid = requireUserId()
        require(idempotencyKey.length >= 4) { "idempotencyKey required for publish_approved_post" }

        val scheduleRef = db.collection("users").document(uid).collection("schedules").document(scheduleId)
        val approvalRef = db.collection("users").document(uid).collection("approvals").document(approvalId)

        val schedSnap = scheduleRef.get().await()
        val schedule = schedSnap.toObject(ScheduledPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: throw NoSuchElementException("Scheduled post $scheduleId not found")

        // Idempotency deduplication: if already published with this exact idempotencyKey, return without re-publishing
        if (schedule.status == "PUBLISHED" && schedule.lastPublishIdempotencyKey == idempotencyKey) {
            return@runCatching schedule
        }

        val apprSnap = approvalRef.get().await()
        val approval = apprSnap.toObject(PublishApproval::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: throw IllegalStateException("APPROVAL_REQUIRED: PublishApproval $approvalId not found")

        if (approval.status == "CONSUMED") {
            throw IllegalStateException("APPROVAL_ALREADY_CONSUMED: Approval token was already used")
        }
        if (approval.status == "REJECTED") {
            throw IllegalStateException("APPROVAL_REJECTED: Rejected approvals cannot be reused")
        }
        if (approval.status == "EXPIRED" || System.currentTimeMillis() > approval.expiresAtEpochMillis) {
            throw IllegalStateException("APPROVAL_EXPIRED: Approval token has expired")
        }
        if (approval.status != "APPROVED") {
            throw IllegalStateException("APPROVAL_NOT_GRANTED: Approval status is ${approval.status}")
        }

        val expectedBindingHash = SecurityHashUtil.computePublishBindingHash(
            assetId = assetId,
            destinationPlatform = destinationPlatform,
            scheduledEpochMillis = scheduledEpochMillis,
            caption = caption
        )
        if (expectedBindingHash != approval.bindingHash) {
            writeAuditLog(
                actorRole = actorRole,
                action = "publish_approved_post",
                targetCollection = "approvals",
                targetId = approval.id,
                idempotencyKey = idempotencyKey,
                outcome = "DENIED",
                details = "Rejected publish: parameter tampering detected (bindingHash mismatch)"
            )
            throw IllegalStateException("APPROVAL_BINDING_MISMATCH: Parameters do not match approved asset, destination, time, or caption")
        }

        try {
            approvalRef.set(
                approval.copy(
                    status = "CONSUMED",
                    consumedByIdempotencyKey = idempotencyKey,
                    decisionNotes = "${approval.decisionNotes} | Consumed by $idempotencyKey",
                    updatedAt = null
                )
            ).await()

            scheduleRef.set(
                schedule.copy(
                    status = "PUBLISHED",
                    lastPublishIdempotencyKey = idempotencyKey,
                    updatedAt = null
                )
            ).await()

            writeAuditLog(
                actorRole = actorRole,
                action = "publish_approved_post",
                targetCollection = "schedules",
                targetId = schedule.id,
                idempotencyKey = idempotencyKey,
                outcome = "ALLOWED",
                details = "Published post ${schedule.id} to $destinationPlatform with verified approval ${approval.id}"
            )

            val updatedSnap = scheduleRef.get().await()
            checkNotNull(
                updatedSnap.toObject(ScheduledPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, scheduleRef.path)
            throw e
        }
    }

    // --- Segmented Analytics ---

    suspend fun recordAnalyticsEntry(
        assetId: String,
        personaId: String,
        platformName: String,
        sourceProvenance: String, // VERIFIED_PLATFORM | IMPORTED_DATASET | MANUAL_ENTRY
        impressions: Long,
        engagements: Long,
        conversions: Long,
        revenueMicros: Long
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(sourceProvenance in listOf("VERIFIED_PLATFORM", "IMPORTED_DATASET", "MANUAL_ENTRY")) {
            "Invalid sourceProvenance: $sourceProvenance"
        }
        val entryId = "stat_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("analytics").document(entryId)
        val payload = mapOf(
            "userId" to uid,
            "assetId" to assetId,
            "personaId" to personaId,
            "platformName" to platformName.trim(),
            "sourceProvenance" to sourceProvenance,
            "impressions" to impressions,
            "engagements" to engagements,
            "conversions" to conversions,
            "revenueMicros" to revenueMicros,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            entryId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    // --- Append-Only Audit Log ---

    suspend fun writeAuditLog(
        actorRole: String,
        action: String,
        targetCollection: String,
        targetId: String,
        idempotencyKey: String,
        outcome: String,
        details: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("auditLogs").document(logId)
        val payload = mapOf(
            "userId" to uid,
            "actorRole" to actorRole,
            "action" to action,
            "targetCollection" to targetCollection,
            "targetId" to targetId,
            "idempotencyKey" to idempotencyKey,
            "outcome" to outcome,
            "details" to details.take(1900),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
        }
    }
}
