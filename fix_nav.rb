content = File.read("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt")

# Replace navigateTo
nav_to_replacement = <<~KOTLIN
    fun navigateTo(target: Route) {
        navController.navigate(target) {
            if (target is Route.Inventory && currentDestination?.hasRoute<Route.Inventory>() == true) {
                popUpTo<Route.Inventory> { inclusive = true }
            }
        }
    }
KOTLIN
content.gsub!(/fun navigateTo\(target: Route\)\s*\{\s*navController\.navigate\(target\)\s*\{[^{}]*\{[^{}]*\}[^{}]*\}\s*\}[^{}]*else\s*\{[^{}]*\}[^{}]*\}/m, nav_to_replacement.strip)

# Replace navigateBack
nav_back_replacement = <<~KOTLIN
    fun navigateBack(): Boolean {
        return navController.popBackStack()
    }
KOTLIN
content.gsub!(/fun navigateBack\(\):\s*Boolean\s*\{\s*return\s*navController\.popBackStack\(\)\s*\}[^{}]*else\s*\{[^{}]*\}[^{}]*\}/m, nav_back_replacement.strip)

File.write("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt", content)
