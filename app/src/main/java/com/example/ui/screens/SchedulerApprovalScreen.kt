package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.MediaAsset
import com.example.data.model.PublishApproval
import com.example.data.model.ScheduledPost
import com.example.data.model.SecurityHashUtil
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
import com.example.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CREATOR_DESTINATION_PLATFORMS = listOf(
    "Creator Subscription Portal (Webhook)",
    "Patreon Creator Hub",
    "Instagram Creator Studio",
    "TikTok Creator Portal",
    "YouTube Shorts",
    "Ko-fi VIP Feed",
    "Custom Creator Webhook"
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SchedulerApprovalScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val assetsState by viewModel.assetsState.collectAsStateWithLifecycle()
    val schedulesState by viewModel.schedulesState.collectAsStateWithLifecycle()
    val approvalsState by viewModel.approvalsState.collectAsStateWithLifecycle()

    val allAssets = (assetsState as? UiState.Success<List<MediaAsset>>)?.data.orEmpty()
    val approvedAssets = allAssets.filter { it.reviewStatus == "APPROVED" }
    val draftAssets = allAssets.filter { it.reviewStatus == "DRAFT" }
    val schedules = (schedulesState as? UiState.Success<List<ScheduledPost>>)?.data.orEmpty()
    val approvals = (approvalsState as? UiState.Success<List<PublishApproval>>)?.data.orEmpty()

    var selectedAssetIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedAsset = approvedAssets.getOrNull(
        selectedAssetIndex.coerceIn(0, (approvedAssets.size - 1).coerceAtLeast(0))
    )

    var selectedPlatforms by rememberSaveable {
        mutableStateOf(listOf(CREATOR_DESTINATION_PLATFORMS.first()))
    }
    var customPlatformOverride by rememberSaveable { mutableStateOf("") }

    // Specific date and time state (defaults to +6 hours from now, rounded to minute)
    var scheduledEpochMillis by rememberSaveable {
        mutableLongStateOf(
            ((System.currentTimeMillis() / 60_000L) * 60_000L) + 6 * 3600_000L
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var caption by rememberSaveable {
        mutableStateOf("Exclusive studio series drop — consistent character expression set #CreatorStudio #VIP")
    }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }

    val dateTimeFormatter = remember { SimpleDateFormat("EEE, MMM dd yyyy • HH:mm", Locale.US) }
    val dateOnlyFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val timeOnlyFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = scheduledEpochMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedDateUtc ->
                            val currentCal = Calendar.getInstance().apply { timeInMillis = scheduledEpochMillis }
                            val hour = currentCal.get(Calendar.HOUR_OF_DAY)
                            val minute = currentCal.get(Calendar.MINUTE)
                            val newCal = Calendar.getInstance().apply {
                                timeInMillis = pickedDateUtc
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            scheduledEpochMillis = newCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Set Date", color = ElectricCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = scheduledEpochMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Posting Time (24h)") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedCal = Calendar.getInstance().apply {
                            timeInMillis = scheduledEpochMillis
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        scheduledEpochMillis = updatedCal.timeInMillis
                        showTimePicker = false
                    }
                ) {
                    Text("Set Time", color = ElectricCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("scheduler_approval_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Queue Summary Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QueueStatBadge(
                    title = "READY ASSETS",
                    count = approvedAssets.size,
                    accent = SignalLime,
                    modifier = Modifier.weight(1f)
                )
                QueueStatBadge(
                    title = "PENDING APPROVAL",
                    count = approvals.count { it.status == "PENDING" },
                    accent = WarningAmber,
                    modifier = Modifier.weight(1f)
                )
                QueueStatBadge(
                    title = "SCHEDULED / LIVE",
                    count = schedules.size,
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Section 1: Content Scheduler Builder
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSlate),
                border = BorderStroke(1.dp, StudioBorderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ScheduleSend,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.scheduler_title),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "Select approved generated media, choose target creator platforms, set exact posting date & time, and bind cryptographic publish approval.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fast approval helper if user only has DRAFT assets so far
                    if (approvedAssets.isEmpty() && draftAssets.isNotEmpty()) {
                        Surface(
                            color = ControlDeckSlate,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "You have ${draftAssets.size} unreviewed DRAFT asset(s). Approve an asset to unlock external scheduling:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WarningAmber
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    draftAssets.take(3).forEach { draft ->
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.reviewAssetDecision(
                                                    asset = draft,
                                                    reviewStatus = "APPROVED",
                                                    notes = "Approved for scheduling queue"
                                                )
                                            },
                                            modifier = Modifier.testTag("quick_approve_draft_${draft.id}")
                                        ) {
                                            Icon(
                                                Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = SignalLime,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Approve ${draft.personaName} (${draft.emotion})")
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (approvedAssets.isEmpty()) {
                        Surface(
                            color = ControlDeckSlate,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "No APPROVED generated media yet. Generate a portrait in Expression Studio or Video Storyboard and approve it in Asset Library to schedule.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WarningAmber,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "1. SELECT GENERATED MEDIA (${approvedAssets.size} APPROVED)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(approvedAssets, key = { _, item -> item.id }) { idx, asset ->
                                val isSelected = idx == selectedAssetIndex
                                val bitmap = remember(asset.storageUri) {
                                    viewModel.loadVaultBitmap(asset.storageUri)
                                }
                                Surface(
                                    color = if (isSelected) ElectricCyan.copy(alpha = 0.14f) else ControlDeckSlate,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) ElectricCyan else StudioBorderSubtle
                                    ),
                                    modifier = Modifier
                                        .width(175.dp)
                                        .clickable { selectedAssetIndex = idx }
                                        .testTag("schedule_asset_chip_${asset.id}")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(92.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(ObsidianVoid),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = asset.emotion,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Image,
                                                    contentDescription = null,
                                                    tint = ElectricCyan,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = asset.personaName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = asset.emotion,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SignalLime,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = asset.stylePreset,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StudioTextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "2. CHOOSE TARGET CREATOR PLATFORMS (MULTI-DESTINATION)",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Select one or more creator platforms or custom subscription webhook destinations (PG-13 Play Creator Program compliant):",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CREATOR_DESTINATION_PLATFORMS.forEach { platform ->
                                val isSelected = selectedPlatforms.contains(platform)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedPlatforms = if (isSelected && selectedPlatforms.size > 1) {
                                            selectedPlatforms - platform
                                        } else if (!isSelected) {
                                            selectedPlatforms + platform
                                        } else {
                                            selectedPlatforms
                                        }
                                    },
                                    label = { Text(platform) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = ElectricCyan
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customPlatformOverride,
                            onValueChange = { customPlatformOverride = it },
                            label = { Text("Optional Custom Platform / Webhook Label") },
                            placeholder = { Text("e.g. VIP Member Portal Webhook") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "3. SET SPECIFIC POSTING DATE & TIME",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_schedule_date_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(dateOnlyFormatter.format(Date(scheduledEpochMillis)))
                            }

                            OutlinedButton(
                                onClick = { showTimePicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_schedule_time_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessTime,
                                    contentDescription = null,
                                    tint = SignalLime,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${timeOnlyFormatter.format(Date(scheduledEpochMillis))} (24h)")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "+1h", 6 to "+6h", 24 to "Tomorrow", 72 to "+3 Days").forEach { (hrs, label) ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        scheduledEpochMillis =
                                            ((System.currentTimeMillis() / 60_000L) * 60_000L) + hrs * 3600_000L
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Scheduled Timestamp: ${dateTimeFormatter.format(Date(scheduledEpochMillis))}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalLime
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            label = { Text("4. Post Caption & Hashtags (Bound into SHA-256 Approval Hash)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("schedule_caption_input"),
                            minLines = 2
                        )

                        selectedAsset?.let { asset ->
                            val primaryTarget = customPlatformOverride.trim().ifBlank { selectedPlatforms.first() }
                            val previewHash = SecurityHashUtil.computePublishBindingHash(
                                assetId = asset.id,
                                destinationPlatform = primaryTarget,
                                scheduledEpochMillis = scheduledEpochMillis,
                                caption = caption
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Cryptographic Approval Binding (SHA-256): ${previewHash.take(28)}...",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                selectedAsset?.let { asset ->
                                    val targets = if (customPlatformOverride.isNotBlank()) {
                                        selectedPlatforms + customPlatformOverride.trim()
                                    } else {
                                        selectedPlatforms
                                    }
                                    targets.distinct().forEach { destination ->
                                        viewModel.createScheduleDraft(
                                            asset = asset,
                                            destinationPlatform = destination,
                                            scheduledEpochMillis = scheduledEpochMillis,
                                            caption = caption
                                        )
                                    }
                                }
                            },
                            enabled = selectedAsset != null && caption.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_schedule_draft_button")
                        ) {
                            Text(
                                text = "${stringResource(R.string.action_prepare_schedule)} (${selectedPlatforms.size + if (customPlatformOverride.isNotBlank()) 1 else 0} Target(s))",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Owner Approval Vault
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSlate),
                border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = NeonMagenta
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Owner Approval Vault (Cryptographic Binding)",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Separates request_publish_approval from publish_approved_post. Bound to exact asset, destination, timestamp, and caption.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    if (approvals.isEmpty()) {
                        Text(
                            text = "No approval tickets pending. Create a schedule draft and click 'Request Publish Approval' below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            approvals.forEach { approval ->
                                ApprovalTicketCard(
                                    approval = approval,
                                    onApprove = {
                                        viewModel.ownerDecideApproval(
                                            approval = approval,
                                            decision = "APPROVED",
                                            notes = "Explicitly approved in Android Owner Vault"
                                        )
                                    },
                                    onReject = {
                                        viewModel.ownerDecideApproval(
                                            approval = approval,
                                            decision = "REJECTED",
                                            notes = "Rejected by owner in Android Vault"
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Scheduled Posts Queue & Status Filter
        item {
            Column {
                Text(
                    text = "SCHEDULED POSTS CALENDAR QUEUE (${schedules.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL", "DRAFT", "PENDING_APPROVAL", "APPROVED", "PUBLISHED").forEach { status ->
                        FilterChip(
                            selected = statusFilter == status,
                            onClick = { statusFilter = status },
                            label = { Text(status) }
                        )
                    }
                }
            }
        }

        val filteredSchedules = schedules.filter {
            statusFilter == "ALL" || it.status == statusFilter
        }

        if (filteredSchedules.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ControlDeckSlate),
                    border = BorderStroke(1.dp, StudioBorderSubtle)
                ) {
                    Text(
                        text = "No scheduled posts match filter '$statusFilter'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredSchedules, key = { it.id }) { schedule ->
                ScheduledPostCard(
                    schedule = schedule,
                    onRequestApproval = {
                        viewModel.requestApprovalForSchedule(schedule, "OWNER_ANDROID")
                    },
                    onPublishApproved = {
                        viewModel.executeApprovedPublish(schedule, overrideCaptionForTamperTest = null)
                    },
                    onTestTamperedCaption = {
                        viewModel.executeApprovedPublish(
                            schedule = schedule,
                            overrideCaptionForTamperTest = "${schedule.caption} [TAMPERED_UNAPPROVED_EDIT]"
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueStatBadge(
    title: String,
    count: Int,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CarbonSlate,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ApprovalTicketCard(
    approval: PublishApproval,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val badgeColor = when (approval.status) {
        "APPROVED", "CONSUMED" -> SignalLime
        "REJECTED", "EXPIRED" -> AlertCrimson
        else -> WarningAmber
    }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US) }

    Surface(
        color = ControlDeckSlate,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Approval #${approval.id.take(10)} • Role: ${approval.requestedByRole}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )
                Text(
                    text = approval.status,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Destination: ${approval.destinationPlatform} | Asset: ${approval.assetId}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Publish Time: ${dateFormat.format(Date(approval.scheduledEpochMillis))}",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan
            )
            Text(
                text = "Locked Caption: \"${approval.caption}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "SHA-256 Binding: ${approval.bindingHash}",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = SignalLime
            )

            if (approval.status == "PENDING") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalLime,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("approve_ticket_${approval.id}")
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_owner_approve), fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.testTag("reject_ticket_${approval.id}")
                    ) {
                        Icon(
                            Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = AlertCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_owner_reject), color = AlertCrimson)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledPostCard(
    schedule: ScheduledPost,
    onRequestApproval: () -> Unit,
    onPublishApproved: () -> Unit,
    onTestTamperedCaption: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEE, MMM dd yyyy • HH:mm", Locale.US) }
    val formattedTime = dateFormat.format(Date(schedule.scheduledEpochMillis))

    val statusColor = when (schedule.status) {
        "PUBLISHED" -> SignalLime
        "APPROVED" -> ElectricCyan
        "PENDING_APPROVAL" -> WarningAmber
        "REJECTED" -> AlertCrimson
        else -> StudioTextSecondary
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CarbonSlate),
        border = BorderStroke(1.dp, StudioBorderSubtle),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${schedule.personaName} -> ${schedule.destinationPlatform}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = schedule.status,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Posting Time: $formattedTime • Asset: ${schedule.assetId}",
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "\"${schedule.caption}\"",
                style = MaterialTheme.typography.bodyMedium
            )
            if (schedule.bindingHash.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bound SHA-256: ${schedule.bindingHash.take(28)}...",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalLime
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (schedule.status == "DRAFT" || schedule.status == "REJECTED") {
                    Button(
                        onClick = onRequestApproval,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningAmber,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("request_approval_btn_${schedule.id}")
                    ) {
                        Icon(Icons.Filled.GppGood, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_request_approval), fontWeight = FontWeight.Bold)
                    }
                }

                if (schedule.status == "APPROVED" || schedule.status == "PENDING_APPROVAL") {
                    Button(
                        onClick = onPublishApproved,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalLime,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("publish_approved_btn_${schedule.id}")
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_publish_approved), fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onTestTamperedCaption,
                        modifier = Modifier.testTag("tamper_test_btn_${schedule.id}")
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = AlertCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verify Hash Lock", color = AlertCrimson)
                    }
                }
            }
        }
    }
}
