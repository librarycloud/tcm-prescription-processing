package com.tcm.admin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TcmAdminApp() }
    }
}

internal sealed class ScreenTarget {
    object Login : ScreenTarget()
    object Dashboard : ScreenTarget()
    object Prescriptions : ScreenTarget()
    object E6Imports : ScreenTarget()
    data class E6ImportDetail(val id: Int) : ScreenTarget()
    data class E6ImportConfirm(val initial: JSONObject, val mergeIds: List<Int> = emptyList()) : ScreenTarget()
    data class PrescriptionDetail(val id: Int) : ScreenTarget()
    data class PrescriptionEdit(val initial: JSONObject = JSONObject()) : ScreenTarget()
    object Processing : ScreenTarget()
    data class ProcessingPlanDetail(val id: Int) : ScreenTarget()
    data class ProcessingPlanForm(val initial: JSONObject = JSONObject()) : ScreenTarget()
    data class WorkflowOperation(val plan: JSONObject, val currentStep: String, val action: String) : ScreenTarget()
    object Packages : ScreenTarget()
    data class PackageDetail(val item: PackageItem) : ScreenTarget()
    data class PackageForm(val initial: PackageItem? = null) : ScreenTarget()
    data class PackageVerify(val initialCode: String = "") : ScreenTarget()
    object Herbs : ScreenTarget()
    data class HerbLocationAssign(val location: JSONObject, val storeId: Int?) : ScreenTarget()
    object Profile : ScreenTarget()
    object ProfileDetail : ScreenTarget()
    object Settings : ScreenTarget()
    object About : ScreenTarget()
    data class Inventory(val initialQuery: String = "") : ScreenTarget()
    object Stocktaking : ScreenTarget()
    data class StocktakingDetail(val checkId: Int) : ScreenTarget()
    data class StocktakingEntry(val checkId: Int, val item: JSONObject? = null) : ScreenTarget()
    object Differences : ScreenTarget()
    data class DifferenceRegister(val defaultProduct: JSONObject? = null) : ScreenTarget()
    object Transfers : ScreenTarget()
    data class TransferDetail(val id: Int) : ScreenTarget()
    object TransferCreate : ScreenTarget()
}

