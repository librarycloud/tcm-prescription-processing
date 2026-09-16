import SwiftUI

@MainActor
public struct SettingsView: View {
    @ObservedObject private var router = Router.shared
    @ObservedObject private var session = SessionManager.shared
    
    @State private var cacheSize: String = "24.5 MB"
    @State private var isClearingCache = false
    @State private var isShowingServerConfig = false
    @State private var configuredBaseURL = ApiClient.shared.baseURL
    
    public init() {}
    
    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("界面与风格")
                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(.ink)
                    .padding(.horizontal, 4)
                
                AppCard(padding: 0) {
                    VStack(spacing: 0) {
                        ProfileRow(icon: "paintpalette.fill", title: "主题与外观", value: "跟随系统") {
                            router.navigate(to: .themeAppearance)
                        }
                    }
                }
                
                Text("系统与数据")
                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(.ink)
                    .padding(.horizontal, 4)
                    .padding(.top, 8)
                
                AppCard(padding: 0) {
                    VStack(spacing: 0) {
                        ProfileRow(icon: "trash.fill", title: "清除缓存", value: isClearingCache ? "清理中..." : cacheSize) {
                            isClearingCache = true
                            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                                cacheSize = "0 KB"
                                isClearingCache = false
                            }
                        }
                        Divider().padding(.leading, 48)
                        ProfileRow(icon: "server.rack", title: "API 服务器地址", value: ApiClient.shared.baseURL) {
                            configuredBaseURL = ApiClient.shared.baseURL
                            isShowingServerConfig = true
                        }
                        Divider().padding(.leading, 48)
                        ProfileRow(icon: "bell.fill", title: "新消息通知", value: "已开启") {}
                        Divider().padding(.leading, 48)
                        ProfileRow(icon: "lock.shield.fill", title: "安全与隐私") {}
                    }
                }
            }
            .padding(16)
        }
        .background(Color.pageBackground)
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("TCMServerConfigImported"))) { notif in
            if let newURL = notif.object as? String {
                configuredBaseURL = newURL
            }
        }
        .sheet(isPresented: $isShowingServerConfig) {
            NavigationView {
                Form {
                    Section(header: Text("后端服务 API 地址 (Base URL)"), footer: Text("提示：在 Mac 电脑模拟器中运行，连接本机服务直接填 http://127.0.0.1:3000 或 http://localhost:3000；若是真机调试，请填 Mac 的局域网 IP 地址。")) {
                        TextField("http://127.0.0.1:3000", text: $configuredBaseURL)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                    }
                    
                    Section(header: Text("快捷预设")) {
                        Button("本机开发机 (http://127.0.0.1:3000)") {
                            configuredBaseURL = "http://127.0.0.1:3000"
                        }
                        Button("本地主机名 (http://localhost:3000)") {
                            configuredBaseURL = "http://localhost:3000"
                        }
                    }
                }
                .navigationTitle("服务器设置")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("取消") { isShowingServerConfig = false }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("保存") {
                            ApiClient.shared.baseURL = configuredBaseURL
                            isShowingServerConfig = false
                        }
                        .fontWeight(.bold)
                    }
                }
            }
        }
    }
}

// MARK: - 主题与外观
@MainActor
public struct ThemeAppearanceView: View {
    @ObservedObject private var theme = ThemeManager.shared
    
    let themeModes = [("跟随系统", 0, "circle.lefthalf.filled"),
                      ("浅色模式", 1, "sun.max.fill"),
                      ("深色模式", 2, "moon.fill")]
    
    let presets = [
        ("本草蓝", "#2563EB"),
        ("国风绿", "#059669"),
        ("典雅紫", "#7C3AED"),
        ("深砖红", "#B91C1C"),
        ("琥珀黄", "#D97706"),
        ("青黛", "#0F766E"),
        ("沉香褐", "#78350F"),
        ("胭脂红", "#BE123C")
    ]
    
