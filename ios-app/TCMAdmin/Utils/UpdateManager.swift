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
    
    public static var currentDeviceModel: String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let machineMirror = Mirror(reflecting: systemInfo.machine)
        let identifier = machineMirror.children.reduce("") { identifier, element in
            guard let value = element.value as? Int8, value != 0 else { return identifier }
            return identifier + String(UnicodeScalar(UInt8(value)))
        }
        if identifier.isEmpty {
            return UIDevice.current.model
        }
        let modelMap: [String: String] = [
            "i386": "iPhone Simulator",
            "x86_64": "iPhone Simulator",
            "arm64": "iPhone Simulator (Apple Silicon)",
            "iPhone14,2": "iPhone 13 Pro",
            "iPhone14,3": "iPhone 13 Pro Max",
            "iPhone14,4": "iPhone 13 mini",
            "iPhone14,5": "iPhone 13",
            "iPhone14,7": "iPhone 14",
            "iPhone14,8": "iPhone 14 Plus",
            "iPhone15,2": "iPhone 14 Pro",
            "iPhone15,3": "iPhone 14 Pro Max",
            "iPhone15,4": "iPhone 15",
            "iPhone15,5": "iPhone 15 Plus",
            "iPhone16,1": "iPhone 15 Pro",
            "iPhone16,2": "iPhone 15 Pro Max",
            "iPhone17,1": "iPhone 16 Pro",
            "iPhone17,2": "iPhone 16 Pro Max",
            "iPhone17,3": "iPhone 16",
            "iPhone17,4": "iPhone 16 Plus",
        ]
        return modelMap[identifier] ?? "\(UIDevice.current.model) (\(identifier))"
    }
    
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
        let deviceModel = Self.currentDeviceModel
        let osVersion = "\(UIDevice.current.systemName) \(UIDevice.current.systemVersion)"
        
        var urlComponents = URLComponents(string: "\(base)/api/apps/\(appId)/version/\(platform)")
        urlComponents?.queryItems = [
            URLQueryItem(name: "versionCode", value: currentVersionCode),
            URLQueryItem(name: "deviceId", value: deviceId),
            URLQueryItem(name: "deviceModel", value: deviceModel),
            URLQueryItem(name: "osVersion", value: osVersion)
        ]
        
        guard let url = urlComponents?.url else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(deviceId, forHTTPHeaderField: "x-device-id")
        request.setValue(deviceModel, forHTTPHeaderField: "x-device-model")
        request.setValue(osVersion, forHTTPHeaderField: "x-os-version")
        
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
