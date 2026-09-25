import SwiftUI

@MainActor
public struct SettingsView: View {
    @Bindable private var router = Router.shared
    var session = SessionManager.shared
    
    @State private var cacheSize: String = "24.5 MB"
    @State private var isClearingCache = false
    @AppStorage("tcm_server_api_base_url") private var currentServerURL: String = "http://127.0.0.1:3000"
    @AppStorage("keep_screen_awake") private var keepScreenAwake: Bool = false
    @State private var isShowingServerConfig = false
    @State private var configuredBaseURL = ""
    @State private var isShowingServerChangeAlert = false
    @State private var pendingBaseURL = ""
    
    public init() {}
    
    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("界面与风格")
                    .scaledFont(16, weight: .bold)
                    .foregroundStyle(Color.ink)
                    .padding(.horizontal, 4)
                
                AppCard(padding: 0) {
                    VStack(spacing: 0) {
                        let modeText = ThemeManager.shared.colorSchemeMode == 1 ? "浅色模式" : (ThemeManager.shared.colorSchemeMode == 2 ? "深色模式" : "跟随系统")
                        ProfileRow(icon: "paintpalette.fill", title: "主题与外观", value: modeText) {
                            router.navigate(to: .themeAppearance)
                        }
                        Divider().padding(.leading, 48)
                        HStack(spacing: 12) {
                            Image(systemName: "lightbulb.max.fill")
                                .foregroundStyle(Color.appPrimary)
                                .frame(width: 20)
                            VStack(alignment: .leading, spacing: 2) {
                                Toggle("保持屏幕常亮", isOn: $keepScreenAwake)
                                    .scaledFont(15)
                                    .foregroundStyle(Color.ink)
                            }
                        }
                        .padding(.horizontal, 16)
                        .frame(minHeight: 52)
                        .padding(.vertical, 6)
                        .onChange(of: keepScreenAwake) { _, newValue in
                            UIApplication.shared.isIdleTimerDisabled = newValue
                        }
                    }
                }
                
                Text("系统与数据")
                    .scaledFont(16, weight: .bold)
                    .foregroundStyle(Color.ink)
                    .padding(.horizontal, 4)
                    .padding(.top, 8)
                
