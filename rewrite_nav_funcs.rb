content = File.read("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt")

# 1. onUnauthorized
content.gsub!(/backStack\.clear\(\)\s*backStack\.add\(Route\.Login\)/, "navController.navigate(Route.Login) { popUpTo(0) { inclusive = true } }")

# 2. navigateTo
nav_to_replacement = <<~KOTLIN
    fun navigateTo(target: Route) {
        navController.navigate(target) {
            if (target is Route.Inventory && currentDestination?.hasRoute<Route.Inventory>() == true) {
                popUpTo<Route.Inventory> { inclusive = true }
            }
        }
    }
KOTLIN
content.gsub!(/fun navigateTo\(target: Route\) \{.*?    \}/m, nav_to_replacement.strip)

# 3. navigateBack
nav_back_replacement = <<~KOTLIN
    fun navigateBack(): Boolean {
        return navController.popBackStack()
    }
KOTLIN
content.gsub!(/fun navigateBack\(\): Boolean \{.*?    \}/m, nav_back_replacement.strip)

# 4. switchTab
switch_tab_replacement = <<~KOTLIN
    fun switchTab(target: Route) {
        navController.navigate(target) {
            popUpTo(0) { inclusive = true }
        }
    }
KOTLIN
content.gsub!(/fun switchTab\(target: Route\) \{.*?    \}/m, switch_tab_replacement.strip)

# 5. BackHandler
back_handler_replacement = <<~KOTLIN
    BackHandler(enabled = currentDestination?.hasRoute<Route.Login>() == false) {
        if (!navigateBack()) {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2000L) {
                activity?.finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(appContext, "再按一次退出应用", Toast.LENGTH_SHORT).show()
            }
        }
    }
KOTLIN
content.gsub!(/BackHandler\(enabled = currentScreen !is Route\.Login\) \{.*?    \}/m, back_handler_replacement.strip)

File.write("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt", content)
