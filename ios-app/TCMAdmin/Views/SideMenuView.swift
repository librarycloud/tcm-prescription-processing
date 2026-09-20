import SwiftUI

@MainActor
public struct SideMenuView: View {
    @Binding var isShowing: Bool
    @Binding var selectedTab: Int
    @Bindable private var router = Router.shared
    
    private let menuWidth: CGFloat = 280
    
    public init(isShowing: Binding<Bool>, selectedTab: Binding<Int>) {
        self._isShowing = isShowing
        self._selectedTab = selectedTab
    }
    
    public var body: some View {
        ZStack(alignment: .leading) {
            // 背景蒙版
            if isShowing {
                Color.black
                    .opacity(0.35)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation(.spring()) { isShowing = false }
                    }
            }
            
            // 抽屉内容
            if isShowing {
                VStack(alignment: .leading, spacing: 0) {
                    // Header 区域
                    HStack(spacing: 12) {
                        Image(systemName: "cross.case.fill")
                            .scaledFont(20)
                            .foregroundStyle(Color.white)
                            .frame(width: 38, height: 38)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 10))
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("药房助手")
                                .scaledFont(17, weight: .bold)
                                .foregroundStyle(Color.ink)
                            Text("中药房移动工作台")
                                .scaledFont(12)
                                .foregroundStyle(Color.muted)
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 60)
                    .padding(.bottom, 20)
                    
                    Divider().padding(.horizontal, 16)
                    
                    ScrollView {
                        VStack(alignment: .leading, spacing: 2) {
                            DrawerItem(icon: "archivebox", title: "库存查询", isSelected: selectedTab == 0) {
                                selectTab(0)
                            }
                            DrawerItem(icon: "doc.text.fill", title: "处方管理", isSelected: false) {
                                navigateTo(.prescriptions)
                            }
                            DrawerItem(icon: "icloud.and.arrow.down.fill", title: "E6诊所处方导入", isSelected: false) {
                                navigateTo(.e6Imports)
                            }
                            DrawerItem(icon: "arrow.triangle.2.circlepath", title: "加工管理", isSelected: selectedTab == 2) {
                                selectTab(2)
                            }
                            DrawerItem(icon: "bag.fill", title: "包裹管理", isSelected: selectedTab == 3) {
                                selectTab(3)
                            }
                            DrawerItem(icon: "square.grid.2x2.fill", title: "斗谱管理", isSelected: selectedTab == 1) {
                                selectTab(1)
                            }
                            
                            Divider().padding(.vertical, 6).padding(.horizontal, 16)
                            
                            DrawerItem(icon: "arrow.left.arrow.right", title: "商品盘点", isSelected: false) {
                                navigateTo(.stocktaking)
                            }
                            DrawerItem(icon: "slider.horizontal.3", title: "库存差异", isSelected: false) {
                                navigateTo(.differences)
                            }
                            DrawerItem(icon: "arrow.2.squarepath", title: "门店调拨", isSelected: false) {
                                navigateTo(.transfers)
                            }
                            
                            Divider().padding(.vertical, 6).padding(.horizontal, 16)
                            
                            DrawerItem(icon: "person.circle.fill", title: "我的", isSelected: selectedTab == 4) {
                                selectTab(4)
                            }
                            DrawerItem(icon: "gearshape.fill", title: "系统设置", isSelected: false) {
                                navigateTo(.settings)
                            }
                            DrawerItem(icon: "info.circle.fill", title: "关于药房助手", isSelected: false) {
                                navigateTo(.about)
                            }
                        }
                        .padding(.top, 12)
                    }
                }
                .frame(width: menuWidth)
                .background(Color.surface)
                .ignoresSafeArea()
                .transition(.move(edge: .leading))
            }
        }
        .allowsHitTesting(isShowing)
    }
    
    private func selectTab(_ index: Int) {
        withAnimation {
            selectedTab = index
            isShowing = false
        }
    }
    
    private func navigateTo(_ route: AppRoute) {
        withAnimation {
            isShowing = false
        }
        router.navigate(to: route)
    }
}

// MARK: - 侧滑菜单单项 View
struct DrawerItem: View {
    var icon: String
    var title: String
    var isSelected: Bool
    var badgeCount: Int = 0
    var action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .scaledFont(16, weight: .regular)
                    .foregroundStyle(isSelected ? Color.appPrimary : Color.muted)
                    .frame(width: 22, height: 22)
                
                Text(title)
                    .scaledFont(14, weight: isSelected ? .semibold : .regular)
                    .foregroundStyle(isSelected ? Color.appPrimary : Color.ink)
                
                Spacer()
                
                if badgeCount > 0 {
                    Circle()
                        .fill(Color.danger)
                        .frame(width: 8, height: 8)
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 42)
            .background(isSelected ? Color.appPrimarySoft : Color.clear)
            .clipShape(.rect(cornerRadius: 8))
            .padding(.horizontal, 10)
        }
    }
}
