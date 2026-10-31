package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Persona
import com.example.viewmodel.SynthesisOperationState
import com.example.viewmodel.UiState

@Composable
fun PersonaVaultScreen(
    personasState: UiState<List<Persona>>,
    extractedDescriptor: String,
    synthesisState: SynthesisOperationState,
    onDescriptorChange: (String) -> Unit,
    onAnalyzeReferencePhoto: (Bitmap?) -> Unit,
    onCreatePersona: (
        name: String,
        handle: String,
        descriptor: String,
        consentSubject: String,
        consentVerified: Boolean,
        retentionDays: Long,
        bitmap: Bitmap?
    ) -> Unit,
    onIncrementPersonaVersion: (Persona) -> Unit,
    onDeletePersona: (Persona) -> Unit,
    loadVaultBitmap: (String) -> Bitmap?,
    onSelectPersonaForStudio: (Persona) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var handle by remember { mutableStateOf("") }
    var consentSubject by remember { mutableStateOf("") }
    var consentChecked by remember { mutableStateOf(true) }
    var retentionDaysText by remember { mutableStateOf("365") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Zero-permission Android Photo Picker per Google Play Policy
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                selectedBitmap = bmp
            }
        }
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
                text = "PERSONA VAULT & FACIAL DNA LOCK",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Create consented character profiles with structured facial geometry locks and versioned snapshots. Binary portraits are stored in your private vault (never in Firestore documents).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Create Persona Card
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
                        text = "NEW CHARACTER PERSONA",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Persona Name") },
                            placeholder = { Text("e.g. Aria Vance") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("persona_name_input")
                        )
                        OutlinedTextField(
                            value = handle,
                            onValueChange = { handle = it },
                            label = { Text("Studio Handle") },
                            placeholder = { Text("@ariavance") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("persona_handle_input")
                        )
                    }

                    // Reference Photo Picker + AI Facial Trait Extractor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pick_reference_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "Pick Reference Photo"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedBitmap != null) "Photo Attached" else "Pick Reference")
                        }

                        Button(
                            onClick = { onAnalyzeReferencePhoto(selectedBitmap) },
                            enabled = synthesisState !is SynthesisOperationState.Running,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("extract_facial_dna_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Extract Facial DNA"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Trait Lock")
                        }
                    }

                    selectedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_persona_avatar),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (synthesisState is SynthesisOperationState.Running) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                text = synthesisState.stepLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = extractedDescriptor,
                        onValueChange = onDescriptorChange,
                        label = { Text("Structured Facial Identity Lock Descriptor") },
                        placeholder = {
                            Text("Face shape, eye geometry, brow arch, nose bridge, lip contour, skin tone, hair signature, distinguishing marks...")
                        },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("persona_descriptor_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = consentSubject,
                            onValueChange = { consentSubject = it },
                            label = { Text("Consented Subject / Model Name") },
                            placeholder = { Text("Written release holder") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("consent_subject_input")
                        )
                        OutlinedTextField(
                            value = retentionDaysText,
                            onValueChange = { retentionDaysText = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Retention (Days)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("retention_days_input")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = consentChecked,
                            onCheckedChange = { consentChecked = it },
                            modifier = Modifier.testTag("consent_checkbox")
                        )
                        Text(
                            text = "I attest explicit written likeness consent is on file and revocable per retention policy.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            val effectiveName = name.ifBlank { "Aria Vance" }
                            val effectiveHandle = handle.ifBlank { "@ariavance" }
                            val effectiveDescriptor = extractedDescriptor.ifBlank {
                                "Face: Sculpted oval jawline | Eyes: Deep-set hazel almond eyes | Brows: High natural arch | Nose: Straight slender bridge | Lips: Full balanced contour | Skin: Warm olive | Hair: Espresso waves | Marks: Beauty mark below left eye"
                            }
                            val effectiveConsentName = consentSubject.ifBlank { "$effectiveName (Consented Creator)" }
                            val retentionDays = retentionDaysText.toLongOrNull()?.coerceIn(1L, 3650L) ?: 365L

                            onCreatePersona(
                                effectiveName,
                                effectiveHandle,
                                effectiveDescriptor,
                                effectiveConsentName,
                                consentChecked,
                                retentionDays,
                                selectedBitmap
                            )
                            name = ""
                            handle = ""
                            consentSubject = ""
                            selectedBitmap = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_persona_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FaceRetouchingNatural,
                            contentDescription = "Save Persona"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lock Facial DNA & Save Persona to Vault")
                    }
                }
            }
        }

        // Existing Personas List
        when (personasState) {
            is UiState.Loading -> {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = personasState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is UiState.Success -> {
                val personas = personasState.data
                if (personas.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No Personas in Vault Yet",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Use the form above (or tap 'Lock Facial DNA & Save Persona to Vault' with defaults) to register your first consented persona.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(personas, key = { it.id }) { persona ->
                        val refBmp = remember(persona.referenceStorageUri) {
                            loadVaultBitmap(persona.referenceStorageUri)
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("persona_card_${persona.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = persona.name,
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "v${persona.profileVersion} • ${persona.status}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${persona.handle} • Consent: ${persona.consentSubjectName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeletePersona(persona) },
                                        modifier = Modifier.testTag("delete_persona_${persona.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Persona and Purge Vault Media",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }

                                if (refBmp != null) {
                                    Image(
                                        bitmap = refBmp.asImageBitmap(),
                                        contentDescription = stringResource(R.string.cd_persona_avatar),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "Locked Facial DNA",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "FACIAL IDENTITY LOCK DESCRIPTOR",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = persona.facialIdentityDescriptor,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Vault URI: ${persona.referenceStorageUri} | Retention: ${persona.retentionPolicyDays}d",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onIncrementPersonaVersion(persona) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("bump_version_${persona.id}")
                                    ) {
                                        Text("Snapshot v${persona.profileVersion + 1}")
                                    }
                                    Button(
                                        onClick = { onSelectPersonaForStudio(persona) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("open_in_studio_${persona.id}")
                                    ) {
                                        Text("Open in Expression Studio")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
