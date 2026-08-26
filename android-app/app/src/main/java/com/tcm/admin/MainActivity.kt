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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
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
import org.json.JSONObject

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

private data class PackageItem(val name: String, val customer: String, val code: String, val status: String, val time: String)
private data class InventoryItem(val name: String, val spec: String, val code: String, val stock: String, val location: String, val low: Boolean)
private data class TransferItem(val no: String, val from: String, val to: String, val count: String, val status: String, val date: String)

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
                Screen.Herbs -> MainShell(screen, go) { HerbsScreen(go) }
                Screen.Profile -> MainShell(screen, go) { ProfileScreen { ApiClient.setToken(null); session = null; stats = null; go(Screen.Login) } }
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
    var adminMode by remember { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 56.dp), verticalArrangement = Arrangement.Center) {
        Text("中药处方加工与取药管理系统", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("管理员工作台", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedButton("管理员", adminMode) { adminMode = true }
            SegmentedButton("门店员工", !adminMode) { adminMode = false }
        }
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
        Text("请使用后端管理员账号登录", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun MainShell(current: Screen, go: (Screen) -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { AppTopBar("中药取药助手", showMenu = true) }, bottomBar = { BottomNav(current, go) }, containerColor = PageBackground) { padding -> Box(Modifier.padding(padding)) { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailShell(title: String, go: (Screen) -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton({ go(Screen.Dashboard) }) { Icon(Icons.Default.ArrowBack, "返回") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground)) }, containerColor = PageBackground) { padding -> Box(Modifier.padding(padding)) { content() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String, showMenu: Boolean) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { if (showMenu) IconButton({}) { Icon(Icons.Default.Menu, "菜单") } }, actions = { IconButton({}) { Icon(Icons.Default.QrCodeScanner, "扫码") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground))
}

@Composable
private fun BottomNav(current: Screen, go: (Screen) -> Unit) {
    val items = listOf(Screen.Dashboard to ("概览" to Icons.Default.Assignment), Screen.Herbs to ("斗谱" to Icons.Default.Inventory), Screen.Processing to ("加工" to Icons.Default.Sync), Screen.Packages to ("包裹" to Icons.Default.AssignmentTurnedIn), Screen.Profile to ("我的" to Icons.Default.AccountCircle))
    NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = Color.White) { items.forEach { (screen, pair) -> NavigationBarItem(selected = current == screen, onClick = { go(screen) }, icon = { Icon(pair.second, pair.first) }, label = { Text(pair.first, fontSize = 11.sp) }) } }
}

@Composable
private fun DashboardScreen(go: (Screen) -> Unit, stats: JSONObject?) {
    var store by remember { mutableStateOf("全部门店") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Store, null, tint = Primary); Spacer(Modifier.width(10.dp)); Text("当前门店", color = Muted); Spacer(Modifier.weight(1f)); Text(store, fontWeight = FontWeight.SemiBold); Icon(Icons.Default.ChevronRight, null, tint = Muted) } }
        Spacer(Modifier.height(18.dp)); SectionTitle("加工概况")
        StatsGrid(listOf("今日待加工" to stat(stats, "waitingCount", "12"), "逾期未开工" to stat(stats, "overdueCount", "3"), "加工中" to stat(stats, "processingCount", "8"), "今日完成" to stat(stats, "todayFinished", "26"), "等待顾客" to stat(stats, "waitingNoticeCount", "5"), "明日加工" to stat(stats, "tomorrowWaitingCount", "18")))
        Spacer(Modifier.height(18.dp)); SectionTitle("包裹概况")
        StatsGrid(listOf("待取货" to stat(stats, "pendingCount", "34"), "今日新增" to stat(stats, "todayAdded", "19"), "今日已取" to stat(stats, "todayPicked", "41"), "总包裹" to stat(stats, "totalCount", "268")))
        Spacer(Modifier.height(18.dp)); SectionTitle("业务管理")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { QuickAction("库存查询", Icons.Default.Inventory) { go(Screen.Inventory) }; QuickAction("商品盘点", Icons.Default.Assignment) { go(Screen.Stocktaking) }; QuickAction("库存差异", Icons.Default.Tune) { go(Screen.Differences) }; QuickAction("门店调拨", Icons.Default.LocalShipping) { go(Screen.Transfers) } }
    }
}

