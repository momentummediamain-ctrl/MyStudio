package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AnalyticsEntry
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
import java.util.Locale

private val MCP_TOOLS_CATALOG = listOf(
    "get_studio_status",
    "list_personas",
    "get_persona",
    "create_persona_draft",
    "list_assets",
    "generate_image_draft",
    "get_generation_status",
    "review_asset",
    "prepare_content_schedule",
    "list_scheduled_posts",
    "get_analytics_summary",
    "request_publish_approval",
    "publish_approved_post"
)

private val PROVENANCE_TIERS = listOf(
    "VERIFIED_PLATFORM",
    "IMPORTED_DATASET",
    "MANUAL_ENTRY"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalyticsMcpBillingScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val analyticsState by viewModel.analyticsState.collectAsStateWithLifecycle()
    val quota by viewModel.quotaState.collectAsStateWithLifecycle()
    val mcpConsoleOutput by viewModel.mcpConsoleOutput.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val personasState by viewModel.personasState.collectAsStateWithLifecycle()
    val assetsState by viewModel.assetsState.collectAsStateWithLifecycle()

    val analytics = (analyticsState as? UiState.Success<List<AnalyticsEntry>>)?.data.orEmpty()
    val personas = (personasState as? UiState.Success)?.data.orEmpty()
    val assets = (assetsState as? UiState.Success)?.data.orEmpty()

    // Analytics filter & entry inputs
    var activeProvenanceFilter by rememberSaveable { mutableStateOf("ALL") }
    var selectedProvenance by rememberSaveable { mutableStateOf("VERIFIED_PLATFORM") }
    var platformName by rememberSaveable { mutableStateOf("Creator Subscription Portal") }
    var viewsInput by rememberSaveable { mutableStateOf("28400") }
    var engagementsInput by rememberSaveable { mutableStateOf("4120") }
    var followerGrowthInput by rememberSaveable { mutableStateOf("390") }

    // Budget & Provider Config inputs
    var budgetDollarsInput by rememberSaveable(quota.monthlyBudgetLimitMicros) {
        mutableStateOf(String.format(Locale.US, "%.2f", quota.monthlyBudgetLimitMicros / 1_000_000.0))
    }
    var dailyLimitInput by rememberSaveable(quota.dailyGenerationLimit) {
        mutableStateOf(quota.dailyGenerationLimit.toString())
    }
    var falEnabledToggle by rememberSaveable(quota.falAiEnabled) { mutableStateOf(quota.falAiEnabled) }
    var replicateEnabledToggle by rememberSaveable(quota.replicateEnabled) { mutableStateOf(quota.replicateEnabled) }

    // SSRF Guard live test
    var ssrfTestUrl by rememberSaveable { mutableStateOf("https://queue.fal.run/fal-ai/flux-pro") }
    var ssrfValidationResult by remember { mutableStateOf<String?>(null) }

    val filteredAnalytics = remember(analytics, activeProvenanceFilter) {
        if (activeProvenanceFilter == "ALL") analytics
        else analytics.filter { it.sourceProvenance == activeProvenanceFilter }
    }

    val totalViews = filteredAnalytics.sumOf { it.impressions }
    val totalEngagements = filteredAnalytics.sumOf { it.engagements }
    val totalFollowerGrowth = filteredAnalytics.sumOf { it.conversions }
    val engagementRatePercent = if (totalViews > 0L) {
        (totalEngagements.toDouble() / totalViews.toDouble()) * 100.0
    } else {
        0.0
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .testTag("analytics_mcp_billing_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 1: Executive Analytics KPI Cards (Views, Engagement Rate %, Follower Growth, Content Performance)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSlate),
                border = BorderStroke(1.dp, StudioBorderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Analytics, contentDescription = null, tint = ElectricCyan)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.analytics_title),
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = "Views, engagement rates, follower growth & content performance segmented by provenance.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StudioTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Provenance Filter Strip
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ALL", "VERIFIED_PLATFORM", "IMPORTED_DATASET", "MANUAL_ENTRY").forEach { filter ->
                            FilterChip(
                                selected = activeProvenanceFilter == filter,
                                onClick = { activeProvenanceFilter = filter },
                                label = { Text(filter) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Top KPI Row: Views, Engagement Rate, Follower Growth
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiMetricCard(
                            title = "TOTAL VIEWS",
                            value = String.format(Locale.US, "%,d", totalViews),
                            subtitle = "${filteredAnalytics.size} tracked posts",
                            accent = ElectricCyan,
                            icon = Icons.Filled.Visibility,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "ENGAGEMENT RATE",
                            value = String.format(Locale.US, "%.1f%%", engagementRatePercent),
                            subtitle = String.format(Locale.US, "%,d interactions", totalEngagements),
                            accent = SignalLime,
                            icon = Icons.Filled.TrendingUp,
                            modifier = Modifier.weight(1f)
                        )
                        KpiMetricCard(
                            title = "FOLLOWER GROWTH",
                            value = String.format(Locale.US, "+%,d", totalFollowerGrowth),
                            subtitle = "Net new subscribers",
                            accent = NeonMagenta,
                            icon = Icons.Filled.GroupAdd,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Provenance Breakdown Cards (Never blends Verified API with Manual/Imported)
                    val verifiedList = analytics.filter { it.sourceProvenance == "VERIFIED_PLATFORM" }
                    val importedList = analytics.filter { it.sourceProvenance == "IMPORTED_DATASET" }
                    val manualList = analytics.filter { it.sourceProvenance == "MANUAL_ENTRY" }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProvenanceMetricSummaryCard(
                            label = "VERIFIED API",
                            views = verifiedList.sumOf { it.impressions },
                            engagements = verifiedList.sumOf { it.engagements },
                            followers = verifiedList.sumOf { it.conversions },
                            accent = SignalLime,
                            modifier = Modifier.weight(1f)
                        )
                        ProvenanceMetricSummaryCard(
                            label = "CSV IMPORTED",
                            views = importedList.sumOf { it.impressions },
                            engagements = importedList.sumOf { it.engagements },
                            followers = importedList.sumOf { it.conversions },
                            accent = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        ProvenanceMetricSummaryCard(
                            label = "MANUAL LOG",
                            views = manualList.sumOf { it.impressions },
                            engagements = manualList.sumOf { it.engagements },
                            followers = manualList.sumOf { it.conversions },
                            accent = WarningAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    ProvenanceAndGrowthChart(
                        verifiedEngagements = verifiedList.sumOf { it.engagements },
                        importedEngagements = importedList.sumOf { it.engagements },
                        manualEngagements = manualList.sumOf { it.engagements },
                        entries = filteredAnalytics
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "CONTENT PERFORMANCE BREAKDOWN BY ASSET & PLATFORM",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (filteredAnalytics.isEmpty()) {
                        Surface(
                            color = ControlDeckSlate,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StudioBorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "No telemetry records logged yet for filter '$activeProvenanceFilter'. Log a record below or load a sample multi-channel creator breakdown:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StudioTextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val firstAssetId = assets.firstOrNull()?.id ?: "asset_editorial_01"
                                        val firstPersonaId = personas.firstOrNull()?.id ?: "persona_primary"
                                        viewModel.addAnalyticsRecord(
                                            assetId = firstAssetId,
                                            personaId = firstPersonaId,
                                            platformName = "Creator Subscription Portal",
                                            sourceProvenance = "VERIFIED_PLATFORM",
                                            impressions = 42500L,
                                            engagements = 6890L,
                                            conversions = 640L,
                                            revenueMicros = 3_200_000_000L
                                        )
                                        viewModel.addAnalyticsRecord(
                                            assetId = firstAssetId,
                                            personaId = firstPersonaId,
                                            platformName = "Instagram Creator Studio",
                                            sourceProvenance = "IMPORTED_DATASET",
                                            impressions = 81200L,
                                            engagements = 9450L,
                                            conversions = 1120L,
                                            revenueMicros = 1_450_000_000L
                                        )
                                    },
                                    modifier = Modifier.testTag("seed_analytics_sample_button")
                                ) {
                                    Text("Load Multi-Platform Telemetry Breakdown", color = ElectricCyan)
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredAnalytics.take(8).forEach { item ->
                                ContentPerformanceBreakdownRow(entry = item, assets = assets)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "LOG NEW CONTENT TELEMETRY RECORD",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PROVENANCE_TIERS.forEach { tier ->
                            FilterChip(
                                selected = selectedProvenance == tier,
                                onClick = { selectedProvenance = tier },
                                label = { Text(tier) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("provenance_chip_$tier")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = platformName,
                        onValueChange = { platformName = it },
                        label = { Text("Target Platform / Creator Channel") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = viewsInput,
                            onValueChange = { viewsInput = it },
                            label = { Text("Views") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = engagementsInput,
                            onValueChange = { engagementsInput = it },
                            label = { Text("Engagements") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = followerGrowthInput,
                            onValueChange = { followerGrowthInput = it },
                            label = { Text("Follower +") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val assetId = assets.firstOrNull()?.id ?: "asset_general"
                            val personaId = personas.firstOrNull()?.id ?: "persona_general"
                            viewModel.addAnalyticsRecord(
                                assetId = assetId,
                                personaId = personaId,
                                platformName = platformName.ifBlank { "Creator Channel" },
                                sourceProvenance = selectedProvenance,
                                impressions = viewsInput.toLongOrNull() ?: 0L,
                                engagements = engagementsInput.toLongOrNull() ?: 0L,
                                conversions = followerGrowthInput.toLongOrNull() ?: 0L,
                                revenueMicros = (followerGrowthInput.toLongOrNull() ?: 0L) * 5_000_000L
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("record_analytics_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_add_analytics),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section 2: Billing, Quotas, Provider Toggles & SSRF Guard
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSlate),
                border = BorderStroke(1.dp, StudioBorderSubtle),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = SignalLime)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Billing, Quotas & Protected Backend Adapters",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Hard pre-flight cost gate + default-disabled external adapters (fal.ai / Replicate).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    val spendDollars = quota.currentMonthSpendMicros / 1_000_000.0
                    val limitDollars = (quota.monthlyBudgetLimitMicros / 1_000_000.0).coerceAtLeast(0.01)
                    val spendFraction = (spendDollars / limitDollars).toFloat().coerceIn(0f, 1f)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Monthly Spend: $%.2f / $%.2f",
                                spendDollars,
                                limitDollars
                            ),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (spendFraction >= 0.8f) WarningAmber else SignalLime
                        )
                        Text(
                            text = "Daily Quota: ${quota.todayGenerationCount} / ${quota.dailyGenerationLimit} jobs",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { spendFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (spendFraction >= 0.8f) WarningAmber else SignalLime,
                        trackColor = ControlDeckSlate
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = budgetDollarsInput,
                            onValueChange = { budgetDollarsInput = it },
                            label = { Text("Monthly Cap ($ USD)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dailyLimitInput,
                            onValueChange = { dailyLimitInput = it },
                            label = { Text("Daily Job Limit") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("fal.ai Cloud Run Adapter", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Default: DISABLED until FAL_KEY is configured on Cloud Run",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextSecondary
                            )
                        }
                        Switch(
                            checked = falEnabledToggle,
                            onCheckedChange = { falEnabledToggle = it },
                            modifier = Modifier.testTag("toggle_fal_adapter")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Replicate Cloud Run Adapter", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Default: DISABLED until REPLICATE_API_TOKEN is configured on Cloud Run",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextSecondary
                            )
                        }
                        Switch(
                            checked = replicateEnabledToggle,
                            onCheckedChange = { replicateEnabledToggle = it },
                            modifier = Modifier.testTag("toggle_replicate_adapter")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            viewModel.updateQuotaAndProviderConfig(
                                monthlyBudgetDollars = budgetDollarsInput.toDoubleOrNull() ?: 25.0,
                                dailyLimit = dailyLimitInput.toLongOrNull() ?: 50L,
                                falEnabled = falEnabledToggle,
                                replicateEnabled = replicateEnabledToggle
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalLime,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_quota_config_button")
                    ) {
                        Text("Save Quota & Provider Gate Settings", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "SSRF PROVIDER URL ALLOWLIST VALIDATOR",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonMagenta
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = ssrfTestUrl,
                        onValueChange = { ssrfTestUrl = it },
                        label = { Text("Test Provider Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                ssrfValidationResult = runCatching {
                                    "ALLOWED: " + SecurityHashUtil.validateProviderUrl(ssrfTestUrl)
                                }.getOrElse { "BLOCKED: ${it.message}" }
                            }
                        ) {
                            Text("Validate URL")
                        }
                        OutlinedButton(
                            onClick = {
                                ssrfTestUrl = "http://169.254.169.254/latest/meta-data/"
                                ssrfValidationResult = runCatching {
                                    "ALLOWED: " + SecurityHashUtil.validateProviderUrl(ssrfTestUrl)
                                }.getOrElse { "BLOCKED: ${it.message}" }
                            }
                        ) {
                            Text("Test SSRF Metadata Attack", color = AlertCrimson)
                        }
                    }
                    ssrfValidationResult?.let { res ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = res,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (res.startsWith("ALLOWED")) SignalLime else AlertCrimson
                        )
                    }
                }
            }
        }

        // Section 3: ChatGPT Remote MCP Server Inspector (All 13 Tools)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CarbonSlate),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Hub, contentDescription = null, tint = ElectricCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ChatGPT Remote MCP Plugin Console (13 Tools)",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Test any of the 13 authenticated Streamable HTTP MCP tools against your live Firestore tenant state.",
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
                        MCP_TOOLS_CATALOG.forEach { toolName ->
                            OutlinedButton(
                                onClick = { viewModel.invokeMcpToolInteractive(toolName) },
                                modifier = Modifier.testTag("mcp_tool_btn_$toolName")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = toolName,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = ObsidianVoid,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StudioBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = mcpConsoleOutput,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalLime,
                            modifier = Modifier
                                .padding(12.dp)
                                .testTag("mcp_console_output")
                        )
                    }
                }
            }
        }

        // Section 4: Immutable Append-Only Audit Log
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = NeonMagenta)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "APPEND-ONLY SECURITY AUDIT LOG (${auditLogs.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonMagenta
                )
            }
        }

        items(auditLogs.take(15), key = { it.id.ifBlank { it.hashCode().toString() } }) { entry ->
            Surface(
                color = ControlDeckSlate,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StudioBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${entry.action} [${entry.actorRole}]",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        Text(
                            text = entry.outcome,
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.outcome == "ALLOWED") SignalLime else AlertCrimson
                        )
                    }
                    Text(
                        text = "${entry.targetCollection}/${entry.targetId} • ${entry.details}",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ControlDeckSlate,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary
            )
        }
    }
}

@Composable
private fun ProvenanceMetricSummaryCard(
    label: String,
    views: Long,
    engagements: Long,
    followers: Long,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val engRate = if (views > 0) (engagements.toDouble() / views.toDouble()) * 100.0 else 0.0
    Surface(
        color = ControlDeckSlate,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$views views",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format(Locale.US, "%.1f%% eng • +%d flw", engRate, followers),
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary
            )
        }
    }
}

@Composable
private fun ContentPerformanceBreakdownRow(
    entry: AnalyticsEntry,
    assets: List<com.example.data.model.MediaAsset>
) {
    val matchedAsset = assets.firstOrNull { it.id == entry.assetId }
    val rate = if (entry.impressions > 0L) {
        (entry.engagements.toDouble() / entry.impressions.toDouble()) * 100.0
    } else {
        0.0
    }
    val provColor = when (entry.sourceProvenance) {
        "VERIFIED_PLATFORM" -> SignalLime
        "IMPORTED_DATASET" -> ElectricCyan
        else -> WarningAmber
    }

    Surface(
        color = ControlDeckSlate,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, StudioBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.platformName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.sourceProvenance,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = provColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (matchedAsset != null) {
                    "Asset: ${matchedAsset.personaName} • ${matchedAsset.emotion} (${matchedAsset.stylePreset})"
                } else {
                    "Asset ID: ${entry.assetId}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = StudioTextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Views: ${entry.impressions}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Text(
                    text = String.format(Locale.US, "Eng Rate: %.1f%% (%d)", rate, entry.engagements),
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalLime
                )
                Text(
                    text = "Followers: +${entry.conversions}",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonMagenta
                )
            }
        }
    }
}

