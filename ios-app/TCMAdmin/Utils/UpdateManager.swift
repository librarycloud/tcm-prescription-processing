import Foundation
import UIKit
import Observation

public struct AppUpdateInfo: Decodable {
    public let hasUpdate: Bool
    public let updateType: String?
    public let versionCode: Int?
    public let versionName: String?
    public let forceUpdate: Bool?
    public let releaseNotes: [String]?
    public let downloadUrl: String?
}

@Observable
@MainActor
public class UpdateManager {
    public static let shared = UpdateManager()
    
    public let appId = "tcm-admin-ios"
    public let platform = "ios"
    
    public var isChecking = false
    public var updateInfo: AppUpdateInfo?
    
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
        // 非强制检查时，如果距离上次检查时间还在间隔内，直接返回缓存结果
        if !force, let last = lastCheckTime, Date().timeIntervalSince(last) < checkInterval {
            if let cachedInfo = updateInfo {
                return cachedInfo
            }
        }
        
        self.isChecking = true
        defer { self.isChecking = false }
        
        let bundleId = Bundle.main.bundleIdentifier ?? "com.tcm.admin"
        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
        
        guard let url = URL(string: "https://itunes.apple.com/cn/lookup?bundleId=\(bundleId)") else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }
        
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let results = json?["results"] as? [[String: Any]] ?? []
        
        if let appInfo = results.first,
           let storeVersion = appInfo["version"] as? String,
           storeVersion.compare(currentVersion, options: .numeric) == .orderedDescending {
            
            let releaseNotes = appInfo["releaseNotes"] as? String ?? appInfo["description"] as? String ?? "有新版本发布，快去看看吧！"
            let trackId = appInfo["trackId"] as? Int ?? 0
            
            // 使用 itms-apps:// 协议可直接在 iOS 端唤起 App Store 并跳转至对应应用页面
            let downloadUrl = "itms-apps://itunes.apple.com/app/id\(trackId)"
            
            let resData = AppUpdateInfo(
                hasUpdate: true,
                updateType: "appstore",
                versionCode: nil,
                versionName: storeVersion,
                forceUpdate: false, // 官方 App Store API 无法标识强更，默认非强更
                releaseNotes: releaseNotes.components(separatedBy: "\n").filter { !$0.isEmpty },
                downloadUrl: downloadUrl
            )
            self.lastCheckTime = Date()
            self.updateInfo = resData
            return resData
        } else {
            let resData = AppUpdateInfo(
                hasUpdate: false,
                updateType: nil,
                versionCode: nil,
                versionName: currentVersion,
                forceUpdate: false,
                releaseNotes: nil,
                downloadUrl: nil
            )
            self.lastCheckTime = Date()
            self.updateInfo = resData
            return resData
        }
    }
}
