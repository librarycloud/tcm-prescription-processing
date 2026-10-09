import SwiftUI

@MainActor
public struct LoginView: View {
    @State private var session = SessionManager.shared
    @State private var identifier = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    
    // 服务器配置弹窗
    @AppStorage("tcm_server_api_base_url") private var currentServerURL: String = "http://127.0.0.1:3000"
    @State private var isShowingServerConfig = false
    @State private var configuredBaseURL = ""
    @State private var serverConfigErrorMessage = ""
    @State private var currentTaskID: UUID = UUID()
    @State private var webUrlToShow: String? = nil
    
    public init() {}
    
    public var body: some View {
        ZStack {
            Color.pageBackground.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // 顶部配置按钮
                HStack {
                    Spacer()
                    Button(action: {
                        configuredBaseURL = currentServerURL
                        serverConfigErrorMessage = ""
                        isShowingServerConfig = true
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "server.rack")
                                .scaledFont(13)
                            Text("服务器设置")
                                .scaledFont(12)
                        }
                        .foregroundStyle(Color.muted)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color.surface)
                        .clipShape(.rect(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color.cardBorder, lineWidth: 1)
                        )
                    }
                    .padding(.trailing, 16)
                    .padding(.top, 12)
                }
                
                GeometryReader { geometry in
                    ScrollView {
                        VStack(spacing: 0) {
                            Spacer().frame(height: 24)
                            
                            // App Logo
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.appPrimary)
                                .frame(width: 64, height: 64)
                                .overlay(
                                    Image(systemName: "cross.case.fill")
                                        .scaledFont(32)
                                        .foregroundStyle(Color.white)
                                )
                                .shadow(color: Color.appPrimary.opacity(0.3), radius: 8, x: 0, y: 4)
                            
                            Spacer().frame(height: 16)
                            
                            Text("药房助手 管理端")
                                .scaledFont(22, weight: .bold)
                                .foregroundStyle(Color.ink)
                            
                            Spacer().frame(height: 4)
                            
                            Text("中药代加工与药房工作台管理系统")
                                .scaledFont(13)
                                .foregroundStyle(Color.muted)
                            
                            // 显示当前连接的服务器
                            Text("当前服务器: \(currentServerURL)")
                                .scaledFont(11)
                                .foregroundStyle(Color.appPrimary)
                                .padding(.top, 6)
                            
                            Spacer().frame(height: 30)
                            
                            // 登录卡片
                            AppCard(padding: 20) {
                                VStack(spacing: 16) {
                                    // 账号输入框
                                    HStack(spacing: 12) {
                                        Image(systemName: "person.fill")
                                            .foregroundStyle(Color.muted)
                                            .frame(width: 20)
                                        TextField("用户名 / 手机号", text: $identifier)
                                            .scaledFont(15)
                                            .autocapitalization(.none)
                                            .disableAutocorrection(true)
                                    }
                                    .padding(.horizontal, 14)
                                    .frame(height: 48)
                                    .background(Color(UIColor.systemGray6))
                                    .clipShape(.rect(cornerRadius: 8))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color.cardBorder, lineWidth: 1)
                                    )
                                    
                                    // 密码输入框
                                    HStack(spacing: 12) {
                                        Image(systemName: "lock.fill")
                                            .foregroundStyle(Color.muted)
                                            .frame(width: 20)
                                        SecureField("登录密码", text: $password)
                                            .scaledFont(15)
                                    }
                                    .padding(.horizontal, 14)
                                    .frame(height: 48)
                                    .background(Color(UIColor.systemGray6))
                                    .clipShape(.rect(cornerRadius: 8))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color.cardBorder, lineWidth: 1)
                                    )
                                    
                                    // 错误提示
                                    if let error = errorMessage {
                                        HStack {
                                            Image(systemName: "exclamationmark.circle.fill")
                                                .foregroundStyle(Color.danger)
                                                .scaledFont(13)
                                            Text(error)
                                                .scaledFont(13)
                                                .foregroundStyle(Color.danger)
                                            Spacer()
                                        }
                                        .padding(.top, 2)
                                    }
                                    
