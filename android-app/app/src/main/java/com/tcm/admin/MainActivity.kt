package com.tcm.admin

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal val PageBackground = Color(0xFFF5F7FA)
internal val Primary = Color(0xFF409EFF)
internal val PrimaryDark = Color(0xFF337ECC)
internal val PrimarySoft = Color(0xFFECF5FF)
internal val Ink = Color(0xFF303133)
internal val Muted = Color(0xFF909399)
internal val Border = Color(0xFFE4E7ED)
internal val Success = Color(0xFF67C23A)
internal val Warning = Color(0xFFE6A23C)
internal val Danger = Color(0xFFF56C6C)
internal val CardShape = RoundedCornerShape(8.dp)
internal val FieldShape = RoundedCornerShape(8.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TcmAdminApp() }
    }
}

internal enum class Screen { Login, Dashboard, Prescriptions, Processing, Packages, Herbs, Profile, Inventory, Stocktaking, Differences, Transfers }

@Composable
private fun TcmAdminApp() {
    val appContext = LocalContext.current.applicationContext
    val restoredSession = remember { ApiClient.loadSession(appContext) }
    var screen by remember { mutableStateOf(if (restoredSession != null) Screen.Dashboard else Screen.Login) }
    var session by remember { mutableStateOf(restoredSession) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var loginLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var selectedPackage by remember { mutableStateOf<PackageItem?>(null) }
    val go: (Screen) -> Unit = { screen = it }
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(primary = Primary, onPrimary = Color.White, primaryContainer = PrimarySoft, onPrimaryContainer = PrimaryDark, secondary = Color(0xFF0F766E), onSecondary = Color.White, secondaryContainer = Color(0xFFE6FFFB), onSecondaryContainer = Color(0xFF115E59), tertiary = Color(0xFF8B5CF6), background = PageBackground, onBackground = Ink, surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFF0F2F5), onSurfaceVariant = Muted, outline = Border, error = Danger, onError = Color.White),
        shapes = Shapes(small = RoundedCornerShape(6.dp), medium = FieldShape, large = CardShape),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = PageBackground) {
            when (screen) {
                Screen.Login -> LoginScreen(loginLoading, loginError) { identifier, password ->
                    loginLoading = true; loginError = null
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { ApiClient.login(identifier, password) } }
                            .onSuccess { value -> ApiClient.saveSession(appContext, value); session = value; screen = Screen.Dashboard }
                            .onFailure { loginError = it.message ?: "登录失败" }
                        loginLoading = false
                    }
                }
                Screen.Dashboard -> MainShell(screen, go) { DashboardScreen(go, stats) }
                Screen.Prescriptions -> MainShell(screen, go) { PrescriptionsScreen() }
                Screen.Processing -> MainShell(screen, go) { ProcessingScreenV2() }
                Screen.Packages -> MainShell(screen, go) { PackagesScreenV3(onOpen = { selectedPackage = it }) }
                Screen.Herbs -> MainShell(screen, go) { HerbsScreen() }
                Screen.Profile -> MainShell(screen, go) { ProfileScreen(session?.user) { ApiClient.clearSession(appContext); session = null; stats = null; go(Screen.Login) } }
                Screen.Inventory -> DetailShell("库存查询", go) { InventoryScreen() }
                Screen.Stocktaking -> DetailShell("商品盘点", go) { StocktakingScreen() }
                Screen.Differences -> DetailShell("库存差异", go) { DifferencesScreen() }
                Screen.Transfers -> DetailShell("门店调拨", go) { TransfersScreen() }
            }
            if (selectedPackage != null) {
                PackageDetailDialogV2(selectedPackage!!) { selectedPackage = null }
            }
        }
    }
    LaunchedEffect(session) {
        if (session != null) {
            runCatching { withContext(Dispatchers.IO) { ApiClient.stats() } }.onSuccess { stats = it }
        }
    }
}

