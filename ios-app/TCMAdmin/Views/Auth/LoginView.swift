import SwiftUI

@MainActor
public struct LoginView: View {
    @State private var session = SessionManager.shared
    @State private var identifier = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    
    // 服务器配置弹窗
    @State private var isShowingServerConfig = false
    @State private var configuredBaseURL = ApiClient.shared.baseURL
    
    public init() {}
    
    public var body: some View {
        ZStack {
            Color.pageBackground.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // 顶部配置按钮
                HStack {
                    Spacer()
                    Button(action: {
                        configuredBaseURL = ApiClient.shared.baseURL
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
                        Text("当前服务器: \(ApiClient.shared.baseURL)")
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
                        
                        Spacer()
                    }
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("TCMServerConfigImported"))) { notif in
            if let newURL = notif.object as? String {
                configuredBaseURL = newURL
            }
        }
        .sheet(isPresented: $isShowingServerConfig) {
            NavigationStack {
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
                        Button("取消") {
                            isShowingServerConfig = false
                        }
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
    
    private var canSubmit: Bool {
        !identifier.trimmingCharacters(in: .whitespaces).isEmpty &&
        !password.trimmingCharacters(in: .whitespaces).isEmpty
    }
    
    private func handleLogin() {
        guard canSubmit else { return }
        guard !isLoading else { return }
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
                    isLoading = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }
}