    @State private var showCustomColorPicker = false
    @State private var customColor = Color.blue
    
    public init() {}
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // 实时预览卡片 (Live Theme Preview)
                LiveThemePreviewCard()
                
                // 外观模式 (Segmented Theme Selector)
                VStack(alignment: .leading, spacing: 10) {
                    Text("外观风格")
                        .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.ink)
                        .padding(.horizontal, 4)
                    
                    HStack(spacing: 12) {
                        ForEach(themeModes, id: \.1) { mode in
                            let isSelected = theme.colorSchemeMode == mode.1
                            
                            VStack(spacing: 8) {
                                Image(systemName: mode.2)
                                    .font(.system(size: (20) * ThemeManager.shared.fontScale))
                                    .foregroundColor(isSelected ? .appPrimary : .muted)
                                
                                Text(mode.0)
                                    .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .appPrimary : .ink)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(isSelected ? Color.appPrimarySoft.opacity(0.3) : Color.surface)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(isSelected ? Color.appPrimary : Color.cardBorder, lineWidth: isSelected ? 2 : 1)
                            )
                            .onTapGesture {
                                theme.colorSchemeMode = mode.1
                                theme.updateTheme()
                            }
                        }
                    }
                }
                
                // 字体大小
                VStack(alignment: .leading, spacing: 10) {
                    Text("界面字体")
                        .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.ink)
                        .padding(.horizontal, 4)
                    
                    let fontGears = [("标准", 0), ("中大", 1), ("大号", 2), ("特大", 3)]
                    AppCard(padding: 16) {
                        HStack(spacing: 0) {
                            ForEach(fontGears, id: \.1) { gear in
                                let isSelected = theme.fontSizeGear == gear.1
                                Text(gear.0)
                                    .font(.system(size: (14 + CGFloat(gear.1)) * ThemeManager.shared.fontScale, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .white : .ink)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(isSelected ? Color.appPrimary : Color.clear)
                                    .cornerRadius(8)
                                    .onTapGesture {
                                        theme.fontSizeGear = gear.1
                                        theme.updateTheme()
                                    }
                            }
                        }
                        .padding(4)
                        .background(Color.surface)
                        .cornerRadius(12)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.cardBorder, lineWidth: 1))
                    }
                }
                
                // 主题强调色 (Theme Accent)
                VStack(alignment: .leading, spacing: 10) {
                    Text("本草主题色彩")
                        .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.ink)
                        .padding(.horizontal, 4)
                    
                    AppCard(padding: 16) {
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 20) {
                            ForEach(presets, id: \.1) { item in
                                let isSelected = theme.themeColorHex.uppercased() == item.1.uppercased()
                                
                                VStack(spacing: 8) {
                                    ZStack {
                                        Circle()
                                            .fill(Color(hex: item.1))
                                            .frame(width: 40, height: 40)
                                        
                                        if isSelected {
                                            Image(systemName: "checkmark")
                                                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                                .foregroundColor(.white)
                                        }
                                    }
                                    .overlay(
                                        Circle()
                                            .stroke(isSelected ? Color.ink.opacity(0.1) : Color.clear, lineWidth: 2)
                                            .frame(width: 46, height: 46)
                                    )
                                    
                                    Text(item.0)
                                        .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: isSelected ? .bold : .regular))
                                        .foregroundColor(isSelected ? .appPrimary : .ink)
                                }
                                .onTapGesture {
                                    theme.themeColorHex = item.1
                                    theme.updateTheme()
                                }
                            }
                            
                            // 自定义颜色
                            let isCustom = !presets.contains(where: { $0.1.uppercased() == theme.themeColorHex.uppercased() })
                            VStack(spacing: 8) {
                                ZStack {
                                    ColorPicker("", selection: Binding(get: {
                                        Color(hex: theme.themeColorHex)
                                    }, set: { newColor in
                                        let uiColor = UIColor(newColor)
                                        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0
                                        uiColor.getRed(&r, green: &g, blue: &b, alpha: nil)
                                        let hex = String(format: "#%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
                                        theme.themeColorHex = hex
                                        theme.updateTheme()
                                    }))
                                    .labelsHidden()
                                    .scaleEffect(1.2)
                                    .frame(width: 40, height: 40)
                                    
                                    if !isCustom {
                                        Circle()
                                            .fill(Color(UIColor.systemGray5).opacity(0.8))
                                            .frame(width: 40, height: 40)
                                            .allowsHitTesting(false)
                                        
                                        Image(systemName: "paintpalette")
                                            .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .semibold))
                                            .foregroundColor(.ink)
                                            .allowsHitTesting(false)
                                    }
                                }
                                .overlay(
                                    Circle()
                                        .stroke(isCustom ? Color.appPrimary.opacity(0.5) : Color.clear, lineWidth: 2)
                                        .frame(width: 46, height: 46)
                                )
                                
                                Text("自定义")
                                    .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: isCustom ? .bold : .regular))
                                    .foregroundColor(isCustom ? .appPrimary : .ink)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Color.pageBackground)
        .navigationTitle("主题与外观")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - 实时预览卡片
