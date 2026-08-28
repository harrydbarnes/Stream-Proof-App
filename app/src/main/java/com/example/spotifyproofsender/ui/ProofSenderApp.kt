package com.example.spotifyproofsender.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spotifyproofsender.AppViewModel
import com.example.spotifyproofsender.data.AppSettings
import com.example.spotifyproofsender.data.DEFAULT_INSTAGRAM_INBOX_URL
import com.example.spotifyproofsender.data.DEFAULT_STATS_URL
import com.example.spotifyproofsender.data.ProofSlot
import com.example.spotifyproofsender.data.ProofStorage
import com.example.spotifyproofsender.data.ScreenshotFormat
import com.example.spotifyproofsender.data.isHttpsUrl
import com.example.spotifyproofsender.data.isInstagramGroupChatUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class AppTab(
    val label: String,
) {
    SPOTIFY("Stats"),
    INSTAGRAM("Instagram"),
    PROOFS("Proofs"),
}

@Composable
fun StreamProofApp(viewModel: AppViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val captureSlot by viewModel.captureSlot.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var preparedSlotIndex by rememberSaveable { mutableIntStateOf(-1) }
    var explicitInstagramUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var instagramNavigationNumber by rememberSaveable { mutableIntStateOf(0) }
    var nativePickerSelection by rememberSaveable { mutableStateOf<String?>(null) }
    val preparedSlot = ProofSlot.entries.getOrNull(preparedSlotIndex)

    val nativePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            nativePickerSelection = null
        } else {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            nativePickerSelection = ProofStorage.displayName(context.contentResolver, uri) ?: uri.toString()
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Selected ${nativePickerSelection ?: "image"}. Instagram Web still needs its own attachment selection.",
                )
            }
        }
    }

    val savedGroupUrl = settings.savedInstagramGroupUrl.trim()
    val defaultInstagramDestination = savedGroupUrl
        .takeIf { isInstagramGroupChatUrl(it) }
        ?: settings.instagramInboxUrl
    val instagramDestination = explicitInstagramUrl ?: defaultInstagramDestination
    val instagramNavigationKey = "$instagramNavigationNumber|$instagramDestination"

    fun navigateInstagram(url: String) {
        explicitInstagramUrl = url
        instagramNavigationNumber += 1
    }

    fun prepareProof(slot: ProofSlot) {
        val proof = settings.proofFor(slot)
        when {
            proof == null -> showMessage("Capture ${slot.label} in the Spotify Stats tab first.")
            !ProofStorage.exists(context.contentResolver, Uri.parse(proof.uri)) -> {
                showMessage("The saved ${slot.label} image is no longer available. Capture it again.")
            }

            else -> {
                preparedSlotIndex = slot.ordinal
                explicitInstagramUrl = settings.savedInstagramGroupUrl
                    .trim()
                    .takeIf { isInstagramGroupChatUrl(it) }
                    ?: settings.instagramInboxUrl
                instagramNavigationNumber += 1
                selectedTabIndex = AppTab.INSTAGRAM.ordinal
            }
        }
    }

    fun openExternalUrl(url: String, packageName: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            if (packageName != null) setPackage(packageName)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { showMessage("Could not open the link: ${it.message ?: "unknown error"}") }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == tab.ordinal,
                        onClick = { selectedTabIndex = tab.ordinal },
                        enabled = captureSlot == null,
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AppTab.SPOTIFY -> Icons.Default.Image
                                    AppTab.INSTAGRAM -> Icons.Default.CameraAlt
                                    AppTab.PROOFS -> Icons.Default.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Keep both WebViews in the composition. Inactive tabs are reduced to a tiny,
            // transparent view so login state and the current page survive tab changes.
            SpotifyStatsScreen(
                settings = settings,
                viewModel = viewModel,
                captureSlot = captureSlot,
                onPrepareProof = ::prepareProof,
                modifier = if (selectedTabIndex == AppTab.SPOTIFY.ordinal) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(1.dp).alpha(0f)
                },
            )
            InstagramScreen(
                settings = settings,
                preparedSlot = preparedSlot,
                currentNavigationUrl = instagramDestination,
                navigationKey = instagramNavigationKey,
                nativePickerSelection = nativePickerSelection,
                onNavigate = ::navigateInstagram,
                onPrepareProof = ::prepareProof,
                onSaveCurrentGroupUrl = viewModel::saveCurrentInstagramGroupUrl,
                onOpenPicker = {
                    runCatching { nativePickerLauncher.launch(arrayOf("image/*")) }
                        .onFailure { showMessage("Android could not open an image picker: ${it.message ?: "unknown error"}") }
                },
                onOpenExternal = ::openExternalUrl,
                onShare = { slot -> viewModel.shareProof(context, slot) },
                onMarkSent = viewModel::markSent,
                onMessage = ::showMessage,
                modifier = if (selectedTabIndex == AppTab.INSTAGRAM.ordinal) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.size(1.dp).alpha(0f)
                },
            )
            if (selectedTabIndex == AppTab.PROOFS.ordinal) {
                ProofsSettingsScreen(
                    settings = settings,
                    viewModel = viewModel,
                    onMessage = ::showMessage,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotifyStatsScreen(
    settings: AppSettings,
    viewModel: AppViewModel,
    captureSlot: ProofSlot?,
    onPrepareProof: (ProofSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val userAgent = resolveUserAgent(
        context = context,
        customUserAgent = settings.customStatsUserAgent,
        forceMobile = settings.forceMobileUserAgent,
        useDesktop = settings.statsDesktopUserAgent,
    )
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(settings.statsUrl) }
    var historyVersion by remember { mutableIntStateOf(0) }
    val canGoBack = remember(webView, historyVersion) { webView?.canGoBack() == true }
    val canGoForward = remember(webView, historyVersion) { webView?.canGoForward() == true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Spotify Stats") },
            actions = {
                IconButton(
                    onClick = { webView?.goBack() },
                    enabled = canGoBack,
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                IconButton(
                    onClick = { webView?.goForward() },
                    enabled = canGoForward,
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                }
                IconButton(
                    onClick = { webView?.reload() },
                    enabled = webView != null,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Stats page")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text(
                        errorMessage ?: "The page could not be loaded",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            ConfiguredWebView(
                modifier = Modifier.fillMaxSize(),
                initialUrl = settings.statsUrl,
                navigationKey = settings.statsUrl,
                userAgent = userAgent,
                allowThirdPartyCookies = settings.thirdPartyCookies,
                onPageStarted = { url ->
                    currentUrl = url
                    pageReady = false
                    loading = true
                    errorMessage = null
                },
                onPageFinished = { url ->
                    currentUrl = url
                    loading = false
                    pageReady = errorMessage == null
                },
                onPageError = { message ->
                    pageReady = false
                    loading = false
                    errorMessage = message
                },
                onUrlChanged = {
                    currentUrl = it
                    historyVersion += 1
                },
                onWebViewReady = { webView = it },
            )
        }

        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!pageReady && errorMessage == null) {
                    Text(
                        "Wait for Stats for Spotify to finish loading before capturing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { webView?.let { viewModel.captureProof(it, ProofSlot.PLAYLIST_1) } },
                        enabled = pageReady && captureSlot == null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp),
                    ) {
                        if (captureSlot == ProofSlot.PLAYLIST_1) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Capture Playlist 1 Proof")
                    }
                    Button(
                        onClick = { webView?.let { viewModel.captureProof(it, ProofSlot.PLAYLIST_2) } },
                        enabled = pageReady && captureSlot == null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 58.dp),
                    ) {
                        if (captureSlot == ProofSlot.PLAYLIST_2) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Capture Playlist 2 Proof")
                    }
                }

                val latestProofWithSlot = listOfNotNull(
                    settings.playlist1Proof?.let { ProofSlot.PLAYLIST_1 to it },
                    settings.playlist2Proof?.let { ProofSlot.PLAYLIST_2 to it },
                ).maxByOrNull { it.second.createdAt }
                val latestProof = latestProofWithSlot?.second
                if (latestProof != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProofThumbnail(latestProof, Modifier.size(42.dp))
                        Text(
                            latestProof.displayName,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { onPrepareProof(latestProofWithSlot!!.first) }) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Go to Instagram")
                        }
                    }
                }
                Text(
                    currentUrl,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstagramScreen(
    settings: AppSettings,
    preparedSlot: ProofSlot?,
    currentNavigationUrl: String,
    navigationKey: String,
    nativePickerSelection: String?,
    onNavigate: (String) -> Unit,
    onPrepareProof: (ProofSlot) -> Unit,
    onSaveCurrentGroupUrl: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenExternal: (String, String?) -> Unit,
    onShare: (ProofSlot) -> Unit,
    onMarkSent: (ProofSlot, Boolean) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val userAgent = resolveUserAgent(
        context = context,
        customUserAgent = settings.customInstagramUserAgent,
        forceMobile = settings.forceMobileUserAgent,
        useDesktop = settings.instagramDesktopUserAgent,
    )
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember(currentNavigationUrl) { mutableStateOf(currentNavigationUrl) }
    var historyVersion by remember { mutableIntStateOf(0) }
    val canGoBack = remember(webView, historyVersion) { webView?.canGoBack() == true }
    val helperEnabled = settings.instagramHelperClicks
    val instagramInstalled = isPackageInstalled(context, "com.instagram.android")
    val chromeInstalled = isPackageInstalled(context, "com.android.chrome")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Instagram") },
            actions = {
                IconButton(
                    onClick = { webView?.reload() },
                    enabled = webView != null,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Instagram page")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { onNavigate(settings.instagramInboxUrl) },
                label = { Text("Open Inbox") },
                leadingIcon = {
                    Icon(Icons.Default.Inbox, contentDescription = null)
                },
            )
            AssistChip(
                onClick = {
                    if (!isInstagramGroupChatUrl(settings.savedInstagramGroupUrl)) {
                        onMessage("No saved group chat URL. Open the chat, then save the current URL.")
                    } else {
                        onNavigate(settings.savedInstagramGroupUrl.trim())
                    }
                },
                label = { Text("Saved group chat") },
                leadingIcon = {
                    Icon(Icons.Default.Send, contentDescription = null)
                },
            )
            AssistChip(
                onClick = { onSaveCurrentGroupUrl(currentUrl) },
                label = { Text("Save current URL") },
                leadingIcon = {
                    Icon(Icons.Default.Save, contentDescription = null)
                },
            )
            IconButton(
                onClick = { webView?.goBack() },
                enabled = canGoBack,
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }

        Text(
            currentUrl,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { onPrepareProof(ProofSlot.PLAYLIST_1) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
            ) {
                Text("Prepare Playlist 1 Proof")
            }
            FilledTonalButton(
                onClick = { onPrepareProof(ProofSlot.PLAYLIST_2) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
            ) {
                Text("Prepare Playlist 2 Proof")
            }
        }
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text(
                        errorMessage ?: "The page could not be loaded",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            ConfiguredWebView(
                modifier = Modifier.fillMaxSize(),
                initialUrl = currentNavigationUrl,
                navigationKey = navigationKey,
                userAgent = userAgent,
                allowThirdPartyCookies = settings.thirdPartyCookies,
                fileChooserEnabled = true,
                onPageStarted = { url ->
                    currentUrl = url
                    loading = true
                    errorMessage = null
                },
                onPageFinished = { url ->
                    currentUrl = url
                    loading = false
                },
                onPageError = { message ->
                    loading = false
                    errorMessage = message
                },
                onUrlChanged = {
                    currentUrl = it
                    historyVersion += 1
                },
                onWebViewReady = { webView = it },
            )

            if (preparedSlot != null) {
                val proof = settings.proofFor(preparedSlot)
                ProofSendPanel(
                    // Keep the composer area at the bottom of Instagram visible. The panel
                    // can be collapsed when the user needs to interact with the conversation.
                    modifier = Modifier.align(Alignment.TopCenter),
                    slot = preparedSlot,
                    proof = proof,
                    sent = settings.sentFor(preparedSlot),
                    hasSavedGroupUrl = isInstagramGroupChatUrl(settings.savedInstagramGroupUrl),
                    nativePickerSelection = nativePickerSelection,
                    instagramInstalled = instagramInstalled,
                    chromeInstalled = chromeInstalled,
                    helperEnabled = helperEnabled,
                    onOpenPicker = onOpenPicker,
                    onOpenInstagramApp = {
                        val url = settings.savedInstagramGroupUrl
                            .takeIf { isInstagramGroupChatUrl(it) }
                            ?: currentUrl
                        onOpenExternal(url, "com.instagram.android")
                    },
                    onOpenChrome = {
                        val url = settings.savedInstagramGroupUrl
                            .takeIf { isInstagramGroupChatUrl(it) }
                            ?: currentUrl
                        onOpenExternal(url, if (chromeInstalled) "com.android.chrome" else null)
                    },
                    onShare = { onShare(preparedSlot) },
                    onMarkSent = { onMarkSent(preparedSlot, true) },
                    onTryHelper = {
                        val activeWebView = webView
                        if (activeWebView == null) {
                            onMessage("Instagram Web is not ready yet.")
                        } else {
                            tryInstagramAttachmentHelper(activeWebView, onMessage)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProofSendPanel(
    slot: ProofSlot,
    proof: com.example.spotifyproofsender.data.ProofRecord?,
    sent: Boolean,
    hasSavedGroupUrl: Boolean,
    nativePickerSelection: String?,
    instagramInstalled: Boolean,
    chromeInstalled: Boolean,
    helperEnabled: Boolean,
    onOpenPicker: () -> Unit,
    onOpenInstagramApp: () -> Unit,
    onOpenChrome: () -> Unit,
    onShare: () -> Unit,
    onMarkSent: () -> Unit,
    onTryHelper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(slot) {
        expanded = true
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = if (expanded) 320.dp else 78.dp)
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${slot.label} ready",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { expanded = !expanded },
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) {
                            "Collapse proof actions"
                        } else {
                            "Expand proof actions"
                        },
                    )
                }
            }
            if (expanded) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (proof == null) {
                        Text("No proof reference is available. Return to Spotify Stats and capture it first.")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SelectionContainerText(proof.displayName, Modifier.weight(1f))
                            CopyFilenameButton(proof.displayName)
                        }
                        Text(
                            "Screenshot ready: ${proof.displayName}. Tap Instagram's image/photo button, choose the latest SpotifyProof image, then send.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!hasSavedGroupUrl) {
                            Text(
                                "No saved group chat URL. Open the group chat in this tab, then tap Save Current Instagram URL as Group Chat.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            "The Android picker cannot inject a file into Instagram Web. Manual selection and sending are required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = onOpenPicker,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open Android Photo Picker / Files")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (instagramInstalled) {
                                OutlinedButton(
                                    onClick = onOpenInstagramApp,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open in Instagram app")
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenChrome,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (chromeInstalled) "Open in Chrome" else "Open in browser")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Share via Android\n(may not support group chats)")
                            }
                            Button(
                                onClick = onMarkSent,
                                enabled = !sent,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(if (sent) "Marked sent" else "Mark sent")
                            }
                        }
                        if (helperEnabled) {
                            OutlinedButton(
                                onClick = onTryHelper,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Try Instagram Web helper clicks")
                            }
                        }
                        if (nativePickerSelection != null) {
                            Text(
                                "Native picker selected: $nativePickerSelection. Select the same file again when Instagram Web opens its attachment picker.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Tap to show attachment and fallback actions. Instagram's composer remains visible below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectionContainerText(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.selection.SelectionContainer(modifier = modifier) {
        Text(
            text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun CopyFilenameButton(filename: String) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Spotify proof filename", filename))
        },
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Copy filename")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProofsSettingsScreen(
    settings: AppSettings,
    viewModel: AppViewModel,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var statsUrl by remember(settings.statsUrl) { mutableStateOf(settings.statsUrl) }
    var instagramInboxUrl by remember(settings.instagramInboxUrl) { mutableStateOf(settings.instagramInboxUrl) }
    var savedGroupUrl by remember(settings.savedInstagramGroupUrl) { mutableStateOf(settings.savedInstagramGroupUrl) }
    var customStatsUserAgent by remember(settings.customStatsUserAgent) { mutableStateOf(settings.customStatsUserAgent) }
    var customInstagramUserAgent by remember(settings.customInstagramUserAgent) { mutableStateOf(settings.customInstagramUserAgent) }
    var jpegQuality by remember(settings.jpegQuality) { mutableStateOf(settings.jpegQuality.toFloat()) }
    var captureDelay by remember(settings.captureDelayMs) { mutableStateOf(settings.captureDelayMs.toFloat()) }
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all settings?") },
            text = { Text("This restores the defaults and forgets both proof references. Images already saved in Pictures/SpotifyProof will remain there.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        viewModel.resetAllSettings()
                    },
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = { Text("Proofs / Settings") },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Session checklist") {
                    ChecklistRow("Playlist 1 captured", settings.playlist1Proof != null)
                    ChecklistRow("Playlist 1 sent", settings.playlist1Sent)
                    ChecklistRow("Playlist 2 captured", settings.playlist2Proof != null)
                    ChecklistRow("Playlist 2 sent", settings.playlist2Sent)
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProofSummaryCard(
                        title = "Playlist 1 proof",
                        proof = settings.playlist1Proof,
                        sent = settings.playlist1Sent,
                        onView = { viewModel.viewProof(context, ProofSlot.PLAYLIST_1) },
                        onShare = { viewModel.shareProof(context, ProofSlot.PLAYLIST_1) },
                        onDeleteReference = { viewModel.clearProofReference(ProofSlot.PLAYLIST_1) },
                    )
                    ProofSummaryCard(
                        title = "Playlist 2 proof",
                        proof = settings.playlist2Proof,
                        sent = settings.playlist2Sent,
                        onView = { viewModel.viewProof(context, ProofSlot.PLAYLIST_2) },
                        onShare = { viewModel.shareProof(context, ProofSlot.PLAYLIST_2) },
                        onDeleteReference = { viewModel.clearProofReference(ProofSlot.PLAYLIST_2) },
                    )
                }
            }
            item {
                SettingsSectionCard(title = "URLs") {
                    OutlinedTextField(
                        value = statsUrl,
                        onValueChange = { statsUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Stats URL") },
                        supportingText = { Text("Default: $DEFAULT_STATS_URL") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = instagramInboxUrl,
                        onValueChange = { instagramInboxUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Instagram inbox URL") },
                        supportingText = { Text("Default: $DEFAULT_INSTAGRAM_INBOX_URL") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = savedGroupUrl,
                        onValueChange = { savedGroupUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Saved Instagram group chat URL") },
                        supportingText = { Text("Use an Instagram /direct/t/... URL, or leave blank to start at the inbox") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val valid = isHttpsUrl(statsUrl) && isHttpsUrl(instagramInboxUrl) &&
                                (savedGroupUrl.isBlank() || isInstagramGroupChatUrl(savedGroupUrl))
                            if (!valid) {
                                onMessage("Use valid HTTPS URLs and an Instagram /direct/t/... group chat URL.")
                            } else {
                                viewModel.saveUrls(statsUrl, instagramInboxUrl, savedGroupUrl)
                            }
                        },
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save URL settings")
                    }
                }
            }
            item {
                SettingsSectionCard(title = "Screenshot format") {
                    Text("PNG keeps text lossless. JPG is smaller and uses the quality below.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScreenshotFormat.entries.forEach { format ->
                            FilterChip(
                                selected = settings.screenshotFormat == format,
                                onClick = { viewModel.setScreenshotFormat(format) },
                                label = { Text(format.name) },
                            )
                        }
                    }
                    Text("JPEG quality: ${jpegQuality.roundToInt()}")
                    Slider(
                        value = jpegQuality,
                        onValueChange = { jpegQuality = it },
                        onValueChangeFinished = { viewModel.setJpegQuality(jpegQuality.roundToInt()) },
                        valueRange = 10f..100f,
                        steps = 8,
                        enabled = settings.screenshotFormat == ScreenshotFormat.JPG,
                    )
                    Text("Capture delay: ${captureDelay.roundToLong()} ms")
                    Slider(
                        value = captureDelay,
                        onValueChange = { captureDelay = it },
                        onValueChangeFinished = { viewModel.setCaptureDelay(captureDelay.roundToLong()) },
                        valueRange = 0f..3_000f,
                        steps = 29,
                    )
                    Text(
                        "The delay lets the page settle immediately before the visible WebView is drawn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SettingsSectionCard(title = "WebView behaviour") {
                    SettingSwitchRow(
                        title = "Use desktop user agent for Stats",
                        description = "Reloads the Stats WebView with a desktop Chrome user agent.",
                        checked = settings.statsDesktopUserAgent,
                        onCheckedChange = viewModel::setStatsDesktopUserAgent,
                    )
                    SettingSwitchRow(
                        title = "Use desktop user agent for Instagram",
                        description = "May expose a different Instagram Web layout.",
                        checked = settings.instagramDesktopUserAgent,
                        onCheckedChange = viewModel::setInstagramDesktopUserAgent,
                    )
                    SettingSwitchRow(
                        title = "Force mobile user agent",
                        description = "Takes precedence over the desktop toggles unless a custom user agent is set.",
                        checked = settings.forceMobileUserAgent,
                        onCheckedChange = viewModel::setForceMobileUserAgent,
                    )
                    SettingSwitchRow(
                        title = "Enable third-party cookies",
                        description = "Useful for some login and embedded Instagram flows.",
                        checked = settings.thirdPartyCookies,
                        onCheckedChange = viewModel::setThirdPartyCookies,
                    )
                    SettingSwitchRow(
                        title = "Try Instagram Web helper clicks",
                        description = "Experimental, off by default. It only looks for an obvious image button and never sends a message or injects a file.",
                        checked = settings.instagramHelperClicks,
                        onCheckedChange = viewModel::setInstagramHelperClicks,
                    )
                }
            }
            item {
                SettingsSectionCard(title = "Custom user agents") {
                    Text(
                        "Leave blank to use the default mobile WebView agent or the desktop toggle. Changes apply when the WebView reloads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = customStatsUserAgent,
                        onValueChange = { customStatsUserAgent = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom Stats user agent") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    OutlinedTextField(
                        value = customInstagramUserAgent,
                        onValueChange = { customInstagramUserAgent = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Custom Instagram user agent") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.setCustomStatsUserAgent(customStatsUserAgent)
                            viewModel.setCustomInstagramUserAgent(customInstagramUserAgent)
                            onMessage("Custom user-agent settings saved")
                        },
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save custom user agents")
                    }
                }
            }
            item {
                SettingsSectionCard(title = "Privacy and reset") {
                    Text(
                        "Cookie clearing is best effort and scoped to the selected domain. Android WebView uses a shared cookie store, so unusual third-party cookies may remain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { viewModel.clearDomainCookies("statsforspotify.com", "Stats for Spotify") }) {
                        Text("Clear Spotify/Stats WebView cookies")
                    }
                    OutlinedButton(onClick = { viewModel.clearDomainCookies("instagram.com", "Instagram") }) {
                        Text("Clear Instagram WebView cookies")
                    }
                    Button(onClick = { showResetDialog = true }) {
                        Text("Reset all settings")
                    }
                }
            }
            item {
                Text(
                    "Images are saved under Pictures/SpotifyProof using Android scoped storage. No broad storage permission is requested.",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) = SectionCard(title = title, content = content)

@Composable
private fun ChecklistRow(label: String, complete: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (complete) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(label)
    }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)
