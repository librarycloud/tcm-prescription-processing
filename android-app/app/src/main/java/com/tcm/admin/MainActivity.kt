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

private data class PackageItem(val name: String, val customer: String, val code: String, val status: String, val time: String, val id: Int = 0, val phone: String = "-", val store: String = "", val method: String = "", val info: String = "", val statusCode: Int = 0, val methodCode: Int = 0)

@Composable
private fun TcmAdminApp() {
    var screen by remember { mutableStateOf(Screen.Login) }
    var session by remember { mutableStateOf<AdminSession?>(null) }
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
                            .onSuccess { value -> session = value; screen = Screen.Dashboard }
                            .onFailure { loginError = it.message ?: "登录失败" }
                        loginLoading = false
                    }
                }
                Screen.Dashboard -> MainShell(screen, go) { DashboardScreen(go, stats) }
                Screen.Prescriptions -> MainShell(screen, go) { PrescriptionsScreen() }
                Screen.Processing -> MainShell(screen, go) { ProcessingScreenV2() }
                Screen.Packages -> MainShell(screen, go) { PackagesScreenV3(onOpen = { selectedPackage = it }) }
                Screen.Herbs -> MainShell(screen, go) { HerbsScreen() }
                Screen.Profile -> MainShell(screen, go) { ProfileScreen(session?.user) { ApiClient.setToken(null); session = null; stats = null; go(Screen.Login) } }
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
        Text("中药处方加工与取药管理系统", fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
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
                    Text("中药取药助手", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = PrimaryDark)
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
        Scaffold(topBar = { AppTopBar("中药取药助手", onMenu = { scope.launch { drawerState.open() } }, onScan = { scannerLauncher.launch(Intent(context, ScannerActivity::class.java)) }) }, bottomBar = { BottomNav(current, go) }, containerColor = PageBackground) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
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


@Composable
private fun ProcessingScreen() {
    var plans by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { error = null; runCatching { withContext(Dispatchers.IO) { ApiClient.plans() } }.onSuccess { a -> plans = (0 until a.length()).map { a.getJSONObject(it) } }.onFailure { error = it.message ?: "加载加工计划失败" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("今日加工计划", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink); Spacer(Modifier.height(10.dp))
        if (plans == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (plans != null && plans!!.isEmpty()) Text("暂无加工计划", color = Muted)
        plans.orEmpty().forEach { plan ->
            val id = plan.optInt("id", 0); val status = plan.optInt("status", 0); val statusText = planStatus(status)
            val prescription = plan.optJSONObject("prescription")
            val customer = plan.optString("customerName", prescription?.optString("customerName", "客户") ?: "客户")
            val phone = plan.optString("customerPhone", prescription?.optString("phone", "-") ?: "-")
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(customer, fontWeight = FontWeight.SemiBold); Text(phone, color = Muted, fontSize = 12.sp) }; StatusPill(statusText) }
                    Spacer(Modifier.height(8.dp)); Text("${plan.optInt("totalDose", 0)} 剂 · 计划日期 ${plan.optString("processDate", "-").take(10)}", color = Muted, fontSize = 13.sp)
                    if (id > 0 && status in 0..2) { Spacer(Modifier.height(10.dp)); Button(enabled = busy == null, onClick = { busy = id; scope.launch { runCatching { withContext(Dispatchers.IO) { if (status == 0) ApiClient.transitionPlan(id, 1) else if (status == 1) ApiClient.transitionPlan(id, 2) else ApiClient.generatePackage(id) } }.onSuccess { reload++ }.onFailure { error = it.message ?: "操作失败" }; busy = null } }, shape = RoundedCornerShape(6.dp)) { Text(if (status == 0) "开始加工" else if (status == 1) "加工完成" else "生成包裹") } }
                }
            }
        }
    }
}

private fun planStatus(status: Int): String = when (status) { 0 -> "待加工"; 1 -> "加工中"; 2 -> "加工完成"; 3 -> "待领取"; 4 -> "已领取"; 5 -> "已取消"; else -> "未知" }

@Composable private fun StatusPill(text: String) { val color = when { text in listOf("加工完成", "已领取", "已完成", "已调平") -> Success; text in listOf("实货少", "已取消") -> Danger; text in listOf("加工中", "实货多") -> Primary; else -> Warning }; Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(5.dp)) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 12.sp) } }

@Composable
private fun PackagesScreen(onOpen: (PackageItem) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var remote by remember { mutableStateOf<List<PackageItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { ApiClient.packages() } }.onSuccess { array -> remote = (0 until array.length()).map { i -> val o = array.getJSONObject(i); PackageItem(o.optString("itemName", "处方包裹"), o.optString("receiverName", "客户"), o.optString("pickupCode", "------"), if (o.optInt("status") == 1) "已领取" else "待领取", o.optString("createdAt", "-").replace("T", " ").take(16)) } }.onFailure { error = it.message ?: "加载包裹失败" } }
    val list = remote.orEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索姓名、手机号或取货码") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("全部", statusFilter == "all") { statusFilter = "all" }; SegmentedButton("待领取", statusFilter == "pending") { statusFilter = "pending" }; SegmentedButton("已领取", statusFilter == "picked") { statusFilter = "picked" } }; Spacer(Modifier.height(14.dp)); if (error != null) Text(error!!, color = Danger, fontSize = 13.sp); if (remote != null && list.isEmpty()) Text("暂无包裹", color = Muted); list.filter { (statusFilter == "all" || (statusFilter == "pending" && it.status == "待领取") || (statusFilter == "picked" && it.status == "已领取")) && (keyword.isBlank() || it.name.contains(keyword) || it.customer.contains(keyword) || it.code.contains(keyword)) }.forEach { item -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpen(item) }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(item.customer, color = Muted, fontSize = 12.sp) }; StatusPill(item.status) }; Spacer(Modifier.height(9.dp)); Text("取货码：${item.code}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Primary); Text("录入：${item.time}", color = Muted, fontSize = 12.sp) } } } }
}

@Composable
private fun PackageDetailDialog(item: PackageItem, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose, confirmButton = { Button(onClose) { Text("关闭") } }, title = { Text("包裹详情") }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(8.dp)); Text(item.code, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary); Spacer(Modifier.height(10.dp)); FakeQr(item.code); Spacer(Modifier.height(8.dp)); Text("客户：${item.customer}\n状态：${item.status}\n门店：人民路店", color = Muted) } })
}

@Composable
private fun FakeQr(value: String) { Canvas(Modifier.size(150.dp).background(Color.White)) { val cells = 21; val cell = size.minDimension / cells; for (x in 0 until cells) for (y in 0 until cells) if (((x * 31 + y * 17 + value.length * 13) % 7) < 3 || (x < 7 && y < 7) || (x > 13 && y < 7) || (x < 7 && y > 13)) drawRect(if ((x + y) % 3 == 0) Color.Black else Color.DarkGray, androidx.compose.ui.geometry.Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell, cell)) } }

