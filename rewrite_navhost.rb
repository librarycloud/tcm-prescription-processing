content = File.read("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt")

navhost_block = %Q{            NavHost(
                navController = navController,
                startDestination = if (session != null) Route.Inventory() else Route.Login,
                modifier = Modifier.fillMaxSize()
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
                                    popUpTo(0) { inclusive = true }
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
                    val initial = RouteParams.peek(route.argId) as? JSONObject ?: JSONObject()
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
                                navController.navigate(Route.Login) { popUpTo(0) { inclusive = true } }
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
                    val initial = RouteParams.peek(route.argId) as? JSONObject ?: JSONObject()
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
                    val initial = RouteParams.peek(route.argId) as? JSONObject ?: JSONObject()
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
                    val plan = RouteParams.peek(route.argId) as? JSONObject
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
                    val item = RouteParams.peek(route.argId) as? PackageItem
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
                    val initial = RouteParams.peek(route.argId) as? PackageItem
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
                    val location = RouteParams.peek(route.argId) as? JSONObject ?: JSONObject()
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
            }}

new_content = content.gsub(/when\s*\(\s*currentScreen\s*\)\s*\{.*?\n\s*\}/m, navhost_block)

File.write("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt", new_content)
