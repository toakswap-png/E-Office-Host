package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.data.HostRule
import com.example.viewmodel.HostsBrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsBrowserApp(
    viewModel: HostsBrowserViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isProxyActive by viewModel.isProxyActive.collectAsStateWithLifecycle()
    val proxyPort by viewModel.proxyPort.collectAsStateWithLifecycle()
    val ignoreSslErrors by viewModel.ignoreSslErrors.collectAsStateWithLifecycle()

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val grantedList = results.filterValues { it }.keys
        Log.d("Permissions", "Granted permissions: $grantedList")
    }

    LaunchedEffect(Unit) {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        permissionsLauncher.launch(permissions)
    }

    // Whenever proxyPort shifts, update WebView proxy mappings globally inside Chromium
    LaunchedEffect(proxyPort) {
        val port = proxyPort
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (port > 0) {
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("127.0.0.1:$port")
                    .addDirect()
                    .build()
                try {
                    ProxyController.getInstance().setProxyOverride(proxyConfig, { command -> command.run() }, {
                        Log.d("ProxyConfig", "WebView Proxy Overridden dynamically on port $port")
                    })
                } catch (e: Exception) {
                    Log.e("ProxyConfig", "Proxy controller call failed", e)
                }
            } else {
                try {
                    ProxyController.getInstance().clearProxyOverride({ command -> command.run() }, {
                        Log.d("ProxyConfig", "WebView Proxy Override Cleared")
                    })
                } catch (e: Exception) {
                    Log.e("ProxyConfig", "Proxy controller clear call failed", e)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "E-Office HDSC",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isProxyActive) Color(0xFF4CAF50) else Color(0xFFF44336))
                            )
                            Text(
                                text = if (isProxyActive) "Proxy Active (Port $proxyPort)" else "Proxy Idle (Tap to start)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleProxy() },
                        modifier = Modifier.testTag("proxy_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isProxyActive) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = if (isProxyActive) "Pause Proxy" else "Start Proxy",
                            tint = if (isProxyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Browser") },
                    label = { Text("Browser", fontSize = 12.sp) },
                    modifier = Modifier.testTag("tab_browser")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Host Mapping") },
                    label = { Text("Host Maps", fontSize = 12.sp) },
                    modifier = Modifier.testTag("tab_host_maps")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Help Guide") },
                    label = { Text("Help", fontSize = 12.sp) },
                    modifier = Modifier.testTag("tab_help")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> WebBrowserTab(viewModel = viewModel)
                1 -> HostRulesManagerTab(viewModel = viewModel)
                2 -> HelpGuideTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun CompactToggleChip(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val textColor = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
fun CompactLinkChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

@Composable
fun WebBrowserLoadingOverlay(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_loading")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        rotationZ = rotationAngle
                    }
                    .background(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFF1E88E5),
                                Color(0xFF00B0FF),
                                Color(0xFF0288D1),
                                Color(0xFF1E88E5)
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Secure Loading",
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer {
                                rotationZ = -rotationAngle
                            },
                        tint = Color(0xFF1E88E5)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "eOffice Loading...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF1E88E5)
                )
                Text(
                    text = "Loading: $progress%",
                    fontSize = 12.sp,
                    color = Color(0xFF1E88E5).copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E88E5).copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color(0xFF1E88E5).copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info icon",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF1E88E5)
                    )
                    Text(
                        text = "By AKS Hardoi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E88E5),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebBrowserTab(viewModel: HostsBrowserViewModel) {
    val browserUrl by viewModel.browserUrl.collectAsStateWithLifecycle()
    val ignoreSslErrors by viewModel.ignoreSslErrors.collectAsStateWithLifecycle()
    var urlText by remember { mutableStateOf(browserUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var textZoomLevel by remember { mutableIntStateOf(80) }
    var isDesktopMode by remember { mutableStateOf(true) }

    var filePathCallbackRef by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val dataUri = result.data?.data
            val dataUris = result.data?.clipData?.let { clipData ->
                val list = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    list.add(clipData.getItemAt(i).uri)
                }
                list.toTypedArray()
            }
            val results = when {
                dataUris != null -> dataUris
                dataUri != null -> arrayOf(dataUri)
                else -> null
            }
            filePathCallbackRef?.onReceiveValue(results)
        } else {
            filePathCallbackRef?.onReceiveValue(null)
        }
        filePathCallbackRef = null
    }

    // Synchronize browser URL bar input when loaded URL changes
    LaunchedEffect(browserUrl) {
        if (browserUrl != urlText) {
            urlText = browserUrl
        }
        webViewRef?.loadUrl(browserUrl)
    }

    // Capture system back press within the app to support proper WebView navigation backstack
    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Address input bar area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = webViewRef?.canGoBack() == true,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("browser_back")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(24.dp),
                            tint = if (webViewRef?.canGoBack() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = webViewRef?.canGoForward() == true,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("browser_forward")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(24.dp),
                            tint = if (webViewRef?.canGoForward() == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    TextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("url_input_field"),
                        placeholder = { Text("http://google.in", fontSize = 13.sp) },
                        singleLine = true,
                        leadingIcon = {
                            val isHttps = urlText.startsWith("https://", ignoreCase = true)
                            Icon(
                                imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.Warning,
                                contentDescription = if (isHttps) "Secure connection" else "Unsecure connection",
                                tint = if (isHttps) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (urlText.isNotEmpty()) {
                                IconButton(
                                    onClick = { urlText = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { viewModel.updateBrowserUrl(urlText) }
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                    )

                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("browser_reload")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.updateBrowserUrl(urlText) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("load_url_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Go",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = {
                            val query = urlText.trim()
                            if (query.isNotEmpty()) {
                                val encoded = try {
                                    java.net.URLEncoder.encode(query, "UTF-8")
                                } catch (e: Exception) {
                                    query
                                }
                                viewModel.updateBrowserUrl("https://www.google.com/search?q=$encoded")
                            } else {
                                viewModel.updateBrowserUrl("https://www.google.com")
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("google_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Google Search",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactToggleChip(
                        text = "SSL Bypass",
                        checked = ignoreSslErrors,
                        onCheckedChange = { viewModel.setIgnoreSslErrors(it) },
                        modifier = Modifier.testTag("checkbox_bypass_ssl")
                    )

                    CompactToggleChip(
                        text = "Desktop Mode",
                        checked = isDesktopMode,
                        onCheckedChange = { isDesktopMode = it },
                        modifier = Modifier.testTag("checkbox_desktop_mode")
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )

                    CompactLinkChip(
                        text = "UP eOffice (e-File)",
                        onClick = { viewModel.updateBrowserUrl("https://districts.upeoffice.gov.in") }
                    )

                    CompactLinkChip(
                        text = "Google",
                        onClick = { viewModel.updateBrowserUrl("https://www.google.com") }
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Zoom: $textZoomLevel%",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable { if (textZoomLevel > 50) textZoomLevel -= 5 },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable { if (textZoomLevel < 150) textZoomLevel += 5 },
                              contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Horizontal web progress indication
        if (isWebLoading) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Web view integration frame with built-in custom animated loading layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            textZoom = textZoomLevel
                            if (isDesktopMode) {
                                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            } else {
                                userAgentString = null
                            }
                        }

                        setDownloadListener { url, userAgentLocal, contentDisposition, mimetype, contentLength ->
                            try {
                                val request = DownloadManager.Request(Uri.parse(url)).apply {
                                    setMimeType(mimetype)
                                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    if (cookies != null) {
                                        addRequestHeader("Cookie", cookies)
                                    }
                                    
                                    addRequestHeader("User-Agent", userAgentLocal)
                                    setTitle(fileName)
                                    setDescription("Downloading file")
                                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                                }
                                
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.enqueue(request)
                                
                                Toast.makeText(context, "Download started: ${URLUtil.guessFileName(url, contentDisposition, mimetype)}", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isWebLoading = true
                                loadProgress = 10
                                url?.let { urlText = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isWebLoading = false
                                loadProgress = 100
                                url?.let { urlText = url ?: "" }

                                // Inject CSS to scale webpage images to 50% size
                                val cssInject = """
                                    (function() {
                                        var style = document.getElementById('webview-img-50-percent');
                                        if (!style) {
                                            style = document.createElement('style');
                                            style.id = 'webview-img-50-percent';
                                            style.type = 'text/css';
                                            style.innerHTML = 'img { max-width: 50% !important; height: auto !important; }';
                                            document.head.appendChild(style);
                                        }
                                    })()
                                """.trimIndent()
                                view?.evaluateJavascript(cssInject, null)

                                // Force Desktop Viewport Scaling
                                if (isDesktopMode) {
                                    val viewportScript = """
                                        (function() {
                                            var meta = document.querySelector('meta[name="viewport"]');
                                            if (meta) {
                                                meta.setAttribute('content', 'width=1280, initial-scale=0.35, minimum-scale=0.1, maximum-scale=3.5, user-scalable=yes');
                                            } else {
                                                meta = document.createElement('meta');
                                                meta.name = 'viewport';
                                                meta.content = 'width=1280, initial-scale=0.35, minimum-scale=0.1, maximum-scale=3.5, user-scalable=yes';
                                                document.getElementsByTagName('head')[0].appendChild(meta);
                                            }
                                        })()
                                    """.trimIndent()
                                    view?.evaluateJavascript(viewportScript, null)

                                    // Inject comprehensive landscape bypass script
                                    val landscapeBypassScript = """
                                        (function() {
                                            try {
                                                Object.defineProperty(window, 'orientation', { get: function() { return 90; } });
                                            } catch(e) {}
                                            try {
                                                if (screen.orientation) {
                                                    Object.defineProperty(screen.orientation, 'type', { get: function() { return 'landscape-primary'; } });
                                                    Object.defineProperty(screen.orientation, 'angle', { get: function() { return 90; } });
                                                }
                                            } catch(e) {}
                                            try {
                                                var w = Math.max(window.screen.width, window.screen.height) || 1280;
                                                var h = Math.min(window.screen.width, window.screen.height) || 800;
                                                Object.defineProperty(window.screen, 'width', { get: function() { return w; } });
                                                Object.defineProperty(window.screen, 'height', { get: function() { return h; } });
                                                Object.defineProperty(window.screen, 'availWidth', { get: function() { return w; } });
                                                Object.defineProperty(window.screen, 'availHeight', { get: function() { return h; } });
                                            } catch(e) {}
                                            try {
                                                var iw = Math.max(window.innerWidth, window.innerHeight) || 1280;
                                                var ih = Math.min(window.innerWidth, window.innerHeight) || 720;
                                                Object.defineProperty(window, 'innerWidth', { get: function() { return iw; } });
                                                Object.defineProperty(window, 'innerHeight', { get: function() { return ih; } });
                                            } catch(e) {}

                                            // Suppress or remove any block overlays showing landscape advisories or rotation alerts
                                            function hideLandscapeMessageOverlays() {
                                                var keywords = [
                                                    'landscape mode', 'landscape orientation', 'landscape layout', 'rotate your', 
                                                    'rotate to landscape', 'please use landscape', 'portrait mode not supported',
                                                    'best viewed in landscape', 'only in landscape', 'landscape only', 'use landscape',
                                                    'landscape_mode', 'landscape-mode'
                                                ];
                                                var allElements = document.getElementsByTagName('*');
                                                for (var i = 0; i < allElements.length; i++) {
                                                    var el = allElements[i];
                                                    if (!el) continue;
                                                    if (el.tagName === 'BODY' || el.tagName === 'HTML') continue;

                                                    var isHint = false;
                                                    if (el.innerText) {
                                                        var textLow = el.innerText.toLowerCase();
                                                        for (var j = 0; j < keywords.length; j++) {
                                                            if (textLow.indexOf(keywords[j]) !== -1) {
                                                                isHint = true;
                                                                break;
                                                            }
                                                        }
                                                    }

                                                    var idOrClassLow = ((el.id || '') + ' ' + (el.className || '')).toLowerCase();
                                                    if (idOrClassLow.indexOf('landscape') !== -1 || idOrClassLow.indexOf('rotate') !== -1 || idOrClassLow.indexOf('orientation') !== -1) {
                                                        isHint = true;
                                                    }

                                                    if (isHint) {
                                                        var computed = window.getComputedStyle(el);
                                                        if (computed.position === 'fixed' || computed.position === 'absolute' || computed.zIndex > 5 || idOrClassLow.indexOf('landscape') !== -1) {
                                                            el.style.setProperty('display', 'none', 'important');
                                                            el.style.setProperty('visibility', 'hidden', 'important');
                                                            el.style.setProperty('opacity', '0', 'important');
                                                            el.style.setProperty('pointer-events', 'none', 'important');
                                                        }
                                                    }
                                                }
                                            }

                                            hideLandscapeMessageOverlays();
                                            if (!window._landscapeIntervalStarted) {
                                                window._landscapeIntervalStarted = true;
                                                setInterval(hideLandscapeMessageOverlays, 700);
                                            }
                                        })()
                                    """.trimIndent()
                                    view?.evaluateJavascript(landscapeBypassScript, null)
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                isWebLoading = true
                                loadProgress = 10
                                return false // Direct all loading within this webview frame
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                if (ignoreSslErrors) {
                                    handler?.proceed() // Proceed safely bypassing custom/untrusted SSL certs in intranets
                                } else {
                                    handler?.cancel()
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                loadProgress = newProgress
                                if (newProgress == 100) {
                                    isWebLoading = false
                                }

                                // Inject early orientation & screen size spoofing to bypass loading blockers
                                if (newProgress > 25 && isDesktopMode) {
                                    val earlyScript = """
                                        (function() {
                                            try {
                                                Object.defineProperty(window, 'orientation', { get: function() { return 90; } });
                                            } catch(e) {}
                                            try {
                                                if (screen.orientation) {
                                                    Object.defineProperty(screen.orientation, 'type', { get: function() { return 'landscape-primary'; } });
                                                    Object.defineProperty(screen.orientation, 'angle', { get: function() { return 90; } });
                                                }
                                            } catch(e) {}
                                            try {
                                                var w = Math.max(window.screen.width, window.screen.height) || 1280;
                                                var h = Math.min(window.screen.width, window.screen.height) || 800;
                                                Object.defineProperty(window.screen, 'width', { get: function() { return w; } });
                                                Object.defineProperty(window.screen, 'height', { get: function() { return h; } });
                                                Object.defineProperty(window.screen, 'availWidth', { get: function() { return w; } });
                                                Object.defineProperty(window.screen, 'availHeight', { get: function() { return h; } });
                                            } catch(e) {}
                                            try {
                                                var iw = Math.max(window.innerWidth, window.innerHeight) || 1280;
                                                var ih = Math.min(window.innerWidth, window.innerHeight) || 720;
                                                Object.defineProperty(window, 'innerWidth', { get: function() { return iw; } });
                                                Object.defineProperty(window, 'innerHeight', { get: function() { return ih; } });
                                            } catch(e) {}
                                        })()
                                    """.trimIndent()
                                    view?.evaluateJavascript(earlyScript, null)
                                }
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                filePathCallbackRef?.onReceiveValue(null)
                                filePathCallbackRef = filePathCallback

                                val intent = fileChooserParams?.createIntent() ?: android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                    type = "*/*"
                                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                }
                                try {
                                    fileChooserLauncher.launch(intent)
                                } catch (e: Exception) {
                                    filePathCallbackRef?.onReceiveValue(null)
                                    filePathCallbackRef = null
                                    Toast.makeText(context, "Cannot open file chooser", Toast.LENGTH_SHORT).show()
                                    return false
                                }
                                return true
                            }
                        }

                        webViewRef = this
                        loadUrl(browserUrl)
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    webView.settings.textZoom = textZoomLevel

                    val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    val currentUA = webView.settings.userAgentString
                    val targetUA = if (isDesktopMode) desktopUA else null

                    if (currentUA != targetUA) {
                        webView.settings.userAgentString = targetUA
                        webView.reload()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = isWebLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                WebBrowserLoadingOverlay(progress = loadProgress)
            }
        }
    }
}

@Composable
fun HostRulesManagerTab(viewModel: HostsBrowserViewModel) {
    val rules by viewModel.hostRules.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Intranet Host Name Mappings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Define virtual host rules below. These resolve local domains to secure corporate server IP tunnels directly, mimicking /etc/hosts changes without root.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "No Mappings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No custom hosts active.",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Tap the '+' button down below to add a custom server address rule.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        HostRuleCard(
                            rule = rule,
                            onToggle = { viewModel.toggleRuleEnabled(rule) },
                            onDelete = { viewModel.deleteRule(rule) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_rule_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Rule")
        }

        if (showAddDialog) {
            AddRuleDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { host, ip, desc ->
                    viewModel.addHostRule(host, ip, desc)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun HostRuleCard(
    rule: HostRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("host_rule_${rule.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.hostname,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (rule.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = rule.ipAddress,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (rule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (rule.description.isNotEmpty()) {
                    Text(
                        text = rule.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("rule_toggle_${rule.id}")
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("rule_delete_${rule.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Mapping",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var hostname by remember { mutableStateOf("") }
    var ipAddress by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Add Host Mapping",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                TextField(
                    value = hostname,
                    onValueChange = { hostname = it },
                    label = { Text("Hostname (e.g. eoffsigner.eoffice.gov.in)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_hostname"),
                    singleLine = true
                )

                TextField(
                    value = ipAddress,
                    onValueChange = { ipAddress = it },
                    label = { Text("IP Address (e.g. 127.0.0.1)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_ip_address"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_description"),
                    singleLine = true
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (hostname.isBlank()) {
                                errorMsg = "Hostname cannot be blank."
                            } else if (ipAddress.isBlank()) {
                                errorMsg = "IP Address cannot be blank."
                            } else {
                                onConfirm(hostname, ipAddress, description)
                            }
                        },
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("dialog_confirm_button")
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun HelpGuideTab(viewModel: HostsBrowserViewModel) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Setup & VPN Configuration Guide",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Below are direct instructions on how to use eOffice under VPN environments without running into non-rooted DNS lookup issues.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Why this app is required:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "To run government/intranet e-office tools, domains like 'districts.upeoffice.gov.in' must point to private servers like '192.168.39.110'. Similarly, e-signing tools link with eoffsigner on localhost '127.0.0.1'. Since Android restrains normal users from editing '/etc/hosts' without root access, this app runs an encrypted TCP DNS overlay proxy locally. Modern Chromium renders pages while our loopback socket forces correct IP routing over AnyConnect VPN tunnels safely.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "How to connect step-by-step:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                HelpStepItem(
                    stepNum = "1",
                    title = "Connect AnyConnect VPN",
                    desc = "Open your AnyConnect client on Android. Input server configurations and connect successfully."
                )

                HelpStepItem(
                    stepNum = "2",
                    title = "Verify Dynamic Proxy Active Status",
                    desc = "Ensure the proxy is active. Check the green status badge indicating the server port is listening."
                )

                HelpStepItem(
                    stepNum = "3",
                    title = "Use Custom DNS Browser Tab",
                    desc = "Tap on the first 'Browser' tab, enter 'https://districts.upeoffice.gov.in/efile/' and enjoy flawless secure access with all login/signing operations intact!"
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun HelpStepItem(stepNum: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNum,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Modifier.size48Modifier(): Modifier {
    return this.size(32.dp) // Perfect fit inside buttons
}
