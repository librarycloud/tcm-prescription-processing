import SwiftUI

@MainActor
public struct ProfileDetailView: View {
    var session = SessionManager.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var nickname: String = ""
    @State private var username: String = ""
    @State private var phone: String = ""
    @State private var password: String = ""
    
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var showSuccessToast = false
    @State private var showLogoutConfirm = false
    
    public init() {}
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header Avatar
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color.appPrimarySoft)
                            .frame(width: 80, height: 80)
                        
                        Text(avatarText)
                            .scaledFont(32, weight: .bold)
                            .foregroundStyle(Color.appPrimary)
                    }
                    
                    Text(session.currentUser?.roleName ?? "管理员")
                        .scaledFont(14)
                        .foregroundStyle(Color.appPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                        .background(Color.appPrimarySoft)
                        .clipShape(.rect(cornerRadius: 12))
                }
                .padding(.top, 24)
                .frame(maxWidth: .infinity)
                
                // Form Fields
                VStack(spacing: 16) {
                    InputField(title: "昵称 (真实姓名)", placeholder: "请输入昵称", text: $nickname)
                    InputField(title: "登录账号", placeholder: "请输入登录账号", text: $username)
                    InputField(title: "手机号码", placeholder: "请输入手机号码", text: $phone, keyboardType: .numberPad)
                    InputField(title: "重置密码", placeholder: "不修改请留空", text: $password, isSecure: true)
                }
                .padding(16)
                .background(Color.surface)
                .clipShape(.rect(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.cardBorder, lineWidth: 1)
                )
                
                if let error = errorMessage {
                    Text(error)
                        .scaledFont(13)
                        .foregroundStyle(Color.danger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 8)
                }
                
                // Save Button
                Button(action: saveProfile) {
                    if isSaving {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text("保存修改")
                            .scaledFont(16, weight: .bold)
                    }
                }
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.appPrimary)
                .clipShape(.rect(cornerRadius: 10))
                .disabled(isSaving)
                .padding(.top, 16)
                
                // Logout Button
                Button(action: {
                    showLogoutConfirm = true
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                            .scaledFont(15)
                        Text("退出登录")
                            .scaledFont(16, weight: .medium)
                    }
                    .foregroundStyle(Color.danger)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color.surface)
                    .clipShape(.rect(cornerRadius: 10))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color.cardBorder, lineWidth: 1)
                    )
                }
                .padding(.top, 4)
            }
            .padding(16)
        }
        .background(Color.pageBackground.ignoresSafeArea())
        .navigationTitle("个人资料")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let user = session.currentUser {
                nickname = user.displayName
                username = user.username ?? ""
                phone = user.phone ?? ""
            }
        }
        .alert("保存成功", isPresented: $showSuccessToast) {
            Button("确定", role: .cancel) {
                dismiss()
            }
        } message: {
            Text("你的个人资料已更新")
        }
        .confirmationDialog("确定要退出当前账号吗？", isPresented: $showLogoutConfirm, titleVisibility: .visible) {
            Button("退出登录", role: .destructive) {
                session.clearSession()
            }
            Button("取消", role: .cancel) {}
        }
    }
    
    private var avatarText: String {
        let name = session.currentUser?.displayName ?? "管"
        return String(name.first ?? "管")
    }
    
    private func saveProfile() {
        guard !nickname.isEmpty, !username.isEmpty else {
            errorMessage = "昵称和登录账号不能为空"
            return
        }
        
        isSaving = true
        errorMessage = nil
        
        Task {
            do {
                let (_, updatedUser) = try await ApiClient.shared.updateMe(
                    nickname: nickname,
                    username: username,
                    phone: phone,
                    password: password.isEmpty ? nil : password
                )
                
                DispatchQueue.main.async {
                    if let token = session.token {
                        session.saveSession(token: token, user: updatedUser)
                    }
                    self.isSaving = false
                    self.showSuccessToast = true
                }
            } catch {
                DispatchQueue.main.async {
                    self.isSaving = false
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }
}

struct InputField: View {
    var title: String
    var placeholder: String
    @Binding var text: String
    var isSecure: Bool = false
    var keyboardType: UIKeyboardType = .default
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .scaledFont(14, weight: .medium)
                .foregroundStyle(Color.ink)
            
            if isSecure {
                SecureField(placeholder, text: $text)
                    .scaledFont(15)
                    .padding(12)
                    .background(Color.pageBackground)
                    .clipShape(.rect(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.cardBorder, lineWidth: 1)
                    )
            } else {
                TextField(placeholder, text: $text)
                    .keyboardType(keyboardType)
                    .scaledFont(15)
                    .padding(12)
                    .background(Color.pageBackground)
                    .clipShape(.rect(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.cardBorder, lineWidth: 1)
                    )
            }
        }
    }
}
