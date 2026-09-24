import SwiftUI

@main
struct TCMAdminApp: App {
    @State private var session = SessionManager.shared
    @State private var theme = ThemeManager.shared
    @State private var importedAlertMessage: String? = nil
    @State private var showImportAlert = false
    @State private var hasAgreedPrivacy: Bool = UserDefaults.standard.bool(forKey: "agreed_privacy")
    @AppStorage("keep_screen_awake") private var keepScreenAwake: Bool = false

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
                        }
                    )
                    .zIndex(1)
                }
            }
            .id(theme.themeId)
            .preferredColorScheme(theme.currentColorScheme)
            .environment(\.sizeCategory, theme.currentSizeCategory)
            .environment(session)
            .tint(theme.primaryColor)
            .enableGlobalKeyboardDismiss()
            .onOpenURL { url in
                let res = ApiClient.shared.importServerConfig(from: url)
                importedAlertMessage = res.success ? "已成功自动导入并切换服务器地址：\n\n\(res.newURL ?? "")" : res.message
                showImportAlert = true
            }
            .alert("服务器配置", isPresented: $showImportAlert) {
                Button("好的", role: .cancel) { }
            } message: {
                Text(importedAlertMessage ?? "")
            }
            .onAppear {
                // 强制按用户设置初始化，防止系统或第三方 SDK 遗留不正确的值
                UIApplication.shared.isIdleTimerDisabled = keepScreenAwake
            }
            .onChange(of: keepScreenAwake) { _, newValue in
                UIApplication.shared.isIdleTimerDisabled = newValue
            }
            .onChange(of: session.isAuthenticated) { _, _ in
                // 切换登录状态时（登录/登出）重置为用户设定值
                UIApplication.shared.isIdleTimerDisabled = keepScreenAwake
            }
        }
    }
}

