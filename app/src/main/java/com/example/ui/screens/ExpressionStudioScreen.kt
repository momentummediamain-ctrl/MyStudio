package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.MediaAsset
import com.example.data.model.Persona
import com.example.data.model.QuotaBudgetConfig
import com.example.viewmodel.SynthesisOperationState
import kotlin.math.roundToInt

private val EMOTION_MATRIX = listOf(
    "Warm Smile",
    "Serene Confidence",
    "Playful Wink",
    "Fierce Determination",
    "Surprised Joy",
    "Contemplative Gaze",
    "Radiant Laugh",
    "Subtle Smirk",
    "Focused Intensity",
    "Curious Raise-Brow",
    "Empathetic Warmth",
    "Editorial Poise"
)

private val STYLE_PRESETS = listOf(
    "Anamorphic 35mm Studio Key Light",
    "High-Contrast Editorial Portrait",
    "Golden Hour Natural Rim Light",
    "Cyber-Noir Neon Split Lighting",
    "Soft Diffused Beauty Dish 85mm"
)

private val AI_PROVIDERS = listOf(
    Triple("FIREBASE_AI_LOGIC", "gemini-nano-banana-2.1", "Gemini Nano Banana 2.1 (Live)"),
    Triple("FIREBASE_AI_LOGIC", "gemini-3-pro-image", "Gemini 3 Pro Image (Live)"),
    Triple("FAL_AI", "fal-ai/flux-pro/v1.1", "fal.ai Flux Pro (Protected Backend)"),
    Triple("REPLICATE", "black-forest-labs/flux-dev", "Replicate Flux (Protected Backend)")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpressionStudioScreen(
    personas: List<Persona>,
    assets: List<MediaAsset>,
    quota: QuotaBudgetConfig,
    selectedPersona: Persona?,
    synthesisState: SynthesisOperationState,
    onSelectPersona: (Persona) -> Unit,
    onSynthesizeExpression: (
        persona: Persona,
        emotion: String,
        intensity: Int,
        stylePreset: String,
        scenePrompt: String,
        provider: String,
        modelId: String
    ) -> Unit,
    onReviewAsset: (MediaAsset, String, String) -> Unit,
    loadVaultBitmap: (String) -> Bitmap?
) {
    val activePersona = selectedPersona ?: personas.firstOrNull()
    var selectedEmotion by remember { mutableStateOf(EMOTION_MATRIX.first()) }
    var emotionIntensity by remember { mutableFloatStateOf(85f) }
    var selectedStyle by remember { mutableStateOf(STYLE_PRESETS.first()) }
    var selectedProviderIdx by remember { mutableStateOf(0) }
    var scenePrompt by remember {
        mutableStateOf("Medium close-up portrait in an acoustic dark studio with subtle cyan rim lighting and catchlights")
    }

    val (providerKey, modelId, _) = AI_PROVIDERS[selectedProviderIdx]
    val estimatedCostCents = when {
        providerKey == "FAL_AI" -> 6.5
        providerKey == "REPLICATE" -> 5.5
        modelId == "gemini-3-pro-image" -> 8.0
        else -> 4.0
    }

    val latestPersonaAsset = remember(assets, activePersona) {
        assets.firstOrNull { it.personaId == activePersona?.id && it.mediaType == "IMAGE" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "EXPRESSION STUDIO & SIDE-BY-SIDE COMPARE",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Generate emotionally expressive character portraits locked to your persona's facial geometry, compare against source reference, and approve or reject drafts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (activePersona == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Create a consented Persona in the Persona Vault tab first to unlock Expression Synthesis.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
            return@LazyColumn
        }

        // Persona Selector Strip
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1. ACTIVE PERSONA & LOCKED FACIAL DNA",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        personas.forEach { p ->
                            FilterChip(
                                selected = p.id == activePersona.id,
                                onClick = { onSelectPersona(p) },
                                label = { Text("${p.name} (v${p.profileVersion})") },
                                modifier = Modifier.testTag("select_persona_chip_${p.id}")
                            )
                        }
                    }
                    Text(
                        text = "Locked Traits: ${activePersona.facialIdentityDescriptor}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 12-Emotion Matrix & Intensity
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. EMOTIONAL EXPRESSION MATRIX",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EMOTION_MATRIX.forEach { emotion ->
                            FilterChip(
                                selected = selectedEmotion == emotion,
                                onClick = { selectedEmotion = emotion },
                                label = { Text(emotion) },
                                modifier = Modifier.testTag("emotion_chip_$emotion")
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Expression Micro-Intensity", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${emotionIntensity.roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = emotionIntensity,
                        onValueChange = { emotionIntensity = it },
                        valueRange = 25f..100f,
                        modifier = Modifier.testTag("emotion_intensity_slider")
                    )
                }
            }
        }

        // Style Preset, Provider & Cost Gate
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "3. CINEMATOGRAPHY, MODEL ROUTER & COST ESTIMATE",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        STYLE_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = selectedStyle == preset,
                                onClick = { selectedStyle = preset },
                                label = { Text(preset) }
                            )
                        }
                    }

                    Text(
                        text = "AI Generation Provider (Zero Client Secrets)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AI_PROVIDERS.forEachIndexed { idx, (_, _, label) ->
                            FilterChip(
                                selected = selectedProviderIdx == idx,
                                onClick = { selectedProviderIdx = idx },
                                label = { Text(label) },
                                modifier = Modifier.testTag("provider_chip_$idx")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = scenePrompt,
                        onValueChange = { scenePrompt = it },
                        label = { Text("Scene Direction (PG-13 Compliant)") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scene_prompt_input")
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PRE-FLIGHT COST ESTIMATE: $${"%.3f".format(estimatedCostCents / 100.0)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "Quota Used Today: ${quota.todayGenerationCount}/${quota.dailyGenerationLimit} • Monthly Spend: $${
                                        "%.2f".format(quota.currentMonthSpendMicros / 1_000_000.0)
                                    }/$${"%.2f".format(quota.monthlyBudgetLimitMicros / 1_000_000.0)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onSynthesizeExpression(
                                activePersona,
                                selectedEmotion,
                                emotionIntensity.roundToInt(),
                                selectedStyle,
                                scenePrompt,
                                providerKey,
                                modelId
                            )
                        },
                        enabled = synthesisState !is SynthesisOperationState.Running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("generate_expression_button")
                    ) {
                        if (synthesisState is SynthesisOperationState.Running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Synthesizing (30-60s)...")
                        } else {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "Synthesize")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesize Expression Draft ($selectedEmotion)")
                        }
                    }

                    when (synthesisState) {
                        is SynthesisOperationState.Running -> {
                            Text(
                                text = synthesisState.stepLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is SynthesisOperationState.Failed -> {
                            Text(
                                text = "Error: ${synthesisState.errorMessage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("synthesis_error_text")
                            )
                        }
                        is SynthesisOperationState.Completed -> {
                            Text(
                                text = synthesisState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        SynthesisOperationState.Idle -> {}
                    }
                }
            }
        }

        // Side-by-Side Comparison & Review Gate
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Side by side comparison",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "4. SIDE-BY-SIDE SOURCE vs GENERATED REVIEW GATE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val refBmp = remember(activePersona.referenceStorageUri) {
                        loadVaultBitmap(activePersona.referenceStorageUri)
                    }
                    val genBmp = remember(latestPersonaAsset?.storageUri) {
                        latestPersonaAsset?.storageUri?.let { loadVaultBitmap(it) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SOURCE REFERENCE (v${activePersona.profileVersion})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (refBmp != null) {
                                Image(
                                    bitmap = refBmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.cd_persona_avatar),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Trait Lock Active:\n${activePersona.facialIdentityDescriptor.take(90)}...",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LATEST DRAFT (${latestPersonaAsset?.reviewStatus ?: "NONE"})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (genBmp != null) {
                                Image(
                                    bitmap = genBmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.cd_generated_asset),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = if (latestPersonaAsset != null) {
                                                "Asset ${latestPersonaAsset.id}\nEmotion: ${latestPersonaAsset.emotion}"
                                            } else {
                                                "No generated draft yet for ${activePersona.name}"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (latestPersonaAsset != null) {
                        Text(
                            text = "Provenance: ${latestPersonaAsset.modelId} • Cost: $${
                                "%.3f".format(latestPersonaAsset.costMicros / 1_000_000.0)
                            } • Status: ${latestPersonaAsset.reviewStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onReviewAsset(
                                        latestPersonaAsset,
                                        "REJECTED",
                                        "Facial drift or expression mismatch rejected in studio compare"
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("reject_latest_asset_button")
                            ) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = "Reject Draft")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reject Draft")
                            }
                            Button(
                                onClick = {
                                    onReviewAsset(
                                        latestPersonaAsset,
                                        "APPROVED",
                                        "Approved in side-by-side studio comparison"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("approve_latest_asset_button")
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Approve Draft")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Approve for Export")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
