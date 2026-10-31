package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.PersonaForgeAiService
import com.example.auth.configureAppCheck
import com.example.data.model.MediaAsset
import com.example.data.model.Persona
import com.example.data.repository.PersonaForgeRepository
import com.example.data.storage.PrivateMediaVault
import com.example.ui.screens.AnalyticsMcpBillingScreen
import com.example.ui.screens.AssetLibraryScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExpressionStudioScreen
import com.example.ui.screens.PersonaVaultScreen
import com.example.ui.screens.SchedulerApprovalScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.theme.CarbonSlate
import com.example.ui.theme.ControlDeckSlate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SignalLime
import com.example.ui.theme.StudioTextSecondary
import com.example.viewmodel.StudioViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

enum class StudioTab(val labelRes: Int, val icon: ImageVector, val tag: String) {
    VAULT(R.string.nav_vault, Icons.Filled.FaceRetouchingNatural, "nav_tab_vault"),
    STUDIO(R.string.nav_studio, Icons.Filled.AutoAwesome, "nav_tab_studio"),
    LIBRARY(R.string.nav_library, Icons.Filled.Collections, "nav_tab_library"),
    VIDEO(R.string.nav_video, Icons.Filled.MovieCreation, "nav_tab_video"),
    SCHEDULER(R.string.nav_scheduler, Icons.Filled.ScheduleSend, "nav_tab_scheduler"),
    MCP_OPS(R.string.nav_mcp_ops, Icons.Filled.Hub, "nav_tab_mcp_ops")
}

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must run before any Firebase AI Logic or Firestore calls per firebase-ai-logic-android
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                PersonaForgeRoot()
            }
        }
    }
}

@Composable
fun PersonaForgeRoot() {
    val context = LocalContext.current
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val repository = remember(user.uid) { PersonaForgeRepository(context) }
        val mediaVault = remember(user.uid) { PrivateMediaVault(context) }
        val aiService = remember { PersonaForgeAiService() }

        val studioViewModel: StudioViewModel = viewModel(
            key = "studio_vm_${user.uid}",
            factory = StudioViewModel.provideFactory(
                repository = repository,
                mediaVault = mediaVault,
                aiService = aiService,
                currentUserId = user.uid
            )
        )

        MainStudioWorkspace(
            user = user,
            viewModel = studioViewModel,
            onSignOut = {
                Firebase.auth.signOut()
                CoroutineScope(Dispatchers.Main).launch {
                    runCatching {
                        CredentialManager.create(context)
                            .clearCredentialState(ClearCredentialStateRequest())
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainStudioWorkspace(
    user: FirebaseUser,
    viewModel: StudioViewModel,
    onSignOut: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(StudioTab.VAULT) }
    var selectedPersonaForStudio by remember { mutableStateOf<Persona?>(null) }

    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val personasState by viewModel.personasState.collectAsStateWithLifecycle()
    val assetsState by viewModel.assetsState.collectAsStateWithLifecycle()
    val quota by viewModel.quotaState.collectAsStateWithLifecycle()
    val extractedDescriptor by viewModel.extractedDescriptor.collectAsStateWithLifecycle()
    val synthesisState by viewModel.synthesisState.collectAsStateWithLifecycle()

    val personas = (personasState as? UiState.Success<List<Persona>>)?.data.orEmpty()
    val assets = (assetsState as? UiState.Success<List<MediaAsset>>)?.data.orEmpty()

    // BackHandler on all secondary tabs to return to VAULT home screen
    BackHandler(enabled = selectedTab != StudioTab.VAULT) {
        selectedTab = StudioTab.VAULT
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = ObsidianVoid,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${user.email ?: user.uid.take(10)} • MCP + AI Logic Ready",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(R.string.auth_sign_out),
                                tint = StudioTextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CarbonSlate
                    )
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = CarbonSlate,
                        tonalElevation = 0.dp
                    ) {
                        StudioTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ObsidianVoid,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan,
                                    unselectedIconColor = StudioTextSecondary,
                                    unselectedTextColor = StudioTextSecondary
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = CarbonSlate,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        StudioTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ObsidianVoid,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = ElectricCyan,
                                    unselectedIconColor = StudioTextSecondary,
                                    unselectedTextColor = StudioTextSecondary
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ObsidianVoid)
                ) {
                    bannerMessage?.let { msg ->
                        Surface(
                            color = ControlDeckSlate,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SignalLime),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SignalLime,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { viewModel.dismissBanner() }) {
                                    Text("OK", color = ElectricCyan)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            StudioTab.VAULT -> PersonaVaultScreen(
                                personasState = personasState,
                                extractedDescriptor = extractedDescriptor,
                                synthesisState = synthesisState,
                                onDescriptorChange = { viewModel.setExtractedDescriptorText(it) },
                                onAnalyzeReferencePhoto = { viewModel.analyzeReferencePhotoWithAi(it) },
                                onCreatePersona = { name, handle, desc, subject, verified, retention, bmp ->
                                    viewModel.createPersona(
                                        name = name,
                                        handle = handle,
                                        facialDescriptor = desc,
                                        consentSubjectName = subject,
                                        consentVerified = verified,
                                        retentionDays = retention,
                                        referenceBitmap = bmp
                                    )
                                },
                                onIncrementPersonaVersion = { persona ->
                                    viewModel.updatePersonaVersion(
                                        persona = persona,
                                        newName = persona.name,
                                        newDescriptor = persona.facialIdentityDescriptor,
                                        newStatus = "ACTIVE"
                                    )
                                },
                                onDeletePersona = { viewModel.deletePersonaWithRetentionPurge(it) },
                                loadVaultBitmap = { viewModel.loadVaultBitmap(it) },
                                onSelectPersonaForStudio = { persona ->
                                    selectedPersonaForStudio = persona
                                    selectedTab = StudioTab.STUDIO
                                }
                            )
                            StudioTab.STUDIO -> ExpressionStudioScreen(
                                personas = personas,
                                assets = assets,
                                quota = quota,
                                selectedPersona = selectedPersonaForStudio,
                                synthesisState = synthesisState,
                                onSelectPersona = { selectedPersonaForStudio = it },
                                onSynthesizeExpression = { persona, emotion, intensity, style, prompt, provider, modelId ->
                                    viewModel.generateExpressionImage(
                                        persona = persona,
                                        emotion = emotion,
                                        emotionIntensity = intensity,
                                        stylePreset = style,
                                        scenePrompt = prompt,
                                        provider = provider,
                                        modelId = modelId
                                    )
                                },
                                onReviewAsset = { asset, status, notes ->
                                    viewModel.reviewAssetDecision(asset, status, notes)
                                },
                                loadVaultBitmap = { viewModel.loadVaultBitmap(it) }
                            )
                            StudioTab.LIBRARY -> AssetLibraryScreen(
                                assetsState = assetsState,
                                onReviewAsset = { asset, status, notes ->
                                    viewModel.reviewAssetDecision(asset, status, notes)
                                },
                                onScheduleApprovedAsset = { _ ->
                                    selectedTab = StudioTab.SCHEDULER
                                },
                                loadVaultBitmap = { viewModel.loadVaultBitmap(it) }
                            )
                            StudioTab.VIDEO -> VideoStudioScreen(viewModel = viewModel)
                            StudioTab.SCHEDULER -> SchedulerApprovalScreen(viewModel = viewModel)
                            StudioTab.MCP_OPS -> AnalyticsMcpBillingScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