                AppCard(padding: 0) {
                    VStack(spacing: 0) {
                        ProfileRow(icon: "lock.shield.fill", title: "安全与隐私", showArrow: true) {
                            Router.shared.navigate(to: .securityPrivacy)
                        }
                        Divider().padding(.leading, 48)
                        ProfileRow(icon: "trash.fill", title: "清除缓存", value: isClearingCache ? "清理中..." : cacheSize) {
                            if isClearingCache { return }
                            isClearingCache = true
                            Task {
                                await clearAppCache()
                                cacheSize = "0 KB"
                                isClearingCache = false
                            }
                        }
                        Divider().padding(.leading, 48)
                        ProfileRow(icon: "server.rack", title: "API 服务器地址", value: currentServerURL) {
                            configuredBaseURL = currentServerURL
                            isShowingServerConfig = true
                        }
                    }
                }
            }
            .padding(16)
        }
        .task {
            cacheSize = await calculateCacheSize()
        }
        .background(Color.pageBackground)
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.inline)

        .sheet(isPresented: $isShowingServerConfig) {
            NavigationStack {
                Form {
                    Section(header: Text("后端服务 API 地址 (Base URL)"), footer: Text("请输入药房系统的后端服务器地址。如不清楚，请联系系统管理员。")) {
                        TextField("http://127.0.0.1:3000", text: $configuredBaseURL)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
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
                            let trimmed = configuredBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard trimmed != ApiClient.shared.baseURL else {
                                isShowingServerConfig = false
                                return
                            }
                            if SessionManager.shared.isAuthenticated {
                                // 已登录状态切换服务器 → 弹出确认框
                                pendingBaseURL = trimmed
                                isShowingServerChangeAlert = true
                            } else {
                                ApiClient.shared.baseURL = trimmed
                                isShowingServerConfig = false
                            }
                        }
                        .fontWeight(.bold)
                    }
                }
            }
        }
        .alert("切换服务器需要重新登录", isPresented: $isShowingServerChangeAlert) {
            Button("取消", role: .cancel) {}
            Button("确认切换", role: .destructive) {
                let oldBaseURL = ApiClient.shared.baseURL
                let newBaseURL = pendingBaseURL
                isShowingServerConfig = false
                Task { @MainActor in
                    // 通知旧服务器退出当前 session（best-effort，失败不阻断）
                    struct EmptyResponse: Decodable {}
                    _ = try? await ApiClient.shared.request(
                        path: "/auth/logout",
                        method: "POST"
                    ) as EmptyResponse
                    
                    ApiClient.shared.baseURL = newBaseURL
                    SessionManager.shared.clearSession()
                }
            }
        } message: {
            Text("切换到新的服务器地址后，当前账号登录状态将被清除，需要重新登录。\n\n新地址：\(pendingBaseURL)")
        }
    }
    
    nonisolated private func calculateCacheSize() async -> String {
        return await Task.detached {
            var totalSize: Int = 0
            
            // 1. URLCache 大小
            totalSize += URLCache.shared.currentDiskUsage
            totalSize += URLCache.shared.currentMemoryUsage
            
            // 2. Caches 文件夹大小 (这里存放着各种图片和临时文件)
            if let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first,
               let enumerator = FileManager.default.enumerator(at: cacheDir, includingPropertiesForKeys: [.fileSizeKey]) {
                while let item = enumerator.nextObject() {
                    guard let url = item as? URL else { continue }
                    if let attr = try? url.resourceValues(forKeys: [.fileSizeKey]), let size = attr.fileSize {
                        totalSize += size
                    }
                }
            }
            
            let formatter = ByteCountFormatter()
            formatter.allowedUnits = [.useMB, .useKB]
            formatter.countStyle = .file
            return formatter.string(fromByteCount: Int64(totalSize))
        }.value
    }
    
    nonisolated private func clearAppCache() async {
        await Task.detached {
            // 清理系统 URLCache
            URLCache.shared.removeAllCachedResponses()
            
            // 清理 Caches 目录
            if let cacheDir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first,
               let contents = try? FileManager.default.contentsOfDirectory(at: cacheDir, includingPropertiesForKeys: nil) {
                for url in contents {
                    try? FileManager.default.removeItem(at: url)
                }
            }
        }.value
        
        // 回到 MainActor 清理 ApiClient 内存缓存
        await MainActor.run {
            ApiClient.shared.clearResponseCache()
        }
    }
}

// MARK: - 主题与外观
@MainActor
public struct ThemeAppearanceView: View {
    var theme = ThemeManager.shared
    
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
        ("胭脂红", "#BE123C"),
        ("天青蓝", "#0284C7"),
        ("石绿", "#10B981"),
        ("水墨灰", "#475569")
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
                        .scaledFont(16, weight: .bold)
                        .foregroundStyle(Color.ink)
                        .padding(.horizontal, 4)
                    