@Composable
private fun HerbsScreen() {
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var keyword by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<JSONObject?>(null) }
    var assignLocation by remember { mutableStateOf<JSONObject?>(null) }
    var moveAssignment by remember { mutableStateOf<JSONObject?>(null) }
    var editHerb by remember { mutableStateOf<JSONObject?>(null) }
    var reload by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.stores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString()
            }
            .onFailure { error = it.message ?: "加载门店失败" }
    }
    LaunchedEffect(selectedStoreId, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.herbLocations(selectedStoreId) } }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "加载斗谱失败" }
    }

    val locations = data?.optJSONArray("locations")?.let { values ->
        (0 until values.length()).map { values.getJSONObject(it) }
    }.orEmpty()
    val filtered = locations.filter { location ->
        val locationType = location.optString("type")
        val herbs = location.optJSONArray("herbs") ?: JSONArray()
        val herbText = (0 until herbs.length()).joinToString(" ") { index ->
            val herb = herbs.getJSONObject(index)
            "${herb.optString("name")} ${herb.optString("code")}".lowercase()
        }
        val searchText = "${location.optString("code")} $herbText".lowercase()
        (type.isBlank() || locationType == type) && (keyword.isBlank() || searchText.contains(keyword.trim().lowercase()))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("斗谱管理")
                Text(data?.optJSONObject("store")?.optString("name") ?: "当前门店", color = Muted, fontSize = 13.sp)
            }
            Text("${filtered.size} 个位置", color = Primary, fontWeight = FontWeight.SemiBold)
        }
        if (stores.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text("选择门店", color = Muted, fontSize = 12.sp)
            stores.forEach { store ->
                val id = store.opt("id")?.toString().orEmpty()
                val selected = id == selectedStoreId
                if (selected) Button({ selectedStoreId = id }, Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(6.dp)) { Text(store.optString("name")) }
                else OutlinedButton({ selectedStoreId = id }, Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(6.dp)) { Text(store.optString("name")) }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索药材名称、编码或位置") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(10.dp))
        Text("位置类型", color = Muted, fontSize = 12.sp)
        listOf("" to "全部位置", "D" to "斗 D", "G" to "柜 G", "F" to "冰箱 F", "C" to "仓库 C").chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    if (type == option.first) Button({ type = option.first }, Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) { Text(option.second, fontSize = 12.sp) }
                    else OutlinedButton({ type = option.first }, Modifier.weight(1f), shape = RoundedCornerShape(6.dp)) { Text(option.second, fontSize = 12.sp) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (data == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (data != null && filtered.isEmpty()) Text("暂无匹配位置", color = Muted)
        filtered.forEach { location ->
            val herbs = location.optJSONArray("herbs") ?: JSONArray()
            val herbNames = (0 until herbs.length()).joinToString(" / ") { herbs.getJSONObject(it).optString("name") }.ifBlank { "未配置药材" }
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { selectedLocation = location }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(location.optString("code"), Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Primary, fontSize = 17.sp)
                        StatusPill(locationTypeLabel(location.optString("type")))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(positionLabel(location), color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(herbNames, color = Ink, fontSize = 14.sp)
                }
            }
        }
    }
    selectedLocation?.let { location ->
        val herbs = location.optJSONArray("herbs") ?: JSONArray()
        AlertDialog(
            onDismissRequest = { selectedLocation = null },
            title = { Text(location.optString("code")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${locationTypeLabel(location.optString("type"))} · ${positionLabel(location)}", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    if (herbs.length() == 0) Text("当前库位未配置药材", color = Muted)
                    (0 until herbs.length()).forEach { index ->
                        val herb = herbs.getJSONObject(index)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(herb.optString("name"), fontWeight = FontWeight.SemiBold)
                                Text("${herb.optString("code").ifBlank { "-" }} · ${herb.optString("specification").ifBlank { "未填写规格" }} · 格内 ${herb.opt("slotNo") ?: "-"}", color = Muted, fontSize = 12.sp)
                            }
                            TextButton({ editHerb = herb }) { Text("编辑") }
                            TextButton({ moveAssignment = herb }) { Text("移动") }
                            TextButton({
                                scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.deleteHerbLocationAssignment(herb.optInt("assignmentId")) } }
                                    .onSuccess { selectedLocation = null; reload++ }
                                    .onFailure { error = it.message ?: "移除药材失败" } }
                            }) { Text("移除", color = Danger) }
                        }
                        if (index < herbs.length() - 1) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ assignLocation = location }) { Text("配置药材") }; OutlinedButton({ selectedLocation = null }) { Text("关闭") } } },
        )
    }
    assignLocation?.let { location ->
        val herbs = data?.optJSONArray("herbs")?.let { values -> (0 until values.length()).map { values.getJSONObject(it) } }.orEmpty()
        var selectedHerbId by remember(location, reload) { mutableStateOf(0) }
        var herbName by remember(location) { mutableStateOf("") }
        var herbCode by remember(location) { mutableStateOf("") }
        var specification by remember(location) { mutableStateOf("") }
        var slotNo by remember(location) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { assignLocation = null }, title = { Text("配置药材 · ${location.optString("code")}") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (herbs.isNotEmpty()) {
                    Text("已有药材", color = Muted, fontSize = 12.sp)
                    herbs.take(12).forEach { herb -> SegmentedButton(herb.optString("name"), selectedHerbId == herb.optInt("id")) { selectedHerbId = herb.optInt("id"); herbName = ""; herbCode = ""; specification = "" } }
                    Spacer(Modifier.height(8.dp))
                }
                Text("或新增药材", color = Muted, fontSize = 12.sp)
                OutlinedTextField(herbName, { herbName = it }, Modifier.fillMaxWidth(), label = { Text("药材名称") }, singleLine = true)
                OutlinedTextField(herbCode, { herbCode = it }, Modifier.fillMaxWidth(), label = { Text("药材编码（可选）") }, singleLine = true)
                OutlinedTextField(specification, { specification = it }, Modifier.fillMaxWidth(), label = { Text("规格（可选）") }, singleLine = true)
                if (location.optString("type") == "D") OutlinedTextField(slotNo, { slotNo = it.filter(Char::isDigit).take(1) }, Modifier.fillMaxWidth(), label = { Text("格内序号 1-3") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        }, confirmButton = {
            Button(enabled = selectedHerbId > 0 || herbName.isNotBlank(), onClick = {
                val payload = JSONObject().put("locationCode", location.optString("code"))
                selectedStoreId?.toIntOrNull()?.let { payload.put("storeId", it) }
                if (selectedHerbId > 0) payload.put("herbId", selectedHerbId) else payload.put("name", herbName.trim()).put("code", herbCode.trim()).put("specification", specification.trim())
                slotNo.toIntOrNull()?.let { payload.put("slotNo", it) }
                scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.assignHerbLocation(payload) } }
                    .onSuccess { assignLocation = null; selectedLocation = null; reload++ }
                    .onFailure { error = it.message ?: "配置药材失败" } }
            }) { Text("保存") }
        }, dismissButton = { TextButton({ assignLocation = null }) { Text("取消") } })
    }
    moveAssignment?.let { assignment ->
        var locationCode by remember(assignment) { mutableStateOf(selectedLocation?.optString("code").orEmpty()) }
        AlertDialog(onDismissRequest = { moveAssignment = null }, title = { Text("移动 ${assignment.optString("name")}") }, text = { Column { OutlinedTextField(locationCode, { locationCode = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("目标位置编号，例如 D-1-1-1") }, singleLine = true) } }, confirmButton = {
            Button(enabled = locationCode.isNotBlank(), onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.moveHerbLocationAssignment(assignment.optInt("assignmentId"), JSONObject().put("locationCode", locationCode.trim())) } }
                .onSuccess { moveAssignment = null; selectedLocation = null; reload++ }
                .onFailure { error = it.message ?: "移动药材失败" } } }) { Text("移动") }
        }, dismissButton = { TextButton({ moveAssignment = null }) { Text("取消") } })
    }
    editHerb?.let { herb ->
        var name by remember(herb) { mutableStateOf(herb.optString("name")) }
        var code by remember(herb) { mutableStateOf(herb.optString("code")) }
        var specification by remember(herb) { mutableStateOf(herb.optString("specification")) }
        AlertDialog(onDismissRequest = { editHerb = null }, title = { Text("编辑药材") }, text = { Column { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("药材名称") }, singleLine = true); OutlinedTextField(code, { code = it }, Modifier.fillMaxWidth(), label = { Text("药材编码") }, singleLine = true); OutlinedTextField(specification, { specification = it }, Modifier.fillMaxWidth(), label = { Text("规格") }, singleLine = true) } }, confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updateHerb(herb.optInt("id"), JSONObject().put("name", name.trim()).put("code", code.trim()).put("specification", specification.trim())) } }
                .onSuccess { editHerb = null; selectedLocation = null; reload++ }
                .onFailure { error = it.message ?: "保存药材失败" } } }) { Text("保存") }
        }, dismissButton = { TextButton({ editHerb = null }) { Text("取消") } })
    }
}

private fun locationTypeLabel(type: String): String = when (type) { "D" -> "药斗"; "G" -> "药柜"; "F" -> "冰箱"; "C" -> "仓库"; else -> "位置" }
private fun positionLabel(location: JSONObject): String {
    val type = location.optString("type")
    val unit = location.opt("unitNo") ?: "-"
    val layer = location.opt("layerNo") ?: "-"
    val column = location.opt("columnNo")
    return if (type == "D") "斗$unit · ${if (layer.toString() == "0") "顶层" else "${layer}行"} · ${column ?: "-"}列" else "${locationTypeLabel(type)}$unit · $layer 层"
}

@Composable private fun ProfileScreen(user: JSONObject?, onLogout: () -> Unit) { val displayName = user?.optString("nickname").orEmpty().ifBlank { user?.optString("username").orEmpty().ifBlank { "管理员" } }; val role = when (user?.optInt("role", 0)) { 0 -> "全局管理员"; 2 -> "门店管理员"; 3 -> "门店员工"; else -> "管理员" }; Column(Modifier.fillMaxSize().padding(16.dp)) { Card(colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(52.dp), shape = RoundedCornerShape(26.dp), color = Primary.copy(alpha = .14f)) { Box(contentAlignment = Alignment.Center) { Text(displayName.take(1), color = Primary, fontWeight = FontWeight.Bold, fontSize = 20.sp) } }; Spacer(Modifier.width(12.dp)); Column { Text(displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(role, color = Muted, fontSize = 13.sp) } } }; Spacer(Modifier.height(14.dp)); listOf("手机号" to (user?.optString("phone").orEmpty().ifBlank { "-" }), "用户名" to (user?.optString("username").orEmpty().ifBlank { "-" }), "所属门店" to (user?.optJSONObject("store")?.optString("name").orEmpty().ifBlank { "全部门店" })).forEach { (label, value) -> InfoRow(label, value) }; Spacer(Modifier.height(24.dp)); OutlinedButton(onLogout, Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) { Text("退出登录") } } }
@Composable private fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, modifier = Modifier.width(86.dp), color = Muted); Text(value, color = Ink); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted) }; HorizontalDivider(color = Color(0xFFE5E6EB)) }