/** Stable Material 3 roles for the pharmacy workspace. */
private fun tcmLightColorScheme() = lightColorScheme(
    // Modern Medical / Tech Blue palette
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = Color(0xFF1E40AF),
    inversePrimary = Color(0xFF93C5FD),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF10B981),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECFDF5),
    onTertiaryContainer = Color(0xFF065F46),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    surfaceTint = Color(0xFF2563EB),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    scrim = Color(0xFF000000),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private fun tcmDarkColorScheme() = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE8A3),
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF002117),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFB8F5D6),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
private fun TcmAdminApp() {
    val appContext = LocalContext.current.applicationContext
    val restoredSession = remember { ApiClient.loadSession(appContext) }
    val backStack = remember {
        mutableStateListOf<ScreenTarget>(if (restoredSession != null) ScreenTarget.Dashboard else ScreenTarget.Login)
    }
    val currentScreen = backStack.lastOrNull() ?: ScreenTarget.Login

    var session by remember { mutableStateOf(restoredSession) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var dashboardStores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var dashboardStoreId by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginLoading by remember { mutableStateOf(false) }
    val updatePreferences = remember(appContext) {
        appContext.getSharedPreferences("android_update_check", android.content.Context.MODE_PRIVATE)
    }
    val settingsPreferences = remember(appContext) {
        appContext.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    }
    var themeMode by remember(settingsPreferences) {
        mutableStateOf(settingsPreferences.getString("theme_mode", "system") ?: "system")
    }
    var hasAppUpdate by remember(updatePreferences) {
        mutableStateOf(
            updatePreferences.getString("cached_update", null)
                ?.let { value -> runCatching { JSONObject(value).optInt("versionCode", 0) }.getOrDefault(0) }
                ?.let { versionCode -> versionCode > BuildConfig.VERSION_CODE }
                ?: false,
        )
    }

    val scope = rememberCoroutineScope()

    fun navigateTo(target: ScreenTarget) {
        backStack.add(target)
    }

    fun navigateBack(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            true
        } else {
            false
        }
    }

    fun switchTab(target: ScreenTarget) {
        backStack.clear()
        backStack.add(target)
    }

    fun checkForAppUpdateIfDue() {
        val lastCheckedAt = updatePreferences.getLong("last_update_check_at", 0L)
        val due = lastCheckedAt <= 0L ||
            System.currentTimeMillis() - lastCheckedAt >= 24L * 60L * 60L * 1000L
        if (!due) return

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ApiClient.androidAppVersion() }
            }.onSuccess { version ->
                updatePreferences.edit()
                    .putLong("last_update_check_at", System.currentTimeMillis())
                    .putString("cached_update", version.toString())
                    .apply()
                hasAppUpdate = version.optInt("versionCode", 0) > BuildConfig.VERSION_CODE
            }
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    val colorScheme = when (themeMode) {
        "dark" -> tcmDarkColorScheme()
        "light" -> tcmLightColorScheme()
        else -> if (isSystemInDarkTheme()) tcmDarkColorScheme() else tcmLightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = Shapes(
            small = RoundedCornerShape(6.dp),
            medium = FieldShape,
            large = CardShape,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
            when (currentScreen) {
                is ScreenTarget.Login -> LoginScreen(loginLoading, loginError) { identifier, password ->
                    loginLoading = true
                    loginError = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                ApiClient.login(identifier, password)
                            }
                        }.onSuccess { value ->
                            ApiClient.saveSession(appContext, value)
                            session = value
                            backStack.clear()
                            backStack.add(ScreenTarget.Dashboard)
                        }.onFailure {
                            loginError = it.message ?: "登录失败"
                        }
                        loginLoading = false
                    }
                }

                // Main Navigation Tabs
                is ScreenTarget.Dashboard -> MainShell(currentScreen, ::switchTab, ::navigateTo, hasAppUpdate) {
                    DashboardScreen(
                        onNavigate = ::navigateTo,
                        stats = stats,
                        user = session?.user,
                        stores = dashboardStores,
                        selectedStoreId = dashboardStoreId,
                        onSelectStore = { dashboardStoreId = it },
                    )
                }
                is ScreenTarget.Prescriptions -> MainShell(currentScreen, ::switchTab, ::navigateTo, hasAppUpdate) {
                    PrescriptionsScreen(user = session?.user, onNavigate = ::navigateTo)
                }
                is ScreenTarget.E6Imports -> MainShell(currentScreen, ::switchTab, ::navigateTo, hasAppUpdate) {
                    E6ImportsScreen(user = session?.user, onNavigate = ::navigateTo)
                }
                is ScreenTarget.E6ImportDetail -> DetailShell("E6订单详情", onBack = { navigateBack() }) {
                    E6ImportDetailScreen(
                        id = currentScreen.id,
                        onConfirm = { item -> navigateTo(ScreenTarget.E6ImportConfirm(item)) },
                        onPrescription = { prescriptionId -> navigateTo(ScreenTarget.PrescriptionDetail(prescriptionId)) },
                    )
                }
                is ScreenTarget.E6ImportConfirm -> DetailShell("确认导入并生成加工计划", onBack = { navigateBack() }) {
                    E6ImportConfirmScreen(
                        initial = currentScreen.initial,
                        mergeIds = currentScreen.mergeIds,
                        onDone = {
                            navigateBack()
                            if (backStack.lastOrNull() is ScreenTarget.E6ImportDetail) navigateBack()
                        },
                    )
                }
                is ScreenTarget.Processing -> MainShell(currentScreen, ::switchTab, ::navigateTo, hasAppUpdate) {
                    ProcessingScreenV2(
                        user = session?.user,
                        onNavigate = ::navigateTo,
                    )
                }
                is ScreenTarget.Packages -> MainShell(currentScreen, ::switchTab, ::navigateTo, hasAppUpdate) {
                    PackagesScreen(user = session?.user, onNavigate = ::navigateTo)
                }
                is ScreenTarget.Herbs -> MainShell(currentScreen, ::switchTab, ::navigateTo, hasAppUpdate) {
                    HerbsScreen(user = session?.user, onNavigate = ::navigateTo)
                }
                is ScreenTarget.Profile -> MainShell(currentScreen, ::switchTab, ::navigateTo, hasAppUpdate) {
                    ProfileScreen(
                        user = session?.user,
                        onOpenDetails = { navigateTo(ScreenTarget.ProfileDetail) },
                        onOpenSettings = { navigateTo(ScreenTarget.Settings) },
                        onOpenAbout = { navigateTo(ScreenTarget.About) },
                        onEntered = ::checkForAppUpdateIfDue,
                        onSessionUpdated = { updated ->
                            ApiClient.saveSession(appContext, updated)
                            session = updated
                        },
                    )
                }
                is ScreenTarget.About -> DetailShell("关于药房助手", onBack = { navigateBack() }) {
                    AboutScreen { hasAppUpdate = it }
                }
                is ScreenTarget.ProfileDetail -> DetailShell("个人资料", onBack = { navigateBack() }) {
                    ProfileDetailScreen(
                        user = session?.user,
                        onLogout = {
                            ApiClient.clearSession(appContext)
                            session = null
                            stats = null
                            dashboardStores = emptyList()
                            dashboardStoreId = ""
                            backStack.clear()
                            backStack.add(ScreenTarget.Login)
                        },
                        onSessionUpdated = { updated ->
                            ApiClient.saveSession(appContext, updated)
                            session = updated
                        },
                    )
                }
                is ScreenTarget.Settings -> DetailShell("设置", onBack = { navigateBack() }) {
                    SettingsScreen(
                        selectedTheme = themeMode,
                        onThemeSelected = { mode ->
                            themeMode = mode
                            settingsPreferences.edit().putString("theme_mode", mode).apply()
                        },
                    )
                }

                // Sub-screens & Details (Page navigation instead of dialogs)
                is ScreenTarget.Inventory -> DetailShell("库存查询", onBack = { navigateBack() }) {
                    InventoryScreen(
                        user = session?.user,
                        initialQuery = currentScreen.initialQuery,
                    )
                }
                is ScreenTarget.PrescriptionDetail -> DetailShell("处方详情", onBack = { navigateBack() }) {
                    PrescriptionDetailScreen(
                        id = currentScreen.id,
                        user = session?.user,
                        onNavigate = ::navigateTo,
                        onBack = { navigateBack() },
                    )
                }
                is ScreenTarget.PrescriptionEdit -> DetailShell(
                    if (currentScreen.initial.has("id")) "编辑处方" else "新建处方",
                    onBack = { navigateBack() },
                ) {
                    PrescriptionFormScreen(
                        initial = currentScreen.initial,
                        user = session?.user,
                        onSaved = { navigateBack() },
                    )
                }
                is ScreenTarget.ProcessingPlanDetail -> DetailShell("加工计划详情", onBack = { navigateBack() }) {
                    Text("请从加工工作台打开加工计划详情")
                }
                is ScreenTarget.ProcessingPlanForm -> DetailShell(
                    if (currentScreen.initial.has("id")) "编辑加工计划" else "新建加工计划",
                    onBack = { navigateBack() },
                ) {
                    ProcessingPlanFormScreen(
                        initial = currentScreen.initial,
                        onSaved = { navigateBack() },
                    )
                }
                is ScreenTarget.WorkflowOperation -> DetailShell("工序详情", onBack = { navigateBack() }) {
                    WorkflowOperationScreen(
                        plan = currentScreen.plan,
                        onNavigatePrescription = { prescriptionId -> navigateTo(ScreenTarget.PrescriptionDetail(prescriptionId)) },
                        onBack = { navigateBack() },
                    )
                }
                is ScreenTarget.PackageDetail -> DetailShell("包裹详情", onBack = { navigateBack() }) {
                    PackageDetailPage(
                        pkg = currentScreen.item,
                        showStore = session?.user?.optInt("role", -1) == 0,
                        onNavigate = ::navigateTo,
                        onBack = { navigateBack() },
                    )
                }
                is ScreenTarget.PackageForm -> DetailShell(
                    if (currentScreen.initial != null) "编辑包裹" else "创建包裹",
                    onBack = { navigateBack() },
                ) {
                    PackageFormScreen(initial = currentScreen.initial, onSaved = { navigateBack() })
                }
                is ScreenTarget.PackageVerify -> DetailShell("取货码核销", onBack = { navigateBack() }) {
                    PackageVerifyScreen(initialCode = currentScreen.initialCode, onVerified = { navigateBack() })
                }
                is ScreenTarget.HerbLocationAssign -> DetailShell("配置货位", onBack = { navigateBack() }) {
                    HerbLocationAssignScreen(
                        location = currentScreen.location,
                        storeId = currentScreen.storeId,
                        onSaved = { navigateBack() },
                    )
                }
                is ScreenTarget.Stocktaking -> DetailShell("商品盘点", onBack = { navigateBack() }) {
                    StocktakingScreen(user = session?.user, onNavigate = ::navigateTo)
                }
                is ScreenTarget.StocktakingDetail -> DetailShell("盘点单明细", onBack = { navigateBack() }) {
                    StocktakingDetailScreen(
                        checkId = currentScreen.checkId,
                        user = session?.user,
                        onNavigate = ::navigateTo,
                        onBack = { navigateBack() },
                    )
                }
                is ScreenTarget.StocktakingEntry -> DetailShell("录入盘点", onBack = { navigateBack() }) {
                    StocktakingEntryScreen(
                        checkId = currentScreen.checkId,
                        initialItem = currentScreen.item,
                        onSaved = { navigateBack() },
                    )
                }
                is ScreenTarget.Differences -> DetailShell("库存差异", onBack = { navigateBack() }) {
                    DifferencesScreen()
                }
                is ScreenTarget.DifferenceRegister -> DetailShell("登记库存差异", onBack = { navigateBack() }) {
                    DifferencesScreen()
                }
                is ScreenTarget.Transfers -> DetailShell("门店调拨", onBack = { navigateBack() }) {
                    TransfersScreen(user = session?.user, onNavigate = ::navigateTo)
                }
                is ScreenTarget.TransferDetail -> DetailShell("调拨详情", onBack = { navigateBack() }) {
                    TransferDetailScreen(id = currentScreen.id, onBack = { navigateBack() })
                }
                is ScreenTarget.TransferCreate -> DetailShell("新建门店调拨", onBack = { navigateBack() }) {
                    TransfersScreen(user = session?.user, onNavigate = ::navigateTo)
                }
            }
        }
    }

    LaunchedEffect(session) {
        if (session != null) {
            if (session?.user?.optInt("role", -1) == 0) {
                runCatching { withContext(Dispatchers.IO) { ApiClient.availableStores() } }
                    .onSuccess { values -> dashboardStores = (0 until values.length()).map { values.getJSONObject(it) } }
            } else {
                dashboardStores = emptyList()
                dashboardStoreId = ""
            }
        }
    }

    LaunchedEffect(session, dashboardStoreId) {
        if (session != null) {
            runCatching { withContext(Dispatchers.IO) { ApiClient.stats(dashboardStoreId.toIntOrNull()) } }
                .onSuccess { stats = it }
        }
    }
}