struct LiveThemePreviewCard: View {
    var body: some View {
        AppCard(padding: 16) {
            VStack(spacing: 16) {
                HStack {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(Color.appPrimary)
                            .frame(width: 8, height: 8)
                        Text("界面实时渲染预览")
                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                            .foregroundColor(.ink)
                    }
                    Spacer()
                    Text("实时生效")
                        .font(.system(size: (11) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.appPrimary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.appPrimarySoft)
                        .cornerRadius(6)
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(Color.appPrimary.opacity(0.3), lineWidth: 0.5)
                        )
                }
                
                // Simulated Mini TCM App Card
                VStack(spacing: 12) {
                    HStack {
                        HStack(spacing: 10) {
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color.appPrimary)
                                .frame(width: 28, height: 28)
                                .overlay(
                                    Image(systemName: "checkmark")
                                        .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                        .foregroundColor(.white)
                                )
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("补中益气汤加减")
                                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                Text("处方号 RX-2026-088 · 3剂")
                                    .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                            }
                        }
                        
                        Spacer()
                        
                        Text("调剂完成")
                            .font(.system(size: (11) * ThemeManager.shared.fontScale, weight: .semibold))
                            .foregroundColor(.appPrimary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.appPrimarySoft)
                            .cornerRadius(12)
                    }
                    
                    // Herb Tags
                    HStack(spacing: 8) {
                        ForEach(["黄芪 15g", "党参 10g", "白术 10g", "甘草 6g"], id: \.self) { herb in
                            Text(herb)
                                .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                .foregroundColor(.ink)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.surface)
                                .cornerRadius(6)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 6)
                                        .stroke(Color.cardBorder, lineWidth: 0.5)
                                )
                        }
                        Spacer()
                    }
                    
                    // Action Buttons
                    HStack(spacing: 10) {
                        Button(action: {}) {
                            Text("出库发药")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 32)
                                .background(Color.appPrimary)
                                .cornerRadius(8)
                        }
                        
                        Button(action: {}) {
                            Text("打印药签")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                                .foregroundColor(.appPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 32)
                                .background(Color.clear)
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(Color.appPrimary, lineWidth: 1)
                                )
                        }
                    }
                }
                .padding(14)
                .background(Color.pageBackground)
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.cardBorder, lineWidth: 1)
                )
            }
        }
    }
}

