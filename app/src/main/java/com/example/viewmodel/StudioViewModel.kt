package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.PersonaForgeAiService
import com.example.ai.StoryboardScene
import com.example.data.model.AnalyticsEntry
import com.example.data.model.AuditLogEntry
import com.example.data.model.GenerationJob
import com.example.data.model.MediaAsset
import com.example.data.model.Persona
import com.example.data.model.PublishApproval
import com.example.data.model.QuotaBudgetConfig
import com.example.data.model.ScheduledPost
import com.example.data.model.SecurityHashUtil
import com.example.data.repository.PersonaForgeRepository
import com.example.data.storage.PrivateMediaVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface SynthesisOperationState {
    data object Idle : SynthesisOperationState
    data class Running(val stepLabel: String) : SynthesisOperationState
    data class Completed(val message: String, val generatedAssetId: String? = null) : SynthesisOperationState
    data class Failed(val errorMessage: String) : SynthesisOperationState
}

class StudioViewModel(
    private val repository: PersonaForgeRepository,
    private val mediaVault: PrivateMediaVault,
    private val aiService: PersonaForgeAiService,
    val currentUserId: String
) : ViewModel() {

    // Two-tier Flow error handling per firebase-firestore-android
    val personasState: StateFlow<UiState<List<Persona>>> = repository.observePersonas(currentUserId)
        .map<List<Persona>, UiState<List<Persona>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing personas", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to observe personas"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val jobsState: StateFlow<UiState<List<GenerationJob>>> = repository.observeJobs(currentUserId)
        .map<List<GenerationJob>, UiState<List<GenerationJob>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing jobs", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to observe jobs"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val assetsState: StateFlow<UiState<List<MediaAsset>>> = repository.observeAssets(currentUserId)
        .map<List<MediaAsset>, UiState<List<MediaAsset>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing assets", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to observe assets"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val schedulesState: StateFlow<UiState<List<ScheduledPost>>> = repository.observeSchedules(currentUserId)
        .map<List<ScheduledPost>, UiState<List<ScheduledPost>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing schedules", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to observe schedules"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val approvalsState: StateFlow<UiState<List<PublishApproval>>> = repository.observeApprovals(currentUserId)
        .map<List<PublishApproval>, UiState<List<PublishApproval>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing approvals", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to observe approvals"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val analyticsState: StateFlow<UiState<List<AnalyticsEntry>>> = repository.observeAnalytics(currentUserId)
        .map<List<AnalyticsEntry>, UiState<List<AnalyticsEntry>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing analytics", e)
            emit(UiState.Error(e.localizedMessage ?: "Failed to observe analytics"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val quotaState: StateFlow<QuotaBudgetConfig> = repository.observeQuotaConfig(currentUserId)
        .map { it ?: QuotaBudgetConfig(userId = currentUserId) }
        .catch { emit(QuotaBudgetConfig(userId = currentUserId)) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            QuotaBudgetConfig(userId = currentUserId)
        )

    val auditLogsState: StateFlow<List<AuditLogEntry>> = repository.observeAuditLogs(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _synthesisState = MutableStateFlow<SynthesisOperationState>(SynthesisOperationState.Idle)
    val synthesisState: StateFlow<SynthesisOperationState> = _synthesisState.asStateFlow()

    private val _extractedDescriptor = MutableStateFlow("")
    val extractedDescriptor: StateFlow<String> = _extractedDescriptor.asStateFlow()

    private val _storyboardScenes = MutableStateFlow<List<StoryboardScene>>(emptyList())
    val storyboardScenes: StateFlow<List<StoryboardScene>> = _storyboardScenes.asStateFlow()

    private val _mcpConsoleOutput = MutableStateFlow(
        "// Remote MCP Server Inspector Ready\n// Select any of the 13 authenticated MCP tools to execute against your live tenant state."
    )
    val mcpConsoleOutput: StateFlow<String> = _mcpConsoleOutput.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repository.ensureOrGetQuotaConfig() }
        }
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun resetSynthesisState() {
        _synthesisState.value = SynthesisOperationState.Idle
    }

    fun setExtractedDescriptorText(value: String) {
        _extractedDescriptor.value = value
    }

    fun loadVaultBitmap(storageUri: String): Bitmap? {
        return mediaVault.loadBitmapFromVault(currentUserId, storageUri)
    }

    fun analyzeReferencePhotoWithAi(referenceBitmap: Bitmap?) {
        viewModelScope.launch {
            _synthesisState.value = SynthesisOperationState.Running("Analyzing facial geometry with Gemini 3.8 Flash...")
            val result = aiService.extractFacialDnaFromReference(referenceBitmap)
            result.fold(
                onSuccess = { extracted ->
                    val compact = extracted.toCompactDescriptor()
                    _extractedDescriptor.value = compact
                    _synthesisState.value = SynthesisOperationState.Completed("Facial identity lock extracted.")
                },
                onFailure = { err ->
                    _synthesisState.value = SynthesisOperationState.Failed(
                        err.localizedMessage ?: "Facial DNA analysis failed."
                    )
                }
            )
        }
    }

    fun createPersona(
        name: String,
        handle: String,
        facialDescriptor: String,
        consentSubjectName: String,
        consentVerified: Boolean,
        retentionDays: Long,
        referenceBitmap: Bitmap?
    ) {
        viewModelScope.launch {
            if (!consentVerified) {
                _bannerMessage.value = "Consent attestation is required before saving a persona."
                return@launch
            }
            val fileId = "ref_${UUID.randomUUID().toString().replace("-", "").take(10)}"
            val storedRef = if (referenceBitmap != null) {
                mediaVault.saveBitmapToVault(currentUserId, "refs", fileId, referenceBitmap)
            } else {
                val hash = SecurityHashUtil.sha256Hex("$currentUserId:$name:$facialDescriptor")
                PrivateMediaVault.StoredMediaRef(
                    storageUri = "vault://users/$currentUserId/refs/$fileId.jpg",
                    sha256Hash = hash,
                    byteSize = 0L
                )
            }

            val res = repository.createPersonaDraft(
                name = name,
                handle = handle,
                facialIdentityDescriptor = facialDescriptor,
                referenceStorageUri = storedRef.storageUri,
                referenceSha256 = storedRef.sha256Hash,
                consentSubjectName = consentSubjectName,
                consentVerified = consentVerified,
                retentionPolicyDays = retentionDays,
                status = "ACTIVE"
            )
            res.fold(
                onSuccess = { id ->
                    _bannerMessage.value = "Persona '$name' created (ID: $id, Profile v1)."
                    _extractedDescriptor.value = ""
                },
                onFailure = { err ->
                    _bannerMessage.value = "Failed to create persona: ${err.localizedMessage}"
                }
            )
        }
    }

    fun updatePersonaVersion(persona: Persona, newName: String, newDescriptor: String, newStatus: String) {
        viewModelScope.launch {
            repository.updatePersonaProfile(persona, newName, newDescriptor, newStatus).fold(
                onSuccess = {
                    _bannerMessage.value = "Updated ${persona.name} to profileVersion v${persona.profileVersion + 1}."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Update failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun deletePersonaWithRetentionPurge(persona: Persona) {
        viewModelScope.launch {
            mediaVault.purgeVaultUri(currentUserId, persona.referenceStorageUri)
            repository.deletePersona(persona.id).fold(
                onSuccess = {
                    _bannerMessage.value = "Deleted ${persona.name} and purged private vault media."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Delete failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun estimateGenerationCostMicros(provider: String, modelId: String, isVideo: Boolean = false): Long {
        if (isVideo) return 350_000L // $0.35
        return when {
            provider == "FAL_AI" -> 65_000L
            provider == "REPLICATE" -> 55_000L
            modelId == "gemini-3-pro-image" -> 80_000L
            else -> 40_000L
        }
    }

    fun generateExpressionImage(
        persona: Persona,
        emotion: String,
        emotionIntensity: Int,
        stylePreset: String,
        scenePrompt: String,
        provider: String,
        modelId: String,
        customIdempotencyKey: String? = null
    ) {
        viewModelScope.launch {
            val idemKey = customIdempotencyKey?.takeIf { it.isNotBlank() }
                ?: "idem_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val costMicros = estimateGenerationCostMicros(provider, modelId, isVideo = false)
            val quota = quotaState.value

            if (provider == "FAL_AI" && !quota.falAiEnabled) {
                _synthesisState.value = SynthesisOperationState.Failed(
                    "fal.ai backend adapter is DISABLED by default. Enable it in MCP & Ops after configuring Cloud Run secrets."
                )
                return@launch
            }
            if (provider == "REPLICATE" && !quota.replicateEnabled) {
                _synthesisState.value = SynthesisOperationState.Failed(
                    "Replicate backend adapter is DISABLED by default. Enable it in MCP & Ops after configuring Cloud Run secrets."
                )
                return@launch
            }
            if (quota.todayGenerationCount >= quota.dailyGenerationLimit) {
                _synthesisState.value = SynthesisOperationState.Failed(
                    "Daily generation quota (${quota.dailyGenerationLimit}) reached."
                )
                return@launch
            }
            if (quota.currentMonthSpendMicros + costMicros > quota.monthlyBudgetLimitMicros) {
                _synthesisState.value = SynthesisOperationState.Failed(
                    "Monthly budget cap reached. Generation blocked by cost control gate."
                )
                return@launch
            }

            _synthesisState.value = SynthesisOperationState.Running(
                "Synthesizing '$emotion' ($emotionIntensity%) for ${persona.name} via $modelId (may take 30-60s)..."
            )

            val refBitmap = mediaVault.loadBitmapFromVault(currentUserId, persona.referenceStorageUri)
            val imageResult = aiService.generateConsistentCharacterImage(
                modelId = modelId,
                referenceBitmap = refBitmap,
                personaName = persona.name,
                facialDescriptor = persona.facialIdentityDescriptor,
                emotion = emotion,
                emotionIntensity = emotionIntensity,
                stylePreset = stylePreset,
                scenePrompt = scenePrompt
            )

            imageResult.fold(
                onSuccess = { bitmap ->
                    val fileId = "gen_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                    val stored = mediaVault.saveBitmapToVault(currentUserId, "assets", fileId, bitmap)
                    val recordResult = repository.enqueueAndRecordGeneration(
                        idempotencyKey = idemKey,
                        persona = persona,
                        jobType = "IMAGE_DRAFT",
                        provider = provider,
                        modelId = modelId,
                        prompt = scenePrompt,
                        emotion = "$emotion ($emotionIntensity%)",
                        stylePreset = stylePreset,
                        estimatedCostMicros = costMicros,
                        outputStorageUri = stored.storageUri,
                        outputSha256Hash = stored.sha256Hash
                    )
                    recordResult.fold(
                        onSuccess = { asset ->
                            _synthesisState.value = SynthesisOperationState.Completed(
                                message = "Draft asset ${asset.id} saved to private vault. Review to approve or reject.",
                                generatedAssetId = asset.id
                            )
                        },
                        onFailure = { err ->
                            _synthesisState.value = SynthesisOperationState.Failed(
                                err.localizedMessage ?: "Failed to record asset metadata."
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _synthesisState.value = SynthesisOperationState.Failed(
                        err.localizedMessage ?: "Image generation failed."
                    )
                }
            )
        }
    }

    fun generateVideoStoryboardFirst(
        persona: Persona,
        emotionArc: String,
        stylePreset: String,
        conceptPrompt: String
    ) {
        viewModelScope.launch {
            _synthesisState.value = SynthesisOperationState.Running(
                "Generating 4-scene storyboard for ${persona.name} with Gemini 3.8 Flash..."
            )
            val result = aiService.generateVideoStoryboard(
                personaName = persona.name,
                facialDescriptor = persona.facialIdentityDescriptor,
                emotionArc = emotionArc,
                stylePreset = stylePreset,
                conceptPrompt = conceptPrompt
            )
            result.fold(
                onSuccess = { scenes ->
                    _storyboardScenes.value = scenes
                    val summary = scenes.joinToString("\n") {
                        "Scene ${it.sceneNumber} [${it.shotType} | ${it.cameraMovement} | ${it.emotionBeat}]: ${it.visualPrompt}"
                    }
                    val hash = SecurityHashUtil.sha256Hex(summary)
                    val idemKey = "idem_sb_${UUID.randomUUID().toString().replace("-", "").take(10)}"
                    repository.enqueueAndRecordGeneration(
                        idempotencyKey = idemKey,
                        persona = persona,
                        jobType = "VIDEO_STORYBOARD",
                        provider = "FIREBASE_AI_LOGIC",
                        modelId = "gemini-3.8-flash",
                        prompt = conceptPrompt,
                        emotion = emotionArc,
                        stylePreset = stylePreset,
                        estimatedCostMicros = 20_000L,
                        outputStorageUri = "vault://users/$currentUserId/storyboards/$idemKey.json",
                        outputSha256Hash = hash,
                        storyboardSummary = summary
                    )
                    _synthesisState.value = SynthesisOperationState.Completed(
                        "4-Scene Video Storyboard generated and recorded in Asset Library."
                    )
                },
                onFailure = { err ->
                    _synthesisState.value = SynthesisOperationState.Failed(
                        err.localizedMessage ?: "Storyboard generation failed."
                    )
                }
            )
        }
    }

    fun reviewAssetDecision(asset: MediaAsset, reviewStatus: String, notes: String) {
        viewModelScope.launch {
            repository.reviewAsset(asset, reviewStatus, notes).fold(
                onSuccess = {
                    _bannerMessage.value = "Asset ${asset.id} marked $reviewStatus."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Review failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun createScheduleDraft(
        asset: MediaAsset,
        destinationPlatform: String,
        scheduledEpochMillis: Long,
        caption: String
    ) {
        viewModelScope.launch {
            repository.prepareContentSchedule(asset, destinationPlatform, scheduledEpochMillis, caption).fold(
                onSuccess = { scheduleId ->
                    _bannerMessage.value = "Draft schedule $scheduleId created. Request owner approval before publishing."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Schedule rejected: ${err.localizedMessage}"
                }
            )
        }
    }

    fun requestApprovalForSchedule(schedule: ScheduledPost, requestedByRole: String = "OWNER_ANDROID") {
        viewModelScope.launch {
            repository.requestPublishApproval(schedule, requestedByRole).fold(
                onSuccess = { approval ->
                    _bannerMessage.value =
                        "Approval ${approval.id} created (SHA-256 Binding: ${approval.bindingHash.take(12)}...)."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Approval request failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun ownerDecideApproval(approval: PublishApproval, decision: String, notes: String) {
        viewModelScope.launch {
            repository.decidePublishApprovalByOwner(approval, decision, notes).fold(
                onSuccess = {
                    _bannerMessage.value = "Approval ${approval.id} marked $decision by owner."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Decision failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun executeApprovedPublish(
        schedule: ScheduledPost,
        overrideCaptionForTamperTest: String? = null
    ) {
        viewModelScope.launch {
            val captionToUse = overrideCaptionForTamperTest ?: schedule.caption
            val idemKey = "idem_pub_${schedule.id}_${schedule.activeApprovalId}"
            repository.publishApprovedPost(
                idempotencyKey = idemKey,
                approvalId = schedule.activeApprovalId,
                scheduleId = schedule.id,
                assetId = schedule.assetId,
                destinationPlatform = schedule.destinationPlatform,
                scheduledEpochMillis = schedule.scheduledEpochMillis,
                caption = captionToUse,
                actorRole = "OWNER_ANDROID"
            ).fold(
                onSuccess = {
                    _bannerMessage.value = "Published ${schedule.id} to ${schedule.destinationPlatform}!"
                },
                onFailure = { err ->
                    _bannerMessage.value = "Publish Blocked: ${err.localizedMessage}"
                }
            )
        }
    }

    fun addAnalyticsRecord(
        assetId: String,
        personaId: String,
        platformName: String,
        sourceProvenance: String,
        impressions: Long,
        engagements: Long,
        conversions: Long,
        revenueMicros: Long
    ) {
        viewModelScope.launch {
            repository.recordAnalyticsEntry(
                assetId = assetId,
                personaId = personaId,
                platformName = platformName,
                sourceProvenance = sourceProvenance,
                impressions = impressions,
                engagements = engagements,
                conversions = conversions,
                revenueMicros = revenueMicros
            ).fold(
                onSuccess = {
                    _bannerMessage.value = "Recorded $sourceProvenance metrics for $platformName."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Failed to record analytics: ${err.localizedMessage}"
                }
            )
        }
    }

    fun updateQuotaAndProviderConfig(
        monthlyBudgetDollars: Double,
        dailyLimit: Long,
        falEnabled: Boolean,
        replicateEnabled: Boolean
    ) {
        viewModelScope.launch {
            val micros = (monthlyBudgetDollars * 1_000_000L).toLong().coerceAtLeast(1_000_000L)
            repository.updateQuotaSettings(
                existingConfig = quotaState.value,
                monthlyBudgetLimitMicros = micros,
                dailyGenerationLimit = dailyLimit.coerceAtLeast(1L),
                falAiEnabled = falEnabled,
                replicateEnabled = replicateEnabled
            ).fold(
                onSuccess = {
                    _bannerMessage.value = "Updated budget & provider configuration."
                },
                onFailure = { err ->
                    _bannerMessage.value = "Failed to update quota settings: ${err.localizedMessage}"
                }
            )
        }
    }

    /**
     * Interactive ChatGPT MCP Plugin Simulator & Verifier.
     * Exercises the exact 13 MCP tool contracts against the authenticated user's persisted Firestore records.
     */
    fun invokeMcpToolInteractive(toolName: String) {
        viewModelScope.launch {
            try {
                val personas = (personasState.value as? UiState.Success)?.data.orEmpty()
                val assets = (assetsState.value as? UiState.Success)?.data.orEmpty()
                val jobs = (jobsState.value as? UiState.Success)?.data.orEmpty()
                val schedules = (schedulesState.value as? UiState.Success)?.data.orEmpty()
                val approvals = (approvalsState.value as? UiState.Success)?.data.orEmpty()
                val analytics = (analyticsState.value as? UiState.Success)?.data.orEmpty()
                val quota = quotaState.value

                val responsePayload = JSONObject()
                when (toolName) {
                    "get_studio_status" -> {
                        responsePayload.put("userId", currentUserId)
                        responsePayload.put("monthlyBudgetLimitMicros", quota.monthlyBudgetLimitMicros)
                        responsePayload.put("currentMonthSpendMicros", quota.currentMonthSpendMicros)
                        responsePayload.put("dailyGenerationLimit", quota.dailyGenerationLimit)
                        responsePayload.put("todayGenerationCount", quota.todayGenerationCount)
                        responsePayload.put("pendingApprovals", approvals.count { it.status == "PENDING" })
                        responsePayload.put(
                            "providers",
                            JSONObject()
                                .put("FIREBASE_AI_LOGIC", "ACTIVE_APP_CHECK")
                                .put("FAL_AI", if (quota.falAiEnabled) "ENABLED" else "DISABLED_DEFAULT")
                                .put("REPLICATE", if (quota.replicateEnabled) "ENABLED" else "DISABLED_DEFAULT")
                        )
                    }
                    "list_personas" -> {
                        val arr = JSONArray()
                        personas.forEach { p ->
                            arr.put(
                                JSONObject()
                                    .put("id", p.id)
                                    .put("name", p.name)
                                    .put("handle", p.handle)
                                    .put("status", p.status)
                                    .put("profileVersion", p.profileVersion)
                                    .put("consentVerified", p.consentVerified)
                            )
                        }
                        responsePayload.put("personas", arr)
                    }
                    "get_persona" -> {
                        val first = personas.firstOrNull()
                            ?: throw IllegalStateException("Create a persona first to test get_persona")
                        responsePayload.put("id", first.id)
                        responsePayload.put("name", first.name)
                        responsePayload.put("profileVersion", first.profileVersion)
                        responsePayload.put("facialIdentityDescriptor", first.facialIdentityDescriptor)
                        responsePayload.put("referenceStorageUri", first.referenceStorageUri)
                    }
                    "create_persona_draft" -> {
                        val idem = "mcp_persona_${UUID.randomUUID().toString().take(8)}"
                        val id = repository.createPersonaDraft(
                            name = "Lyra Voss (MCP Draft)",
                            handle = "@lyravoss",
                            facialIdentityDescriptor = "Angular cheekbones, violet-gray eyes, silver pixie cut",
                            referenceStorageUri = "vault://users/$currentUserId/refs/lyra_mcp.jpg",
                            referenceSha256 = SecurityHashUtil.sha256Hex(idem),
                            consentSubjectName = "Lyra Voss Studio Model",
                            consentVerified = true,
                            status = "DRAFT",
                            actorRole = "CHATGPT_MCP",
                            idempotencyKey = idem
                        ).getOrThrow()
                        responsePayload.put("createdPersonaId", id)
                        responsePayload.put("status", "DRAFT")
                        responsePayload.put("idempotencyKey", idem)
                    }
                    "list_assets" -> {
                        val arr = JSONArray()
                        assets.forEach { a ->
                            arr.put(
                                JSONObject()
                                    .put("id", a.id)
                                    .put("personaName", a.personaName)
                                    .put("emotion", a.emotion)
                                    .put("reviewStatus", a.reviewStatus)
                                    .put("modelId", a.modelId)
                                    .put("costMicros", a.costMicros)
                                    .put("storageUri", a.storageUri)
                            )
                        }
                        responsePayload.put("assets", arr)
                    }
                    "generate_image_draft" -> {
                        val targetPersona = personas.firstOrNull()
                            ?: throw IllegalStateException("Create a persona first before calling generate_image_draft")
                        val idem = "mcp_gen_${UUID.randomUUID().toString().take(8)}"
                        val asset = repository.enqueueAndRecordGeneration(
                            idempotencyKey = idem,
                            persona = targetPersona,
                            jobType = "IMAGE_DRAFT",
                            provider = "FIREBASE_AI_LOGIC",
                            modelId = "gemini-nano-banana-2.1",
                            prompt = "MCP queued studio portrait with soft key lighting",
                            emotion = "Warm Smile (85%)",
                            stylePreset = "Editorial 50mm",
                            estimatedCostMicros = 40_000L,
                            outputStorageUri = "vault://users/$currentUserId/assets/$idem.jpg",
                            outputSha256Hash = SecurityHashUtil.sha256Hex(idem),
                            actorRole = "CHATGPT_MCP"
                        ).getOrThrow()
                        responsePayload.put("jobId", asset.jobId)
                        responsePayload.put("assetId", asset.id)
                        responsePayload.put("reviewStatus", asset.reviewStatus)
                        responsePayload.put("costMicros", asset.costMicros)
                    }
                    "get_generation_status" -> {
                        val job = jobs.firstOrNull()
                            ?: throw IllegalStateException("No generation jobs found yet")
                        responsePayload.put("jobId", job.id)
                        responsePayload.put("status", job.status)
                        responsePayload.put("provider", job.provider)
                        responsePayload.put("modelId", job.modelId)
                        responsePayload.put("actualCostMicros", job.actualCostMicros)
                        responsePayload.put("outputAssetId", job.outputAssetId)
                    }
                    "review_asset" -> {
                        val draft = assets.firstOrNull { it.reviewStatus == "DRAFT" } ?: assets.firstOrNull()
                            ?: throw IllegalStateException("Generate an asset draft first")
                        repository.reviewAsset(
                            asset = draft,
                            reviewStatus = "APPROVED",
                            reviewerNotes = "Approved via MCP review_asset verification",
                            actorRole = "CHATGPT_MCP"
                        ).getOrThrow()
                        responsePayload.put("assetId", draft.id)
                        responsePayload.put("newReviewStatus", "APPROVED")
                    }
                    "prepare_content_schedule" -> {
                        val approved = assets.firstOrNull { it.reviewStatus == "APPROVED" }
                            ?: throw IllegalStateException("Approve an asset first (DRAFT assets are rejected by policy)")
                        val schedTime = System.currentTimeMillis() + 3600_000L
                        val schedId = repository.prepareContentSchedule(
                            asset = approved,
                            destinationPlatform = "Patreon",
                            scheduledEpochMillis = schedTime,
                            caption = "Exclusive PersonaForge Studio drop",
                            actorRole = "CHATGPT_MCP"
                        ).getOrThrow()
                        responsePayload.put("scheduleId", schedId)
                        responsePayload.put("status", "DRAFT")
                    }
                    "list_scheduled_posts" -> {
                        val arr = JSONArray()
                        schedules.forEach { s ->
                            arr.put(
                                JSONObject()
                                    .put("id", s.id)
                                    .put("destinationPlatform", s.destinationPlatform)
                                    .put("status", s.status)
                                    .put("activeApprovalId", s.activeApprovalId)
                                    .put("bindingHash", s.bindingHash)
                            )
                        }
                        responsePayload.put("schedules", arr)
                    }
                    "get_analytics_summary" -> {
                        fun bucket(prov: String) = JSONObject().apply {
                            val list = analytics.filter { it.sourceProvenance == prov }
                            put("entries", list.size)
                            put("impressions", list.sumOf { it.impressions })
                            put("engagements", list.sumOf { it.engagements })
                            put("conversions", list.sumOf { it.conversions })
                        }
                        responsePayload.put("VERIFIED_PLATFORM", bucket("VERIFIED_PLATFORM"))
                        responsePayload.put("IMPORTED_DATASET", bucket("IMPORTED_DATASET"))
                        responsePayload.put("MANUAL_ENTRY", bucket("MANUAL_ENTRY"))
                    }
                    "request_publish_approval" -> {
                        val sched = schedules.firstOrNull { it.status == "DRAFT" } ?: schedules.firstOrNull()
                            ?: throw IllegalStateException("Prepare a content schedule first")
                        val approval = repository.requestPublishApproval(sched, "CHATGPT_MCP").getOrThrow()
                        responsePayload.put("approvalId", approval.id)
                        responsePayload.put("status", approval.status)
                        responsePayload.put("bindingHash", approval.bindingHash)
                        responsePayload.put("note", "ChatGPT cannot approve this request; owner must approve in Approval Vault.")
                    }
                    "publish_approved_post" -> {
                        val sched = schedules.firstOrNull { it.activeApprovalId.isNotBlank() }
                            ?: throw IllegalStateException("Request publish approval on a schedule first")
                        val published = repository.publishApprovedPost(
                            idempotencyKey = "mcp_pub_${sched.id}",
                            approvalId = sched.activeApprovalId,
                            scheduleId = sched.id,
                            assetId = sched.assetId,
                            destinationPlatform = sched.destinationPlatform,
                            scheduledEpochMillis = sched.scheduledEpochMillis,
                            caption = sched.caption,
                            actorRole = "CHATGPT_MCP"
                        ).getOrThrow()
                        responsePayload.put("scheduleId", published.id)
                        responsePayload.put("status", published.status)
                    }
                }

                val rpcEnvelope = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", UUID.randomUUID().toString().take(8))
                    .put("tool", toolName)
                    .put("result", responsePayload)

                _mcpConsoleOutput.value = rpcEnvelope.toString(2)
            } catch (e: Exception) {
                val errEnvelope = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("tool", toolName)
                    .put(
                        "error",
                        JSONObject()
                            .put("code", -32000)
                            .put("message", e.localizedMessage ?: "Tool execution rejected by policy")
                    )
                _mcpConsoleOutput.value = errEnvelope.toString(2)
            }
        }
    }

    companion object {
        private const val TAG = "StudioViewModel"

        fun provideFactory(
            repository: PersonaForgeRepository,
            mediaVault: PrivateMediaVault,
            aiService: PersonaForgeAiService,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StudioViewModel(
                    repository = repository,
                    mediaVault = mediaVault,
                    aiService = aiService,
                    currentUserId = currentUserId
                )
            }
        }
    }
}
