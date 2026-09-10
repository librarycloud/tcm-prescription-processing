content = File.read("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt")

# Replace backStack and currentScreen definitions
content.gsub!(/val backStack = remember \{.*?mutableStateListOf<Route>\(.*?\)\s*\}/m, "val navController = rememberNavController()")
content.gsub!(/val currentScreen = backStack\.lastOrNull\(\) \?: Route\.Login/, "val navBackStackEntry by navController.currentBackStackEntryAsState()\n    val currentDestination = navBackStackEntry?.destination")

File.write("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt", content)