@Composable
private fun InventoryScreen() {
    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.inventory(query.trim()) } }
            .onSuccess { values -> products = (0 until values.length()).map { values.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载库存失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索商品名称、编号或条码") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(14.dp))
        if (products == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (products != null && products!!.isEmpty()) Text("暂无库存商品", color = Muted)
        products.orEmpty().forEach { product ->
            val inventories = product.optJSONArray("inventories") ?: JSONArray()
            val locations = (0 until inventories.length()).joinToString("、") { inventories.getJSONObject(it).optString("locationName", "-") }.ifBlank { "-" }
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(product.optString("name"), fontWeight = FontWeight.SemiBold)
                    Text("${product.optString("productCode")} · ${product.optString("specification").ifBlank { "未填写规格" }}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(9.dp))
                    Text("库存 ${product.opt("totalQuantity") ?: 0} ${product.optString("unit")}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Primary)
                    Text("库位：$locations", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StocktakingScreen() {
    var checks by remember { mutableStateOf<List<JSONObject>?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedStoreId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var createVisible by remember { mutableStateOf(false) }
    var checkName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var activeCheckId by remember { mutableStateOf(0) }
    var countItem by remember { mutableStateOf<JSONObject?>(null) }
    var countValue by remember { mutableStateOf("") }
    var candidateVisible by remember { mutableStateOf(false) }
    var candidateKeyword by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var locationItem by remember { mutableStateOf<JSONObject?>(null) }
    var locationValue by remember { mutableStateOf("") }
    var detailLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.stocktakings(), ApiClient.stores()) } }
            .onSuccess { (values, storeValues) ->
                checks = (0 until values.length()).map { values.getJSONObject(it) }
                stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
                if (selectedStoreId.isBlank() && stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString().orEmpty()
            }
            .onFailure { error = it.message ?: "加载盘点单失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("盘点单"); Spacer(Modifier.weight(1f)); Button({ createVisible = true }, shape = RoundedCornerShape(6.dp)) { Text("新建盘点") } }
        Spacer(Modifier.height(14.dp))
        if (checks == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (checks != null && checks!!.isEmpty()) Text("暂无盘点单", color = Muted)
        checks.orEmpty().forEach { check ->
            val summary = check.optJSONObject("summary") ?: JSONObject()
            val status = check.optInt("status")
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(check.optString("checkName"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(goodsCheckStatus(status)) }
                    Spacer(Modifier.height(7.dp))
                    Text("${check.optJSONObject("store")?.optString("name") ?: "-"} · 共 ${summary.optInt("total")} 项，已盘 ${summary.optInt("counted")} 项", color = Muted, fontSize = 13.sp)
                    Text("创建于 ${check.optString("createdAt").take(10)}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton({
                        detailLoading = true
                        scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheck(check.optInt("id")) } }.onSuccess { selected = it; activeCheckId = check.optInt("id") }.onFailure { error = it.message ?: "加载盘点明细失败" }; detailLoading = false }
                    }, shape = RoundedCornerShape(6.dp)) { Text(if (detailLoading) "加载中..." else "查看盘点") }
                }
            }
        }
    }
    if (createVisible) AlertDialog(onDismissRequest = { createVisible = false }, title = { Text("新建盘点") }, text = { Column { if (stores.size > 1) { Text("所属门店", color = Muted, fontSize = 12.sp); StoreSelector(stores, selectedStoreId) { selectedStoreId = it }; Spacer(Modifier.height(10.dp)) }; OutlinedTextField(checkName, { checkName = it }, Modifier.fillMaxWidth(), label = { Text("盘点名称") }, singleLine = true) } }, confirmButton = { Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createGoodsCheck(checkName.trim(), storeId = selectedStoreId.toIntOrNull()) } }.onSuccess { createVisible = false; checkName = ""; reload++ }.onFailure { error = it.message ?: "创建盘点单失败" } } }, enabled = checkName.isNotBlank() && (stores.size <= 1 || selectedStoreId.isNotBlank())) { Text("创建") } }, dismissButton = { TextButton({ createVisible = false }) { Text("取消") } })
    selected?.let { check ->
        val items = check.optJSONArray("items") ?: JSONArray()
        val completed = check.optInt("status") == 2
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(check.optString("checkName")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("${check.optJSONObject("store")?.optString("name") ?: "-"} · ${goodsCheckStatus(check.optInt("status"))}", color = Muted); Spacer(Modifier.height(10.dp)); if (!completed) OutlinedButton({ candidateVisible = true }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) { Text("搜索商品并录入盘点") }; if (items.length() == 0) Text("尚无盘点记录", color = Muted); (0 until items.length()).forEach { index -> val item = items.getJSONObject(index); Text(item.optJSONObject("product")?.optString("name") ?: "商品", fontWeight = FontWeight.SemiBold); Text("系统 ${item.opt("systemQty") ?: 0} · 实盘 ${item.opt("effectiveCount") ?: "未盘"}", color = Muted, fontSize = 12.sp); if (!completed) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton({ countItem = item; countValue = item.opt("effectiveCount")?.toString().orEmpty() }, shape = RoundedCornerShape(6.dp)) { Text(if (item.opt("effectiveCount") == null) "录入实盘" else "修改实盘") }; if (item.optInt("id") > 0 && item.opt("effectiveCount") != null) OutlinedButton({ countItem = item; countValue = item.opt("effectiveCount")?.toString().orEmpty() }, shape = RoundedCornerShape(6.dp)) { Text("复盘") } } }; if (index < items.length() - 1) HorizontalDivider(Modifier.padding(vertical = 8.dp)) } } }, confirmButton = { if (completed) Button({ selected = null }) { Text("关闭") } else Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.finishGoodsCheck(check.optInt("id")) } }.onSuccess { selected = null; reload++ }.onFailure { error = it.message ?: "结束盘点失败" } } }) { Text("结束盘点") } }, dismissButton = { TextButton({ selected = null }) { Text("关闭") } })
    }
    countItem?.let { item ->
        AlertDialog(onDismissRequest = { countItem = null }, title = { Text("录入盘点数量") }, text = { Column { Text(item.optJSONObject("product")?.optString("name", "商品") ?: "商品", fontWeight = FontWeight.SemiBold); OutlinedTextField(countValue, { countValue = it }, Modifier.fillMaxWidth(), label = { Text("实际数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { Button(enabled = countValue.toDoubleOrNull()?.let { it >= 0 } == true && activeCheckId > 0, onClick = { val value = countValue.toDouble(); scope.launch { runCatching { withContext(Dispatchers.IO) { if (item.opt("firstCountQty") == null && item.opt("effectiveCount") == null) ApiClient.addGoodsCheckItem(activeCheckId, JSONObject().put("productId", item.optInt("productId")).put("batchNo", item.optString("batchNo")).put("locationName", item.optString("locationName")).put("firstCountQty", value)) else ApiClient.recountGoodsCheckItem(item.optInt("id"), JSONObject().put("recountQty", value)) } }.onSuccess { countItem = null; selected = null; reload++ }.onFailure { error = it.message ?: "保存盘点数量失败" } } }) { Text("保存") } }, dismissButton = { TextButton({ countItem = null }) { Text("取消") } })
    }
    locationItem?.let { item ->
        AlertDialog(onDismissRequest = { locationItem = null }, title = { Text("修改货位") }, text = { Column { Text(item.optJSONObject("product")?.optString("name", "商品") ?: "商品", fontWeight = FontWeight.SemiBold); OutlinedTextField(locationValue, { locationValue = it }, Modifier.fillMaxWidth(), label = { Text("货位名称") }, singleLine = true) } }, confirmButton = { Button(enabled = locationValue.isNotBlank(), onClick = { scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updateGoodsCheckLocation(item.optInt("id"), JSONObject().put("locationName", locationValue.trim())) } }.onSuccess { locationItem = null; selected = null; reload++ }.onFailure { error = it.message ?: "保存货位失败" } } }) { Text("保存") } }, dismissButton = { TextButton({ locationItem = null }) { Text("取消") } })
    }
    LaunchedEffect(candidateVisible, candidateKeyword, activeCheckId) { if (candidateVisible && activeCheckId > 0) runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheckCandidates(activeCheckId, candidateKeyword) } }.onSuccess { values -> candidates = (0 until values.length()).map { values.getJSONObject(it) } }.onFailure { error = it.message ?: "加载候选商品失败" } }
    if (candidateVisible) AlertDialog(onDismissRequest = { candidateVisible = false }, title = { Text("选择盘点商品") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(candidateKeyword, { candidateKeyword = it }, Modifier.fillMaxWidth(), label = { Text("商品名、编码或条码") }, singleLine = true); Spacer(Modifier.height(8.dp)); candidates.take(30).forEach { candidate -> val product = candidate.optJSONObject("product") ?: candidate; OutlinedButton({ countItem = JSONObject().put("product", product).put("productId", candidate.optInt("productId", product.optInt("id"))).put("batchNo", candidate.optString("batchNo")).put("locationName", candidate.optString("locationName")); countValue = ""; candidateVisible = false }, Modifier.fillMaxWidth().padding(bottom = 6.dp), shape = RoundedCornerShape(6.dp)) { Text("${product.optString("name")} · ${candidate.opt("quantity") ?: 0} ${product.optString("unit")}") } } }, confirmButton = { Button({ candidateVisible = false }) { Text("关闭") } })
}

private fun goodsCheckStatus(status: Int): String = when (status) { 0 -> "待盘点"; 1 -> "盘点中"; 2 -> "已完成"; else -> "未知" }

