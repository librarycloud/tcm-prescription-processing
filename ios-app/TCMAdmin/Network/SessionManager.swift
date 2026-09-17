import Foundation
import Observation

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
        self.token = token
        self.currentUser = user
        self.isAuthenticated = true
        
        if let tokenData = token.data(using: .utf8) {
            let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: tokenKey, kSecValueData as String: tokenData]
            SecItemDelete(query as CFDictionary)
            SecItemAdd(query as CFDictionary, nil)
        }
        if let encoded = try? JSONEncoder().encode(user) {
            UserDefaults.standard.set(encoded, forKey: userKey)
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
    }
}