@Composable
private fun ProvenanceAndGrowthChart(
    verifiedEngagements: Long,
    importedEngagements: Long,
    manualEngagements: Long,
    entries: List<AnalyticsEntry>
) {
    val maxVal = maxOf(verifiedEngagements, importedEngagements, manualEngagements, 1L).toFloat()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ControlDeckSlate, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "ENGAGEMENT BY PROVENANCE & FOLLOWER GROWTH CURVE",
            fontFamily = JetBrainsMonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = StudioTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        ) {
            val barHeight = size.height / 5.0f
            val vWidth = (verifiedEngagements / maxVal) * (size.width * 0.55f)
            val iWidth = (importedEngagements / maxVal) * (size.width * 0.55f)
            val mWidth = (manualEngagements / maxVal) * (size.width * 0.55f)

            drawRoundRect(
                color = SignalLime,
                topLeft = Offset(0f, 4f),
                size = Size(vWidth.coerceAtLeast(12f), barHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = ElectricCyan,
                topLeft = Offset(0f, barHeight * 1.6f),
                size = Size(iWidth.coerceAtLeast(12f), barHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = WarningAmber,
                topLeft = Offset(0f, barHeight * 3.1f),
                size = Size(mWidth.coerceAtLeast(12f), barHeight),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Right-side follower growth trend sparkline
            val growthPoints = if (entries.size >= 2) {
                entries.take(6).reversed().map { it.conversions.toFloat().coerceAtLeast(1f) }
            } else {
                listOf(120f, 240f, 310f, 490f, 640f)
            }
            val maxGrowth = (growthPoints.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val chartLeft = size.width * 0.62f
            val chartWidth = size.width * 0.36f
            val stepX = chartWidth / (growthPoints.size - 1).coerceAtLeast(1)

            val path = Path()
            growthPoints.forEachIndexed { idx, value ->
                val x = chartLeft + idx * stepX
                val y = size.height - ((value / maxGrowth) * (size.height * 0.75f)) - 8f
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                drawCircle(
                    color = NeonMagenta,
                    radius = 4f,
                    center = Offset(x, y)
                )
            }
            drawPath(
                path = path,
                color = NeonMagenta,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
    }
}