@Composable
private fun DifferencesScreen() {
    var tab by remember { mutableStateOf("current") }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var products by remember { mutableStateOf<List<JSONObject>?>(null) }
    var logs by remember { mutableStateOf<List<JSONObject>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var writeOff by remember { mutableStateOf<JSONObject?>(null) }
    var quantity by remember { mutableStateOf("") }
    var registerVisible by remember { mutableStateOf(false) }
    var registerProduct by remember { mutableStateOf<JSONObject?>(null) }
    var registerQuantity by remember { mutableStateOf("") }
    var registerType by remember { mutableStateOf("PRE_RECEIPT") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(tab, reload) {
        error = null
        if (tab == "current") runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.differences(), ApiClient.differenceProducts()) } }
            .onSuccess { (summary, values) -> stats = summary; products = (0 until values.length()).map { values.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载库存差异失败" }
        else runCatching { withContext(Dispatchers.IO) { ApiClient.differenceLogs() } }
            .onSuccess { values -> logs = (0 until values.length()).map { values.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载差异流水失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("当前差异", tab == "current") { tab = "current" }; SegmentedButton("差异流水", tab == "logs") { tab = "logs" } }; Spacer(Modifier.weight(1f)); if (tab == "current") OutlinedButton({ registerVisible = true }) { Text("登记差异") } }
        Spacer(Modifier.height(14.dp))
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (tab == "current") {
            StatsGrid(listOf("有差异货品" to (stats?.opt("total")?.toString() ?: "-"), "实货多" to (stats?.opt("more")?.toString() ?: "-"), "实货少" to (stats?.opt("less")?.toString() ?: "-")))
            Spacer(Modifier.height(14.dp))
            if (products == null && error == null) Text("加载中...", color = Muted)
            if (products != null && products!!.isEmpty()) Text("当前没有库存差异", color = Muted)
            products.orEmpty().forEach { product ->
                val diff = product.optDouble("diffQuantity")
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("${product.optString("name")} · ${product.optString("productCode")}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(if (diff > 0) "实货多" else "实货少") }; Spacer(Modifier.height(8.dp)); Text("当前差异：${if (diff > 0) "+" else ""}$diff ${product.optString("unit")}", color = if (diff > 0) Primary else Danger, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Button({ writeOff = product; quantity = kotlin.math.abs(diff).toString() }, shape = RoundedCornerShape(6.dp)) { Text("销账") } } }
            }
        } else {
            if (logs == null && error == null) Text("加载中...", color = Muted)
            if (logs != null && logs!!.isEmpty()) Text("暂无差异流水", color = Muted)
            logs.orEmpty().forEach { log -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Text(diffOperationLabel(log.optString("operationType")), fontWeight = FontWeight.SemiBold); Text("${log.optJSONObject("product")?.optString("productCode") ?: "-"} · ${log.optJSONObject("product")?.optString("name") ?: "商品"}", color = Muted, fontSize = 13.sp); val change = log.optDouble("changeQuantity"); Text("数量变化：${if (change > 0) "+" else ""}$change · ${log.optString("businessDate").take(10)}", color = if (change >= 0) Primary else Danger, fontSize = 12.sp); if (log.optString("operationType") != "REVERSAL" && log.optInt("id") > 0) TextButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.reverseDifference(log.optInt("id"), "安卓端冲销") } }.onSuccess { reload++ }.onFailure { error = it.message ?: "冲销失败" } } }) { Text("冲销") } } } }
        }
    }
    writeOff?.let { product -> AlertDialog(onDismissRequest = { writeOff = null }, title = { Text("差异销账") }, text = { Column { Text("${product.optString("name")} · 当前差异 ${product.optDouble("diffQuantity")}", color = Muted); Spacer(Modifier.height(10.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("销账数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) } }, confirmButton = { Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.writeOffDifference(JSONObject().put("productId", product.optInt("id")).put("quantity", quantity.toDouble()).put("businessDate", LocalDate.now().toString())) } }.onSuccess { writeOff = null; reload++ }.onFailure { error = it.message ?: "销账失败" } } }, enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true) { Text("确认销账") } }, dismissButton = { TextButton({ writeOff = null }) { Text("取消") } }) }
    if (registerVisible) AlertDialog(onDismissRequest = { registerVisible = false }, title = { Text("登记库存差异") }, text = { Column { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("PRE_RECEIPT" to "先到货", "PRE_SHIPMENT" to "先出货").forEach { (key, label) -> SegmentedButton(label, registerType == key) { registerType = key } } }; Spacer(Modifier.height(8.dp)); products.orEmpty().take(8).forEach { product -> OutlinedButton({ registerProduct = product }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) { Text(if (registerProduct?.optInt("id") == product.optInt("id")) "已选：${product.optString("name")}" else product.optString("name")) } }; OutlinedTextField(registerQuantity, { registerQuantity = it }, Modifier.fillMaxWidth(), label = { Text("数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { Button(enabled = registerProduct != null && registerQuantity.toDoubleOrNull()?.let { it > 0 } == true, onClick = { val product = registerProduct!!; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.registerDifference(JSONObject().put("operationType", registerType).put("businessDate", LocalDate.now().toString()).put("items", JSONArray().put(JSONObject().put("productId", product.optInt("id")).put("quantity", registerQuantity.toDouble()))) } }.onSuccess { registerVisible = false; registerProduct = null; registerQuantity = ""; reload++ }.onFailure { error = it.message ?: "登记差异失败" } } }) { Text("登记") } }, dismissButton = { TextButton({ registerVisible = false }) { Text("取消") } })
}

private fun diffOperationLabel(value: String): String = when (value) { "PRE_RECEIPT" -> "先到货未入库"; "PRE_SHIPMENT" -> "先出货未销库"; "WRITE_OFF_RECEIPT" -> "入库销账"; "WRITE_OFF_SHIPMENT" -> "销库销账"; "REVERSAL" -> "冲销"; "IMPORT_OPENING" -> "导入期初差异"; "IMPORT_ADJUSTMENT" -> "导入调整"; else -> value }

private fun prescriptionStatusLabel(value: Int): String = when (value) { 0 -> "进行中"; 1 -> "已完成"; 2 -> "已取消"; else -> "未知" }

@Composable
private fun PrescriptionsScreen() {
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var items by remember { mutableStateOf<List<JSONObject>?>(null) }
    var doctors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sources by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.doctors(), ApiClient.dictionaries("PrescriptionSource")) } }
            .onSuccess { (doctorArray, sourceArray) ->
                doctors = (0 until doctorArray.length()).map { doctorArray.getJSONObject(it) }
                sources = (0 until sourceArray.length()).map { sourceArray.getJSONObject(it) }
            }
            .onFailure { error = it.message ?: "加载处方选项失败" }
    }
    LaunchedEffect(keyword, status, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.prescriptions(status, keyword) } }
            .onSuccess { array -> items = (0 until array.length()).map { array.getJSONObject(it) } }
            .onFailure { error = it.message ?: "加载处方失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("处方管理"); Spacer(Modifier.weight(1f)); Button({ editing = JSONObject() }, shape = RoundedCornerShape(6.dp)) { Text("新建处方") } }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("处方号、顾客、手机号、医生") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("全部", status == null) { status = null }; SegmentedButton("进行中", status == 0) { status = 0 }; SegmentedButton("已完成", status == 1) { status = 1 }; SegmentedButton("已取消", status == 2) { status = 2 } }
        Spacer(Modifier.height(12.dp)); error?.let { Text(it, color = Danger, fontSize = 13.sp) }
        if (items == null && error == null) Text("加载中...", color = Muted)
        if (items != null && items!!.isEmpty()) Text("暂无处方", color = Muted)
        items.orEmpty().forEach { item ->
            val prescriptionNo = item.optString("prescriptionNo", "处方")
            val state = item.optInt("status")
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { detail = item }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(prescriptionNo, fontWeight = FontWeight.SemiBold); Text("${item.optString("customerName", "未填写")} · ${item.optString("phone", "-")}", color = Muted, fontSize = 12.sp) }; StatusPill(prescriptionStatusLabel(state)) }
                    Spacer(Modifier.height(8.dp)); Text("医生：${item.optJSONObject("doctor")?.optString("name", "-") ?: "-"} · ${item.optJSONObject("source")?.optString("name", "-") ?: "-"}", color = Ink, fontSize = 13.sp)
                    Text("剂数：${item.opt("totalDose") ?: 0} · ${item.optString("createdAt").replace("T", " ").take(16)}", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
    detail?.let { item ->
        val canEdit = item.optInt("status") != 1
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(item.optString("prescriptionNo", "处方详情")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("顾客：${item.optString("customerName", "-")}\n手机号：${item.optString("phone", "-")}\n医生：${item.optJSONObject("doctor")?.optString("name", "-") ?: "-"}\n来源：${item.optJSONObject("source")?.optString("name", "-") ?: "-"}\n状态：${prescriptionStatusLabel(item.optInt("status"))}\n备注：${item.optString("remark", "-")}", color = Ink); Spacer(Modifier.height(10.dp)); val plans = item.optJSONArray("plans") ?: JSONArray(); Text("加工批次：${plans.length()}", color = Muted, fontSize = 13.sp); (0 until plans.length()).forEach { i -> val plan = plans.getJSONObject(i); Text("第${plan.optInt("batchNo", i + 1)}批 · ${plan.optJSONObject("processType")?.optString("name", "加工") ?: "加工"} · ${planStatus(plan.optInt("status"))}", fontSize = 12.sp) } } }, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { if (canEdit) Button({ editing = item; detail = null }) { Text("编辑") }; if (item.optInt("status") == 0) OutlinedButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updatePrescription(item.optInt("id"), JSONObject().put("status", 2)) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "取消处方失败" } } }) { Text("取消处方") }; if (plansEmpty(item)) OutlinedButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.deletePrescription(item.optInt("id")) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "删除处方失败" } } }) { Text("删除") }; OutlinedButton({ detail = null }) { Text("关闭") } } })
    }
    editing?.let { initial -> PrescriptionFormDialog(initial, doctors, sources, onClose = { editing = null }, onSaved = { editing = null; reload++ }, onError = { error = it }) }
}

