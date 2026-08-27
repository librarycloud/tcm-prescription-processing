package com.tcm.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val PageBackground = Color(0xFFF5F7FA)
private val Primary = Color(0xFF176B5B)
private val Ink = Color(0xFF1F2329)
private val Muted = Color(0xFF86909C)
private val Warning = Color(0xFFB76E00)
private val Danger = Color(0xFFC43D3D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TcmAdminApp() }
    }
}

private enum class Screen { Login, Dashboard, Processing, Packages, Herbs, Profile, Inventory, Stocktaking, Differences, Transfers }

private data class PackageItem(val name: String, val customer: String, val code: String, val status: String, val time: String, val id: Int = 0, val phone: String = "-", val store: String = "", val method: String = "", val info: String = "", val statusCode: Int = 0)

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
    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = Primary, background = PageBackground)) {
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
                Screen.Processing -> MainShell(screen, go) { ProcessingScreen() }
                Screen.Packages -> MainShell(screen, go) { PackagesScreen(onOpen = { selectedPackage = it }) }
                Screen.Herbs -> MainShell(screen, go) { HerbsScreen() }
                Screen.Profile -> MainShell(screen, go) { ProfileScreen(session?.user) { ApiClient.setToken(null); session = null; stats = null; go(Screen.Login) } }
                Screen.Inventory -> DetailShell("库存查询", go) { InventoryScreen() }
                Screen.Stocktaking -> DetailShell("商品盘点", go) { StocktakingScreen() }
                Screen.Differences -> DetailShell("库存差异", go) { DifferencesScreen() }
                Screen.Transfers -> DetailShell("门店调拨", go) { TransfersScreen() }
            }
            if (selectedPackage != null) {
                PackageDetailDialog(selectedPackage!!) { selectedPackage = null }
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
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 56.dp), verticalArrangement = Arrangement.Center) {
        Text("中药处方加工与取药管理系统", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("工作台", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(identifier, { identifier = it }, Modifier.fillMaxWidth(), label = { Text("手机号或用户名") }, leadingIcon = { Icon(Icons.Default.AccountCircle, null) }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("密码") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                Spacer(Modifier.height(20.dp))
                Button(onClick = { onLogin(identifier, password) }, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(6.dp), enabled = identifier.isNotBlank() && password.isNotBlank() && !loading) { Text(if (loading) "登录中..." else "登录") }
                if (error != null) { Spacer(Modifier.height(10.dp)); Text(error, color = Danger, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("请使用后端账号登录", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MainShell(current: Screen, go: (Screen) -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { AppTopBar("中药取药助手") }, bottomBar = { BottomNav(current, go) }, containerColor = PageBackground) { padding -> Box(Modifier.padding(padding)) { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailShell(title: String, go: (Screen) -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton({ go(Screen.Dashboard) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground)) }, containerColor = PageBackground) { padding -> Box(Modifier.padding(padding)) { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground))
}

@Composable
private fun BottomNav(current: Screen, go: (Screen) -> Unit) {
    val items = listOf(Screen.Dashboard to ("概览" to Icons.AutoMirrored.Filled.Assignment), Screen.Herbs to ("斗谱" to Icons.Default.Inventory), Screen.Processing to ("加工" to Icons.Default.Sync), Screen.Packages to ("包裹" to Icons.Default.AssignmentTurnedIn), Screen.Profile to ("我的" to Icons.Default.AccountCircle))
    NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = Color.White) { items.forEach { (screen, pair) -> NavigationBarItem(selected = current == screen, onClick = { go(screen) }, icon = { Icon(pair.second, pair.first) }, label = { Text(pair.first, fontSize = 11.sp) }) } }
}

@Composable
private fun DashboardScreen(go: (Screen) -> Unit, stats: JSONObject?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Store, null, tint = Primary); Spacer(Modifier.width(10.dp)); Text("当前门店", color = Muted); Spacer(Modifier.weight(1f)); Text("全部门店", fontWeight = FontWeight.SemiBold); Icon(Icons.Default.ChevronRight, null, tint = Muted) } }
        Spacer(Modifier.height(18.dp)); SectionTitle("加工概况")
        StatsGrid(listOf("今日待加工" to stat(stats, "waitingCount"), "逾期未开工" to stat(stats, "overdueCount"), "加工中" to stat(stats, "processingCount"), "今日完成" to stat(stats, "todayFinished"), "等待顾客" to stat(stats, "waitingNoticeCount"), "明日加工" to stat(stats, "tomorrowWaitingCount")))
        Spacer(Modifier.height(18.dp)); SectionTitle("包裹概况")
        StatsGrid(listOf("待取货" to stat(stats, "pendingCount"), "今日新增" to stat(stats, "todayAdded"), "今日已取" to stat(stats, "todayPicked"), "总包裹" to stat(stats, "totalCount")))
        Spacer(Modifier.height(18.dp)); SectionTitle("业务管理")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { QuickAction("库存查询", Icons.Default.Inventory) { go(Screen.Inventory) }; QuickAction("商品盘点", Icons.AutoMirrored.Filled.Assignment) { go(Screen.Stocktaking) }; QuickAction("库存差异", Icons.Default.Tune) { go(Screen.Differences) }; QuickAction("门店调拨", Icons.Default.LocalShipping) { go(Screen.Transfers) } }
    }
}

private fun stat(stats: JSONObject?, key: String): String = stats?.opt(key)?.toString() ?: "-"

@Composable private fun SectionTitle(text: String) { Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink) }
@Composable private fun StatsGrid(items: List<Pair<String, String>>) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { items.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (label, value) -> Card(Modifier.weight(1f), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(14.dp)) { Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Primary); Text(label, color = Muted, fontSize = 12.sp) } } }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } } } }
@Composable private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { OutlinedButton(onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text(label, modifier = Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null) } }
@Composable private fun SegmentedButton(label: String, selected: Boolean, onClick: () -> Unit) { if (selected) Button(onClick, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp)) { Text(label) } else OutlinedButton(onClick, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp)) { Text(label) } }

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