@Composable
private fun LoginScreen(loading: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp), verticalArrangement = Arrangement.Center) {
        Surface(color = PrimarySoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Inventory, null, tint = Primary, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text("药房助手", fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text("工作台", color = PrimaryDark, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(Color.White), shape = CardShape, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(identifier, { identifier = it }, Modifier.fillMaxWidth(), label = { Text("手机号或用户名") }, leadingIcon = { Icon(Icons.Default.AccountCircle, null) }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { onLogin(identifier, password) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(6.dp), enabled = identifier.isNotBlank() && password.isNotBlank() && !loading) { Text(if (loading) "登录中..." else "登录", fontWeight = FontWeight.SemiBold) }
                if (error != null) { Spacer(Modifier.height(10.dp)); Text(error, color = Danger, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("请使用后端账号登录", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MainShell(current: Screen, go: (Screen) -> Unit, content: @Composable () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var scanResult by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val value = result.data?.getStringExtra(ScannerActivity.SCAN_RESULT)
        if (result.resultCode == Activity.RESULT_OK && !value.isNullOrBlank()) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { val pkg = ApiClient.packageByCode(value); val method = pkg.optInt("pickupMethod", 0); if (method == 2) throw IllegalStateException("快递包裹请在包裹详情中填写快递单号后核销"); ApiClient.verifyPackage(value, method) } }
                    .onSuccess { scanResult = value }
                    .onFailure { scanError = it.message ?: "取货码核验失败" }
            }
        }
    }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.fillMaxWidth().background(PrimarySoft).padding(horizontal = 24.dp, vertical = 22.dp)) {
                    Text("药房助手", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryDark)
                    Spacer(Modifier.height(4.dp))
                    Text("门店运营工作台", color = PrimaryDark.copy(alpha = .72f), fontSize = 12.sp)
                }
                DrawerItem("概览", current == Screen.Dashboard) { go(Screen.Dashboard); scope.launch { drawerState.close() } }
                DrawerItem("处方管理", current == Screen.Prescriptions) { go(Screen.Prescriptions); scope.launch { drawerState.close() } }
                DrawerItem("加工计划", current == Screen.Processing) { go(Screen.Processing); scope.launch { drawerState.close() } }
                DrawerItem("包裹管理", current == Screen.Packages) { go(Screen.Packages); scope.launch { drawerState.close() } }
                DrawerItem("斗谱与库位", current == Screen.Herbs) { go(Screen.Herbs); scope.launch { drawerState.close() } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("业务管理", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = Muted, fontSize = 12.sp)
                DrawerItem("库存查询", current == Screen.Inventory) { go(Screen.Inventory); scope.launch { drawerState.close() } }
                DrawerItem("商品盘点", current == Screen.Stocktaking) { go(Screen.Stocktaking); scope.launch { drawerState.close() } }
                DrawerItem("库存差异", current == Screen.Differences) { go(Screen.Differences); scope.launch { drawerState.close() } }
                DrawerItem("门店调拨", current == Screen.Transfers) { go(Screen.Transfers); scope.launch { drawerState.close() } }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                DrawerItem("我的", current == Screen.Profile) { go(Screen.Profile); scope.launch { drawerState.close() } }
            }
        },
    ) {
        Scaffold(topBar = { AppTopBar("药房助手", onMenu = { scope.launch { drawerState.open() } }, onScan = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) }) }, bottomBar = { BottomNav(current, go) }, containerColor = PageBackground) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
    }
    if (scanResult != null) AlertDialog(onDismissRequest = { scanResult = null }, title = { Text("核验成功") }, text = { Text("取货码 ${scanResult}\n包裹已完成领取核验。") }, confirmButton = { Button({ scanResult = null }) { Text("完成") } })
    if (scanError != null) AlertDialog(onDismissRequest = { scanError = null }, title = { Text("核验失败") }, text = { Text(scanError!!) }, confirmButton = { Button({ scanError = null }) { Text("关闭") } })
}

@Composable
private fun DrawerItem(label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(label = { Text(label) }, selected = selected, onClick = onClick, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailShell(title: String, go: (Screen) -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton(onClick = { go(Screen.Dashboard) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, scrolledContainerColor = Color.White)) }, containerColor = PageBackground) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String, onMenu: () -> Unit, onScan: () -> Unit) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "打开菜单") } }, actions = { IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, "扫码核验") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, scrolledContainerColor = Color.White))
}

@Composable
private fun BottomNav(current: Screen, go: (Screen) -> Unit) {
    val items = listOf(Screen.Dashboard to ("概览" to Icons.AutoMirrored.Filled.Assignment), Screen.Herbs to ("斗谱" to Icons.Default.Inventory), Screen.Processing to ("加工" to Icons.Default.Sync), Screen.Packages to ("包裹" to Icons.Default.AssignmentTurnedIn), Screen.Profile to ("我的" to Icons.Default.AccountCircle))
    NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = Color.White, tonalElevation = 2.dp) { items.forEach { (screen, pair) -> NavigationBarItem(selected = current == screen, onClick = { go(screen) }, icon = { Icon(pair.second, pair.first) }, label = { Text(pair.first, fontSize = 11.sp) }) } }
}
