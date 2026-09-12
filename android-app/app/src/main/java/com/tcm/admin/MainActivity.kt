package com.tcm.admin

import android.content.Intent
import com.tcm.admin.util.DeviceUtils
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcm.admin.util.CacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TcmAdminApp() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ApiClient.onTrimMemory(level)
    }
}





@Composable
private fun TcmAdminApp() {
    val appContext = LocalContext.current.applicationContext
    val restoredSession = remember { ApiClient.loadSession(appContext) }
    val navController = rememberNavController()
    val e6ImportsListState = rememberE6ImportsListState()
    val prescriptionsListState = rememberLazyListState()
    val processingListState = rememberLazyListState()
    val packagesListState = rememberLazyListState()
    val herbsListState = rememberHerbsListState()
    val profileScrollState = rememberScrollState()
    val inventoryListState = rememberLazyListState()
    val stocktakingListState = rememberLazyListState()
    val stocktakingDetailScrollState = rememberScrollState()
    val differencesListState = rememberLazyListState()
    val transfersListState = rememberLazyListState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var session by remember { mutableStateOf(restoredSession) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginLoading by remember { mutableStateOf(false) }
    var stocktakingDetailRevision by remember { mutableStateOf(0) }
    val updatePreferences = remember(appContext) {
        appContext.getSharedPreferences("android_update_check", android.content.Context.MODE_PRIVATE)
    }
    val settingsPreferences = remember(appContext) {
        appContext.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    }
    var themeMode by remember(settingsPreferences) {
        mutableStateOf(settingsPreferences.getString("theme_mode", "system") ?: "system")
    }
    var pureBlackMode by remember(settingsPreferences) {
        mutableStateOf(settingsPreferences.getBoolean("pure_black_mode", false))
    }
    var themeAccentKey by remember(settingsPreferences) {
        mutableStateOf(settingsPreferences.getString("theme_accent_key", "blue") ?: "blue")
    }
    var customColorHex by remember(settingsPreferences) {
        mutableStateOf(settingsPreferences.getString("theme_custom_color", "#2563EB") ?: "#2563EB")
    }
    var textScale by remember(settingsPreferences) {
        mutableStateOf(settingsPreferences.getFloat("text_scale", 1.0f))
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

    DisposableEffect(appContext) {
        ApiClient.onUnauthorized = {
            scope.launch {
                ApiClient.clearSession(appContext)
                clearRetainedListValues()
                session = null
                navController.navigate(Route.Login) { popUpTo(navController.graph.id) { inclusive = true } }
                Toast.makeText(appContext, "登录已过期，请重新登录", Toast.LENGTH_SHORT).show()
            }
        }
        onDispose { ApiClient.onUnauthorized = null }
    }

    fun navigateTo(target: Route) {
    navController.navigate(target) {
        if (target is Route.Inventory && currentDestination?.hasRoute<Route.Inventory>() == true) {
            popUpTo<Route.Inventory> { inclusive = true }
        }
    }
    }

    fun navigateBack(): Boolean {
        if (navController.previousBackStackEntry != null) {
            return navController.popBackStack()
        }
        return false
    }

    fun switchTab(target: Route) {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun checkForAppUpdateIfDue() {
        val lastCheckedAt = updatePreferences.getLong("last_update_check_at", 0L)
        val due = lastCheckedAt <= 0L ||
            System.currentTimeMillis() - lastCheckedAt >= 24L * 60L * 60L * 1000L
        if (!due) return

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val deviceId = DeviceUtils.getDeviceId(appContext)
                    ApiClient.androidAppVersion(
                        versionCode = BuildConfig.VERSION_CODE,
                        deviceId = deviceId,
                        context = appContext,
                    )
                }
            }.onSuccess { version ->
                updatePreferences.edit()
                    .putLong("last_update_check_at", System.currentTimeMillis())
                    .putString("cached_update", version.toString())
                    .apply()
                hasAppUpdate = version.optInt("versionCode", 0) > BuildConfig.VERSION_CODE
            }
        }
    }

    val activity = LocalContext.current as? ComponentActivity
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var lastBackPressTime by remember { mutableStateOf(0L) }
    BackHandler(enabled = currentDestination?.hasRoute<Route.Login>() != true) {
        if (!navigateBack()) {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                activity?.finish()
            } else {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                lastBackPressTime = now
                Toast.makeText(appContext, "再按一次退出应用", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentAccent = remember(themeAccentKey, customColorHex) {
        resolveThemeAccent(themeAccentKey, customColorHex)
    }

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }

    val colorScheme = when {
        isDark && pureBlackMode -> tcmPureBlackColorScheme(currentAccent)
        isDark -> tcmDarkColorScheme(currentAccent)
        else -> tcmLightColorScheme(currentAccent)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                if (Build.VERSION.SDK_INT < 35) {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = colorScheme.surface.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = colorScheme.surface.toArgb()
                }
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity, textScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * textScale,
        )
    }

    CompositionLocalProvider(LocalDensity provides customDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes(
                small = RoundedCornerShape(6.dp),
                medium = FieldShape,
                large = CardShape,
            ),
        ) {
        Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
                        NavHost(
    navController = navController,
    startDestination = if (session != null) Route.Inventory() else Route.Login,
    modifier = Modifier.fillMaxSize(),
    enterTransition = { EnterTransition.None },
    exitTransition = { ExitTransition.None },
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None }
) {
                composable<Route.Login> {
                    LoginScreen(loginLoading, loginError) { identifier, password ->
                        loginLoading = true
                        loginError = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { ApiClient.login(identifier, password) }
                            }.onSuccess { value ->
                                ApiClient.saveSession(appContext, value)
                                session = value
                                navController.navigate(Route.Inventory()) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                }
                            }.onFailure {
                                loginError = it.message ?: "登录失败"
                            }
                            loginLoading = false
                        }
                    }
                }

                composable<Route.Prescriptions> {
                    MainShell(it.toRoute<Route.Prescriptions>(), ::switchTab, ::navigateTo, hasAppUpdate, lazyListState = prescriptionsListState) {
                        PrescriptionsScreen(user = session?.user, onNavigate = ::navigateTo, listState = prescriptionsListState)
                    }
                }
                composable<Route.E6Imports> {
                    MainShell(it.toRoute<Route.E6Imports>(), ::switchTab, ::navigateTo, hasAppUpdate, lazyListState = e6ImportsListState.lazyListState) {
                        E6ImportsScreen(user = session?.user, onNavigate = ::navigateTo, listState = e6ImportsListState)
                    }
                }
                composable<Route.E6ImportDetail> { entry ->
                    val route = entry.toRoute<Route.E6ImportDetail>()
                    DetailShell("E6订单详情", onBack = { navigateBack() }) {
                        E6ImportDetailScreen(
                            id = route.id,
                            user = session?.user,
                            onConfirm = { item -> navigateTo(Route.E6ImportConfirm(RouteParams.put(item))) },
                            onPrescription = { prescriptionId -> navigateTo(Route.PrescriptionDetail(prescriptionId)) },
                        )
                    }
                }
                composable<Route.E6ImportConfirm> { entry ->
                    val route = entry.toRoute<Route.E6ImportConfirm>()
                    val initial = rememberRouteParam(entry, route.argId) { JSONObject() }
                    DetailShell("确认导入并生成加工计划", onBack = { navigateBack() }) {
                        E6ImportConfirmScreen(
                            initial = initial,
                            mergeIds = route.mergeIds,
                            onDone = {
                                e6ImportsListState.loaded = false
                                e6ImportsListState.items = null
                                invalidateRetainedList("prescriptions")
                                invalidateRetainedList("processing")
                                navigateBack()
                                // Wait, to pop E6ImportDetail we can pop up to it
                            },
                        )
                    }
                }
                composable<Route.Processing> {
                    MainShell(it.toRoute<Route.Processing>(), ::switchTab, ::navigateTo, hasAppUpdate, lazyListState = processingListState) {
                        ProcessingScreenV2(user = session?.user, onNavigate = ::navigateTo, listState = processingListState)
                    }
                }
                composable<Route.Packages> {
                    MainShell(it.toRoute<Route.Packages>(), ::switchTab, ::navigateTo, hasAppUpdate, lazyListState = packagesListState) {
                        PackagesScreen(user = session?.user, onNavigate = ::navigateTo, listState = packagesListState)
                    }
                }
                composable<Route.Herbs> {
                    MainShell(it.toRoute<Route.Herbs>(), ::switchTab, ::navigateTo, hasAppUpdate, lazyListState = herbsListState.lazyListState) {
                        HerbsScreen(user = session?.user, onNavigate = ::navigateTo, listState = herbsListState)
                    }
                }
                composable<Route.Profile> {
                    MainShell(it.toRoute<Route.Profile>(), ::switchTab, ::navigateTo, hasAppUpdate, profileScrollState) {
                        ProfileScreen(
                            user = session?.user,
                            onOpenDetails = { navigateTo(Route.ProfileDetail) },
                            onOpenSettings = { navigateTo(Route.Settings) },
                            onOpenAbout = { navigateTo(Route.About) },
                            onEntered = ::checkForAppUpdateIfDue,
                            hasAppUpdate = hasAppUpdate,
                            scrollState = profileScrollState,
                            onSessionUpdated = { updated ->
                                ApiClient.saveSession(appContext, updated)
                                session = updated
                            },
                        )
                    }
                }
                composable<Route.About> {
                    DetailShell("关于药房助手", onBack = { navigateBack() }) {
                        AboutScreen { hasAppUpdate = it }
                    }
                }
                composable<Route.ProfileDetail> {
                    DetailShell("个人资料", onBack = { navigateBack() }) {
                        ProfileDetailScreen(
                            user = session?.user,
                            onLogout = {
                                ApiClient.clearSession(appContext)
                                clearRetainedListValues()
                                session = null
                                navController.navigate(Route.Login) { popUpTo(navController.graph.id) { inclusive = true } }
                            },
                            onSessionUpdated = { updated ->
                                ApiClient.saveSession(appContext, updated)
                                session = updated
                            },
                        )
                    }
                }
                composable<Route.Settings> {
                    DetailShell("设置", onBack = { navigateBack() }) {
                        SettingsScreen(
                            onOpenThemeAppearance = { navigateTo(Route.ThemeAppearance) },
                            selectedTheme = themeMode,
                            themeAccentKey = themeAccentKey,
                            textScale = textScale,
                        )
                    }
                }
                composable<Route.ThemeAppearance> {
                    DetailShell("主题与外观", onBack = { navigateBack() }) {
                        ThemeAppearanceScreen(
                            selectedTheme = themeMode,
                            onThemeSelected = { mode ->
                                themeMode = mode
                                settingsPreferences.edit().putString("theme_mode", mode).apply()
                            },
                            pureBlackMode = pureBlackMode,
                            onPureBlackModeChanged = { enabled ->
                                pureBlackMode = enabled
                                settingsPreferences.edit().putBoolean("pure_black_mode", enabled).apply()
                            },
                            themeAccentKey = themeAccentKey,
                            customColorHex = customColorHex,
                            onThemeAccentSelected = { key ->
                                themeAccentKey = key
                                settingsPreferences.edit().putString("theme_accent_key", key).apply()
                            },
                            onCustomColorChanged = { hex ->
                                customColorHex = hex
                                themeAccentKey = "custom"
                                settingsPreferences.edit()
                                    .putString("theme_custom_color", hex)
                                    .putString("theme_accent_key", "custom")
                                    .apply()
                            },
                            textScale = textScale,
                            onTextScaleChanged = { scale ->
                                textScale = scale
                                settingsPreferences.edit().putFloat("text_scale", scale).apply()
                            },
                        )
                    }
                }

                composable<Route.Inventory> { entry ->
                    val route = entry.toRoute<Route.Inventory>()
                    MainShell(route, ::switchTab, ::navigateTo, hasAppUpdate, lazyListState = inventoryListState) {
                        InventoryScreen(
                            user = session?.user,
                            initialQuery = route.initialQuery,
                            scanRequestId = route.scanRequestId,
                            listState = inventoryListState,
                        )
                    }
                }
                composable<Route.PrescriptionDetail> { entry ->
                    val route = entry.toRoute<Route.PrescriptionDetail>()
                    DetailShell("处方详情", onBack = { navigateBack() }) {
                        PrescriptionDetailScreen(
                            id = route.id,
                            user = session?.user,
                            onNavigate = ::navigateTo,
                        )
                    }
                }
                composable<Route.PrescriptionEdit> { entry ->
                    val route = entry.toRoute<Route.PrescriptionEdit>()
                    val initial = rememberRouteParam(entry, route.argId) { JSONObject() }
                    DetailShell(
                        if (initial.has("id")) "编辑处方" else "新建处方",
                        onBack = { navigateBack() },
                    ) {
                        PrescriptionFormScreen(
                            initial = initial,
                            user = session?.user,
                            onSaved = {
                                invalidateRetainedList("prescriptions")
                                navigateBack()
                            },
                        )
                    }
                }
                composable<Route.ProcessingPlanForm> { entry ->
                    val route = entry.toRoute<Route.ProcessingPlanForm>()
                    val initial = rememberRouteParam(entry, route.argId) { JSONObject() }
                    DetailShell(
                        if (initial.has("id")) "编辑加工计划" else "新建加工计划",
                        onBack = { navigateBack() },
                    ) {
                        ProcessingPlanFormScreen(
                            initial = initial,
                            onSaved = {
                                invalidateRetainedList("processing")
                                invalidateRetainedList("prescriptions")
                                navigateBack()
                            },
                        )
                    }
                }
                composable<Route.WorkflowOperation> { entry ->
                    val route = entry.toRoute<Route.WorkflowOperation>()
                    val plan = rememberRouteParam<JSONObject?>(entry, route.argId) { null }
                    if (plan == null) {
                        LaunchedEffect(Unit) { navigateBack() }
                    } else {
                        DetailShell("工序详情", onBack = { navigateBack() }) {
                            WorkflowOperationScreen(
                                plan = plan,
                                onNavigatePrescription = { prescriptionId -> navigateTo(Route.PrescriptionDetail(prescriptionId)) },
                                onPlanStatusChanged = {
                                    invalidateRetainedList("processing")
                                },
                                onNavigatePlan = { targetPlan -> navigateTo(Route.WorkflowOperation(RouteParams.put(targetPlan), "", "open")) },
                            )
                        }
                    }
                }
                composable<Route.PackageDetail> { entry ->
                    val route = entry.toRoute<Route.PackageDetail>()
                    val item = rememberRouteParam<PackageItem?>(entry, route.argId) { null }
                    if (item == null) {
                        LaunchedEffect(Unit) { navigateBack() }
                    } else {
                        DetailShell("包裹详情", onBack = { navigateBack() }) {
                            PackageDetailPage(
                                pkg = item,
                                showStore = session?.user?.optInt("role", -1) == 0,
                                onNavigate = ::navigateTo,
                                onBack = { navigateBack() },
                            )
                        }
                    }
                }
                composable<Route.PackageForm> { entry ->
                    val route = entry.toRoute<Route.PackageForm>()
                    val initial = rememberRouteParam<PackageItem?>(entry, route.argId) { null }
                    DetailShell(
                        if (initial != null) "编辑包裹" else "创建包裹",
                        onBack = { navigateBack() },
                    ) {
                        PackageFormScreen(initial = initial, onSaved = {
                            invalidateRetainedList("packages")
                            navigateBack()
                        })
                    }
                }
                composable<Route.PackageVerify> { entry ->
                    val route = entry.toRoute<Route.PackageVerify>()
                    DetailShell("取货码核销", onBack = { navigateBack() }) {
                        PackageVerifyScreen(initialCode = route.initialCode, onVerified = {
                            invalidateRetainedList("packages")
                            invalidateRetainedList("processing")
                            navigateBack()
                        })
                    }
                }
                composable<Route.HerbLocationAssign> { entry ->
                    val route = entry.toRoute<Route.HerbLocationAssign>()
                    val location = rememberRouteParam(entry, route.argId) { JSONObject() }
                    DetailShell("配置货位", onBack = { navigateBack() }) {
                        HerbLocationAssignScreen(
                            location = location,
                            storeId = route.storeId,
                            onSaved = {
                                herbsListState.invalidate()
                                navigateBack()
                            },
                        )
                    }
                }
                composable<Route.Stocktaking> {
                    DetailShell("商品盘点", onBack = { navigateBack() }, lazyListState = stocktakingListState) {
                        StocktakingScreen(user = session?.user, onNavigate = ::navigateTo, listState = stocktakingListState)
                    }
                }
                composable<Route.StocktakingDetail> { entry ->
                    val route = entry.toRoute<Route.StocktakingDetail>()
                    DetailShell("盘点单明细", onBack = { navigateBack() }, scrollState = stocktakingDetailScrollState) {
                        StocktakingDetailScreen(
                            checkId = route.checkId,
                            user = session?.user,
                            scrollState = stocktakingDetailScrollState,
                            refreshKey = stocktakingDetailRevision,
                        )
                    }
                }
                composable<Route.Differences> {
                    DetailShell("库存差异", onBack = { navigateBack() }, lazyListState = differencesListState) {
                        DifferencesScreen(user = session?.user, listState = differencesListState)
                    }
                }
                composable<Route.Transfers> {
                    DetailShell("门店调拨", onBack = { navigateBack() }, lazyListState = transfersListState) {
                        TransfersScreen(user = session?.user, onNavigate = ::navigateTo, listState = transfersListState)
                    }
                }
                composable<Route.TransferDetail> { entry ->
                    val route = entry.toRoute<Route.TransferDetail>()
                    DetailShell("调拨详情", onBack = { navigateBack() }) {
                        TransferDetailScreen(id = route.id, onBack = { navigateBack() })
                    }
                }
            }
        }
    }
}

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CacheManager.cleanObsoleteApksAndPatches(appContext)
        }
    }

    LaunchedEffect(session) {
        if (session != null) {
            checkForAppUpdateIfDue()
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
                    tint = MaterialTheme.colorScheme.onPrimary,
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
    current: Route,
    onSwitchTab: (Route) -> Unit,
    onNavigate: (Route) -> Unit,
    showUpdateBadge: Boolean,
    scrollState: ScrollState? = null,
    lazyListState: LazyListState? = null,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerWidth = 280.dp
    var scannedEquipmentInfo by remember { mutableStateOf<JSONObject?>(null) }
    var scanResolving by remember { mutableStateOf(false) }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)?.trim().orEmpty()
        if (result.resultCode == android.app.Activity.RESULT_OK && value.isNotBlank()) {
            scope.launch {
                scanResolving = true
                try {
                    when {
                        // 1. 取货码核销 (TCM:PICKUP:1:...)
                        value.startsWith("TCM:PICKUP:1:") -> {
                            onNavigate(Route.PackageVerify(value))
                        }
                        // 2. 加工计划二维码 (TCM:PLAN:1:...)
                        value.startsWith("TCM:PLAN:1:") -> {
                            val plan = withContext(Dispatchers.IO) { ApiClient.processingPlanByScan(value) }
                            if (plan != null) {
                                onNavigate(Route.WorkflowOperation(RouteParams.put(plan), "", "open"))
                                Toast.makeText(context, "已打开加工计划：${plan.optString("planCode", value)}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "未找到对应加工计划", Toast.LENGTH_SHORT).show()
                            }
                        }
                        // 3. 设备二维码 (TCM:EQUIPMENT:1:...)
                        value.startsWith("TCM:EQUIPMENT:1:") -> {
                            val equip = withContext(Dispatchers.IO) { ApiClient.processingEquipmentByScan(value) }
                            if (equip != null) {
                                val currentUsage = equip.optJSONObject("currentUsage")
                                val occupyingPlan = currentUsage?.optJSONObject("processingPlan")
                                val planCode = occupyingPlan?.optString("planCode").orEmpty()
                                val planId = occupyingPlan?.optInt("id", 0) ?: 0
                                if (planCode.isNotBlank() || planId > 0) {
                                    val fullPlan = withContext(Dispatchers.IO) {
                                        if (planCode.isNotBlank()) ApiClient.processingPlanByScan(planCode)
                                        else ApiClient.processingWorkflow(planId)
                                    } ?: occupyingPlan
                                    if (fullPlan != null) {
                                        onNavigate(Route.WorkflowOperation(RouteParams.put(fullPlan), "", "open"))
                                        Toast.makeText(context, "已定位到设备【${equip.optString("name")}】当前加工计划", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onSwitchTab(Route.Processing)
                                        scannedEquipmentInfo = equip
                                    }
                                } else {
                                    onSwitchTab(Route.Processing)
                                    scannedEquipmentInfo = equip
                                }
                            } else {
                                Toast.makeText(context, "未找到对应设备信息", Toast.LENGTH_SHORT).show()
                            }
                        }
                        // 4. 其余所有扫码（商品条形码、SKU、药材条码等） -> 默认进入商品库存查询
                        else -> {
                            onNavigate(Route.Inventory(value, System.nanoTime()))
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "扫码识别失败", Toast.LENGTH_SHORT).show()
                } finally {
                    scanResolving = false
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().width(drawerWidth),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
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
                            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
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

                DrawerItem("库存查询", current is Route.Inventory, Icons.Default.Inventory) {
                    onSwitchTab(Route.Inventory())
                    scope.launch { drawerState.close() }
                }
                DrawerItem("处方管理", current is Route.Prescriptions, Icons.AutoMirrored.Filled.Assignment) {
                    onSwitchTab(Route.Prescriptions)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("E6诊所处方导入", current is Route.E6Imports, Icons.Default.CloudDownload) {
                    onSwitchTab(Route.E6Imports)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("加工管理", current is Route.Processing, Icons.Default.Sync) {
                    onSwitchTab(Route.Processing)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("包裹管理", current is Route.Packages, Icons.Default.AssignmentTurnedIn) {
                    onSwitchTab(Route.Packages)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("斗谱管理", current is Route.Herbs, Icons.Default.GridView) {
                    onSwitchTab(Route.Herbs)
                    scope.launch { drawerState.close() }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 16.dp), color = CardBorderColor)
                DrawerItem("商品盘点", false, Icons.AutoMirrored.Filled.CompareArrows) {
                    onNavigate(Route.Stocktaking)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("库存差异", false, Icons.Default.Tune) {
                    onNavigate(Route.Differences)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("门店调拨", false, Icons.Default.SwapHoriz) {
                    onNavigate(Route.Transfers)
                    scope.launch { drawerState.close() }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 16.dp), color = CardBorderColor)
                DrawerItem("我的", current is Route.Profile, Icons.Default.AccountCircle) {
                    onSwitchTab(Route.Profile)
                    scope.launch { drawerState.close() }
                }
                DrawerItem("检查新版本（${BuildConfig.VERSION_NAME}）", current is Route.About, Icons.Default.SystemUpdate, showBadge = showUpdateBadge) {
                    onNavigate(Route.About)
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
                    onScan = {
                        scannerLauncher.launch(
                            Intent(context, ScannerActivity::class.java)
                        )
                    },
                )
            },
            bottomBar = {
                BottomNav(
                    current = current,
                    onSwitchTab = onSwitchTab,
                    onReselect = {
                        scope.launch {
                            scrollState?.animateScrollTo(0)
                            lazyListState?.animateScrollToItem(0)
                        }
                    },
                )
            },
            containerColor = PageBackground,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).dismissKeyboardOnTap()) {
                content()
                ScrollToTopButton(
                    scrollState = scrollState,
                    lazyListState = lazyListState,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    if (scanResolving) {
        Dialog(onDismissRequest = { /* keep open while resolving */ }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Primary, strokeWidth = 3.dp)
                    Text("正在识别条码...", fontSize = 15.sp, color = Ink)
                }
            }
        }
    }

    scannedEquipmentInfo?.let { equip ->
        val equipName = equip.optString("name", "设备")
        val equipType = equip.optString("typeName", "")
        val equipNo = equip.optString("equipmentNo", "")
        val statusInt = equip.optInt("status", 1)
        val currentUsage = equip.optJSONObject("currentUsage")
        val statusText = when {
            currentUsage != null -> "使用中"
            statusInt == 1 -> "正常空闲"
            statusInt == 2 -> "维护中"
            statusInt == 0 -> "已停用"
            else -> "空闲"
        }

        AlertDialog(
            onDismissRequest = { scannedEquipmentInfo = null },
            title = { Text(equipName, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (equipType.isNotBlank()) {
                        Text("设备类型：$equipType", fontSize = 14.sp, color = Ink)
                    }
                    if (equipNo.isNotBlank()) {
                        Text("设备编号：$equipNo", fontSize = 14.sp, color = Ink)
                    }
                    Text("当前状态：$statusText", fontSize = 14.sp, color = Ink)
                    Text("该设备当前无正在运行的加工任务，可在加工管理中分配使用。", fontSize = 13.sp, color = Muted)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scannedEquipmentInfo = null
                        onSwitchTab(Route.Processing)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("前往加工管理")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { scannedEquipmentInfo = null }) {
                    Text("关闭")
                }
            },
        )
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
private fun DetailShell(
    title: String,
    onBack: () -> Unit,
    scrollState: ScrollState? = null,
    lazyListState: LazyListState? = null,
    content: @Composable () -> Unit,
) {
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
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                HorizontalDivider(color = CardBorderColor, thickness = 0.5.dp)
            }
        },
        containerColor = PageBackground,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).dismissKeyboardOnTap()) {
            content()
            ScrollToTopButton(
                scrollState = scrollState,
                lazyListState = lazyListState,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
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
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码", tint = Primary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        HorizontalDivider(color = CardBorderColor, thickness = 0.5.dp)
    }
}

@Composable
private fun BottomNav(
    current: Route,
    onSwitchTab: (Route) -> Unit,
    onReselect: () -> Unit,
) {
    val items = listOf(
        Route.Inventory() to ("库存查询" to Icons.Default.Inventory),
        Route.Herbs to ("斗谱" to Icons.Default.GridView),
        Route.Processing to ("加工" to Icons.Default.Sync),
        Route.Packages to ("包裹" to Icons.Default.AssignmentTurnedIn),
        Route.Profile to ("我的" to Icons.Default.AccountCircle),
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(color = CardBorderColor.copy(alpha = 0.65f), thickness = 0.5.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { (target, pair) ->
                    val isSelected = when (target) {
                        is Route.Inventory -> current is Route.Inventory
                        is Route.Herbs -> current is Route.Herbs
                        is Route.Processing -> current is Route.Processing
                        is Route.Packages -> current is Route.Packages
                        is Route.Profile -> current is Route.Profile
                        else -> false
                    }
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.12f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                        label = "navIconScale",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) Primary else Muted,
                        label = "navContentColor",
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (isSelected) onReselect() else onSwitchTab(target)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) PrimarySoft else Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    pair.second,
                                    contentDescription = pair.first,
                                    tint = contentColor,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer(
                                            scaleX = iconScale,
                                            scaleY = iconScale,
                                        ),
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = pair.first,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = contentColor,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollToTopButton(
    scrollState: ScrollState? = null,
    lazyListState: LazyListState? = null,
    modifier: Modifier = Modifier,
) {
    val shouldShow = when {
        lazyListState != null -> lazyListState.firstVisibleItemIndex >= 3
        scrollState != null -> {
            val twoScreens = scrollState.viewportSize * 2
            twoScreens > 0 && scrollState.value >= twoScreens
        }
        else -> false
    }
    if (!shouldShow) return

    val scope = rememberCoroutineScope()
    SmallFloatingActionButton(
        onClick = {
            scope.launch {
                lazyListState?.animateScrollToItem(0)
                scrollState?.animateScrollTo(0)
            }
        },
        modifier = modifier,
        shape = CircleShape,
        containerColor = Primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "返回顶部",
            modifier = Modifier.size(22.dp),
        )
    }
}
