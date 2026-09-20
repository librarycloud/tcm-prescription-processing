import SwiftUI
import Observation

public enum AppRoute: Hashable {
    // 处方管理
    case prescriptions
    case prescriptionDetail(id: Int)
    case prescriptionEdit(id: Int?)
    
    // E6 导入
    case e6Imports
    case e6ImportDetail(id: Int)
    
    // 加工管理
    case workflowOperation(planId: Int, planCode: String)
    
    // 包裹管理
    case packageDetail(id: Int)
    case packageVerify(initialCode: String)
    
    // 斗谱
    case herbLocationAssign(location: HerbLocationItem?)
    
    // 库存
    case inventoryDetail(item: InventoryItem)
    
    // 盘点与差异
    case stocktaking
    case stocktakingDetail(checkId: Int)
    case differences
    
    // 门店调拨
    case transfers
    case transferDetail(id: Int)
    
    // 设置与关于
    case settings
    case themeAppearance
    case about
    case profileDetail
}

@Observable
@MainActor
public class Router {
    public static let shared = Router()
    
    public var navPath = NavigationPath()
    public var isScannerPresented: Bool = false
    public var scannerEnableOCR: Bool = false
    public var scannerOnScanned: ((String) -> Void)? = nil
    
    private init() {}
    
    public func navigate(to route: AppRoute) {
        navPath.append(route)
    }
    
        public func presentScanner(enableOCR: Bool = false, onScanned: ((String) -> Void)? = nil) {
        self.scannerEnableOCR = enableOCR
        self.scannerOnScanned = onScanned
        self.isScannerPresented = true
    }
    
    public func pop() {
        if !navPath.isEmpty {
            navPath.removeLast()
        }
    }
    
    public func popToRoot() {
        navPath.removeLast(navPath.count)
    }
}
