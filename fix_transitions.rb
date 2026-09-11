content = File.read("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt")

unless content.include?("import androidx.compose.animation.EnterTransition")
  content.sub!("import androidx.compose.animation.AnimatedVisibility", "import androidx.compose.animation.AnimatedVisibility\nimport androidx.compose.animation.EnterTransition\nimport androidx.compose.animation.ExitTransition")
end

navhost_replacement = <<~KOTLIN
                        NavHost(
                navController = navController,
                startDestination = if (session != null) Route.Inventory() else Route.Login,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
KOTLIN

content.sub!(/NavHost\(\s*navController = navController,\s*startDestination = if \(session != null\) Route\.Inventory\(\) else Route\.Login,\s*modifier = Modifier\.fillMaxSize\(\)\s*\)\s*\{/m, navhost_replacement.strip + " {")

File.write("/Users/yunfei/Desktop/tcm/android-app/app/src/main/java/com/tcm/admin/MainActivity.kt", content)