private fun plansEmpty(item: JSONObject): Boolean = (item.optJSONArray("plans")?.length() ?: 0) == 0

@Composable
private fun PrescriptionFormDialog(initial: JSONObject, doctors: List<JSONObject>, sources: List<JSONObject>, onClose: () -> Unit, onSaved: () -> Unit, onError: (String) -> Unit) {
    val isEdit = initial.has("id")
    var customer by remember(initial) { mutableStateOf(initial.optString("customerName")) }
    var phone by remember(initial) { mutableStateOf(initial.optString("phone")) }
    var remark by remember(initial) { mutableStateOf(initial.optString("remark")) }
    var doctorId by remember(initial) { mutableStateOf(initial.optInt("doctorId").takeIf { it > 0 } ?: doctors.firstOrNull()?.optInt("id", 0) ?: 0) }
    var sourceId by remember(initial) { mutableStateOf(initial.optInt("sourceId").takeIf { it > 0 } ?: sources.firstOrNull()?.optInt("id", 0) ?: 0) }
    var external by remember(initial) { mutableStateOf(initial.optInt("isExternal") == 1) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (isEdit) "编辑处方" else "新建处方") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(customer, { customer = it }, Modifier.fillMaxWidth(), label = { Text("顾客姓名") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(11) }, Modifier.fillMaxWidth(), label = { Text("手机号（可选）") }, singleLine = true); Spacer(Modifier.height(8.dp)); Text("医生", color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { doctors.take(4).forEach { d -> SegmentedButton(d.optString("name", "医生"), doctorId == d.optInt("id")) { doctorId = d.optInt("id") } } }; Spacer(Modifier.height(8.dp)); Text("处方来源", color = Muted, fontSize = 12.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { sources.take(4).forEach { s -> SegmentedButton(s.optString("name", "来源"), sourceId == s.optInt("id")) { sourceId = s.optInt("id") } } }; Spacer(Modifier.height(8.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("外方处方"); Spacer(Modifier.weight(1f)); androidx.compose.material3.Switch(external, { external = it }) }; OutlinedTextField(remark, { remark = it }, Modifier.fillMaxWidth(), label = { Text("备注") }) } }, confirmButton = { Button(enabled = customer.isNotBlank() && doctorId > 0 && sourceId > 0 && !busy, onClick = { busy = true; val payload = JSONObject().put("customerName", customer.trim()).put("phone", phone.trim()).put("doctorId", doctorId).put("sourceId", sourceId).put("isExternal", external).put("remark", remark.trim()); scope.launch { runCatching { withContext(Dispatchers.IO) { if (isEdit) ApiClient.updatePrescription(initial.optInt("id"), payload) else ApiClient.createPrescription(payload) } }.onSuccess { onSaved() }.onFailure { onError(it.message ?: "保存处方失败") }; busy = false } }) { Text(if (busy) "保存中..." else "保存") } }, dismissButton = { TextButton({ if (!busy) onClose() }) { Text("取消") } })
}

