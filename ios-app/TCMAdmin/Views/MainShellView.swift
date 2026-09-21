import SwiftUI

@MainActor
public struct MainShellView: View {
    @State private var isMenuShowing = false
    @State private var selectedTab = 0
    @Bindable private var router = Router.shared
    var networkMonitor = NetworkMonitor.shared
    
    public init() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Color.surface)
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
    
    public var body: some View {
        ZStack(alignment: .leading) {
            NavigationStack(path: $router.navPath) {
                // 主 Tab 导航区
                TabView(selection: $selectedTab) {
                    // Tab 1: 库存
                    InventoryView()
                        .tabItem {
                            Label("库存查询", systemImage: "archivebox")
                        }
                        .tag(0)
                    
                    // Tab 2: 斗谱
                    HerbsView()
                        .tabItem {
                            Label("斗谱", systemImage: "square.grid.2x2")
                        }
                        .tag(1)
                    
                    // Tab 3: 加工
                    ProcessingView()
                        .tabItem {
                            Label("加工", systemImage: "arrow.triangle.2.circlepath")
                        }
                        .tag(2)
                    
                    // Tab 4: 包裹
                    PackagesView()
                        .tabItem {
                            Label("包裹", systemImage: "bag")
                        }
                        .tag(3)
                    
                    // Tab 5: 我的
                    ProfileView()
                        .tabItem {
                            Label("我的", systemImage: "person.crop.circle")
                        }
                        .tag(4)
                }
                .safeAreaInset(edge: .top) {
                    if !networkMonitor.isConnected {
                        HStack(spacing: 6) {
                            Image(systemName: "wifi.slash")
                                .scaledFont(12)
                            Text("当前网络不可用，请检查网络设置")
                                .scaledFont(12, weight: .medium)
                        }
                        .foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .background(Color.danger)
                        .transition(.move(edge: .top).combined(with: .opacity))
                    }
                }
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    // 中间：标题 (强制居中)
                    ToolbarItem(placement: .principal) {
                        Text(navTitle(for: selectedTab))
                            .font(.headline)
                            .foregroundStyle(Color.ink)
                    }
                    
                    // 左侧：菜单按钮
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button(action: {
                            withAnimation(.spring()) {
                                isMenuShowing.toggle()
                            }
                        }) {
                            Image(systemName: "line.3.horizontal")
                                .foregroundStyle(Color.ink)
                        }
                    }
                    
                    // 右侧：扫码按钮 (打开原生相机 4 路分发扫码)
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button(action: {
                            router.presentScanner(enableOCR: true)
                        }) {
                            Image(systemName: "qrcode.viewfinder")
                                .foregroundStyle(Color.appPrimary)
                        }
                    }
                }
                // 抽屉打开时禁用底层手势
                .disabled(isMenuShowing)
                
                .navigationDestination(for: AppRoute.self) { route in
                    switch route {
                case .prescriptions:
                    PrescriptionsView()
                case .prescriptionDetail(let id):
                    PrescriptionDetailView(id: id)
                case .prescriptionEdit(let id):
                    PrescriptionEditView(id: id)
                case .e6Imports:
                    E6ImportsView()
                case .e6ImportDetail(let id):
                    E6ImportDetailView(id: id)
                case .workflowOperation(let planId, let planCode):
                    WorkflowOperationView(planId: planId, planCode: planCode)
                case .packageDetail(let id):
                    PackageDetailView(id: id)
                case .packageVerify(let code):
                    PackageVerifyView(initialCode: code)
                case .herbLocationAssign(let location):
                    HerbLocationAssignView(location: location)
                case .inventoryDetail(let item):
                    InventoryDetailView(item: item)
                case .stocktaking:
                    StocktakingView()
                case .stocktakingDetail(let checkId):
                    StocktakingDetailView(checkId: checkId)
                case .differences:
                    DifferencesView()
                case .transfers:
                    TransfersView()
                case .transferDetail(let id):
                    TransferDetailView(id: id)
                case .settings:
                    SettingsView()
                case .themeAppearance:
                    ThemeAppearanceView()
                case .about:
                    AboutView()
                case .profileDetail:
                    ProfileDetailView()
                }
            }
            }
            
            // 侧滑抽屉覆盖在整个 NavigationStack (包括 NavigationBar) 之上
            SideMenuView(isShowing: $isMenuShowing, selectedTab: $selectedTab)
        }

        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("SearchInventoryByBarcode"))) { _ in
            self.selectedTab = 0
            self.router.popToRoot()
        }
        .fullScreenCover(isPresented: $router.isScannerPresented) {
                LiveScannerView(enableOCR: router.scannerEnableOCR)
            }
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    TabBarDoubleTapHandler.shared.setup()
                }
            }
    }
    
    private func navTitle(for tab: Int) -> String {
        switch tab {
        case 0: return "库存查询"
        case 1: return "斗谱管理"
        case 2: return "加工管理"
        case 3: return "包裹管理"
        case 4: return "我的"
        default: return "药房助手"
        }
    }
}

// MARK: - 全局双击 Tab 栏监听器
class TabBarDoubleTapHandler: NSObject, UIGestureRecognizerDelegate {
    static let shared = TabBarDoubleTapHandler()
    private var isSetup = false
    
    @MainActor
    func setup() {
        guard !isSetup else { return }
        guard let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let window = windowScene.windows.first else { return }
        
        let recognizer = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        recognizer.numberOfTapsRequired = 2
        recognizer.delegate = self
        recognizer.cancelsTouchesInView = false
        window.addGestureRecognizer(recognizer)
        isSetup = true
    }
    
    @objc func handleTap(_ sender: UITapGestureRecognizer) {
        guard let view = sender.view else { return }
        let location = sender.location(in: view)
        // 判定在底部 90pt 区域（TabBar 区域）
        if location.y > view.bounds.height - 90 {
            NotificationCenter.default.post(name: NSNotification.Name("TabDoubleTapped"), object: nil)
        }
    }
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }
}
import SwiftUI

extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        return viewControllers.count > 1
    }
}
