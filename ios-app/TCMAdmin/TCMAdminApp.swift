import SwiftUI

@main
struct TCMAdminApp: App {
    @StateObject private var session = SessionManager.shared
    @StateObject private var theme = ThemeManager.shared
    @State private var importedAlertMessage: String? = nil
    @State private var showImportAlert = false
    
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
            .onOpenURL { url in
                let res = ApiClient.shared.importServerConfig(from: url)
                importedAlertMessage = res.success ? "已成功自动导入并切换服务器地址：\n\n\(res.newURL ?? "")" : res.message
                showImportAlert = true
            }
            .alert(isPresented: $showImportAlert) {
                Alert(
                    title: Text("服务器配置"),
                    message: Text(importedAlertMessage ?? ""),
                    dismissButton: .default(Text("好的"))
                )
            }
        }
    }
}