private fun stat(stats: JSONObject?, key: String, fallback: String): String = stats?.optInt(key, fallback.toInt())?.toString() ?: fallback

@Composable private fun SectionTitle(text: String) { Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink) }
@Composable private fun StatsGrid(items: List<Pair<String, String>>) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { items.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (label, value) -> Card(Modifier.weight(1f), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(14.dp)) { Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Primary); Text(label, color = Muted, fontSize = 12.sp) } } }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } } } }
@Composable private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) { OutlinedButton(onClick, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label) } }
@Composable private fun SegmentedButton(label: String, selected: Boolean, onClick: () -> Unit) { if (selected) Button(onClick, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp)) { Text(label) } else OutlinedButton(onClick, shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp)) { Text(label) } }

@Composable
private fun ProcessingScreen() {
    var mode by remember { mutableStateOf("加工计划") }
    val tasks = listOf("王女士 · 代煎" to "待加工", "李先生 · 饮片" to "加工中", "赵女士 · 代煎" to "加工完成")
    Column(Modifier.fillMaxSize().padding(16.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("加工计划", mode == "加工计划") { mode = "加工计划" }; SegmentedButton("待领取", mode == "待领取") { mode = "待领取" } }; Spacer(Modifier.height(14.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("今日全部", "今日待加工", "逾期", "加工中", "明日").forEach { OutlinedButton({}, shape = RoundedCornerShape(6.dp)) { Text(it) } } }; Spacer(Modifier.height(14.dp)); tasks.forEach { (name, status) -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text("138****2468 · 张医生", color = Muted, fontSize = 12.sp) }; StatusPill(status) }; Spacer(Modifier.height(10.dp)); Text("第 1 批 · 7 剂 · 计划开工 2026-08-26", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({}, shape = RoundedCornerShape(6.dp)) { Text("详情") }; Button({}, shape = RoundedCornerShape(6.dp)) { Text(if (status == "待加工") "开始加工" else "查看工序") } } } } } }
}

@Composable private fun StatusPill(text: String) { val color = when (text) { "加工中" -> Primary; "加工完成" -> Color(0xFF2B8A57); else -> Warning }; Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(5.dp)) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, fontSize = 12.sp) } }

@Composable
private fun PackagesScreen(onOpen: (PackageItem) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf<List<PackageItem>?>(null) }
    LaunchedEffect(Unit) { runCatching { withContext(Dispatchers.IO) { ApiClient.packages() } }.onSuccess { array -> remote = (0 until array.length()).map { i -> val o = array.getJSONObject(i); PackageItem(o.optString("itemName", "处方包裹"), o.optString("receiverName", "客户"), o.optString("pickupCode", "------"), if (o.optInt("status") == 1) "已领取" else "待领取", o.optString("createdAt", "-").replace("T", " ").take(16)) } } }
    val list = remote ?: listOf(PackageItem("参苓白术散", "王女士", "620381", "待领取", "今天 09:42"), PackageItem("加味逍遥丸", "李先生", "194205", "待领取", "今天 08:16"), PackageItem("四物汤", "赵女士", "830174", "已领取", "昨天 16:20"))
    Column(Modifier.fillMaxSize().padding(16.dp)) { OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索姓名、手机号或取货码") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true); Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("全部", true) {}; OutlinedButton({}, shape = RoundedCornerShape(6.dp)) { Text("待领取") }; OutlinedButton({}, shape = RoundedCornerShape(6.dp)) { Text("已领取") } }; Spacer(Modifier.height(14.dp)); list.filter { keyword.isBlank() || it.name.contains(keyword) || it.customer.contains(keyword) || it.code.contains(keyword) }.forEach { item -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onOpen(item) }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text("${item.customer} · 138****2468", color = Muted, fontSize = 12.sp) }; StatusPill(item.status) }; Spacer(Modifier.height(9.dp)); Text("取货码：${item.code}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Primary); Text("录入：${item.time} · 门店：人民路店", color = Muted, fontSize = 12.sp) } } } }
}

