import Foundation
import UIKit

public enum ApiError: LocalizedError {
    case invalidURL
    case invalidResponse(statusCode: Int, message: String)
    case unauthorized
    case decodingError(Error)
    case networkError(Error)
    
    public var errorDescription: String? {
        switch self {
        case .invalidURL: return "无效的请求地址"
        case .invalidResponse(_, let message): return message
        case .unauthorized: return "登录已过期，请重新登录"
        case .decodingError(let error): return "数据解析失败: \(error.localizedDescription)"
        case .networkError(let error):
            let msg = error.localizedDescription
            if (error as? URLError)?.code == .cancelled || error is CancellationError || msg.lowercased().contains("cancel") {
                return ""
            }
            return "网络连接异常: \(msg)"
        }
    }
}

public class ApiClient {
    public static let shared = ApiClient()
    
    private let serverUrlKey = "tcm_server_api_base_url"
    
    // 后端服务器地址，支持运行时动态配置与持久化存储
    public var baseURL: String {
        get {
            let url = UserDefaults.standard.string(forKey: serverUrlKey) ?? "http://127.0.0.1:3000"
            return url.isEmpty ? "http://127.0.0.1:3000" : url
        }
        set {
            var sanitized = newValue.trimmingCharacters(in: .whitespacesAndNewlines)
            if !sanitized.isEmpty {
                if !sanitized.hasPrefix("http://") && !sanitized.hasPrefix("https://") {
                    sanitized = "http://" + sanitized
                }
                sanitized = sanitized.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            } else {
                sanitized = "http://127.0.0.1:3000"
            }
            UserDefaults.standard.set(sanitized, forKey: serverUrlKey)
            clearResponseCache()
            NotificationCenter.default.post(name: NSNotification.Name("TCMServerConfigImported"), object: sanitized)
        }
    }
    

    // MARK: - 自动导入服务器配置 (支持 Deep Link 与二维码)
    @discardableResult
    public func importServerConfig(from url: URL) -> (success: Bool, newURL: String?, message: String) {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return (false, nil, "无效的配置链接格式")
        }
        
        var targetServer: String? = nil
        
        // 1. 优先解析 Query 参数: ?server=... 或 ?url=... 或 ?baseURL=...
        if let queryItems = components.queryItems {
            for item in queryItems {
                let name = item.name.lowercased()
                if (name == "server" || name == "url" || name == "baseurl" || name == "api"),
                   let val = item.value, !val.isEmpty {
                    targetServer = val
                    break
                }
            }
        }
        
        // 2. 若无 Query，解析 Host/Port 形式: 如 tcmadmin://192.168.1.100:3000
        if targetServer == nil {
            if let host = components.host, !host.isEmpty, host != "config" && host != "server" {
                let port = components.port.map { ":\($0)" } ?? ""
                targetServer = "http://\(host)\(port)"
            }
        }
        
        guard let serverStr = targetServer?.trimmingCharacters(in: .whitespacesAndNewlines), !serverStr.isEmpty else {
            return (false, nil, "未找到有效的服务器地址参数 (例如: tcmadmin://config?server=http://...)")
        }
        
        var finalURL = serverStr
        if !finalURL.hasPrefix("http://") && !finalURL.hasPrefix("https://") {
            finalURL = "http://" + finalURL
        }
        finalURL = finalURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        
        guard URL(string: finalURL) != nil else {
            return (false, nil, "服务器地址格式不正确: \(serverStr)")
        }
        
