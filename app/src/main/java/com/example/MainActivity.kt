package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AlertLog
import com.example.data.MonitoredPage
import com.example.ui.WebMonitorViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                ) { innerPadding ->
                    WebMonitorScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@SuppressLint("ModifierParameter")
@Composable
fun WebMonitorScreen(
    modifier: Modifier = Modifier,
    viewModel: WebMonitorViewModel = viewModel()
) {
    val context = LocalContext.current
    val pages by viewModel.allPages.collectAsStateWithLifecycle()
    val alertLogs by viewModel.allAlerts.collectAsStateWithLifecycle()
    val checkingPagesState by viewModel.checkingPagesState.collectAsStateWithLifecycle()
    val isCheckingAll by viewModel.isCheckingAll.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0 = Websites, 1 = Notifications
    var isAddFormExpanded by remember { mutableStateOf(false) }

    // State for notification permission card
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 1. Top Decorative App Header ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notification Alerts",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Web Monitor",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Alerter & Web Notifier",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Global Swipe Check Status Button
                if (isCheckingAll) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButton(
                        onClick = { viewModel.checkAllPagesNow() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("refresh_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Sync All Websites Now",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // --- 2. Dynamic Notification Request Banner ---
        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Security Alert",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Action Required: Enforce Notifications",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "This app requires notification permissions to alert you of changes and status updates in the background.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("grant_notification_permission_button")
                        ) {
                            Text("Authorize Alerts", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 3. Persistent Background Task Indicator ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "System Status Info",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Background daemon active. Webpages checks occur automatically even if app is fully closed.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // --- 4. Collapsible Add Web Page Form ---
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAddFormExpanded = !isAddFormExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAddFormExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.Add,
                        contentDescription = "Toggle Add Page",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Monitor a New Live Webpage",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (!isAddFormExpanded) {
                    IconButton(
                        onClick = { isAddFormExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Quick Add",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isAddFormExpanded) {
                AddPageForm(onAddPage = { url, name, keyword ->
                    viewModel.addPage(url, name, keyword)
                    isAddFormExpanded = false
                })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

        // --- 5. Navigation Tab Layout (Websites vs Notifications) ---
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Monitored sites (${pages.size})", fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("websites_tab")
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val badgeCount = alertLogs.size
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (badgeCount > 0) "Alert Log ($badgeCount)" else "Alert Log",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                modifier = Modifier.testTag("alerts_tab")
            )
        }

        // --- 6. Scrollable Dynamic Content Lists ---
        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 0) {
                WebsitesListView(
                    pages = pages,
                    checkingPagesState = checkingPagesState,
                    onToggleActive = { viewModel.togglePageActiveStatus(it) },
                    onRefresh = { viewModel.checkPageNow(it) },
                    onDelete = { viewModel.deletePage(it) }
                )
            } else {
                AlertHistoryListView(
                    alertLogs = alertLogs,
                    onClearAll = { viewModel.clearAllAlertHistory() }
                )
            }
        }
    }
}

@Composable
fun AddPageForm(
    onAddPage: (url: String, name: String, keyword: String?) -> Unit
) {
    var urlInput by remember { mutableStateOf("") }
    var friendlyNameInput by remember { mutableStateOf("") }
    var alertKeywordInput by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
        ) {
            Text(
                text = "Add Webpage Details",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Web Address Field
            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    urlError = null
                },
                label = { Text("Webpage URL") },
                placeholder = { Text("https://example.com/status") },
                singleLine = true,
                isError = urlError != null,
                supportingText = {
                    Text(
                        text = urlError ?: "Enter web address (e.g. google.com/alerts)",
                        color = if (urlError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_input_field"),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Friendly Label Field
            OutlinedTextField(
                value = friendlyNameInput,
                onValueChange = { friendlyNameInput = it },
                label = { Text("Custom Site Name") },
                placeholder = { Text("Store Availability / News Tracker") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("name_input_field"),
                shape = RoundedCornerShape(8.dp),
                supportingText = {
                    Text("Friendly name for dashboard logs", fontSize = 11.sp)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Keyword Constraint Field (Optional)
            OutlinedTextField(
                value = alertKeywordInput,
                onValueChange = { alertKeywordInput = it },
                label = { Text("Status Filter Keyword (Optional)") },
                placeholder = { Text("In Stock / Updated / Alert") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keyword_input_field"),
                shape = RoundedCornerShape(8.dp),
                supportingText = {
                    Text("Only notify if this specific term appears/disappears. If left blank, we notify you of any webpage updates.", fontSize = 11.sp)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Button(
                onClick = {
                    val rawUrl = urlInput.trim()
                    if (rawUrl.isEmpty()) {
                        urlError = "URL cannot be empty"
                        return@Button
                    }
                    
                    // Basic check to see if it meets structure
                    val hasProtocol = rawUrl.startsWith("http://") || rawUrl.startsWith("https://")
                    val checkUrl = if (hasProtocol) rawUrl else "https://$rawUrl"
                    
                    if (!Patterns.WEB_URL.matcher(checkUrl).matches()) {
                        urlError = "Invalid webpage URL syntax"
                        return@Button
                    }

                    onAddPage(checkUrl, friendlyNameInput.trim(), alertKeywordInput.trim())
                    
                    // Reset field
                    urlInput = ""
                    friendlyNameInput = ""
                    alertKeywordInput = ""
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_webpage_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Register Website Monitor", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun WebsitesListView(
    pages: List<MonitoredPage>,
    checkingPagesState: Map<Int, Boolean>,
    onToggleActive: (MonitoredPage) -> Unit,
    onRefresh: (MonitoredPage) -> Unit,
    onDelete: (Int) -> Unit
) {
    if (pages.isEmpty()) {
        WebMonitorEmptyState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pages, key = { it.id }) { page ->
                val isChecking = checkingPagesState[page.id] ?: false
                WebPageItemCard(
                    page = page,
                    isChecking = isChecking,
                    onToggleActive = { onToggleActive(page) },
                    onRefresh = { onRefresh(page) },
                    onDelete = { onDelete(page.id) }
                )
            }
        }
    }
}

@Composable
fun WebPageItemCard(
    page: MonitoredPage,
    isChecking: Boolean,
    onToggleActive: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    val isError = page.status.startsWith("Error", ignoreCase = true) || page.status.contains("Http Error", ignoreCase = true)
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
    } else if (!page.isActive) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("webpage_card_${page.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Status Badge + Name/Keyword + Switch Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status glowing circle
                    val statusColor = when {
                        !page.isActive -> Color.Gray
                        isChecking -> MaterialTheme.colorScheme.tertiary
                        isError -> MaterialTheme.colorScheme.error
                        page.status.contains("present", ignoreCase = true) -> Color(0xFF43A047) // Green
                        page.status.contains("absent", ignoreCase = true) -> Color(0xFFE53935) // Reddish
                        else -> Color(0xFF1E88E5) // Custom Blue
                    }

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = page.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (page.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Active Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (page.isActive) "Active" else "Paused",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (page.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Switch(
                        checked = page.isActive,
                        onCheckedChange = { onToggleActive() },
                        modifier = Modifier
                            .scale(0.8f)
                            .testTag("toggle_page_switch_${page.id}")
                    )
                }
            }

            // Url link row
            Text(
                text = page.url,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Dynamic filter tags if user added keywords
            if (!page.keyword.isNullOrBlank()) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Filtering Keyword: '${page.keyword}'", fontSize = 10.sp)
                        }
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                )
            }

            Divider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            // Status details row + time status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Status",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (isChecking) "Scanning webpage..." else page.status,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "Verified Last",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formatRelativeTime(page.lastChecked),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Quick Actions Footer (Run manual sweep / Trash)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_page_button_${page.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Web Monitor",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onRefresh,
                    enabled = page.isActive && !isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("force_refresh_button_${page.id}")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Checking...", fontSize = 11.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Check Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AlertHistoryListView(
    alertLogs: List<AlertLog>,
    onClearAll: () -> Unit
) {
    if (alertLogs.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Perfect Integrity",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No notification log updates received yet. Content has remained stable under current scans.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historical Event Logs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Log", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(alertLogs, key = { it.id }) { alert ->
                    AlertItemCard(alert = alert)
                }
            }
        }
    }
}

@Composable
fun AlertItemCard(alert: AlertLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert.pageName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    val timeString = remember(alert.timestamp) {
                        val format = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
                        format.format(Date(alert.timestamp))
                    }
                    Text(
                        text = timeString,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = alert.changeDescription,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun WebMonitorEmptyState() {
    val context = LocalContext.current
    
    // Dynamically retrieve the generated image with a fallback in case compilation is tricky.
    val generatedDrawableResId = remember {
        getRDrawableId(context, "img_empty")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (generatedDrawableResId != null) {
            Image(
                painter = painterResource(id = generatedDrawableResId),
                contentDescription = "Onboarding representation",
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .padding(bottom = 12.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            // High-quality Icon fallback if resource resolution fails
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Initiate Web Alert Monitors",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No configured webpages saved. Click 'Monitor a New Live Webpage' above, specify a URL, and authorize alerts to start receiving instant desktop notifications of updates.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

/**
 * Returns R.drawable resource id safely by identifier name
 */
private fun getRDrawableId(context: Context, name: String): Int? {
    return try {
        // Find generated image first
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id != 0) return id

        // Fallback search with wildcard suffix for generated unique assets
        val classLoader = context.classLoader
        val rDrawableClass = classLoader.loadClass("${context.packageName}.R\$drawable")
        for (field in rDrawableClass.fields) {
            if (field.name.startsWith(name)) {
                return field.getInt(null)
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}

private fun formatRelativeTime(millis: Long): String {
    if (millis == 0L) return "Never checked"
    val diff = System.currentTimeMillis() - millis
    if (diff < 5000) return "Just now"
    if (diff < 60000) return "${diff / 1000}s ago"
    if (diff < 3600000) return "${diff / 60000}m ago"
    
    val format = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
    return format.format(Date(millis))
}

// Simple local multiplier preview tool helper
private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.padding((scale * 2).dp)  // approximation of simple layout scale padding 
)