                    HStack(spacing: 12) {
                        ForEach(themeModes, id: \.1) { mode in
                            let isSelected = theme.colorSchemeMode == mode.1
                            
                            VStack(spacing: 8) {
                                Image(systemName: mode.2)
                                    .scaledFont(20)
                                    .foregroundStyle(isSelected ? Color.appPrimary : Color.muted)
                                
                                Text(mode.0)
                                    .scaledFont(13, weight: isSelected ? .bold : .medium)
                                    .foregroundStyle(isSelected ? Color.appPrimary : Color.ink)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(isSelected ? Color.appPrimarySoft : Color.surface)
                            .clipShape(.rect(cornerRadius: 12))
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
                        .scaledFont(16, weight: .bold)
                        .foregroundStyle(Color.ink)
                        .padding(.horizontal, 4)
                    
                    let fontGears = [("标准", 0), ("中大", 1), ("大号", 2), ("特大", 3)]
                    AppCard(padding: 16) {
                        HStack(spacing: 0) {
                            ForEach(fontGears, id: \.1) { gear in
                                let isSelected = theme.fontSizeGear == gear.1
                                Text(gear.0)
                                    .font(.system(size: (14 + CGFloat(gear.1)) * ThemeManager.shared.fontScale, weight: isSelected ? .bold : .medium))
                                    .foregroundStyle(isSelected ? Color.white : Color.ink)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(isSelected ? Color.appPrimary : Color.clear)
                                    .clipShape(.rect(cornerRadius: 8))
                                    .onTapGesture {
                                        theme.fontSizeGear = gear.1
                                        theme.updateTheme()
                                    }
                            }
                        }
                        .padding(4)
                        .background(Color.surface)
                        .clipShape(.rect(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.cardBorder, lineWidth: 1))
                    }
                }
                
                // 主题强调色 (Theme Accent)
                VStack(alignment: .leading, spacing: 10) {
                    Text("本草主题色彩")
                        .scaledFont(16, weight: .bold)
                        .foregroundStyle(Color.ink)
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
                                                .scaledFont(14, weight: .bold)
                                                .foregroundStyle(Color.white)
                                        }
                                    }
                                    .overlay(
                                        Circle()
                                            .stroke(isSelected ? Color.ink.opacity(0.1) : Color.clear, lineWidth: 2)
                                            .frame(width: 46, height: 46)
                                    )
                                    
                                    Text(item.0)
                                        .scaledFont(12, weight: isSelected ? .bold : .regular)
                                        .foregroundStyle(isSelected ? Color.appPrimary : Color.ink)
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
                                            .scaledFont(16, weight: .semibold)
                                            .foregroundStyle(Color.ink)
                                            .allowsHitTesting(false)
                                    }
                                }
                                .overlay(
                                    Circle()
                                        .stroke(isCustom ? Color.appPrimary.opacity(0.5) : Color.clear, lineWidth: 2)
                                        .frame(width: 46, height: 46)
                                )
                                
                                Text("自定义")
                                    .scaledFont(12, weight: isCustom ? .bold : .regular)
                                    .foregroundStyle(isCustom ? Color.appPrimary : Color.ink)
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
                            .scaledFont(13, weight: .semibold)
                            .foregroundStyle(Color.ink)
                    }
                    Spacer()
                    Text("实时生效")
                        .scaledFont(11, weight: .bold)
                        .foregroundStyle(Color.appPrimary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.appPrimarySoft)
                        .clipShape(.rect(cornerRadius: 6))
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
                                        .scaledFont(14, weight: .bold)
                                        .foregroundStyle(Color.white)
                                )
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("补中益气汤加减")
                                    .scaledFont(14, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("处方号 RX-2026-088 · 3剂")
                                    .scaledFont(11)
                                    .foregroundStyle(Color.muted)
                            }
                        }
                        
                        Spacer()
                        
                        Text("调剂完成")
                            .scaledFont(11, weight: .semibold)
                            .foregroundStyle(Color.appPrimary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.appPrimarySoft)
                            .clipShape(.rect(cornerRadius: 12))
                    }
                    
                    // Herb Tags
                    HStack(spacing: 8) {
                        ForEach(["黄芪 15g", "党参 10g", "白术 10g", "甘草 6g"], id: \.self) { herb in
                            Text(herb)
                                .scaledFont(11)
                                .foregroundStyle(Color.ink)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.surface)
                                .clipShape(.rect(cornerRadius: 6))
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
                                .scaledFont(13, weight: .medium)
                                .foregroundStyle(Color.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 32)
                                .background(Color.appPrimary)
                                .clipShape(.rect(cornerRadius: 8))
                        }
                        
                        Button(action: {}) {
                            Text("打印药签")
                                .scaledFont(13, weight: .medium)
                                .foregroundStyle(Color.appPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 32)
                                .background(Color.clear)
                                .clipShape(.rect(cornerRadius: 8))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(Color.appPrimary, lineWidth: 1)
                                )
                        }
                    }
                }
                .padding(14)
                .background(Color.pageBackground)
                .clipShape(.rect(cornerRadius: 10))
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
    var updateManager = UpdateManager.shared
    @State private var showingUpdateAlert = false
    @State private var updateMessage = ""
    @State private var updateUrl: String? = nil
    @State private var webUrlToShow: String?
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: 24) {
            Spacer().frame(height: 32)
            
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.appPrimary)
                .frame(width: 80, height: 80)
                .overlay(
                    Image(systemName: "cross.case.fill")
                        .scaledFont(40)
                        .foregroundStyle(Color.white)
                )
                .shadow(color: Color.appPrimary.opacity(0.3), radius: 8, x: 0, y: 4)
            
            VStack(spacing: 6) {
                Text("药房助手 iOS 端")
                    .scaledFont(20, weight: .bold)
                let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "2.5.0"
                let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "20260915"
                Text("Version \(version) (Build \(build))")
                    .scaledFont(13)
                    .foregroundStyle(Color.muted)
            }
            
            Button(action: checkUpdate) {
                if updateManager.isChecking {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    Text("检查新版本")
                        .scaledFont(15, weight: .bold)
                }
            }
            .foregroundStyle(Color.white)
            .frame(width: 160, height: 44)
            .background(Color.appPrimary)
            .clipShape(.rect(cornerRadius: 22))
            .disabled(updateManager.isChecking)
            .padding(.top, 8)

            
            AppCard(padding: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    Text("功能特性").scaledFont(14, weight: .bold)
                    Text("• 现代化 SwiftUI 全量原生重构\n• Apple 原生 VisionKit 纸质处方 OCR 极速识别\n• 4路智能条码扫码分发（取货码、加工计划、设备、库存）\n• 适配 iOS 16/17/18 NavigationStack 架构")
                        .scaledFont(13)
                        .foregroundStyle(Color.muted)
                        .lineSpacing(4)
                }
            }
            .padding(.horizontal, 16)
            
            HStack(spacing: 16) {
                Button("《隐私政策》") {
                    webUrlToShow = "privacy_policy"
                }
                Button("《用户协议》") {
                    webUrlToShow = "user_agreement"
                }
            }
            .scaledFont(13)
            .foregroundStyle(Color.appPrimary)
            .padding(.top, 8)
            
            Spacer()
        }
        .background(Color.pageBackground.ignoresSafeArea())
        .navigationTitle("关于药房助手")
        .navigationBarTitleDisplayMode(.inline)
        .alert("检查更新", isPresented: $showingUpdateAlert) {
            if let urlStr = updateUrl, let url = URL(string: urlStr) {
                Button("前往更新") {
                    UIApplication.shared.open(url)
                }
            }
            Button("确定", role: .cancel) {}
        } message: {
            Text(updateMessage)
        }
        .sheet(item: Binding<String?>(
            get: { webUrlToShow },
            set: { webUrlToShow = $0 }
        )) { type in
            NavigationStack {
                InlineWebView(type: type)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("关闭") {
                                webUrlToShow = nil
                            }
                        }
                    }
            }
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
                        self.updateUrl = info.downloadUrl ?? "itms-beta://"
                    } else {
                        self.updateMessage = "当前已是最新版本"
                        self.updateUrl = nil
                    }
                    self.showingUpdateAlert = true
                }
            } catch {
                DispatchQueue.main.async {
                    self.updateMessage = "检查更新失败: \(error.localizedDescription)"
                    self.updateUrl = nil
                    self.showingUpdateAlert = true
                }
            }
        }
    }
}
import SwiftUI

