package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.example.data.model.MediaAsset
import com.example.viewmodel.UiState

@Composable
fun AssetLibraryScreen(
    assetsState: UiState<List<MediaAsset>>,
    onReviewAsset: (MediaAsset, String, String) -> Unit,
    onScheduleApprovedAsset: (MediaAsset) -> Unit,
    loadVaultBitmap: (String) -> Bitmap?
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }

    val allAssets = (assetsState as? UiState.Success)?.data.orEmpty()
    val filteredAssets = remember(allAssets, searchQuery, statusFilter) {
        allAssets.filter { asset ->
            val matchesStatus = statusFilter == "ALL" || asset.reviewStatus == statusFilter
            val matchesQuery = searchQuery.isBlank() ||
                asset.personaName.contains(searchQuery, ignoreCase = true) ||
                asset.emotion.contains(searchQuery, ignoreCase = true) ||
                asset.modelId.contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ASSET LIBRARY & PROVENANCE LEDGER",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Every asset tracks source reference URI, persona version, model ID, cost, SHA-256 hash, and review status. Export and scheduling are strictly gated to APPROVED assets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by Persona, Emotion, or Model") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("asset_search_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "APPROVED", "DRAFT", "REJECTED").forEach { filter ->
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = { statusFilter = filter },
                        label = { Text(filter) },
                        modifier = Modifier.testTag("asset_filter_$filter")
                    )
                }
            }
        }

        if (filteredAssets.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No matching assets in library. Generate image drafts in the Studio tab or via the MCP generate_image_draft tool.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(filteredAssets, key = { it.id }) { asset ->
                val bmp = remember(asset.storageUri) { loadVaultBitmap(asset.storageUri) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("asset_card_${asset.id}")
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
                                Text(
                                    text = "${asset.personaName} (v${asset.personaVersion}) • ${asset.emotion}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${asset.mediaType} • ${asset.stylePreset}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = when (asset.reviewStatus) {
                                    "APPROVED" -> MaterialTheme.colorScheme.tertiaryContainer
                                    "REJECTED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = asset.reviewStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (asset.reviewStatus) {
                                        "APPROVED" -> MaterialTheme.colorScheme.tertiary
                                        "REJECTED" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = stringResource(R.string.cd_generated_asset),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (asset.storyboardSummary.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = asset.storyboardSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        // Provenance Ledger Box
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "PROVENANCE & TRACEABILITY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Model: ${asset.modelProvider}/${asset.modelId} • Cost: $${
                                        "%.3f".format(asset.costMicros / 1_000_000.0)
                                    }",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "Job ID: ${asset.jobId} • Idempotency: ${asset.idempotencyKey}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "Source Ref: ${asset.sourceReferenceUri}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "Vault URI: ${asset.storageUri} • SHA-256: ${asset.sha256Hash.take(16)}...",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (asset.reviewStatus != "APPROVED") {
                                Button(
                                    onClick = {
                                        onReviewAsset(asset, "APPROVED", "Approved in Asset Library")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("library_approve_${asset.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Approve")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Approve")
                                }
                            }
                            if (asset.reviewStatus != "REJECTED") {
                                OutlinedButton(
                                    onClick = {
                                        onReviewAsset(asset, "REJECTED", "Rejected in Asset Library")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("library_reject_${asset.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.Cancel, contentDescription = "Reject")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reject")
                                }
                            }
                            if (asset.reviewStatus == "APPROVED") {
                                OutlinedButton(
                                    onClick = { onScheduleApprovedAsset(asset) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("library_schedule_${asset.id}")
                                ) {
                                    Text("Queue Schedule")
                                }
                                Button(
                                    onClick = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                "PersonaForge Approved Asset: ${asset.personaName}"
                                            )
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Persona: ${asset.personaName} (v${asset.personaVersion})\nEmotion: ${asset.emotion}\nStyle: ${asset.stylePreset}\nProvenance SHA-256: ${asset.sha256Hash}"
                                            )
                                        }
                                        context.startActivity(
                                            Intent.createChooser(sendIntent, "Export Approved Creator Asset")
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("library_export_${asset.id}")
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Export")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export")
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