@Composable
private fun LoginScreen(loading: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(16.dp),
            color = Primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "药房助手 管理端",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "中药代加工与药房工作台管理系统",
            fontSize = 13.sp,
            color = Muted,
        )

        Spacer(Modifier.height(36.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        ) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("用户名 / 手机号") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Muted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("登录密码") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Muted) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShape,
                )

                if (error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        color = Danger,
                        fontSize = 13.sp,
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { onLogin(identifier.trim(), password.trim()) },
                    enabled = identifier.isNotBlank() && password.isNotBlank() && !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = FieldShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text(
                        text = if (loading) "正在登录..." else "登 录",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    current: ScreenTarget,
    onSwitchTab: (ScreenTarget) -> Unit,
    onNavigate: (ScreenTarget) -> Unit,
    showUpdateBadge: Boolean,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerWidth = 280.dp
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == android.app.Activity.RESULT_OK && value.isNotBlank()) {
            if (value.startsWith("TCM:PICKUP:1:")) {
                onNavigate(ScreenTarget.PackageVerify(value))
            } else {
                onNavigate(ScreenTarget.Inventory(value))
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().width(drawerWidth),
                drawerContainerColor = Color.White,
            ) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = Primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("药房助手", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
                        Text("中药房移动工作台", color = Muted, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = CardBorderColor)
                Spacer(Modifier.height(8.dp))

                DrawerItem("工作台概览", current is ScreenTarget.Dashboard, Icons.AutoMirrored.Filled.Assignment) {
                    onSwitchTab(ScreenTarget.Dashboard)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("处方管理", current is ScreenTarget.Prescriptions, Icons.AutoMirrored.Filled.Assignment) {
                    onSwitchTab(ScreenTarget.Prescriptions)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("加工管理", current is ScreenTarget.Processing, Icons.Default.Sync) {
                    onSwitchTab(ScreenTarget.Processing)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("包裹管理", current is ScreenTarget.Packages, Icons.Default.AssignmentTurnedIn) {
                    onSwitchTab(ScreenTarget.Packages)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("斗谱管理", current is ScreenTarget.Herbs, Icons.Default.Inventory) {
                    onSwitchTab(ScreenTarget.Herbs)
                    scope.launch { drawerState.close() }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 16.dp), color = CardBorderColor)
                DrawerItem("库存查询", false, Icons.Default.Inventory) {
                    onNavigate(ScreenTarget.Inventory())
                    scope.launch { drawerState.close() }
                }
                DrawerItem("商品盘点", false, Icons.Default.Tune) {
                    onNavigate(ScreenTarget.Stocktaking)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("库存差异", false, Icons.Default.Tune) {
                    onNavigate(ScreenTarget.Differences)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("门店调拨", false, Icons.Default.Sync) {
                    onNavigate(ScreenTarget.Transfers)
                    scope.launch { drawerState.close() }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 16.dp), color = CardBorderColor)
                DrawerItem("我的", current is ScreenTarget.Profile, Icons.Default.AccountCircle) {
                    onSwitchTab(ScreenTarget.Profile)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("检查新版本（${BuildConfig.VERSION_NAME}）", current is ScreenTarget.About, Icons.Default.AccountCircle, showBadge = showUpdateBadge) {
                    onNavigate(ScreenTarget.About)
                    scope.launch { drawerState.close() }
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = "药房助手",
                    onMenu = { scope.launch { drawerState.open() } },
                    onScan = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) },
                )
            },
            bottomBar = { BottomNav(current, onSwitchTab) },
            containerColor = PageBackground,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                content()
            }
        }
    }

}

@Composable
private fun DrawerItem(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    showBadge: Boolean = false,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                if (showBadge) {
                    Spacer(Modifier.width(6.dp))
                    Surface(Modifier.size(8.dp), shape = CircleShape, color = Danger) {}
                }
            }
        },
        icon = if (icon != null) { { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) } } else null,
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp).height(44.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = PrimarySoft,
            selectedTextColor = PrimaryDark,
            selectedIconColor = Primary,
            unselectedTextColor = Ink,
            unselectedIconColor = Muted,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailShell(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.White,
                        scrolledContainerColor = Color.White,
                    ),
                )
                HorizontalDivider(color = CardBorderColor, thickness = 0.5.dp)
            }
        },
        containerColor = PageBackground,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String, onMenu: () -> Unit, onScan: () -> Unit) {
    Column {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Default.Menu, contentDescription = "打开菜单")
                }
            },
            actions = {
                IconButton(onClick = onScan) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码搜索商品", tint = Primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White,
                scrolledContainerColor = Color.White,
            ),
        )
        HorizontalDivider(color = CardBorderColor, thickness = 0.5.dp)
    }
}