// MARK: - 关于药房助手
@MainActor
public struct AboutView: View {
    @ObservedObject private var updateManager = UpdateManager.shared
    @State private var showingUpdateAlert = false
    @State private var updateMessage = ""
    @State private var showEditHubUrl = false
    @State private var hubUrlInput = ""
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: 24) {
            Spacer().frame(height: 32)
            
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.appPrimary)
                .frame(width: 80, height: 80)
                .overlay(
                    Image(systemName: "cross.case.fill")
                        .font(.system(size: (40) * ThemeManager.shared.fontScale))
                        .foregroundColor(.white)
                )
                .shadow(color: Color.appPrimary.opacity(0.3), radius: 8, x: 0, y: 4)
            
            VStack(spacing: 6) {
                Text("药房助手 iOS 端")
                    .font(.system(size: (20) * ThemeManager.shared.fontScale, weight: .bold))
                let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "2.5.0"
                let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "20260915"
                Text("Version \(version) (Build \(build))")
                    .font(.system(size: (13) * ThemeManager.shared.fontScale))
                    .foregroundColor(.muted)
            }
            
            Button(action: checkUpdate) {
                if updateManager.isChecking {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    Text("检查新版本")
                        .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                }
            }
            .foregroundColor(.white)
            .frame(width: 160, height: 44)
            .background(Color.appPrimary)
            .cornerRadius(22)
            .disabled(updateManager.isChecking)
            .padding(.top, 8)
            
            AppCard(padding: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("更新服务器").font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold)).foregroundColor(.ink)
                        Spacer()
                        Button(showEditHubUrl ? "保存" : "修改") {
                            if showEditHubUrl {
                                updateManager.hubBaseURL = hubUrlInput
                                showEditHubUrl = false
                            } else {
                                hubUrlInput = updateManager.hubBaseURL
                                showEditHubUrl = true
                            }
                        }
                        .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                        .foregroundColor(.appPrimary)
                    }
                    
                    if showEditHubUrl {
                        TextField("如: https://release.yourdomain.com", text: $hubUrlInput)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .font(.system(size: (13) * ThemeManager.shared.fontScale))
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                    } else {
                        Text(updateManager.hubBaseURL.isEmpty ? "未配置 App Release Hub 地址 (点击修改)" : updateManager.hubBaseURL)
                            .font(.system(size: (13) * ThemeManager.shared.fontScale))
                            .foregroundColor(updateManager.hubBaseURL.isEmpty ? .muted : .ink)
                    }
                }
            }
            .padding(.horizontal, 16)
            
            AppCard(padding: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("功能特性").font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                    Text("• 现代化 SwiftUI 全量原生重构\n• Apple 原生 VisionKit 纸质处方 OCR 极速识别\n• 4路智能条码扫码分发（取货码、加工计划、设备、库存）\n• 适配 iOS 16/17/18 NavigationStack 架构")
                        .font(.system(size: (13) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                        .lineSpacing(4)
                }
            }
            .padding(.horizontal, 16)
            
            Spacer()
        }
        .background(Color.pageBackground.ignoresSafeArea())
        .navigationTitle("关于药房助手")
        .navigationBarTitleDisplayMode(.inline)
        .alert(isPresented: $showingUpdateAlert) {
            Alert(
                title: Text("检查更新"),
                message: Text(updateMessage),
                dismissButton: .default(Text("确定"))
            )
        }
    }
    
    private func checkUpdate() {
        Task {
            do {
                // 手动点击检查新版本，强制忽略时间间隔限制
                let info = try await updateManager.checkUpdate(force: true)
                DispatchQueue.main.async {
                    if info.hasUpdate {
                        let notes = info.releaseNotes?.joined(separator: "\n") ?? "有新版本发布，请前往更新。"
                        self.updateMessage = "发现新版本: \(info.versionName ?? "")\n\n\(notes)"
                    } else {
                        self.updateMessage = "当前已是最新版本"
                    }
                    self.showingUpdateAlert = true
                }
            } catch {
                DispatchQueue.main.async {
                    self.updateMessage = "检查更新失败: \(error.localizedDescription)"
                    self.showingUpdateAlert = true
                }
            }
        }
    }
}
