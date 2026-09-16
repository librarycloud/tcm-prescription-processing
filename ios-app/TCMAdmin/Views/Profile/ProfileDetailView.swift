import SwiftUI

@MainActor
public struct ProfileDetailView: View {
    @ObservedObject private var session = SessionManager.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var nickname: String = ""
    @State private var username: String = ""
    @State private var phone: String = ""
    @State private var password: String = ""
    
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var showSuccessToast = false
    
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
                            .font(.system(size: (32) * ThemeManager.shared.fontScale, weight: .bold))
                            .foregroundColor(.appPrimary)
                    }
                    
                    Text(session.currentUser?.roleName ?? "管理员")
                        .font(.system(size: (14) * ThemeManager.shared.fontScale))
                        .foregroundColor(.appPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                        .background(Color.appPrimarySoft)
                        .cornerRadius(12)
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
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.cardBorder, lineWidth: 1)
                )
                
                if let error = errorMessage {
                    Text(error)
                        .font(.system(size: (13) * ThemeManager.shared.fontScale))
                        .foregroundColor(.danger)
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
                            .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                    }
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.appPrimary)
                .cornerRadius(10)
                .disabled(isSaving)
                .padding(.top, 16)
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
        .alert(isPresented: $showSuccessToast) {
            Alert(
                title: Text("保存成功"),
                message: Text("你的个人资料已更新"),
                dismissButton: .default(Text("确定")) {
                    dismiss()
                }
            )
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
                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .medium))
                .foregroundColor(.ink)
            
            if isSecure {
                SecureField(placeholder, text: $text)
                    .font(.system(size: (15) * ThemeManager.shared.fontScale))
                    .padding(12)
                    .background(Color.pageBackground)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.cardBorder, lineWidth: 1)
                    )
            } else {
                TextField(placeholder, text: $text)
                    .keyboardType(keyboardType)
                    .font(.system(size: (15) * ThemeManager.shared.fontScale))
                    .padding(12)
                    .background(Color.pageBackground)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.cardBorder, lineWidth: 1)
                    )
            }
        }
    }
}