@Composable
private fun TransfersScreen() {
    var transfers by remember { mutableStateOf<List<JSONObject>?>(null) }
    var stores by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var createVisible by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var fromStoreId by remember { mutableStateOf("") }
    var toStoreId by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var itemSpecification by remember { mutableStateOf("") }
    var itemQuantity by remember { mutableStateOf("1") }
    var itemUnit by remember { mutableStateOf("") }
    var expectedReturnDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var returnItem by remember { mutableStateOf<JSONObject?>(null) }
    var returnQuantity by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.transfers(), ApiClient.transferStores()) } }
            .onSuccess { (transferValues, storeValues) ->
                transfers = (0 until transferValues.length()).map { transferValues.getJSONObject(it) }
                stores = (0 until storeValues.length()).map { storeValues.getJSONObject(it) }
            }
            .onFailure { error = it.message ?: "加载门店调拨失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("门店调拨"); Spacer(Modifier.weight(1f)); Button({ createVisible = true }, shape = RoundedCornerShape(6.dp)) { Text("新建调拨") } }
        Spacer(Modifier.height(14.dp))
        if (transfers == null && error == null) Text("加载中...", color = Muted)
        if (error != null) Text(error!!, color = Danger, fontSize = 13.sp)
        if (transfers != null && transfers!!.isEmpty()) Text("暂无调拨单", color = Muted)
        transfers.orEmpty().forEach { transfer ->
            val items = transfer.optJSONArray("items") ?: JSONArray()
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(transfer.optString("transferNo"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(transferStatusLabel(transfer.optInt("status"), transfer.optInt("outboundStatus"))) }
                    Spacer(Modifier.height(8.dp))
                    Text("${transfer.optJSONObject("fromStore")?.optString("name") ?: "-"}  ->  ${transfer.optJSONObject("toStore")?.optString("name") ?: "-"}", color = Ink)
                    Text("${items.length()} 项 · ${transfer.optString("transferDate").take(10)} · 预计归还 ${transfer.optString("expectedReturnDate").take(10)}", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.transferDetail(transfer.optInt("id")) } }.onSuccess { detail = it }.onFailure { error = it.message ?: "加载调拨详情失败" } } }, shape = RoundedCornerShape(6.dp)) { Text("查看详情") }
                }
            }
        }
    }
    if (createVisible) AlertDialog(
        onDismissRequest = { createVisible = false },
        title = { Text("新建调拨") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("调出门店", color = Muted, fontSize = 12.sp)
                StoreSelector(stores, fromStoreId) { fromStoreId = it }
                Spacer(Modifier.height(8.dp))
                Text("调入门店", color = Muted, fontSize = 12.sp)
                StoreSelector(stores, toStoreId) { toStoreId = it }
                OutlinedTextField(itemName, { itemName = it }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("物品名称") }, singleLine = true)
                OutlinedTextField(itemSpecification, { itemSpecification = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("规格（可选）") }, singleLine = true)
                OutlinedTextField(itemQuantity, { itemQuantity = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("借调数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(itemUnit, { itemUnit = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("单位") }, singleLine = true)
                OutlinedTextField(expectedReturnDate, { expectedReturnDate = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("预计归还日期 YYYY-MM-DD") }, singleLine = true)
            }
        },
        confirmButton = {
            val valid = fromStoreId.isNotBlank() && toStoreId.isNotBlank() && fromStoreId != toStoreId && itemName.isNotBlank() && itemUnit.isNotBlank() && itemQuantity.toDoubleOrNull()?.let { it > 0 } == true
            Button({
                val item = JSONObject().put("itemName", itemName.trim()).put("specification", itemSpecification.trim()).put("quantity", itemQuantity.toDouble()).put("unit", itemUnit.trim())
                val payload = JSONObject().put("fromStoreId", fromStoreId.toInt()).put("toStoreId", toStoreId.toInt()).put("transferDate", LocalDate.now().toString()).put("expectedReturnDate", expectedReturnDate).put("items", JSONArray().put(item))
                scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createTransfer(payload) } }.onSuccess { createVisible = false; itemName = ""; itemSpecification = ""; itemQuantity = "1"; itemUnit = ""; reload++ }.onFailure { error = it.message ?: "创建调拨失败" } }
            }, enabled = valid) { Text("创建") }
        },
        dismissButton = { TextButton({ createVisible = false }) { Text("取消") } },
    )
    detail?.let { transfer ->
        val items = transfer.optJSONArray("items") ?: JSONArray()
        val canConfirm = transfer.optJSONObject("permissions")?.optBoolean("canConfirmOutbound") == true
        val pendingReturn = transfer.optJSONArray("returnRecords")?.let { records ->
            (0 until records.length()).map { records.getJSONObject(it) }.firstOrNull { it.optInt("status") == 0 }
        }
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(transfer.optString("transferNo")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("${transfer.optJSONObject("fromStore")?.optString("name") ?: "-"}  ->  ${transfer.optJSONObject("toStore")?.optString("name") ?: "-"}", color = Muted); Spacer(Modifier.height(8.dp)); (0 until items.length()).forEach { index -> val item = items.getJSONObject(index); Text(item.optString("itemName"), fontWeight = FontWeight.SemiBold); Text("${item.opt("quantity") ?: 0} ${item.optString("unit")} · 已归还 ${item.opt("returnedQuantity") ?: 0}", color = Muted, fontSize = 12.sp); val available = item.optDouble("availableReturnQuantity", 0.0); if (available > 0 && transfer.optJSONObject("permissions")?.optBoolean("canSubmitReturn") == true) OutlinedButton({ returnItem = item; returnQuantity = available.toString() }, shape = RoundedCornerShape(6.dp)) { Text("申请归还") }; if (index < items.length() - 1) HorizontalDivider(Modifier.padding(vertical = 8.dp)) }; pendingReturn?.let { record -> Spacer(Modifier.height(10.dp)); Text("待确认归还：${record.opt("quantity") ?: 0} · ${record.optString("returnDate").take(10)}", color = Warning, fontSize = 13.sp) } } }, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (canConfirm) Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.confirmOutbound(transfer.optInt("id")) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "确认调出失败" } } }) { Text("确认调出") }; if (pendingReturn != null && transfer.optJSONObject("permissions")?.optBoolean("canConfirmReturn") == true) Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.confirmReturn(transfer.optInt("id"), pendingReturn.optInt("id")) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "确认归还失败" } } }) { Text("确认归还") }; if (transfer.optJSONObject("permissions")?.optBoolean("canCancel") == true) OutlinedButton({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.cancelTransfer(transfer.optInt("id"), "安卓端取消") } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "取消调拨失败" } } }) { Text("取消") }; OutlinedButton({ detail = null }) { Text("关闭") } } }, dismissButton = { TextButton({ detail = null }) { Text("关闭") } })
    }
    returnItem?.let { item ->
        AlertDialog(onDismissRequest = { returnItem = null }, title = { Text("申请归还") }, text = { Column { Text(item.optString("itemName"), fontWeight = FontWeight.SemiBold); OutlinedTextField(returnQuantity, { returnQuantity = it }, Modifier.fillMaxWidth(), label = { Text("归还数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true) } }, confirmButton = { Button(enabled = returnQuantity.toDoubleOrNull()?.let { it > 0 } == true, onClick = { val transferId = detail?.optInt("id") ?: 0; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.addTransferReturns(transferId, JSONObject().put("returnDate", LocalDate.now().toString()).put("items", JSONArray().put(JSONObject().put("transferItemId", item.optInt("id")).put("quantity", returnQuantity.toDouble()))) } }.onSuccess { returnItem = null; detail = null; reload++ }.onFailure { error = it.message ?: "提交归还失败" } } }) { Text("提交") } }, dismissButton = { TextButton({ returnItem = null }) { Text("取消") } })
    }
}

@Composable
private fun StoreSelector(stores: List<JSONObject>, selectedId: String, onSelect: (String) -> Unit) {
    if (stores.isEmpty()) Text("暂无可用门店", color = Muted, fontSize = 12.sp)
    stores.forEach { store ->
        val id = store.opt("id")?.toString().orEmpty()
        if (id == selectedId) Button({ onSelect(id) }, Modifier.fillMaxWidth().padding(top = 5.dp), shape = RoundedCornerShape(6.dp)) { Text(store.optString("name")) }
        else OutlinedButton({ onSelect(id) }, Modifier.fillMaxWidth().padding(top = 5.dp), shape = RoundedCornerShape(6.dp)) { Text(store.optString("name")) }
    }
}

private fun transferStatusLabel(status: Int, outboundStatus: Int): String = when {
    status == 3 -> "已取消"
    status == 2 -> "已调平"
    status == 1 -> "部分归还"
    outboundStatus == 0 -> "待出库"
    else -> "借出中"
}
private fun packageStatus(status: Int): String = when (status) { 0 -> "待领取"; 1 -> "已领取"; else -> "已关闭" }

private fun packageItem(value: JSONObject): PackageItem {
    val statusCode = value.optInt("status", 0)
    val store = value.optJSONObject("store")?.optString("name", "") ?: ""
    val methodCode = value.optInt("pickupMethod", 0)
    val method = when (methodCode) { 0 -> "自提"; 1 -> "跑腿"; 2 -> "快递"; else -> "未设置" }
    return PackageItem(value.optString("itemName", "包裹"), value.optString("receiverName", "客户"), value.optString("pickupCode", "-"), packageStatus(statusCode), value.optString("createdAt", "-").replace("T", " ").take(16), value.optInt("id", 0), value.optString("receiverPhone", "-"), store, method, value.optString("itemInfo", ""), statusCode, methodCode)
}

@Composable
private fun ProcessingScreenV2() {
    var mode by remember { mutableStateOf("plans") }
    var view by remember { mutableStateOf("today-all") }
    var keyword by remember { mutableStateOf("") }
    var plans by remember { mutableStateOf<List<JSONObject>?>(null) }
    var pickupTasks by remember { mutableStateOf<List<PackageItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var detailPlan by remember { mutableStateOf<JSONObject?>(null) }
    var workflowPlan by remember { mutableStateOf<JSONObject?>(null) }
    var busyId by remember { mutableStateOf<Int?>(null) }
    var reload by remember { mutableStateOf(0) }
    var createVisible by remember { mutableStateOf(false) }
    var prescriptions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var processTypes by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var prescriptionId by remember { mutableStateOf(0) }
    var processTypeId by remember { mutableStateOf(0) }
    var totalDose by remember { mutableStateOf("1") }
    var processDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var bagCount by remember { mutableStateOf("1") }
    var volumeMl by remember { mutableStateOf("200") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { Pair(ApiClient.prescriptions(status = 0), ApiClient.dictionaries("ProcessType")) } }.onSuccess { (p, types) -> prescriptions = (0 until p.length()).map { p.getJSONObject(it) }; processTypes = (0 until types.length()).map { types.getJSONObject(it) }; prescriptionId = prescriptions.firstOrNull()?.optInt("id", 0) ?: 0; processTypeId = processTypes.firstOrNull()?.optInt("id", 0) ?: 0 }.onFailure { error = it.message ?: "加载加工计划选项失败" } }
    LaunchedEffect(mode, view, keyword, reload) {
        error = null
        if (mode == "plans") {
            runCatching { withContext(Dispatchers.IO) { ApiClient.plans(view, keyword) } }.onSuccess { a -> plans = (0 until a.length()).map { a.getJSONObject(it) } }.onFailure { error = it.message ?: "加载加工计划失败" }
        } else {
            runCatching { withContext(Dispatchers.IO) { ApiClient.packages(source = "processing", dateScope = "pickup-workbench", keyword = keyword) } }.onSuccess { a -> pickupTasks = (0 until a.length()).map { packageItem(a.getJSONObject(it)) } }.onFailure { error = it.message ?: "加载待领取任务失败" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { SegmentedButton("加工计划", mode == "plans") { mode = "plans"; view = "today-all" }; SegmentedButton("待领取", mode == "pickup") { mode = "pickup" }; Spacer(Modifier.weight(1f)); if (mode == "plans") OutlinedButton({ createVisible = true }, shape = RoundedCornerShape(6.dp)) { Text("新建计划") } }
        Spacer(Modifier.height(12.dp)); OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("姓名、手机号或备注") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        if (mode == "plans") { Spacer(Modifier.height(10.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("today-all" to "今日全部", "today-waiting" to "待加工", "overdue" to "逾期", "processing" to "加工中", "today-finished" to "今日完成", "tomorrow" to "明日").forEach { (key, label) -> SegmentedButton(label, view == key) { view = key } } } }
        Spacer(Modifier.height(14.dp)); error?.let { Text(it, color = Danger, fontSize = 13.sp) }; if (plans == null && pickupTasks == null && error == null) Text("加载中...", color = Muted)
        if (mode == "plans") {
            val values = plans.orEmpty(); if (plans != null && values.isEmpty()) Text("暂无加工计划", color = Muted)
            values.forEach { plan ->
                val id = plan.optInt("id", 0); val status = plan.optInt("status", 0); val prescription = plan.optJSONObject("prescription"); val customer = prescription?.optString("customerName", "客户") ?: plan.optString("customerName", "客户"); val phone = prescription?.optString("phone", "-") ?: plan.optString("customerPhone", "-"); val processType = plan.optJSONObject("processType")?.optString("name", "加工") ?: "加工"; val store = plan.optJSONObject("store")?.optString("name", "") ?: ""
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("$customer · $processType", fontWeight = FontWeight.SemiBold); Text("$phone${if (store.isNotBlank()) " · $store" else ""}", color = Muted, fontSize = 12.sp) }; StatusPill(planStatus(status)) }; Spacer(Modifier.height(8.dp)); Text("第 ${plan.optInt("batchNo", 1)} 批 · ${plan.optInt("totalDose", 0)} 剂 · ${plan.optString("processDate", "等待通知").take(10)}", color = Muted, fontSize = 13.sp); plan.optString("processRemark", "").takeIf { it.isNotBlank() }?.let { Text("备注：$it", color = Muted, fontSize = 12.sp) }; Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ detailPlan = plan }, shape = RoundedCornerShape(6.dp)) { Text("详情") }; when { status == 0 -> Button(enabled = busyId == null, onClick = { busyId = id; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(id, 1) } }.onSuccess { reload++ }.onFailure { error = it.message ?: "开始加工失败" }; busyId = null } }, shape = RoundedCornerShape(6.dp)) { Text("开始加工") }; status in 1..4 -> OutlinedButton({ workflowPlan = plan }, shape = RoundedCornerShape(6.dp)) { Text("工序详情") } }; if (status == 2) Button(enabled = busyId == null, onClick = { busyId = id; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.generatePackage(id) } }.onSuccess { reload++ }.onFailure { error = it.message ?: "生成包裹失败" }; busyId = null } }, shape = RoundedCornerShape(6.dp)) { Text("生成包裹") }; if (status == 1) OutlinedButton(enabled = busyId == null, onClick = { busyId = id; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(id, 5) } }.onSuccess { reload++ }.onFailure { error = it.message ?: "取消失败" }; busyId = null } }, shape = RoundedCornerShape(6.dp)) { Text("取消加工") } } } }
            }
        } else {
            val values = pickupTasks.orEmpty(); if (pickupTasks != null && values.isEmpty()) Text("暂无待领取任务", color = Muted)
            values.filter { keyword.isBlank() || it.customer.contains(keyword) || it.code.contains(keyword) }.forEach { item -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${item.customer} · ${item.name}", fontWeight = FontWeight.SemiBold); Text("${item.phone} · ${item.time}", color = Muted, fontSize = 12.sp) }; StatusPill(item.status) }; Spacer(Modifier.height(8.dp)); Text("取货码：${item.code}", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ detailPlan = JSONObject().put("packageId", item.id).put("pickupCode", item.code).put("receiverName", item.customer).put("receiverPhone", item.phone) }, shape = RoundedCornerShape(6.dp)) { Text("详情") }; if (item.statusCode == 0) Button({ workflowPlan = JSONObject().put("packageId", item.id).put("packageCode", item.code) }, shape = RoundedCornerShape(6.dp)) { Text("核销") } } } }
        }
    }
    detailPlan?.let { detail -> if (detail.has("packageId")) PackageDetailDialogV2(packageItem(JSONObject().put("id", detail.optInt("packageId")).put("pickupCode", detail.optString("pickupCode")).put("receiverName", detail.optString("receiverName")).put("receiverPhone", detail.optString("receiverPhone"))), { detailPlan = null }) else PlanDetailDialog(detail, onClose = { detailPlan = null }, onDelete = { id -> scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.deleteProcessingPlan(id) } }.onSuccess { detailPlan = null; reload++ }.onFailure { error = it.message ?: "删除加工计划失败" } } }) }
    workflowPlan?.let { plan -> if (plan.has("packageId")) PackageDetailDialogV2(packageItem(JSONObject().put("id", plan.optInt("packageId")).put("pickupCode", plan.optString("packageCode")).put("receiverName", "客户")), { workflowPlan = null }) else WorkflowDialog(plan) { workflowPlan = null } }
    if (createVisible) AlertDialog(onDismissRequest = { createVisible = false }, title = { Text("新建加工计划") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("处方", color = Muted, fontSize = 12.sp); prescriptions.take(8).forEach { prescription -> SegmentedButton("${prescription.optString("prescriptionNo")} · ${prescription.optString("customerName")}", prescriptionId == prescription.optInt("id")) { prescriptionId = prescription.optInt("id") } }; Spacer(Modifier.height(8.dp)); Text("加工类型", color = Muted, fontSize = 12.sp); processTypes.take(6).forEach { type -> SegmentedButton(type.optString("name"), processTypeId == type.optInt("id")) { processTypeId = type.optInt("id") } }; OutlinedTextField(totalDose, { totalDose = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("总剂数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(processDate, { processDate = it }, Modifier.fillMaxWidth(), label = { Text("加工日期 YYYY-MM-DD") }, singleLine = true); val decoction = processTypes.firstOrNull { it.optInt("id") == processTypeId }?.let { it.optString("code") == "DECOCTION" || it.optString("name") == "代煎" } == true; if (decoction) { OutlinedTextField(bagCount, { bagCount = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), label = { Text("袋数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); OutlinedTextField(volumeMl, { volumeMl = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("每袋毫升数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) } } }, confirmButton = { val decoction = processTypes.firstOrNull { it.optInt("id") == processTypeId }?.let { it.optString("code") == "DECOCTION" || it.optString("name") == "代煎" } == true; Button(enabled = prescriptionId > 0 && processTypeId > 0 && totalDose.toIntOrNull()?.let { it > 0 } == true && (!decoction || (bagCount.toIntOrNull()?.let { it > 0 } == true && volumeMl.toIntOrNull()?.let { it > 0 } == true)), onClick = { val payload = JSONObject().put("prescriptionId", prescriptionId).put("processTypeId", processTypeId).put("totalDose", totalDose.toInt()).put("batchNo", 1).put("scheduleType", 1).put("processDate", processDate).put("pickupMethod", 0); if (decoction) { payload.put("bagCount", bagCount.toInt()); payload.put("volumeMl", volumeMl.toInt()) }; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createProcessingPlan(payload) } }.onSuccess { createVisible = false; reload++ }.onFailure { error = it.message ?: "创建加工计划失败" } } }) { Text("创建") } }, dismissButton = { TextButton({ createVisible = false }) { Text("取消") } })
}

@Composable
private fun PlanDetailDialog(plan: JSONObject, onClose: () -> Unit, onDelete: (Int) -> Unit) {
    val prescription = plan.optJSONObject("prescription")
    val customer = prescription?.optString("customerName", "客户") ?: plan.optString("customerName", "客户")
    val text = listOf(
        "顾客：$customer",
        "手机号：${prescription?.optString("phone", "-") ?: "-"}",
        "批次：第${plan.optInt("batchNo", 1)}批",
        "剂数：${plan.optInt("totalDose", 0)}剂",
        "状态：${planStatus(plan.optInt("status", 0))}",
        "备注：${plan.optString("processRemark", plan.optString("remark", "-"))}",
    ).joinToString("\n")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("加工计划详情") },
        text = { Text(text, color = Ink) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (plan.optInt("status") == 0) OutlinedButton({ onDelete(plan.optInt("id")) }) { Text("删除", color = Danger) }
                Button(onClose) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun WorkflowDialog(plan: JSONObject, onClose: () -> Unit) {
    var workflow by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var equipmentCode by remember { mutableStateOf("") }
    var portionNo by remember { mutableStateOf("1") }
    var stage by remember { mutableStateOf(3) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { input -> ApiClient.completeDispensing(plan.optInt("id"), "dispensing-${System.currentTimeMillis()}.jpg", context.contentResolver.getType(uri) ?: "image/jpeg", input.readBytes()) } ?: throw IllegalStateException("无法读取照片") } }
                .onSuccess { workflow = it }
                .onFailure { error = it.message ?: "上传调配照片失败" }
            busy = false
        }
    }
    LaunchedEffect(plan.optInt("id")) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.processingWorkflow(plan.optInt("id")) } }
            .onSuccess { workflow = it }
            .onFailure { error = it.message ?: "工序加载失败" }
    }
    val usages = workflow?.optJSONArray("equipmentUsages")
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { Button(onClose) { Text("关闭") } },
        title = { Text("工序详情") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    error != null -> Text(error!!, color = Danger)
                    workflow == null -> Text("加载中...", color = Muted)
                    else -> {
                        val records = usages ?: JSONArray()
                        Text("当前阶段：${workflow!!.optInt("currentStage", 0)}", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        if (workflow!!.optString("dispensingCompletedAt").isBlank()) Button(enabled = !busy, onClick = { photoLauncher.launch("image/*") }, shape = RoundedCornerShape(6.dp)) { Text(if (busy) "上传中..." else "上传调配完成照片") }
                        Text("扫码工序操作", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SegmentedButton("浸泡", stage == 3) { stage = 3 }; SegmentedButton("煎煮", stage == 4) { stage = 4 } }
                        OutlinedTextField(equipmentCode, { equipmentCode = it }, Modifier.fillMaxWidth(), label = { Text("设备编号或二维码") }, singleLine = true)
                        OutlinedTextField(portionNo, { portionNo = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(), label = { Text("分组编号") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Button(enabled = !busy && equipmentCode.isNotBlank() && portionNo.toIntOrNull()?.let { it > 0 } == true, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.startEquipmentUsage(plan.optInt("id"), JSONObject().put("stage", stage).put("portionNo", portionNo.toInt()).put("equipmentCode", equipmentCode.trim()).put("requestId", "android-${System.currentTimeMillis()}")) } }.onSuccess { workflow = it }.onFailure { error = it.message ?: "开始工序失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text(if (busy) "提交中..." else "开始工序") }
                        Spacer(Modifier.height(8.dp))
                        if (records.length() == 0) Text("暂无设备工序记录", color = Muted)
                        for (i in 0 until records.length()) {
                            val usage = records.getJSONObject(i)
                            val usageStage = usage.optInt("stage")
                            val stageText = when (usageStage) { 3 -> "浸泡"; 4 -> "煎煮"; 5 -> "打包"; else -> "工序" }
                            val usageStatus = usage.optInt("status")
                            val state = when (usageStatus) { 1 -> "进行中"; 3 -> "已作废"; else -> "已完成" }
                            Text("第${usage.optInt("portionNo", 0)}组 · $stageText · $state", color = Ink)
                            Text(usage.optString("startedAt", "-"), color = Muted, fontSize = 12.sp)
                            if (usageStatus == 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (usageStage == 4) Button(enabled = !busy && equipmentCode.isNotBlank(), onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.startPackaging(plan.optInt("id"), usage.optInt("id"), JSONObject().put("equipmentCode", equipmentCode.trim()).put("requestId", "android-${System.currentTimeMillis()}")) } }.onSuccess { workflow = it }.onFailure { error = it.message ?: "开始打包失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text("开始打包") }
                                if (usageStage == 5) Button(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.finishEquipmentUsage(plan.optInt("id"), usage.optInt("id")) } }.onSuccess { workflow = it }.onFailure { error = it.message ?: "完成打包失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text("完成打包") }
                                OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.voidEquipmentUsage(plan.optInt("id"), usage.optInt("id"), "安卓端撤销") } }.onSuccess { workflow = it }.onFailure { error = it.message ?: "撤销失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text("撤销") }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        if (workflow!!.optBoolean("canCompleteWorkflow") || workflow!!.optBoolean("canFinalizeWorkflow")) Button(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.transitionPlan(plan.optInt("id"), 2) } }.onSuccess { onClose() }.onFailure { error = it.message ?: "完成加工失败" }; busy = false } }, shape = RoundedCornerShape(6.dp)) { Text("完成加工") }
                    }
                }
            }
        },
    )
}

@Composable
private fun PackagesScreenV2(onOpen: (PackageItem) -> Unit) {
    var keyword by remember { mutableStateOf("") }; var statusFilter by remember { mutableStateOf<Int?>(null) }; var list by remember { mutableStateOf<List<PackageItem>?>(null) }; var error by remember { mutableStateOf<String?>(null) }; var reload by remember { mutableStateOf(0) }
    LaunchedEffect(keyword, statusFilter, reload) { error = null; runCatching { withContext(Dispatchers.IO) { ApiClient.packages(status = statusFilter, keyword = keyword) } }.onSuccess { a -> list = (0 until a.length()).map { packageItem(a.getJSONObject(it)) } }.onFailure { error = it.message ?: "加载包裹失败" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("取货码、手机号、姓名、物品") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true); Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("全部", statusFilter == null) { statusFilter = null }; SegmentedButton("待取", statusFilter == 0) { statusFilter = 0 }; SegmentedButton("已取", statusFilter == 1) { statusFilter = 1 } }; Spacer(Modifier.height(14.dp)); error?.let { Text(it, color = Danger, fontSize = 13.sp) }; if (list == null && error == null) Text("加载中...", color = Muted); if (list != null && list!!.isEmpty()) Text("暂无包裹", color = Muted); list.orEmpty().filter { keyword.isBlank() || it.customer.contains(keyword) || it.phone.contains(keyword) || it.code.contains(keyword) || it.name.contains(keyword) }.forEach { item -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpen(item) }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text("${item.customer} · ${item.phone}", color = Muted, fontSize = 12.sp) }; StatusPill(item.status) }; Spacer(Modifier.height(8.dp)); Text("取货码：${item.code}", color = Primary, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("${item.method}${if (item.store.isNotBlank()) " · ${item.store}" else ""} · ${item.time}", color = Muted, fontSize = 12.sp) } } }
}

