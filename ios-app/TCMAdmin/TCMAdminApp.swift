import SwiftUI
import UserNotifications
import JPUSHService

// MARK: - AppDelegate for APNs token callbacks + JPush init
class AppDelegate: NSObject, UIApplicationDelegate, JPUSHRegisterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // ─── JPush 初始化 ───────────────────────────────────────────
        // AppKey 需在极光控制台创建 iOS 应用后获取
        let jpushAppKey = Bundle.main.object(forInfoDictionaryKey: "JPUSH_APP_KEY") as? String ?? ""
        let entity = JPUSHRegisterEntity()
        entity.types = Int(JPAuthorizationOptions.alert.rawValue |
                          JPAuthorizationOptions.badge.rawValue |
                          JPAuthorizationOptions.sound.rawValue)
        JPUSHService.register(forRemoteNotificationConfig: entity, delegate: self)
        JPUSHService.setup(withOption: launchOptions,
                           appKey: jpushAppKey,
                           channel: "App Store",
                           apsForProduction: true)
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // Forward APNs token to JPush (JPush needs it to deliver iOS notifications)
        JPUSHService.registerDeviceToken(deviceToken)
        // Also store and upload natively in case JPush is not configured
        PushTokenStore.shared.latestToken = deviceToken
        if SessionManager.shared.isAuthenticated {
            Task { await ApiClient.shared.registerDeviceToken(deviceToken) }
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[Push] APNs registration failed:", error.localizedDescription)
    }

    // MARK: JPUSHRegisterDelegate — called when JPush obtains a Registration ID
    func jpushNotificationAuthorization(_ type: JPAuthorizationStatus, withInfo info: [AnyHashable: Any]!) { }

    func jpushNotificationCenter(_ center: UNUserNotificationCenter!,
                                  willPresent notification: UNNotification!,
                                  withCompletionHandler completionHandler: ((Int) -> Void)!) {
        completionHandler(Int(UNNotificationPresentationOptions.alert.rawValue |
                              UNNotificationPresentationOptions.sound.rawValue))
    }

    func jpushNotificationCenter(_ center: UNUserNotificationCenter!,
                                  didReceive response: UNNotificationResponse!,
                                  withCompletionHandler completionHandler: (() -> Void)!) {
        completionHandler()
    }
}

// MARK: - Token store (survives across scenes)
final class PushTokenStore {
    static let shared = PushTokenStore()
    var latestToken: Data?
    var jpushRegistrationId: String?
}

@main
struct TCMAdminApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @State private var session = SessionManager.shared
    @State private var theme = ThemeManager.shared
    @State private var importedAlertMessage: String? = nil
    @State private var showImportAlert = false
    @State private var hasAgreedPrivacy: Bool = UserDefaults.standard.bool(forKey: "agreed_privacy")
    @AppStorage("keep_screen_awake") private var keepScreenAwake: Bool = false
    @State private var pendingConfigURL: URL? = nil
    @State private var showConfirmConfigAlert = false

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
                            preloadLocalEngines()
                            requestPushPermission()
                        }
                    )
                    .zIndex(1)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.pageBackground.ignoresSafeArea(.all))
            .id(theme.themeId)
            .preferredColorScheme(theme.currentColorScheme)
            .environment(\.sizeCategory, theme.currentSizeCategory)
            .environment(session)
            .tint(theme.primaryColor)
            .enableGlobalKeyboardDismiss()
            .onOpenURL { url in
                if ApiClient.shared.parseServerConfig(from: url) != nil {
                    pendingConfigURL = url
                    showConfirmConfigAlert = true
                } else {
                    importedAlertMessage = "无效的配置链接"
                    showImportAlert = true
                }
            }
            .alert("服务器配置", isPresented: $showImportAlert) {
                Button("好的", role: .cancel) { }
            } message: {
                Text(importedAlertMessage ?? "")
            }
            .alert("确认切换服务器", isPresented: $showConfirmConfigAlert) {
                Button("取消", role: .cancel) { }
                Button("确认", role: .destructive) {
                    guard let pendingUrl = pendingConfigURL else { return }
                    
                    Task { @MainActor in
                        let wasLoggedIn = SessionManager.shared.isAuthenticated
                        var logoutWarning: String?
                        if wasLoggedIn {
                            struct EmptyResponse: Decodable {}
                            do {
                                _ = try await ApiClient.shared.request(path: "/auth/logout", method: "POST") as EmptyResponse
                            } catch is CancellationError {
                                return
                            } catch {
                                logoutWarning = "旧服务器退出失败，本地登录状态已清除：\(error.localizedDescription)"
                            }
                            SessionManager.shared.clearSession()
                        }
                        
                        let res = ApiClient.shared.importServerConfig(from: pendingUrl)
                        if res.success {
                            importedAlertMessage = [logoutWarning, "已成功自动导入并切换服务器地址：\n\n\(res.newURL ?? "")"].compactMap { $0 }.joined(separator: "\n\n")
                        } else {
                            importedAlertMessage = [logoutWarning, res.message].compactMap { $0 }.joined(separator: "\n\n")
                        }
                        showImportAlert = true
                    }
                }
            } message: {
                if let u = pendingConfigURL, let target = ApiClient.shared.parseServerConfig(from: u) {
                    if SessionManager.shared.isAuthenticated {
                        Text("外部链接请求切换服务器。确认后会清除当前登录状态并要求重新登录。\n\n\(target)")
                    } else {
                        Text("外部链接请求导入服务器地址，请确认地址可信。\n\n\(target)")
                    }
                } else {
                    Text("")
                }
            }
            .onAppear {
                if hasAgreedPrivacy {
                    preloadLocalEngines()
                    requestPushPermission()
                }
                UIApplication.shared.isIdleTimerDisabled = keepScreenAwake
            }
            .onChange(of: keepScreenAwake) { _, newValue in
                UIApplication.shared.isIdleTimerDisabled = newValue
            }
            .onChange(of: session.isAuthenticated) { _, isAuth in
                UIApplication.shared.isIdleTimerDisabled = keepScreenAwake
                if isAuth {
                    // Upload APNs token (native channel)
                    if let token = PushTokenStore.shared.latestToken {
                        Task { await ApiClient.shared.registerDeviceToken(token) }
                    }
                    // Upload JPush Registration ID (China channel)
                    if let regId = JPUSHService.registrationID(), !regId.isEmpty {
                        Task { await ApiClient.shared.registerJPushToken(regId) }
                    }
                    requestPushPermission()
                }
            }
            // 每次 App 回到前台时也强制重置，防止 AVCaptureSession 等系统行为修改过该值
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
                UIApplication.shared.isIdleTimerDisabled = keepScreenAwake
            }
        }
    }

    /// Asks for notification permission and registers for remote notifications.
    private func requestPushPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }

    private func preloadLocalEngines() {
        SharedCameraManager.shared.preload()
        Task { @MainActor in
            SharedOCRManager.shared.preload()
        }
    }
}
