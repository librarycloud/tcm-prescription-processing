import SwiftUI
import UserNotifications

enum NotificationPreference {
    static var receives: Bool { UserDefaults.standard.object(forKey: "receive_notifications") as? Bool ?? true }
    static var prescription: Bool { UserDefaults.standard.object(forKey: "prescription_notify") as? Bool ?? true }
    static var transfer: Bool { UserDefaults.standard.object(forKey: "transfer_notify") as? Bool ?? true }

    static func eventCode(from userInfo: [AnyHashable: Any]) -> String {
        if let code = userInfo["eventCode"] as? String { return code.uppercased() }
        if let data = userInfo["data"] as? [String: Any], let code = data["eventCode"] as? String { return code.uppercased() }
        if let data = userInfo["data"] as? NSDictionary, let code = data["eventCode"] as? String { return code.uppercased() }
        return ""
    }

    static func accepts(userInfo: [AnyHashable: Any]) -> Bool {
        guard receives else { return false }
        let code = eventCode(from: userInfo)
        if code.hasPrefix("TRANSFER_") || code.hasPrefix("STOCKTAKING") || code.hasPrefix("GOODS_CHECK") { return transfer }
        if code.hasPrefix("PACKAGE_") || code == "PROCESSING_COMPLETED" || code.hasPrefix("E6") || code.hasPrefix("PRESCRIPTION") { return prescription }
        return true
    }
}

// MARK: - AppDelegate for APNs token callbacks
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    // MARK: 前台收到推送 → 照常弹横幅
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        guard NotificationPreference.accepts(userInfo: notification.request.content.userInfo) else {
            completionHandler([])
            return
        }
        completionHandler([.banner, .sound, .badge])
    }

    // MARK: 用户点击通知 → 深链跳转
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        guard NotificationPreference.accepts(userInfo: userInfo) else {
            completionHandler()
            return
        }
        let action = userInfo["action"] as? String

        var destination: AppRoute? = nil

        if let transferIdStr = userInfo["transferId"] as? String,
           let transferId = Int(transferIdStr) {
            // 所有调拨相关通知 → 调拨详情
            destination = .transferDetail(id: transferId)
        } else if let planIdStr = userInfo["planId"] as? String,
                  let planId = Int(planIdStr),
                  action == "processing_completed" {
            // 加工完成通知 → 加工工作流详情
            let planCode = userInfo["planCode"] as? String ?? ""
            destination = .workflowOperation(planId: planId, planCode: planCode)
        }

        if let route = destination {
            let isTransferRoute: Bool
            if case .transferDetail = route { isTransferRoute = true } else { isTransferRoute = false }

            ApiClient.shared.clearResponseCache()
            Router.shared.popToRoot()
            if isTransferRoute {
                Router.shared.navigate(to: .transfers)
            }
            Router.shared.navigate(to: route)
        }

        UNUserNotificationCenter.current().setBadgeCount(0) { _ in }
        completionHandler()
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        PushTokenStore.shared.latestToken = deviceToken
        NotificationCenter.default.post(name: NSNotification.Name("APNsTokenUpdated"), object: nil)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[Push] APNs registration failed:", error.localizedDescription)
    }
}

// MARK: - Token store (survives across scenes)
final class PushTokenStore {
    static let shared = PushTokenStore()
    var latestToken: Data? {
        didSet {
            UserDefaults.standard.set(latestToken, forKey: "apns_device_token")
        }
    }

    private init() {
        latestToken = UserDefaults.standard.data(forKey: "apns_device_token")
    }

    private var uploadInFlight = false
    private var lastUploadedToken: Data?
    private var lastUploadAt: Date?

    func uploadIfNeeded(force: Bool = false) {
        guard NotificationPreference.receives else { return }
        guard let token = latestToken else { return }
        if !force {
            if uploadInFlight { return }
            if lastUploadedToken == token,
               let lastUploadAt,
               Date().timeIntervalSince(lastUploadAt) < 5 { return }
        }
        if uploadInFlight { return }
        uploadInFlight = true
        Task {
            let uploaded = await ApiClient.shared.registerDeviceToken(token)
            uploadInFlight = false
            if uploaded {
                lastUploadedToken = token
                lastUploadAt = Date()
            }
        }
    }

    func unregisterCurrentToken() async {
        guard let token = latestToken else { return }
        await ApiClient.shared.unregisterDeviceToken(token)
    }
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
    @Environment(\.scenePhase) private var scenePhase

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
                            await PushTokenStore.shared.unregisterCurrentToken()
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
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active && session.isAuthenticated {
                    Task {
                        await session.refreshUserProfile()
                    }
                }
            }
            .onChange(of: session.isAuthenticated) { _, isAuth in
                UIApplication.shared.isIdleTimerDisabled = keepScreenAwake
                if isAuth {
                    PushTokenStore.shared.uploadIfNeeded()
                    requestPushPermission()
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("APNsTokenUpdated"))) { _ in
                if session.isAuthenticated {
                    PushTokenStore.shared.uploadIfNeeded()
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
        guard NotificationPreference.receives else { return }
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
        // Always register for remote notifications to get the APNs token, 
        // regardless of whether the user granted alert permissions (needed for silent push / logic).
        DispatchQueue.main.async {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    private func preloadLocalEngines() {
        SharedCameraManager.shared.preload()
        Task { @MainActor in
            SharedOCRManager.shared.preload()
        }
    }
}
