import Foundation
import Observation
import Network

@Observable
@MainActor
public class SessionManager {
    public static let shared = SessionManager()
    
    public var token: String? = nil
    public var currentUser: UserItem? = nil
    public var isAuthenticated: Bool = false
    
    private let tokenKey = "admin_auth_token"
    private let userKey = "admin_auth_user"
    
    private init() {
        restoreSession()
    }
    
    public func saveSession(token: String, user: UserItem) {
        if self.token != token {
            ApiClient.shared.clearResponseCache()
        }
        self.token = token
        self.currentUser = user
        self.isAuthenticated = true
        
        if let tokenData = token.data(using: .utf8) {
            let deleteQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrAccount as String: tokenKey
            ]
            SecItemDelete(deleteQuery as CFDictionary)
            let addQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrAccount as String: tokenKey,
                kSecValueData as String: tokenData,
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            ]
            SecItemAdd(addQuery as CFDictionary, nil)
        }
        if let encoded = try? JSONEncoder().encode(user) {
            UserDefaults.standard.set(encoded, forKey: userKey)
        }
    }
    
    public func refreshUserProfile() async {
        guard let token = self.token, isAuthenticated else { return }
        do {
            let user: UserItem = try await ApiClient.shared.me()
            self.saveSession(token: token, user: user)
        } catch {
            print("[SessionManager] refreshUserProfile failed:", error.localizedDescription)
        }
    }
    
    public func restoreSession() {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: tokenKey, kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var item: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let tokenData = item as? Data, let savedToken = String(data: tokenData, encoding: .utf8), !savedToken.isEmpty {
            self.token = savedToken
            if let savedUserData = UserDefaults.standard.data(forKey: userKey),
               let user = try? JSONDecoder().decode(UserItem.self, from: savedUserData) {
                self.currentUser = user
                self.isAuthenticated = true
                return
            }
            // A token without a valid user payload cannot authenticate the app.
            let deleteQuery: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: tokenKey]
            SecItemDelete(deleteQuery as CFDictionary)
            UserDefaults.standard.removeObject(forKey: userKey)
        }
        self.token = nil
        self.currentUser = nil
        self.isAuthenticated = false
    }
    
    public func clearSession() {
        self.token = nil
        self.currentUser = nil
        self.isAuthenticated = false
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: tokenKey]
        SecItemDelete(query as CFDictionary)
        UserDefaults.standard.removeObject(forKey: userKey)
        ApiClient.shared.clearResponseCache()
        
        // 彻底清空所有路由栈，防止下一次登录时旧页面“复活”
        Task { @MainActor in
            Router.shared.popToRoot()
        }
    }
}

// MARK: - 网络状态监听器 (全局感知无网络/弱网)
@Observable
@MainActor
public class NetworkMonitor {
    public static let shared = NetworkMonitor()
    
    public var isConnected: Bool = true
    public var isExpensive: Bool = false
    
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.tcm.admin.networkmonitor")
    
    private init() {
        monitor.pathUpdateHandler = { path in
            let isConnected = (path.status == .satisfied)
            let isExpensive = path.isExpensive
            Task { @MainActor in
                NetworkMonitor.shared.isConnected = isConnected
                NetworkMonitor.shared.isExpensive = isExpensive
            }
        }
        monitor.start(queue: queue)
    }
}
