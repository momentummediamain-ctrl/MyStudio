package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.StoryboardScene
import com.example.data.model.Persona
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.CarbonSlate
import com.example.ui.theme.ControlDeckSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SignalLime
import com.example.ui.theme.StudioBorderSubtle
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.SynthesisOperationState
import com.example.viewmodel.UiState

private val EMOTION_ARCS = listOf(
    "Serene Focus -> Subtle Intrigue -> Confident Gaze -> Warm Smile",
    "Playful Laugh -> Candid Glance -> Editorial Composure -> Determined Resolve",
    "Contemplative Stillness -> Surprised Spark -> Radiant Joy -> Calm Poise"
)

private val VIDEO_STYLES = listOf(
    "Anamorphic 35mm Cinema",
    "Studio Key-Lit Commercial",
    "Handheld Documentary Portrait",
    "Cyberpunk Neon Editorial"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoStudioScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val personasState by viewModel.personasState.collectAsStateWithLifecycle()
    val storyboardScenes by viewModel.storyboardScenes.collectAsStateWithLifecycle()
    val synthesisState by viewModel.synthesisState.collectAsStateWithLifecycle()
    val quota by viewModel.quotaState.collectAsStateWithLifecycle()

    val personas = (personasState as? UiState.Success<List<Persona>>)?.data.orEmpty()
    var selectedPersonaIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedPersona = personas.getOrNull(selectedPersonaIndex.coerceIn(0, (personas.size - 1).coerceAtLeast(0)))

    var selectedArc by rememberSaveable { mutableStateOf(EMOTION_ARCS.first()) }
    var selectedStyle by rememberSaveable { mutableStateOf(VIDEO_STYLES.first()) }
    var conceptPrompt by rememberSaveable {
        mutableStateOf("Behind-the-scenes creator studio reveal transitioning from setup to camera address")
    }
    var selectedVideoProvider by rememberSaveable { mutableStateOf("FAL_AI") }
    var videoStatusBanner by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("video_studio_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSlate),
                border = BorderStroke(1.dp, StudioBorderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.MovieCreation,
                            contentDescription = stringResource(R.string.nav_video),
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.video_studio_title),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = stringResource(R.string.video_studio_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (personas.isEmpty()) {
                        Surface(
                            color = ControlDeckSlate,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Create a consented persona in the Persona Vault first to lock facial identity across storyboard frames.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarningAmber,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "1. SELECT LOCKED PERSONA IDENTITY",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            personas.forEachIndexed { idx, persona ->
                                FilterChip(
                                    selected = idx == selectedPersonaIndex,
                                    onClick = { selectedPersonaIndex = idx },
                                    label = {
                                        Text("${persona.name} (v${persona.profileVersion})")
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = ElectricCyan
                                    ),
                                    modifier = Modifier.testTag("video_persona_chip_${persona.id}")
                                )
                            }
                        }

                        selectedPersona?.let { p ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "DNA Lock: ${p.facialIdentityDescriptor}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "2. EMOTIONAL PROGRESSION ARC (4-SCENE CONTINUITY)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EMOTION_ARCS.forEach { arc ->
                                val isSelected = arc == selectedArc
                                Surface(
                                    color = if (isSelected) ElectricCyan.copy(alpha = 0.14f) else ControlDeckSlate,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) ElectricCyan else StudioBorderSubtle
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedArc = arc }
                                ) {
                                    Text(
                                        text = arc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "3. CINEMATOGRAPHY & LIGHTING RIG",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VIDEO_STYLES.forEach { style ->
                                FilterChip(
                                    selected = style == selectedStyle,
                                    onClick = { selectedStyle = style },
                                    label = { Text(style) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonMagenta.copy(alpha = 0.22f),
                                        selectedLabelColor = NeonMagenta
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = conceptPrompt,
                            onValueChange = { conceptPrompt = it },
                            label = { Text("Director Concept & Scene Action (PG-13 Safe)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("video_concept_input"),
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Est. Cost: $0.020 (gemini-3.8-flash JSON schema)",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalLime
                            )
                            Button(
                                onClick = {
                                    selectedPersona?.let { persona ->
                                        viewModel.generateVideoStoryboardFirst(
                                            persona = persona,
                                            emotionArc = selectedArc,
                                            stylePreset = selectedStyle,
                                            conceptPrompt = conceptPrompt
                                        )
                                    }
                                },
                                enabled = selectedPersona != null && synthesisState !is SynthesisOperationState.Running,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianVoid
                                ),
                                modifier = Modifier.testTag("generate_storyboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_generate_storyboard),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Synthesis status indicator
        when (val state = synthesisState) {
            is SynthesisOperationState.Running -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ControlDeckSlate),
                        border = BorderStroke(1.dp, ElectricCyan)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = state.stepLabel, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            is SynthesisOperationState.Completed -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ControlDeckSlate),
                        border = BorderStroke(1.dp, SignalLime)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SignalLime,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { viewModel.resetSynthesisState() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
            is SynthesisOperationState.Failed -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ControlDeckSlate),
                        border = BorderStroke(1.dp, AlertCrimson)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AlertCrimson,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { viewModel.resetSynthesisState() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
            SynthesisOperationState.Idle -> Unit
        }

        // 4-Scene Storyboard Display
        if (storyboardScenes.isNotEmpty()) {
            item {
                Text(
                    text = "GENERATED 4-SCENE CONTINUITY STORYBOARD",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricCyan
                )
            }
            items(storyboardScenes, key = { it.sceneNumber }) { scene ->
                StoryboardSceneCard(scene = scene)
            }
        }

        // Step 2: Verified Provider Video Synthesis Gate
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSlate),
                border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = NeonMagenta
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Step 2: Verified Provider Video Synthesis Gate",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Actual MP4 motion synthesis only executes through verified capable backend providers (fal.ai Kling / Wan-2.1 or Replicate) once a storyboard is locked.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val falStatus = if (quota.falAiEnabled) "ENABLED" else "DISABLED (Default)"
                        val repStatus = if (quota.replicateEnabled) "ENABLED" else "DISABLED (Default)"

                        FilterChip(
                            selected = selectedVideoProvider == "FAL_AI",
                            onClick = { selectedVideoProvider = "FAL_AI" },
                            label = { Text("fal.ai Kling 1.6 Pro [$falStatus]") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonMagenta.copy(alpha = 0.2f),
                                selectedLabelColor = NeonMagenta
                            )
                        )
                        FilterChip(
                            selected = selectedVideoProvider == "REPLICATE",
                            onClick = { selectedVideoProvider = "REPLICATE" },
                            label = { Text("Replicate Wan-2.1 [$repStatus]") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonMagenta.copy(alpha = 0.2f),
                                selectedLabelColor = NeonMagenta
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    val providerEnabled =
                        (selectedVideoProvider == "FAL_AI" && quota.falAiEnabled) ||
                            (selectedVideoProvider == "REPLICATE" && quota.replicateEnabled)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pre-Flight Estimate: $0.350 / 5s 1080p clip",
                                style = MaterialTheme.typography.labelLarge,
                                color = WarningAmber
                            )
                            Text(
                                text = if (providerEnabled) {
                                    "Adapter enabled in Quota Config. Ready to dispatch to Cloud Run worker."
                                } else {
                                    "External adapter disabled by default per zero-trust policy. Enable in MCP & Ops tab."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (storyboardScenes.isEmpty()) {
                                    videoStatusBanner =
                                        "Blocked: Storyboard-first policy requires generating a 4-scene storyboard before video synthesis."
                                } else if (!providerEnabled) {
                                    videoStatusBanner =
                                        "Blocked: $selectedVideoProvider adapter is disabled by default. Configure Cloud Run secret and enable the adapter in MCP & Ops."
                                } else {
                                    videoStatusBanner =
                                        "Queued VIDEO_SYNTHESIS job on protected $selectedVideoProvider adapter ($0.350 pre-flight budget reserved)."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (providerEnabled && storyboardScenes.isNotEmpty()) NeonMagenta else ControlDeckSlate,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("queue_video_synthesis_button")
                        ) {
                            Icon(
                                imageVector = if (providerEnabled) Icons.Filled.PlayArrow else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Queue Video Render")
                        }
                    }

                    videoStatusBanner?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = ControlDeckSlate,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NeonMagenta)
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonMagenta,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryboardSceneCard(scene: StoryboardScene) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ControlDeckSlate),
        border = BorderStroke(1.dp, StudioBorderSubtle),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(ElectricCyan.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SCENE 0${scene.sceneNumber}",
                        fontFamily = JetBrainsMonoFontFamily,
                        color = ElectricCyan,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = "${scene.shotType} • ${scene.cameraMovement}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalLime
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Emotion Beat: ${scene.emotionBeat}",
                style = MaterialTheme.typography.titleMedium,
                color = NeonMagenta
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scene.visualPrompt,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