@Composable
private fun PackageDetailDialogV2(item: PackageItem, onClose: () -> Unit) {
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(item.name) }
    var editReceiver by remember { mutableStateOf(item.customer) }
    var editPhone by remember { mutableStateOf(item.phone) }
    var editInfo by remember { mutableStateOf(item.info) }
    var editMethod by remember { mutableStateOf(item.methodCode) }
    var tracking by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.id) { if (item.id > 0) runCatching { withContext(Dispatchers.IO) { ApiClient.packageDetail(item.id) } }.onSuccess { detail = it }.onFailure { error = it.message ?: "详情加载失败" } }
    val current = detail
    if (editing) {
        AlertDialog(onDismissRequest = { if (!busy) editing = false }, title = { Text("编辑包裹") }, text = { Column { OutlinedTextField(editName, { editName = it }, Modifier.fillMaxWidth(), label = { Text("物品名称") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(editReceiver, { editReceiver = it }, Modifier.fillMaxWidth(), label = { Text("收件人") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(editPhone, { editPhone = it }, Modifier.fillMaxWidth(), label = { Text("手机号") }, singleLine = true); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) -> SegmentedButton(label, editMethod == key) { editMethod = key } } }; OutlinedTextField(editInfo, { editInfo = it }, Modifier.fillMaxWidth(), label = { Text("备注") }) } }, confirmButton = { Button(enabled = editName.isNotBlank() && editReceiver.isNotBlank() && !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.updatePackage(item.id, JSONObject().put("itemName", editName.trim()).put("receiverName", editReceiver.trim()).put("receiverPhone", editPhone.trim()).put("pickupMethod", editMethod).put("itemInfo", editInfo.trim())) } }.onSuccess { editing = false }.onFailure { error = it.message ?: "保存包裹失败" }; busy = false } }) { Text(if (busy) "保存中..." else "保存") } }, dismissButton = { TextButton({ if (!busy) editing = false }) { Text("取消") } })
    } else {
        AlertDialog(onDismissRequest = onClose, confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (item.statusCode == 0) Button(enabled = !busy && (item.methodCode != 2 || tracking.isNotBlank()), onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.verifyPackage(item.code, item.methodCode, tracking.trim()) } }.onSuccess { onClose() }.onFailure { error = it.message ?: "核销失败" }; busy = false } }) { Text(if (busy) "核销中..." else "确认核销") }; if (item.statusCode == 0) OutlinedButton({ editing = true }) { Text("编辑") }; if (item.statusCode == 0) OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.deletePackage(item.id) } }.onSuccess { onClose() }.onFailure { error = it.message ?: "删除包裹失败" }; busy = false } }) { Text("删除") }; OutlinedButton(onClose) { Text("关闭") } } }, title = { Text("包裹详情") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) { Text(item.code, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary); Spacer(Modifier.height(10.dp)); FakeQr(item.code); Spacer(Modifier.height(10.dp)); Text("物品：${current?.optString("itemName", item.name) ?: item.name}\n客户：${current?.optString("receiverName", item.customer) ?: item.customer}\n手机号：${current?.optString("receiverPhone", item.phone) ?: item.phone}\n取货方式：${item.method}\n状态：${item.status}\n门店：${current?.optJSONObject("store")?.optString("name", item.store) ?: item.store}\n备注：${current?.optString("itemInfo", item.info) ?: item.info}", color = Ink); if (item.statusCode == 0 && item.methodCode == 2) { Spacer(Modifier.height(8.dp)); OutlinedTextField(tracking, { tracking = it }, Modifier.fillMaxWidth(), label = { Text("快递单号（核销必填）") }, singleLine = true) }; error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Danger, fontSize = 12.sp) } } })
    }
}