@Composable
private fun BottomNav(current: ScreenTarget, onSwitchTab: (ScreenTarget) -> Unit) {
    val items = listOf(
        ScreenTarget.Dashboard to ("概览" to Icons.AutoMirrored.Filled.Assignment),
        ScreenTarget.Herbs to ("斗谱" to Icons.Default.Inventory),
        ScreenTarget.Processing to ("加工" to Icons.Default.Sync),
        ScreenTarget.Packages to ("包裹" to Icons.Default.AssignmentTurnedIn),
        ScreenTarget.Profile to ("我的" to Icons.Default.AccountCircle),
    )
    Column {
        HorizontalDivider(color = CardBorderColor, thickness = 0.5.dp)
        NavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            containerColor = Color.White,
            tonalElevation = 0.dp,
        ) {
            items.forEach { (target, pair) ->
                val isSelected = when (target) {
                    is ScreenTarget.Dashboard -> current is ScreenTarget.Dashboard
                    is ScreenTarget.Herbs -> current is ScreenTarget.Herbs
                    is ScreenTarget.Processing -> current is ScreenTarget.Processing
                    is ScreenTarget.Packages -> current is ScreenTarget.Packages
                    is ScreenTarget.Profile -> current is ScreenTarget.Profile
                    else -> false
                }
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSwitchTab(target) },
                    icon = { Icon(pair.second, contentDescription = pair.first) },
                    label = { Text(pair.first, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        unselectedIconColor = Muted,
                        unselectedTextColor = Muted,
                        indicatorColor = PrimarySoft,
                    ),
                )
            }
        }
    }
}
