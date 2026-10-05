import os
import re

# 1. Fix DetailShell in MainActivity.kt
main_kt_path = "app/src/main/java/com/tcm/admin/MainActivity.kt"
with open(main_kt_path, "r") as f:
    main_kt = f.read()

main_kt = main_kt.replace(
    "containerColor = PageBackground,\n    ) { padding ->\n        Box(Modifier.fillMaxSize().padding(padding).dismissKeyboardOnTap()) {",
    "containerColor = PageBackground,\n        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),\n    ) { padding ->\n        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).dismissKeyboardOnTap()) {"
)
# Also fix ScrollToTopButton in DetailShell
main_kt = main_kt.replace(
    "modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),",
    "modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars).padding(16.dp),"
)
with open(main_kt_path, "w") as f:
    f.write(main_kt)


# 2. Add Spacer to the end of static scrollable columns
# Screens: ProfileScreen.kt, NotificationSettingsScreen.kt, SecurityPrivacyScreen.kt, AboutScreen.kt
screens = [
    "app/src/main/java/com/tcm/admin/ui/screens/ProfileScreen.kt",
    "app/src/main/java/com/tcm/admin/ui/screens/NotificationSettingsScreen.kt",
    "app/src/main/java/com/tcm/admin/ui/screens/SecurityPrivacyScreen.kt",
    "app/src/main/java/com/tcm/admin/ui/screens/AboutScreen.kt"
]
for screen in screens:
    if os.path.exists(screen):
        with open(screen, "r") as f:
            content = f.read()
        
        # Make sure import exists
        if "import androidx.compose.foundation.layout.windowInsetsBottomHeight" not in content:
            content = content.replace("import androidx.compose.foundation.layout.Spacer", "import androidx.compose.foundation.layout.Spacer\nimport androidx.compose.foundation.layout.windowInsetsBottomHeight\nimport androidx.compose.foundation.layout.WindowInsets\nimport androidx.compose.foundation.layout.navigationBars")

        # In ProfileScreen, ThemeSettingsScreen has a Spacer(Modifier.height(32.dp)) at the end. We replace it.
        content = content.replace("Spacer(Modifier.height(32.dp))\n    }\n}", "Spacer(Modifier.height(32.dp))\n        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))\n    }\n}")
        # In NotificationSettingsScreen, it ends with a Card. We need to add Spacer after the Card but inside the Column.
        # It's easier to just do a regex for the end of the verticalScroll Column.
        # Actually, let's just append it manually for the ones we know.
        with open(screen, "w") as f:
            f.write(content)