@Composable
private fun PackagesScreenV3(onOpen: (PackageItem) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<Int?>(null) }
    var items by remember { mutableStateOf<List<PackageItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf(false) }
    var verify by remember { mutableStateOf(false) }
    var itemName by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf("") }
    var receiverPhone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(0) }
    var tracking by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(keyword, status, reload) {
        error = null
        runCatching { withContext(Dispatchers.IO) { ApiClient.packages(status = status, keyword = keyword) } }
            .onSuccess { array -> items = (0 until array.length()).map { packageItem(array.getJSONObject(it)) } }
            .onFailure { error = it.message ?: "加载包裹失败" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ form = true }) { Text("新增包裹") }; OutlinedButton({ verify = true }) { Text("取货码核销") } }
        Spacer(Modifier.height(10.dp)); OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("取货码、手机号、姓名、物品") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true)
        Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("全部", status == null) { status = null }; SegmentedButton("待取", status == 0) { status = 0 }; SegmentedButton("已取", status == 1) { status = 1 } }
        Spacer(Modifier.height(12.dp)); error?.let { Text(it, color = Danger, fontSize = 13.sp) }; if (items == null && error == null) Text("加载中...", color = Muted); if (items != null && items!!.isEmpty()) Text("暂无包裹", color = Muted)
        items.orEmpty().forEach { item -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpen(item) }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text("${item.customer} · ${item.phone}", color = Muted, fontSize = 12.sp) }; StatusPill(item.status) }; Spacer(Modifier.height(8.dp)); Text("取货码：${item.code}", fontWeight = FontWeight.Bold, color = Primary, fontSize = 17.sp); Text("${item.method} · ${item.time}", color = Muted, fontSize = 12.sp) } } }
    }
    if (form) AlertDialog(onDismissRequest = { if (!busy) form = false }, title = { Text("新增包裹") }, text = { Column { OutlinedTextField(itemName, { itemName = it }, Modifier.fillMaxWidth(), label = { Text("物品名称") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(receiverName, { receiverName = it }, Modifier.fillMaxWidth(), label = { Text("收件人") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(receiverPhone, { receiverPhone = it }, Modifier.fillMaxWidth(), label = { Text("手机号（可选）") }, singleLine = true); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) -> SegmentedButton(label, method == key) { method = key } }; }; if (method == 2) OutlinedTextField(tracking, { tracking = it }, Modifier.fillMaxWidth(), label = { Text("快递单号") }, singleLine = true) } }, confirmButton = { Button(enabled = itemName.isNotBlank() && receiverName.isNotBlank() && !busy && (method != 2 || tracking.isNotBlank()), onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createPackage(JSONObject().put("itemName", itemName.trim()).put("receiverName", receiverName.trim()).put("receiverPhone", receiverPhone.trim()).put("pickupMethod", method).put("expressTrackingNo", tracking.trim())) } }.onSuccess { form = false; itemName = ""; receiverName = ""; receiverPhone = ""; tracking = ""; reload++ }.onFailure { error = it.message ?: "新增包裹失败" }; busy = false } }) { Text(if (busy) "提交中..." else "创建") } }, dismissButton = { TextButton({ if (!busy) form = false }) { Text("取消") } })
    if (verify) AlertDialog(onDismissRequest = { if (!busy) verify = false }, title = { Text("取货码核销") }, text = { Column { OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("6 位取货码") }, singleLine = true); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(0 to "自提", 1 to "跑腿", 2 to "快递").forEach { (key, label) -> SegmentedButton(label, method == key) { method = key } } }; if (method == 2) OutlinedTextField(tracking, { tracking = it }, Modifier.fillMaxWidth(), label = { Text("快递单号") }, singleLine = true) } }, confirmButton = { Button(enabled = code.length == 6 && !busy && (method != 2 || tracking.isNotBlank()), onClick = { busy = true; scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.verifyPackage(code, method, tracking.trim()) } }.onSuccess { verify = false; code = ""; tracking = ""; reload++ }.onFailure { error = it.message ?: "核销失败" }; busy = false } }) { Text(if (busy) "核销中..." else "确认核销") } }, dismissButton = { TextButton({ if (!busy) verify = false }) { Text("取消") } })
}
