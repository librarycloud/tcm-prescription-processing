import SwiftUI

@main
struct TCMAdminApp: App {
    @StateObject private var session = SessionManager.shared
    @StateObject private var theme = ThemeManager.shared
    
    var body: some Scene {
        WindowGroup {
            Group {
                if session.isAuthenticated {
                    MainShellView()
                } else {
                    LoginView()
                }
            }
            .id(theme.themeId)
            .preferredColorScheme(theme.currentColorScheme)
            .environment(\.sizeCategory, theme.currentSizeCategory)
            .tint(theme.primaryColor)
            .enableGlobalKeyboardDismiss()
        }
    }
}