                                    Spacer().frame(height: 4)
                                    
                                    // 登录按钮
                                    Button(action: handleLogin) {
                                        HStack {
                                            if isLoading {
                                                ProgressView()
                                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                                    .padding(.trailing, 4)
                                            }
                                            Text(isLoading ? "正在登录..." : "登 录")
                                                .scaledFont(16, weight: .bold)
                                                .foregroundStyle(Color.white)
                                        }
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 48)
                                        .background(
                                            canSubmit ? Color.appPrimary : Color.appPrimary.opacity(0.4)
                                        )
                                        .clipShape(.rect(cornerRadius: 8))
                                    }
                                    .disabled(!canSubmit || isLoading)
                                }
                            }
                            .padding(.horizontal, 24)
                            
                            Spacer(minLength: 32)
                            
                            // 底部备案与协议
                            VStack(spacing: 6) {
                                HStack(spacing: 12) {
                                    Button("《隐私政策》") {
                                        webUrlToShow = "privacy_policy"
                                    }
                                    Text("·")
                                        .foregroundStyle(Color.muted)
                                    Button("《用户协议》") {
                                        webUrlToShow = "user_agreement"
                                    }
                                }
                                .scaledFont(12)
                                .foregroundStyle(Color.appPrimary)
                                
                                if let beianURL = URL(string: "https://beian.miit.gov.cn/") {
                                    Link("沪ICP备2026040883号-2A", destination: beianURL)
                                        .scaledFont(11.5)
                                        .foregroundStyle(Color.muted)
                                }
                                
                                Text("上海光影韵律科技有限公司 版权所有")
                                    .scaledFont(11)
                                    .foregroundStyle(Color.muted.opacity(0.8))
                            }
                            .padding(.bottom, 16)
                        }
                        .frame(minHeight: geometry.size.height)
                    }
                }
            }
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

        .sheet(isPresented: $isShowingServerConfig) {
            NavigationStack {
                Form {
                    Section(header: Text("后端服务 API 地址 (Base URL)"), footer: VStack(alignment: .leading, spacing: 4) {
                        Text("请输入药房系统的后端服务器地址。如不清楚，请联系系统管理员。")
                        if !serverConfigErrorMessage.isEmpty {
                            Text(serverConfigErrorMessage).foregroundStyle(Color.danger)
                        }
                    }) {
                        TextField("http://127.0.0.1:3000", text: $configuredBaseURL)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                    }

                }
                .navigationTitle("服务器设置")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("取消") {
                            isShowingServerConfig = false
                        }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("保存") {
                            guard let normalized = ApiClient.normalizedBaseURL(configuredBaseURL) else {
                                serverConfigErrorMessage = "服务器地址无效，请输入包含主机名的 HTTP 或 HTTPS 地址"
                                return
                            }
                            ApiClient.shared.baseURL = normalized
                            serverConfigErrorMessage = ""
                            isShowingServerConfig = false
                        }
                        .fontWeight(.bold)
                    }
                }
            }
        }
    }
    
    private var canSubmit: Bool {
        !identifier.trimmingCharacters(in: .whitespaces).isEmpty &&
        !password.trimmingCharacters(in: .whitespaces).isEmpty
    }
    
    private func handleLogin() {
        guard canSubmit else { return }
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        
        Task {
            do {
                let res = try await ApiClient.shared.login(
                    identifier: identifier.trimmingCharacters(in: .whitespaces),
                    password: password.trimmingCharacters(in: .whitespaces)
                )
                await MainActor.run {
                    session.saveSession(token: res.token, user: res.user)
                    if currentTaskID == taskID { isLoading = false }
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    if currentTaskID == taskID { isLoading = false }
                }
            }
        }
    }
}
