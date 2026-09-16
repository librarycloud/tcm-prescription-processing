import Foundation
import UIKit
import Combine

public struct AppUpdateInfo: Decodable {
    public let hasUpdate: Bool
    public let updateType: String?
    public let versionCode: Int?
    public let versionName: String?
    public let forceUpdate: Bool?
    public let releaseNotes: [String]?
    public let downloadUrl: String?
}

@MainActor
public class UpdateManager: ObservableObject {
    public static let shared = UpdateManager()
    
    // 配置您的 App Release Hub 服务端地址
    // 配置您的 App Release Hub 服务端地址
    public var hubBaseURL: String {
        get {
            let val = UserDefaults.standard.string(forKey: "app_release_hub_url")?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            return val
        }
        set {
            let sanitized = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            UserDefaults.standard.set(sanitized, forKey: "app_release_hub_url")
        }
    }
    
    public let appId = "tcm-admin-ios"
    public let platform = "ios"
    
    @Published public var isChecking = false
    @Published public var updateInfo: AppUpdateInfo?
    
    private var lastCheckTime: Date?
    private let checkInterval: TimeInterval = 3600 // 默认间隔 1 小时
    
    private init() {}
    
    public func checkUpdate(force: Bool = false) async throws -> AppUpdateInfo {
        let base = hubBaseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        // 如果未配置或仍然是占位域名，避免发起无效的网络请求
        if base.isEmpty || base.contains("example.com") {
            if force {
                throw NSError(
                    domain: "UpdateManager",
                    code: -1,
                    userInfo: [NSLocalizedDescriptionKey: "尚未配置更新服务器地址，请先在下方配置有效的 App Release Hub 域名或 IP。"]
                )
            } else {
                // 静默检查直接返回无更新，避免控制台产生网络/TLS报错
                return AppUpdateInfo(hasUpdate: false, updateType: nil, versionCode: nil, versionName: nil, forceUpdate: false, releaseNotes: nil, downloadUrl: nil)
            }
        }
        
        // 非强制检查时，如果距离上次检查时间还在间隔内，直接返回缓存结果
        if !force, let last = lastCheckTime, Date().timeIntervalSince(last) < checkInterval {
            if let cachedInfo = updateInfo {
                return cachedInfo
            }
        }
        
        self.isChecking = true
        defer { self.isChecking = false }
        
        let currentVersionCode = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        let deviceId = UIDevice.current.identifierForVendor?.uuidString ?? "unknown_ios_device"
        
        let urlString = "\(base)/api/apps/\(appId)/version/\(platform)?versionCode=\(currentVersionCode)&deviceId=\(deviceId)"
        
        guard let url = URL(string: urlString) else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        
        let resData = try JSONDecoder().decode(AppUpdateInfo.self, from: data)
        self.lastCheckTime = Date()
        DispatchQueue.main.async {
            self.updateInfo = resData
        }
        return resData
    }
}
