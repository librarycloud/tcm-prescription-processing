import SwiftUI

@main
struct TCMAdminApp: App {
    @StateObject private var session = SessionManager.shared
    @StateObject private var theme = ThemeManager.shared
    @State private var importedAlertMessage: String? = nil
    @State private var showImportAlert = false
    @State private var hasAgreedPrivacy: Bool = UserDefaults.standard.bool(forKey: "agreed_privacy")

    var body: some Scene {
        WindowGroup {
            ZStack {
                Group {
                    if session.isAuthenticated {
                        MainShellView()
                    } else {
                        LoginView()
                    }
                }
                
                if !hasAgreedPrivacy {
                    PrivacyPolicyView(
                        onAgree: {
                            UserDefaults.standard.set(true, forKey: "agreed_privacy")
                            hasAgreedPrivacy = true
                            // TODO: Initialize third-party SDKs here (e.g., Push SDK, Analytics SDK)
                        },
                        onDisagree: {
                            exit(0)
                        }
                    )
                    .zIndex(1)
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