@MainActor
public struct SecurityPrivacyView: View {
    @State private var sessions: [SessionItem] = []
    @State private var isLoading = true
    @State private var isRevokingId: String? = nil
    @State private var showRevokeAllAlert = false
    @State private var revokeErrorMessage: String? = nil
    
    public init() {}
    
    private func fetchSessions() async {
        do {
            isLoading = true
            sessions = try await ApiClient.shared.fetchSessions()
        } catch {
            print("Failed to fetch sessions: \(error)")
        }
        isLoading = false
    }
    
    private func revokeSession(jti: String) async {
        isRevokingId = jti
        do {
            try await ApiClient.shared.revokeSession(jti: jti)
            // 只有后端确认成功才从本地移除，避免幽灵记录
            sessions.removeAll { $0.jti == jti }
        } catch {
            revokeErrorMessage = "退出失败，请检查网络后重试"
        }
        isRevokingId = nil
    }
    
    private func revokeAllOtherSessions() async {
        let otherSessions = sessions.filter { !$0.isCurrent }
        for session in otherSessions {
            do {
                try await ApiClient.shared.revokeSession(jti: session.jti)
            } catch {
                print("Failed to revoke session \(session.jti): \(error)")
            }
        }
        await fetchSessions()
    }
    
    public var body: some View {
        Group {
            ScrollView {
                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("登录设备管理")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color.muted)
                            .padding(.horizontal, 16)
                            .padding(.top, 16)
                        
                        AppCard(padding: 0) {
                            VStack(spacing: 0) {
                                if isLoading {
                                    ProgressView()
                                        .padding(.vertical, 24)
                                        .frame(maxWidth: .infinity)
                                } else {
                                    ForEach(Array(sessions.enumerated()), id: \.element.jti) { index, session in
                                        SessionRow(
                                            session: session,
                                            isRevoking: isRevokingId == session.jti,
                                            onRevoke: {
                                                Task {
                                                    await revokeSession(jti: session.jti)
                                                }
                                            }
                                        )
                                        
                                        if index < sessions.count - 1 {
                                            Divider().padding(.leading, 16)
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        
                        Text("这些是当前登录了你账号的设备。如果有不认识的设备，或者已经不再使用的设备，请将其退出登录。")
                            .font(.system(size: 13))
                            .foregroundColor(Color.muted)
                            .padding(.horizontal, 24)
                            .padding(.top, 4)
                    }
                    
                    if !isLoading && sessions.filter({ !$0.isCurrent }).count > 0 {
                        Button(action: { showRevokeAllAlert = true }) {
                            Text("退出所有其他设备")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.red)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.surface)
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.cardBorder, lineWidth: 1)
                                )
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                    }
                }
                .padding(.bottom, 32)
            }
            .background(Color.pageBackground.ignoresSafeArea())
        }
        .navigationTitle("安全与隐私")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await fetchSessions()
        }
        .alert("退出所有其他设备", isPresented: $showRevokeAllAlert) {
            Button("取消", role: .cancel) {}
            Button("确认退出", role: .destructive) {
                Task {
                    await revokeAllOtherSessions()
                }
            }
        } message: {
            Text("确认将当前账号在所有其他设备上退出登录吗？")
        }
        .alert("操作失败", isPresented: Binding(
            get: { revokeErrorMessage != nil },
            set: { if !$0 { revokeErrorMessage = nil } }
        )) {
            Button("确定", role: .cancel) {}
        } message: {
            Text(revokeErrorMessage ?? "")
        }
    }
}