@Composable private fun StatusPill(text: String) { val color = when (text) { "加工中" -> Primary; "加工完成" -> Color(0xFF2B8A57); else -> Warning }; Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(5.dp)) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 12.sp) } }

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
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { ApiClient.stores() } }
            .onSuccess { values ->
                stores = (0 until values.length()).map { values.getJSONObject(it) }
                if (stores.size == 1) selectedStoreId = stores.first().opt("id")?.toString()
            }
            .onFailure { error = it.message ?: "加载门店失败" }
    }
    LaunchedEffect(selectedStoreId) {
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
                        Text(herb.optString("name"), fontWeight = FontWeight.SemiBold)
                        Text("${herb.optString("code").ifBlank { "-" }} · ${herb.optString("specification").ifBlank { "未填写规格" }} · 格内 ${herb.opt("slotNo") ?: "-"}", color = Muted, fontSize = 12.sp)
                        if (index < herbs.length() - 1) HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { Button({ selectedLocation = null }) { Text("关闭") } },
        )
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
                        scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.goodsCheck(check.optInt("id")) } }.onSuccess { selected = it }.onFailure { error = it.message ?: "加载盘点明细失败" }; detailLoading = false }
                    }, shape = RoundedCornerShape(6.dp)) { Text(if (detailLoading) "加载中..." else "查看盘点") }
                }
            }
        }
    }
    if (createVisible) AlertDialog(onDismissRequest = { createVisible = false }, title = { Text("新建盘点") }, text = { Column { if (stores.size > 1) { Text("所属门店", color = Muted, fontSize = 12.sp); StoreSelector(stores, selectedStoreId) { selectedStoreId = it }; Spacer(Modifier.height(10.dp)) }; OutlinedTextField(checkName, { checkName = it }, Modifier.fillMaxWidth(), label = { Text("盘点名称") }, singleLine = true) } }, confirmButton = { Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.createGoodsCheck(checkName.trim(), storeId = selectedStoreId.toIntOrNull()) } }.onSuccess { createVisible = false; checkName = ""; reload++ }.onFailure { error = it.message ?: "创建盘点单失败" } } }, enabled = checkName.isNotBlank() && (stores.size <= 1 || selectedStoreId.isNotBlank())) { Text("创建") } }, dismissButton = { TextButton({ createVisible = false }) { Text("取消") } })
    selected?.let { check ->
        val items = check.optJSONArray("items") ?: JSONArray()
        val completed = check.optInt("status") == 2
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(check.optString("checkName")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("${check.optJSONObject("store")?.optString("name") ?: "-"} · ${goodsCheckStatus(check.optInt("status"))}", color = Muted); Spacer(Modifier.height(10.dp)); if (items.length() == 0) Text("尚无盘点记录", color = Muted); (0 until items.length()).forEach { index -> val item = items.getJSONObject(index); Text(item.optJSONObject("product")?.optString("name") ?: "商品", fontWeight = FontWeight.SemiBold); Text("系统 ${item.opt("systemQty") ?: 0} · 实盘 ${item.opt("effectiveCount") ?: "未盘"}", color = Muted, fontSize = 12.sp); if (index < items.length() - 1) HorizontalDivider(Modifier.padding(vertical = 8.dp)) } } }, confirmButton = { if (completed) Button({ selected = null }) { Text("关闭") } else Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.finishGoodsCheck(check.optInt("id")) } }.onSuccess { selected = null; reload++ }.onFailure { error = it.message ?: "结束盘点失败" } } }) { Text("结束盘点") } }, dismissButton = { TextButton({ selected = null }) { Text("关闭") } })
    }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("当前差异", tab == "current") { tab = "current" }; SegmentedButton("差异流水", tab == "logs") { tab = "logs" } }
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
            logs.orEmpty().forEach { log -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Text(diffOperationLabel(log.optString("operationType")), fontWeight = FontWeight.SemiBold); Text("${log.optJSONObject("product")?.optString("productCode") ?: "-"} · ${log.optJSONObject("product")?.optString("name") ?: "商品"}", color = Muted, fontSize = 13.sp); val change = log.optDouble("changeQuantity"); Text("数量变化：${if (change > 0) "+" else ""}$change · ${log.optString("businessDate").take(10)}", color = if (change >= 0) Primary else Danger, fontSize = 12.sp) } } }
        }
    }
    writeOff?.let { product -> AlertDialog(onDismissRequest = { writeOff = null }, title = { Text("差异销账") }, text = { Column { Text("${product.optString("name")} · 当前差异 ${product.optDouble("diffQuantity")}", color = Muted); Spacer(Modifier.height(10.dp)); OutlinedTextField(quantity, { quantity = it }, Modifier.fillMaxWidth(), label = { Text("销账数量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) } }, confirmButton = { Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.writeOffDifference(JSONObject().put("productId", product.optInt("id")).put("quantity", quantity.toDouble()).put("businessDate", LocalDate.now().toString())) } }.onSuccess { writeOff = null; reload++ }.onFailure { error = it.message ?: "销账失败" } } }, enabled = quantity.toDoubleOrNull()?.let { it > 0 } == true) { Text("确认销账") } }, dismissButton = { TextButton({ writeOff = null }) { Text("取消") } }) }
}

private fun diffOperationLabel(value: String): String = when (value) { "PRE_RECEIPT" -> "先到货未入库"; "PRE_SHIPMENT" -> "先出货未销库"; "WRITE_OFF_RECEIPT" -> "入库销账"; "WRITE_OFF_SHIPMENT" -> "销库销账"; "REVERSAL" -> "冲销"; "IMPORT_OPENING" -> "导入期初差异"; "IMPORT_ADJUSTMENT" -> "导入调整"; else -> value }

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
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(transfer.optString("transferNo")) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text("${transfer.optJSONObject("fromStore")?.optString("name") ?: "-"}  ->  ${transfer.optJSONObject("toStore")?.optString("name") ?: "-"}", color = Muted); Spacer(Modifier.height(8.dp)); (0 until items.length()).forEach { index -> val item = items.getJSONObject(index); Text(item.optString("itemName"), fontWeight = FontWeight.SemiBold); Text("${item.opt("quantity") ?: 0} ${item.optString("unit")} · 已归还 ${item.opt("returnedQuantity") ?: 0}", color = Muted, fontSize = 12.sp); if (index < items.length() - 1) HorizontalDivider(Modifier.padding(vertical = 8.dp)) } } }, confirmButton = { if (canConfirm) Button({ scope.launch { runCatching { withContext(Dispatchers.IO) { ApiClient.confirmOutbound(transfer.optInt("id")) } }.onSuccess { detail = null; reload++ }.onFailure { error = it.message ?: "确认调出失败" } } }) { Text("确认调出") } else Button({ detail = null }) { Text("关闭") } }, dismissButton = { TextButton({ detail = null }) { Text("关闭") } })
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