        self.baseURL = finalURL
        NotificationCenter.default.post(name: NSNotification.Name("TCMServerConfigImported"), object: finalURL)
        return (true, finalURL, "成功导入服务器地址: \(finalURL)")
    }
    
    @discardableResult
    public func importServerConfig(from string: String) -> (success: Bool, newURL: String?, message: String) {
        let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
        if let url = URL(string: trimmed) {
            if url.scheme == "tcmadmin" || url.scheme == "tcm" {
                return importServerConfig(from: url)
            }
            // 如果是标准网页中转落地页 (如 http://域名/app-config?server=...)
            if let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
               let queryItems = components.queryItems,
               queryItems.contains(where: { ["server", "url", "baseurl", "api"].contains($0.name.lowercased()) }) {
                return importServerConfig(from: url)
            }
            // 如果是直接填入的标准后端 HTTP/HTTPS API 根地址
            if url.scheme == "http" || url.scheme == "https" {
                let sanitized = trimmed.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                self.baseURL = sanitized
                NotificationCenter.default.post(name: NSNotification.Name("TCMServerConfigImported"), object: sanitized)
                return (true, sanitized, "成功导入服务器地址: \(sanitized)")
            }
        }
        return (false, nil, "无法识别为有效的服务器配置格式")
    }

    private init() {}
    
    // MARK: - Generic Response Cache
    private struct CacheEntry {
        let data: Data
        let savedAt: Date
    }
    
    private var memoryCache: [String: CacheEntry] = [:]
    private let cacheQueue = DispatchQueue(label: "com.tcm.apiCache", attributes: .concurrent)
    
    private func getCacheTTL(for path: String) -> TimeInterval? {
        let route = path.components(separatedBy: "?").first ?? path
        switch route {
        case "/admin/e6/imports": return 5 * 60
        case "/admin/doctors", "/admin/dictionaries", "/stores", "/admin/store-transfers/stores", "/admin/herb-locations/stores": return 24 * 60 * 60
        case "/admin/herb-locations": return 24 * 60 * 60
        case "/admin/prescriptions", "/admin/processing-plans", "/admin/packages", "/admin/store-transfers", "/admin/store-transfers/stats": return 15 * 60
        // NOTE: /admin/e6-pharmacy/products is deliberately NOT cached so barcode scan and inventory search always fetch live inventory!
        case "/admin/stats", "/admin/products", "/admin/product-differences/stats", "/admin/product-differences/logs", "/admin/yd-goods-check": return 30
        default:
            if route.hasPrefix("/admin/prescriptions/") || route.hasPrefix("/admin/processing-plans/") || route.hasPrefix("/admin/packages/") || route.hasPrefix("/admin/store-transfers/") || route.hasPrefix("/admin/herb-locations/") || route.hasPrefix("/admin/e6/imports/") {
                return 5 * 60
            }
            return nil
        }
    }
    
    private func invalidateCache(for route: String) {
        let prefixes: [String]
        if route.hasPrefix("/admin/processing-plans/") && route.contains("/photos") {
            let planId = route.components(separatedBy: "/")[3]
            prefixes = ["/admin/processing-plans/\(planId)"]
        } else if route.hasPrefix("/admin/prescriptions") {
            prefixes = ["/admin/prescriptions", "/admin/processing-plans", "/admin/stats"]
        } else if route.hasPrefix("/admin/processing-plans") {
            prefixes = ["/admin/processing-plans", "/admin/packages", "/admin/stats"]
        } else if route.hasPrefix("/admin/packages") {
            prefixes = ["/admin/packages", "/admin/processing-plans", "/admin/stats"]
        } else if route.hasPrefix("/admin/store-transfers") {
            prefixes = ["/admin/store-transfers"]
        } else if route.hasPrefix("/admin/yd-goods-check") {
            prefixes = ["/admin/yd-goods-check"]
        } else if route.hasPrefix("/admin/product-differences") {
            prefixes = ["/admin/product-differences", "/admin/products"]
        } else if route.hasPrefix("/admin/herb-locations") {
            prefixes = ["/admin/herb-locations"]
        } else if route.hasPrefix("/admin/e6/imports") {
            prefixes = ["/admin/e6/imports", "/admin/prescriptions", "/admin/processing-plans", "/admin/stats"]
        } else {
            prefixes = ["/admin/prescriptions", "/admin/processing-plans", "/admin/packages", "/admin/stats"]
        }
        
        cacheQueue.async(flags: .barrier) {
            self.memoryCache = self.memoryCache.filter { entry in
                !prefixes.contains { entry.key.starts(with: $0) }
            }
        }
    }
    

    public func clearResponseCache() {
        cacheQueue.async(flags: .barrier) {
            self.memoryCache.removeAll()
        }
    }
    
    // MARK: - 基础请求方法
    public func request<T: Decodable>(
        path: String,
        method: String = "GET",
        body: [String: Any]? = nil,
        queryParams: [String: String]? = nil
    ) async throws -> T {
        let queryString = queryParams?.sorted(by: { $0.key < $1.key }).map { "\($0.key)=\($0.value)" }.joined(separator: "&") ?? ""
        let cacheKey = path + (queryString.isEmpty ? "" : "?\(queryString)")
        
        if method == "GET" {
            if let ttl = getCacheTTL(for: path) {
                var cachedData: Data? = nil
                cacheQueue.sync {
                    if let entry = memoryCache[cacheKey], Date().timeIntervalSince(entry.savedAt) < ttl {
                        cachedData = entry.data
                    }
                }
                if let data = cachedData {
                    do {
                        return try JSONDecoder().decode(T.self, from: data)
                    } catch {
                        // Ignore decode error and fallback to network
                    }
                }
            }
        }
        var urlString = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path
        if let queryParams = queryParams, !queryParams.isEmpty {
            var components = URLComponents(string: urlString)
            components?.queryItems = queryParams.map { URLQueryItem(name: $0.key, value: $0.value) }
            if let resolved = components?.url?.absoluteString {
                urlString = resolved
            }
        }
        
        guard let url = URL(string: urlString) else {
            throw ApiError.invalidURL
        }
        
        var request = URLRequest(url: url, timeoutInterval: 15.0)
        request.httpMethod = method
        request.addValue("application/json", forHTTPHeaderField: "Accept")
        
        // 自动注入 Bearer Token
        if let token = SessionManager.shared.token, !token.isEmpty {
            request.addValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        #if os(iOS)
        request.addValue(UIDevice.current.name, forHTTPHeaderField: "X-Device-Name")
        #endif
        
        if let body = body {
            request.addValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            if Task.isCancelled || (error as? URLError)?.code == .cancelled || error is CancellationError {
                throw CancellationError()
            }
            throw ApiError.networkError(error)
        }
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse(statusCode: -1, message: "服务器未响应")
        }
        
        if httpResponse.statusCode == 401 {
            await MainActor.run { SessionManager.shared.clearSession() }
            throw ApiError.unauthorized
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            let errorMsg = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["message"] as? String ?? "请求失败 (\(httpResponse.statusCode))"
            throw ApiError.invalidResponse(statusCode: httpResponse.statusCode, message: errorMsg)
        }
        
        // 解析标准响应体: { code: 0, data: ..., message: "..." }
        do {
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            let code = json?["code"] as? Int ?? 0
            if code != 0 {
                let msg = json?["message"] as? String ?? "业务处理失败"
                throw ApiError.invalidResponse(statusCode: code, message: msg)
            }
            
            // 如果泛型就是标准的无包装解码
            let targetData: Data
            if let payload = json?["data"], !(payload is NSNull) {
                targetData = try JSONSerialization.data(withJSONObject: payload)
            } else {
                targetData = "{}".data(using: .utf8) ?? Data()
            }
            
            if method == "GET" {
                if getCacheTTL(for: path) != nil {
                    cacheQueue.async(flags: .barrier) {
                        self.memoryCache[cacheKey] = CacheEntry(data: targetData, savedAt: Date())
                    }
                }
            } else {
                invalidateCache(for: path)
            }
            
            return try JSONDecoder().decode(T.self, from: targetData)
        } catch let err as ApiError {
            throw err
        } catch {
            throw ApiError.decodingError(error)
        }
    }
    
    // MARK: - 1. 认证接口
    public func login(identifier: String, password: String) async throws -> (token: String, user: UserItem) {
        struct LoginResponse: Decodable {
            let token: String
            let user: UserItem
        }
        let res: LoginResponse = try await request(
            path: "/auth/login",
            method: "POST",
            body: ["identifier": identifier, "password": password]
        )
        return (res.token, res.user)
    }
    
    
    public func updateMe(nickname: String?, username: String?, phone: String?, password: String?) async throws -> (token: String, user: UserItem) {
        var body: [String: Any] = [:]
        if let nickname = nickname { body["nickname"] = nickname }
        if let username = username { body["username"] = username }
        if let phone = phone { body["phone"] = phone }
        if let password = password, !password.isEmpty { body["password"] = password }
        
        struct LoginResponse: Decodable {
            let token: String
            let user: UserItem
        }
        let res: LoginResponse = try await request(
            path: "/user/me",
            method: "PUT",
            body: body
        )
        return (res.token, res.user)
    }
    
    public func fetchSessions() async throws -> [SessionItem] {
        return try await request(path: "/auth/sessions")
    }
    
    public func revokeSession(jti: String) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/auth/sessions/\(jti)", method: "DELETE")
    }
    // MARK: - 2. 处方管理接口
    public func fetchPrescriptions(
        status: Int? = nil,
        keyword: String = "",
        storeId: Int? = nil,
        doctorId: Int? = nil,
        page: Int = 1,
        pageSize: Int = 20
    ) async throws -> [PrescriptionItem] {
        var params: [String: String] = ["page": "\(page)", "pageSize": "\(pageSize)"]
        if let s = status { params["status"] = "\(s)" }
        if let st = storeId { params["storeId"] = "\(st)" }
        if let doc = doctorId { params["doctorId"] = "\(doc)" }
        if !keyword.isEmpty { params["keyword"] = keyword }
        
        struct PagedList: Decodable {
            let list: [PrescriptionItem]?
        }
        let res: PagedList = try await request(path: "/admin/prescriptions", queryParams: params)
        return res.list ?? []
    }
    
    public func fetchPrescriptionDetail(id: Int) async throws -> PrescriptionItem? {
        return try await request(path: "/admin/prescriptions/\(id)")
    }
    
    // MARK: - 3. 加工管理接口
    public func fetchProcessingPlans(
        view: String = "today-all",
        keyword: String = "",
        storeId: Int? = nil,
        page: Int = 1,
        pageSize: Int = 20
    ) async throws -> [ProcessingPlanItem] {
        var params: [String: String] = ["view": view, "page": "\(page)", "pageSize": "\(pageSize)"]
        if let st = storeId { params["storeId"] = "\(st)" }
        if !keyword.isEmpty { params["keyword"] = keyword }
        
        struct PagedList: Decodable {
            let list: [ProcessingPlanItem]?
        }
        let res: PagedList = try await request(path: "/admin/processing-plans", queryParams: params)
        return res.list ?? []
    }
    
    public func fetchProcessingPlanByScan(code: String) async throws -> ProcessingPlanItem {
        let cleanCode = code.replacingOccurrences(of: "TCM:PLAN:1:", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        let list = try await fetchProcessingPlans(view: "all", keyword: cleanCode, page: 1, pageSize: 1)
        guard let plan = list.first else {
            throw ApiError.invalidResponse(statusCode: 404, message: "未找到对应加工计划")
        }
        return plan
    }
    
    // MARK: - 4. 包裹管理接口
    public func fetchPackages(
        status: Int? = nil,
        keyword: String = "",
        storeId: Int? = nil,
        sortBy: String? = nil,
        page: Int = 1,
        pageSize: Int = 20
    ) async throws -> [PackageModel] {
        var params: [String: String] = ["page": "\(page)", "pageSize": "\(pageSize)"]
        if let s = status { params["status"] = "\(s)" }
        if let st = storeId { params["storeId"] = "\(st)" }
        if let sb = sortBy { params["sortBy"] = sb }
        if !keyword.isEmpty { params["keyword"] = keyword }
        
        struct PagedList: Decodable {
            let list: [PackageModel]?
        }
        let res: PagedList = try await request(path: "/admin/packages", queryParams: params)
        return res.list ?? []
    }
    
    public func verifyPackage(code: String, pickupMethod: Int = 0, expressTrackingNo: String = "", pickupQrContent: String? = nil) async throws -> PackageModel {
        var body: [String: Any] = ["pickupCode": code, "pickupMethod": pickupMethod]
        if !expressTrackingNo.isEmpty {
            body["expressTrackingNo"] = expressTrackingNo
        }
        if let qr = pickupQrContent, !qr.isEmpty {
            body["pickupQrContent"] = qr
        }
        return try await request(
            path: "/admin/packages/verify",
            method: "POST",
            body: body
        )
    }
    
    public func fetchPackageDetail(id: Int) async throws -> PackageModel {
        return try await request(path: "/admin/packages/\(id)")
    }
    
    public func fetchPackageByCode(pickupCode: String) async throws -> PackageModel {
        return try await request(path: "/admin/packages/by-code/\(pickupCode)")
    }
    
    // MARK: - 5. 门店列表
    public func fetchStores() async throws -> [StoreItem] {
        return try await request(path: "/stores", queryParams: ["status": "1"])
    }
    
    // MARK: - 6. 库存商品查询 (E6 Pharmacy)
    public func fetchInventory(
        keyword: String = "",
        storeId: Int? = nil,
        page: Int = 1,
        pageSize: Int = 30
    ) async throws -> [InventoryItem] {
        var params: [String: String] = ["page": "\(page)", "pageSize": "\(pageSize)"]
        if let st = storeId { params["storeId"] = "\(st)" }
        if !keyword.isEmpty { params["keyword"] = keyword }
        
        struct InventoryResponse: Decodable {
            let list: [InventoryItem]?
        }
        let res: InventoryResponse = try await request(path: "/admin/e6-pharmacy/products", queryParams: params)
        return res.list ?? []
    }
    
    // MARK: - 7. E6 诊所处方导入
    public func fetchE6Imports(
        keyword: String = "",
        status: Int? = nil,
        orderDate: String? = nil,
        storeId: Int? = nil,
        page: Int = 1,
        pageSize: Int = 20
    ) async throws -> [E6ImportItem] {
        var params: [String: String] = ["page": "\(page)", "pageSize": "\(pageSize)"]
        if let s = status { params["status"] = "\(s)" }
        if let od = orderDate, !od.isEmpty { params["orderDate"] = od }
        if let st = storeId { params["storeId"] = "\(st)" }
        if !keyword.isEmpty { params["keyword"] = keyword }
        
        struct E6Response: Decodable {
            let list: [E6ImportItem]?
        }
        let res: E6Response = try await request(path: "/admin/e6/imports", queryParams: params)
        return res.list ?? []
    }
    
    // MARK: - 8. 斗谱与货位
    public func fetchHerbLocations(storeId: Int? = nil) async throws -> HerbLocationData {
        var params: [String: String] = [:]
        if let st = storeId { params["storeId"] = "\(st)" }
        
        return try await request(path: "/admin/herb-locations", queryParams: params)
    }
    
    public func updateHerbLocation(id: Int, location: String) async throws {
        let body: [String: Any] = [
            "herbId": id,
            "locationCode": location
        ]
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(
            path: "/admin/herb-locations/assignments",
            method: "POST",
            body: body
        )
    }
    
    // MARK: - 9. 统计数据
    public func fetchProcessingStats(storeId: Int? = nil) async throws -> ProcessingStatsModel {
        var params: [String: String] = [:]
        if let st = storeId { params["storeId"] = "\(st)" }
        return try await request(path: "/admin/stats", queryParams: params)
    }
    
    // MARK: - 10. 商品盘点
    public func fetchStocktakings(storeId: Int? = nil, page: Int = 1, pageSize: Int = 20) async throws -> [StocktakingModel] {
        var params: [String: String] = ["page": "\(page)", "pageSize": "\(pageSize)"]
        if let st = storeId { params["storeId"] = "\(st)" }
        struct PagedCheck: Decodable {
            let list: [StocktakingModel]?
        }
        let res: PagedCheck = try await request(path: "/admin/yd-goods-check", queryParams: params)
        return res.list ?? []
    }
    
    // MARK: - 11. 差异商品与统计
    public func fetchDifferences(page: Int = 1, pageSize: Int = 30) async throws -> [DifferenceProductModel] {
        let params: [String: String] = ["onlyDifference": "1", "page": "\(page)", "pageSize": "\(pageSize)"]
        struct DiffResponse: Decodable {
            let list: [DifferenceProductModel]?
        }
        let res: DiffResponse = try await request(path: "/admin/products", queryParams: params)
        return res.list ?? []
    }
    
    public func fetchDifferenceStats(storeId: Int? = nil) async throws -> DifferenceStatsModel {
        var params: [String: String] = [:]
        if let st = storeId { params["storeId"] = "\(st)" }
        return try await request(path: "/admin/product-differences/stats", queryParams: params)
    }
    
    public func fetchDifferenceLogs(keyword: String = "", storeId: Int? = nil, page: Int = 1, pageSize: Int = 20) async throws -> [DifferenceLogModel] {
        var params: [String: String] = ["page": "\(page)", "pageSize": "\(pageSize)"]
        if let st = storeId { params["storeId"] = "\(st)" }
        if !keyword.isEmpty { params["keyword"] = keyword }
        struct DiffLogResponse: Decodable {
            let list: [DifferenceLogModel]?
        }
        let res: DiffLogResponse = try await request(path: "/admin/product-differences/logs", queryParams: params)
        return res.list ?? []
    }
    
    // MARK: - 12. 门店调拨
    public func fetchTransfers(keyword: String = "", status: Int? = nil, overdueOnly: Bool = false, storeId: Int? = nil, page: Int = 1, pageSize: Int = 20) async throws -> [TransferModel] {
        var params: [String: String] = ["page": "\(page)", "pageSize": "\(pageSize)"]
        if overdueOnly {
            params["overdue"] = "1"
        } else if let s = status {
            params["status"] = "\(s)"
        }
        if let st = storeId { params["storeId"] = "\(st)" }
        if !keyword.isEmpty { params["keyword"] = keyword }
        struct TransferResponse: Decodable {
            let list: [TransferModel]?
        }
        let res: TransferResponse = try await request(path: "/admin/store-transfers", queryParams: params)
        return res.list ?? []
    }
    
    public func fetchTransferDetail(id: Int) async throws -> TransferModel {
        return try await request(path: "/admin/store-transfers/\(id)")
    }
    
    // MARK: - 13. 工序流转
    public func fetchProcessingWorkflow(id: Int) async throws -> WorkflowDetailModel {
        return try await request(path: "/admin/processing-plans/\(id)/workflow")
    }
    
    public func transitionPlan(id: Int, status: Int, createPackage: Bool = false) async throws {
        struct EmptyResponse: Decodable {}
        let body: [String: Any] = ["status": status, "createPackage": createPackage]
        let _: EmptyResponse = try await request(path: "/admin/processing-plans/\(id)/transition", method: "POST", body: body)
    }
    
    public func delayPlan(id: Int, days: Int = 1) async throws {
        let calendar = Calendar.current
        let targetDate = calendar.date(byAdding: .day, value: days, to: Date()) ?? Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let dateStr = formatter.string(from: targetDate)
        
        let body: [String: Any] = [
            "scheduleType": 1,
            "processDate": dateStr,
            "days": days
        ]
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/processing-plans/\(id)/delay", method: "POST", body: body)
    }
    
    public func generatePlanPackage(id: Int, itemInfo: String = "") async throws {
        struct EmptyResponse: Decodable {}
        let body: [String: Any] = ["itemInfo": itemInfo]
        let _: EmptyResponse = try await request(path: "/admin/processing-plans/\(id)/generate-package", method: "POST", body: body)
    }
    
    public func cancelPlan(id: Int, reason: String = "") async throws {
        try await transitionPlan(id: id, status: 5)
    }
    
    public func completeDispensing(planId: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/processing-plans/\(planId)/dispensing-complete", method: "POST")
    }
    
    public func startEquipmentUsage(planId: Int, stage: Int) async throws {
        struct EmptyResponse: Decodable {}
        let body: [String: Any] = ["stage": stage]
        let _: EmptyResponse = try await request(path: "/admin/processing-plans/\(planId)/equipment-usages/manual", method: "POST", body: body)
    }
    
    // MARK: - Multipart 上传与文件下载
    public func uploadMultipart(
        path: String,
        fieldName: String = "file",
        fileName: String,
        mimeType: String,
        fileData: Data,
        category: String = "default",
        onProgress: ((Double) -> Void)? = nil
    ) async throws -> [String: Any] {
        final class UploadProgressDelegate: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
            let onProgress: ((Double) -> Void)?
            init(onProgress: ((Double) -> Void)?) { self.onProgress = onProgress }
            func urlSession(_ session: URLSession, task: URLSessionTask, didSendBodyData bytesSent: Int64, totalBytesSent: Int64, totalBytesExpectedToSend: Int64) {
                if totalBytesExpectedToSend > 0 {
                    let progress = Double(totalBytesSent) / Double(totalBytesExpectedToSend)
                    onProgress?(progress)
                }
            }
        }
        let delegate = UploadProgressDelegate(onProgress: onProgress)
        
        // 1. 尝试获取直传策略
        struct StrategyEnvelope: Decodable {
            let data: StrategyData?
            struct StrategyData: Decodable {
                let uploadUrl: String?
                let storagePath: String?
            }
        }
        var strategyData: StrategyEnvelope.StrategyData? = nil
        do {
            let encodedFilename = fileName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? fileName
            let encodedMime = mimeType.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? mimeType
            let strategyRes: StrategyEnvelope = try await self.request(
                path: "/admin/upload/strategy",
                queryParams: ["category": category, "filename": encodedFilename, "mimeType": encodedMime]
            )
            strategyData = strategyRes.data
        } catch {
            print("OSS upload strategy unavailable: \(error)")
        }
        
        let uploadUrlString = strategyData?.uploadUrl
        var directUploadAllowed = false
        if let urlStr = uploadUrlString, !urlStr.isEmpty {
            if urlStr.lowercased().hasPrefix("https://") || urlStr.lowercased().hasPrefix("http://") {
                directUploadAllowed = true
            }
        }
        
        if directUploadAllowed, let urlStr = uploadUrlString, let url = URL(string: urlStr) {
            // S3/OSS PUT 直传
            var putReq = URLRequest(url: url)
            putReq.httpMethod = "PUT"
            putReq.setValue(mimeType, forHTTPHeaderField: "Content-Type")
            
            let (data, response) = try await URLSession.shared.upload(for: putReq, from: fileData, delegate: delegate)
            
            guard let httpRes = response as? HTTPURLResponse else {
                throw ApiError.invalidResponse(statusCode: -1, message: "直传服务器未响应")
            }
            if !(200...299).contains(httpRes.statusCode) {
                let errorMsg = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["message"] as? String ?? "直传失败 (\(httpRes.statusCode))"
                throw ApiError.invalidResponse(statusCode: httpRes.statusCode, message: errorMsg)
            }
            
            // 回调后端
            let notifyPayload: [String: Any] = [
                "storagePath": strategyData?.storagePath ?? "",
                "originalName": fileName,
                "mimeType": mimeType,
                "mimetype": mimeType,
                "filename": fileName,
                "size": fileData.count
            ]
            let backendPath = path.components(separatedBy: "?").first ?? path
            struct NotifyRes: Decodable {}
            let _: NotifyRes = try await self.request(path: backendPath, method: "POST", body: notifyPayload)
            return ["success": true, "storagePath": strategyData?.storagePath ?? ""]
        }
        
        // 2. 降级回传统 Multipart 上传
        let boundary = "Boundary-\(UUID().uuidString)"
        let urlString = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path
        guard let url = URL(string: urlString) else {
            throw ApiError.invalidURL
        }
        
        var request = URLRequest(url: url, timeoutInterval: 15.0)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if let token = SessionManager.shared.token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8) ?? Data())
        body.append("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(fileName)\"\r\n".data(using: .utf8) ?? Data())
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8) ?? Data())
        body.append(fileData)
        body.append("\r\n".data(using: .utf8) ?? Data())
        body.append("--\(boundary)--\r\n".data(using: .utf8) ?? Data())
        
        let (data, response) = try await URLSession.shared.upload(for: request, from: body, delegate: delegate)
        guard let httpRes = response as? HTTPURLResponse else {
            throw ApiError.invalidResponse(statusCode: -1, message: "服务器未响应")
        }
        if httpRes.statusCode == 401 {
            await MainActor.run { SessionManager.shared.clearSession() }
            throw ApiError.unauthorized
        }
        guard (200...299).contains(httpRes.statusCode) else {
            let errorMsg = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["message"] as? String ?? "上传失败 (\(httpRes.statusCode))"
            throw ApiError.invalidResponse(statusCode: httpRes.statusCode, message: errorMsg)
        }
        return (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
    
    public func requestBytes(path: String) async throws -> Data {
        let cacheKey = "bytes_" + path
        var cachedData: Data? = nil
        
        cacheQueue.sync {
            // Photos change rarely, cache for 24 hours
            if let entry = memoryCache[cacheKey], Date().timeIntervalSince(entry.savedAt) < 24 * 60 * 60 {
                cachedData = entry.data
            }
        }
        
        if let data = cachedData {
            return data
        }
        
        let urlString = baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + path
        guard let url = URL(string: urlString) else { throw ApiError.invalidURL }
        var request = URLRequest(url: url, timeoutInterval: 15.0)
        if let token = SessionManager.shared.token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        #if os(iOS)
        request.setValue(UIDevice.current.name, forHTTPHeaderField: "X-Device-Name")
        #endif
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpRes = response as? HTTPURLResponse, (200...299).contains(httpRes.statusCode) else {
            throw ApiError.invalidResponse(statusCode: (response as? HTTPURLResponse)?.statusCode ?? -1, message: "获取文件失败")
        }
        
        cacheQueue.async(flags: .barrier) {
            self.memoryCache[cacheKey] = CacheEntry(data: data, savedAt: Date())
        }
        
        return data
    }
    
    // MARK: - 工序与设备流转
    public func startEquipmentUsage(planId: Int, stage: Int, portionNo: Int, equipmentCode: String) async throws {
        struct EmptyResponse: Decodable {}
        let body: [String: Any] = [
            "stage": stage,
            "portionNo": portionNo,
            "equipmentCode": equipmentCode,
            "requestId": "ios-\(Int(Date().timeIntervalSince1970 * 1000))"
        ]
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans/\(planId)/equipment-usages",
            method: "POST",
            body: body
        )
    }
    
    public func startPackaging(planId: Int, usageId: Int, equipmentCode: String) async throws {
        struct EmptyResponse: Decodable {}
        let body: [String: Any] = [
            "equipmentCode": equipmentCode,
            "requestId": "ios-\(Int(Date().timeIntervalSince1970 * 1000))"
        ]
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans/\(planId)/equipment-usages/\(usageId)/start-packaging",
            method: "POST",
            body: body
        )
    }
    
    public func finishEquipmentUsage(planId: Int, usageId: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans/\(planId)/equipment-usages/\(usageId)/finish",
            method: "POST"
        )
    }
    
    public func voidEquipmentUsage(planId: Int, usageId: Int, reason: String) async throws {
        struct EmptyResponse: Decodable {}
        let body: [String: Any] = ["reason": reason]
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans/\(planId)/equipment-usages/\(usageId)/void",
            method: "POST",
            body: body
        )
    }
    
    public func transferFaultyEquipment(planId: Int, usageId: Int, reason: String, equipmentCode: String) async throws {
        struct EmptyResponse: Decodable {}
        let body: [String: Any] = [
            "reason": reason,
            "equipmentCode": equipmentCode,
            "requestId": "ios-\(Int(Date().timeIntervalSince1970 * 1000))"
        ]
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans/\(planId)/equipment-usages/\(usageId)/fault-transfer",
            method: "POST",
            body: body
        )
    }
    
    public func completeDispensing(planId: Int, fileName: String, mimeType: String, data: Data, onProgress: ((Double) -> Void)? = nil) async throws {
        _ = try await uploadMultipart(
            path: "/admin/processing-plans/\(planId)/dispensing-complete",
            fieldName: "file",
            fileName: fileName,
            mimeType: mimeType,
            fileData: data,
            category: "dispensing_photo",
            onProgress: onProgress
        )
    }
    
    public func fetchProcessingPhoto(planId: Int, photoId: Int) async throws -> Data {
        return try await requestBytes(path: "/admin/processing-plans/\(planId)/photos/\(photoId)")
    }
    
    public func deleteProcessingPhoto(planId: Int, photoId: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans/\(planId)/photos/\(photoId)",
            method: "DELETE"
        )
    }
    
    public func deleteProcessingPlan(id: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans/\(id)",
            method: "DELETE"
        )
    }
    
    public func updateProcessingPlan(id: Int, payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans/\(id)",
            method: "PUT",
            body: payload
        )
    }
    

    
    // MARK: - 处方管理接口
    public func createPrescription(payload: [String: Any]) async throws -> PrescriptionItem {
        return try await request(path: "/admin/prescriptions", method: "POST", body: payload)
    }
    
    public func updatePrescription(id: Int, payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/prescriptions/\(id)", method: "PUT", body: payload)
    }
    
    public func deletePrescription(id: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/prescriptions/\(id)", method: "DELETE")
    }
    
    public func uploadPrescriptionAttachment(id: Int, fileName: String, mimeType: String, data: Data, onProgress: ((Double) -> Void)? = nil) async throws {
        _ = try await uploadMultipart(
            path: "/admin/prescriptions/\(id)/attachment",
            fieldName: "file",
            fileName: fileName,
            mimeType: mimeType,
            fileData: data,
            category: "prescription_attachment",
            onProgress: onProgress
        )
    }
    
    public func fetchPrescriptionAttachment(id: Int) async throws -> Data {
        return try await requestBytes(path: "/admin/prescriptions/\(id)/attachment")
    }
    
    public func deletePrescriptionAttachment(id: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/prescriptions/\(id)/attachment", method: "DELETE")
    }
    
    // MARK: - 包裹管理接口
    public func createPackage(payload: [String: Any]) async throws -> PackageModel {
        return try await request(path: "/admin/packages", method: "POST", body: payload)
    }
    
    public func updatePackage(id: Int, payload: [String: Any]) async throws -> PackageModel {
        return try await request(path: "/admin/packages/\(id)", method: "PUT", body: payload)
    }
    
    public func deletePackage(id: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/packages/\(id)", method: "DELETE")
    }
    
    // MARK: - 门店调拨接口
    public func createTransfer(payload: [String: Any]) async throws -> TransferModel {
        return try await request(path: "/admin/store-transfers", method: "POST", body: payload)
    }
    
    public func cancelTransfer(id: Int, reason: String) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/store-transfers/\(id)/cancel", method: "POST", body: ["reason": reason])
    }
    
    public func confirmOutbound(id: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/store-transfers/\(id)/confirm-outbound", method: "POST")
    }
    
    public func confirmReturn(transferId: Int, returnId: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/store-transfers/\(transferId)/returns/\(returnId)/confirm", method: "POST")
    }
    
    public func addTransferReturns(transferId: Int, payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/store-transfers/\(transferId)/returns", method: "POST", body: payload)
    }
    
    public func fetchTransferStats(storeId: Int? = nil) async throws -> [String: Int] {
        var qp: [String: String]? = nil
        if let s = storeId { qp = ["storeId": "\(s)"] }
        return (try? await request(path: "/admin/store-transfers/stats", queryParams: qp)) ?? [:]
    }
    
    // MARK: - 库存差异接口
    public func registerDifference(payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/product-differences/register", method: "POST", body: payload)
    }
    
    public func writeOffDifference(payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/product-differences/write-off", method: "POST", body: payload)
    }
    
    public func reverseDifference(logId: Int, reason: String) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/product-differences/logs/\(logId)/reverse", method: "POST", body: ["reason": reason])
    }
    
    public func fetchProductCatalog(keyword: String = "") async throws -> [DifferenceProductModel] {
        struct Res: Decodable {
            let list: [DifferenceProductModel]?
        }
        var qp: [String: String]? = nil
        if !keyword.isEmpty { qp = ["keyword": keyword] }
        let r: Res = try await request(path: "/admin/product-catalog", queryParams: qp)
        return r.list ?? []
    }
    
    // MARK: - E6 导入接口
    public func mergeE6Imports(payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/e6/imports/merge", method: "POST", body: payload)
    }
    
    public func rejectE6Import(id: Int, reason: String) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/e6/imports/\(id)/reject", method: "POST", body: ["reason": reason])
    }
    
    public func revalidateE6Import(id: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/e6/imports/\(id)/revalidate", method: "POST")
    }
    
    // MARK: - 斗谱与货位接口
    public func fetchHerbLocationMatrix(storeId: Int? = nil, keyword: String = "", type: String = "") async throws -> HerbLocationData {
        var qp: [String: String] = [:]
        if let s = storeId { qp["storeId"] = "\(s)" }
        if !keyword.isEmpty { qp["keyword"] = keyword }
        if !type.isEmpty { qp["type"] = type }
        return try await request(path: "/admin/herb-locations/matrix", queryParams: qp)
    }
    
    public func assignHerbLocation(payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/herb-locations/assignments", method: "POST", body: payload)
    }
    
    public func updateHerb(id: Int, payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/herb-locations/herbs/\(id)", method: "PUT", body: payload)
    }
    
    public func moveHerbLocationAssignment(id: Int, payload: [String: Any]) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/herb-locations/assignments/\(id)", method: "PUT", body: payload)
    }
    
    public func deleteHerbLocationAssignment(id: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/herb-locations/assignments/\(id)", method: "DELETE")
    }
    
    // MARK: - 补充接口 (用于替换 Mock 数据)
    public func fetchEquipmentByCode(code: String) async throws -> EquipmentModel? {
        struct PagedList: Decodable {
            let list: [EquipmentModel]?
        }
        let res: PagedList = try await request(
            path: "/admin/processing-equipment",
            queryParams: ["keyword": code]
        )
        return res.list?.first
    }
    
    public func fetchStocktakingDetail(id: Int, status: String = "", page: Int = 1, pageSize: Int = 20) async throws -> StocktakingModel {
        var qp: [String: String] = ["page": "\(page)", "pageSize": "\(pageSize)"]
        if !status.isEmpty { qp["status"] = status }
        return try await request(path: "/admin/yd-goods-check/\(id)", queryParams: qp)
    }
    
    public func createGoodsCheck(name: String, type: Int = 1, storeId: Int? = nil) async throws {
        var body: [String: Any] = ["checkName": name, "checkType": type]
        if let st = storeId { body["storeId"] = st }
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/yd-goods-check", method: "POST", body: body)
    }
    
    public func fetchGoodsCheckCandidates(checkId: Int, keyword: String = "") async throws -> [StocktakingCandidateModel] {
        var qp: [String: String] = ["page": "1", "pageSize": "50"]
        if !keyword.isEmpty { qp["keyword"] = keyword }
        return try await request(path: "/admin/yd-goods-check/\(checkId)/candidates", queryParams: qp)
    }
    
    public func addGoodsCheckItem(checkId: Int, productId: Int, quantity: Double, locationCode: String? = nil, batchNo: String? = nil) async throws {
        var body: [String: Any] = ["productId": productId, "firstCountQty": quantity]
        if let loc = locationCode {
            body["locationCode"] = loc
            body["locationName"] = loc
        }
        if let b = batchNo { body["batchNo"] = b }
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/yd-goods-check/\(checkId)/items", method: "POST", body: body)
    }
    
    public func recountGoodsCheckItem(itemId: Int, quantity: Double) async throws {
        let body: [String: Any] = ["recountQty": quantity]
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/yd-goods-check/items/\(itemId)/recount", method: "PUT", body: body)
    }
    
    public func updateGoodsCheckLocation(itemId: Int, batchNo: String?, locationCode: String?) async throws {
        var body: [String: Any] = [:]
        if let b = batchNo { body["batchNo"] = b }
        if let l = locationCode {
            body["locationCode"] = l
            body["locationName"] = l
            body["countLocationName"] = l
        }
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/yd-goods-check/items/\(itemId)/location", method: "PUT", body: body)
    }
    
    public func finishGoodsCheck(id: Int) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(path: "/admin/yd-goods-check/\(id)/finish", method: "POST")
    }
    
    public func fetchE6ImportDetail(id: Int) async throws -> E6ImportItem {
        return try await request(path: "/admin/e6/imports/\(id)")
    }
    
    public func confirmE6Import(id: Int, payload: [String: Any]? = nil) async throws {
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(
            path: "/admin/e6/imports/\(id)/confirm",
            method: "POST",
            body: payload
        )
    }
    
    public func createProcessingPlan(
        prescriptionId: Int,
        processType: String,
        totalDose: Int,
        bagCount: Int,
        volumeMl: Int,
        usageMethod: String,
        pickupMethod: Int,
        expressAddress: String,
        scheduleType: Int,
        processDate: Date? = nil,
        isUrgent: Bool,
        paymentStatus: Int,
        processRemark: String,
        remark: String
    ) async throws {
        var body: [String: Any] = [
            "prescriptionId": prescriptionId,
            "method": processType,
            "totalDose": totalDose,
            "bagCount": bagCount,
            "volumeMl": volumeMl,
            "usageMethod": usageMethod,
            "pickupMethod": pickupMethod,
            "expressAddress": expressAddress,
            "scheduleType": scheduleType,
            "priority": isUrgent ? 1 : 0,
            "paymentStatus": paymentStatus,
            "processRemark": processRemark,
            "remark": remark
        ]
        if scheduleType == 1, let pDate = processDate {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd"
            body["processDate"] = formatter.string(from: pDate)
        }
        
        struct EmptyResponse: Decodable {}
        let _: EmptyResponse = try await request(
            path: "/admin/processing-plans",
            method: "POST",
            body: body
        )
    }
    
    // MARK: - 基础字典与医生接口
    public func fetchDoctors() async throws -> [DoctorItem] {
        return try await request(path: "/admin/doctors", queryParams: ["page": "1", "pageSize": "100"])
    }
    
    public func fetchProcessTypes() async throws -> [ProcessTypeItem] {
        do {
            return try await request(path: "/admin/process-types")
        } catch {
            return [
                ProcessTypeItem(id: 1, name: "代煎", code: "DECOCTION"),
                ProcessTypeItem(id: 2, name: "原药", code: "RAW")
            ]
        }
    }
    
    public func fetchDictionaries(type: String) async throws -> [DictionaryItem] {
        do {
            return try await request(path: "/admin/dictionaries", queryParams: ["type": type])
        } catch {
            return []
        }
    }
    
    public func fetchPrescriptionSources() async throws -> [DictionaryItem] {
        return try await fetchDictionaries(type: "PrescriptionSource")
    }
}