@Composable
private fun PackageDetailDialog(item: PackageItem, onClose: () -> Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onClose, confirmButton = { Button(onClose) { Text("关闭") } }, title = { Text("包裹详情") }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(item.name, fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(8.dp)); Text(item.code, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary); Spacer(Modifier.height(10.dp)); FakeQr(item.code); Spacer(Modifier.height(8.dp)); Text("客户：${item.customer}\n状态：${item.status}\n门店：人民路店", color = Muted) } })
}

@Composable
private fun FakeQr(value: String) { Canvas(Modifier.size(150.dp).background(Color.White)) { val cells = 21; val cell = size.minDimension / cells; for (x in 0 until cells) for (y in 0 until cells) if (((x * 31 + y * 17 + value.length * 13) % 7) < 3 || (x < 7 && y < 7) || (x > 13 && y < 7) || (x < 7 && y > 13)) drawRect(if ((x + y) % 3 == 0) Color.Black else Color.DarkGray, androidx.compose.ui.geometry.Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell, cell)) } }

@Composable private fun HerbsScreen(go: (Screen) -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { SectionTitle("斗谱与库位"); Spacer(Modifier.height(12.dp)); listOf("药材库位", "库位布局", "药材绑定").forEach { label -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable {}, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Inventory, null, tint = Primary); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Icon(Icons.Default.ChevronRight, null, tint = Muted) } } }; Spacer(Modifier.height(16.dp)); Text("库存运营", fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); QuickAction("库存查询", Icons.Default.Inventory) { go(Screen.Inventory) } } }

@Composable private fun ProfileScreen(onLogout: () -> Unit) { Column(Modifier.fillMaxSize().padding(16.dp)) { Card(colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(52.dp), shape = RoundedCornerShape(26.dp), color = Primary.copy(alpha = .14f)) { Box(contentAlignment = Alignment.Center) { Text("管", color = Primary, fontWeight = FontWeight.Bold, fontSize = 20.sp) } }; Spacer(Modifier.width(12.dp)); Column { Text("管理员", fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("全局管理员 · 全部门店", color = Muted, fontSize = 13.sp) } } }; Spacer(Modifier.height(14.dp)); listOf("手机号" to "138****2468", "用户名" to "admin", "微信绑定" to "已绑定").forEach { (label, value) -> InfoRow(label, value) }; Spacer(Modifier.height(24.dp)); OutlinedButton(onLogout, Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp)) { Text("退出登录") } } }
@Composable private fun InfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = Muted, Modifier.width(86.dp)); Text(value, color = Ink); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ChevronRight, null, tint = Muted) }; HorizontalDivider(color = Color(0xFFE5E6EB)) }

@Composable private fun SearchField(placeholder: String) { OutlinedTextField("", {}, Modifier.fillMaxWidth(), placeholder = { Text(placeholder) }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
@Composable private fun InventoryScreen() { var query by remember { mutableStateOf("") }; val items = listOf(InventoryItem("黄芪", "片 · 500g", "H001", "128", "A-01-03", false), InventoryItem("党参", "段 · 250g", "D014", "18", "A-02-01", true), InventoryItem("当归", "片 · 500g", "D021", "76", "A-01-06", false)); Column(Modifier.fillMaxSize().padding(16.dp)) { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索商品名称、编号或条码") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true); Spacer(Modifier.height(14.dp)); items.filter { query.isBlank() || it.name.contains(query) || it.code.contains(query) }.forEach { item -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text("${item.code} · ${item.spec}", color = Muted, fontSize = 12.sp) }; if (item.low) StatusPill("库存预警") }; Spacer(Modifier.height(9.dp)); Row { Text("库存 ${item.stock}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (item.low) Danger else Primary); Spacer(Modifier.weight(1f)); Text("库位 ${item.location}", color = Muted) } } } } } }