@MainActor
struct SessionRow: View {
    let session: SessionItem
    let isRevoking: Bool
    let onRevoke: () -> Void
    
    @State private var showRevokeConfirm = false
    
    private var timeText: String {
        let date = Date(timeIntervalSince1970: session.lastActiveAt / 1000)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(session.deviceName.isEmpty ? "未知设备" : session.deviceName)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Color.ink)
                        
                        if session.isCurrent {
                            Text("当前设备")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color.success)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.success.opacity(0.1))
                                .cornerRadius(4)
                        }
                    }
                    
                    VStack(alignment: .leading, spacing: 4) {
                        let ipText = session.ip.isEmpty ? "未知 IP" : session.ip
                        Label("\(ipText) (\(session.location ?? "未知地域"))", systemImage: "network")
                            .font(.caption)
                            .foregroundColor(Color.muted)
                        
                        Label("活跃于 \(timeText)", systemImage: "clock")
                            .font(.caption)
                            .foregroundColor(Color.muted)
                    }
                }
                
                Spacer()
                
                if !session.isCurrent {
                    Button(action: { showRevokeConfirm = true }) {
                        if isRevoking {
                            ProgressView()
                                .scaleEffect(0.8)
                        } else {
                            Text("退出")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.red)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.red.opacity(0.1))
                                .cornerRadius(8)
                        }
                    }
                    .disabled(isRevoking)
                }
            }
            .padding(16)
        }
        .background(Color.surface)
        .alert("退出登录", isPresented: $showRevokeConfirm) {
            Button("取消", role: .cancel) {}
            Button("确认", role: .destructive) {
                onRevoke()
            }
        } message: {
            Text("确认将当前账号在 \(session.deviceName.isEmpty ? "该设备" : session.deviceName) 上退出登录吗？")
        }
    }
}