@Composable private fun StocktakingScreen() { Column(Modifier.fillMaxSize().padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("盘点单"); Spacer(Modifier.weight(1f)); Button({}, shape = RoundedCornerShape(6.dp)) { Text("新建盘点") } }; Spacer(Modifier.height(14.dp)); listOf("PD-20260826-001" to "盘点中", "PD-20260825-003" to "待复核", "PD-20260820-008" to "已完成").forEach { (no, status) -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(no, fontWeight = FontWeight.SemiBold, Modifier.weight(1f)); StatusPill(status) }; Spacer(Modifier.height(8.dp)); Text("人民路店 · 128 个商品 · 已盘 96 个", color = Muted, fontSize = 13.sp); Text("创建人：管理员 · 2026-08-26", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(10.dp)); OutlinedButton({}, shape = RoundedCornerShape(6.dp)) { Text(if (status == "盘点中") "继续盘点" else "查看详情") } } } } } }

@Composable private fun DifferencesScreen() { var tab by remember { mutableStateOf("当前差异") }; Column(Modifier.fillMaxSize().padding(16.dp)) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SegmentedButton("当前差异", tab == "当前差异") { tab = "当前差异" }; SegmentedButton("差异流水", tab == "差异流水") { tab = "差异流水" } }; Spacer(Modifier.height(14.dp)); if (tab == "当前差异") { StatsGrid(listOf("有差异货品" to "7", "实货多" to "3", "实货少" to "4")); Spacer(Modifier.height(14.dp)); listOf("党参 · D014" to "+6 盒", "川贝母 · C008" to "-2 盒").forEach { (name, diff) -> DifferenceCard(name, diff) } } else listOf("先到货未入库" to "+6", "销库销账" to "-2", "导入调整" to "+12").forEach { (op, value) -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Text(op, fontWeight = FontWeight.SemiBold); Text("D014 · 党参", color = Muted, fontSize = 13.sp); Text("数量变化：$value · 2026-08-26", color = Muted, fontSize = 12.sp) } } } } }
@Composable private fun DifferenceCard(name: String, diff: String) { Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(if (diff.startsWith("+")) "实货多" else "实货少") }; Spacer(Modifier.height(8.dp)); Text("当前差异：$diff", color = if (diff.startsWith("+")) Primary else Danger, fontWeight = FontWeight.Bold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) { Button({}, shape = RoundedCornerShape(6.dp)) { Text("销账") }; OutlinedButton({}, shape = RoundedCornerShape(6.dp)) { Text("流水") } } } } }

@Composable private fun TransfersScreen() { Column(Modifier.fillMaxSize().padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { SectionTitle("门店调拨"); Spacer(Modifier.weight(1f)); Button({}, shape = RoundedCornerShape(6.dp)) { Text("新建调拨") } }; Spacer(Modifier.height(14.dp)); listOf(TransferItem("DB-20260826-002", "人民路店", "城南店", "12 个商品", "待出库", "今天 10:12"), TransferItem("DB-20260825-007", "城南店", "人民路店", "4 个商品", "已出库", "昨天 15:40"), TransferItem("DB-20260818-003", "人民路店", "城北店", "8 个商品", "部分归还", "08-18")).forEach { item -> Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(8.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(item.no, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); StatusPill(item.status) }; Spacer(Modifier.height(8.dp)); Text("${item.from}  →  ${item.to}", color = Ink); Text("${item.count} · ${item.date}", color = Muted, fontSize = 12.sp); Spacer(Modifier.height(10.dp)); OutlinedButton({}, shape = RoundedCornerShape(6.dp)) { Text("查看详情") } } } } } }
