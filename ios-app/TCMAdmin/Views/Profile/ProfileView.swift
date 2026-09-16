import SwiftUI

@MainActor
struct ProfileView: View {
    @ObservedObject private var session = SessionManager.shared
    @ObservedObject private var updateManager = UpdateManager.shared
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Header: User Info Card
                NavigationLink(destination: ProfileDetailView()) {
                    AppCard(padding: 20) {
                        HStack(spacing: 16) {
                            // Avatar
                            ZStack {
                                Circle()
                                    .fill(Color.appPrimarySoft)
                                    .frame(width: 56, height: 56)
                                
                                Text(avatarText)
                                    .font(.system(size: (22) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.appPrimary)
                            }
                            
                            VStack(alignment: .leading, spacing: 6) {
                                Text(displayName)
                                    .font(.system(size: (18) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                
                                StatusPill(text: roleText)
                            }
                            
                            Spacer()
                            
                            Image(systemName: "chevron.right")
                                .foregroundColor(.muted)
                        }
                    }
                }
                
                Spacer().frame(height: 4)
                
                // Settings Button
                NavigationLink(destination: SettingsView()) {
                    HStack {
                        Image(systemName: "gearshape")
                            .font(.system(size: (16) * ThemeManager.shared.fontScale))
                        Text("设置")
                            .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .medium))
                    }
                    .foregroundColor(.ink)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.surface)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.cardBorder, lineWidth: 1)
                    )
                }
                
                // Update Button
                NavigationLink(destination: AboutView()) {
                    HStack {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.system(size: (16) * ThemeManager.shared.fontScale))
                        Text("检查新版本（\(appVersion)）")
                            .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .medium))
                        
                        if hasUpdate {
                            Circle()
                                .fill(Color.red)
                                .frame(width: 6, height: 6)
                                .padding(.leading, 2)
                        }
                    }
                    .foregroundColor(.ink)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color.surface)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.cardBorder, lineWidth: 1)
                    )
                }
                
                Spacer().frame(height: 24)
            }
            .padding(16)
        }
        .background(Color.pageBackground)
        .navigationTitle("我的")
        .onAppear {
            // 当页面显示时触发检查，UpdateManager 内部已加入 1 小时节流间隔
            Task {
                try? await updateManager.checkUpdate()
            }
        }
    }
    
    private var hasUpdate: Bool {
        return updateManager.updateInfo?.hasUpdate == true
    }
    
    private var appVersion: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "2.5.0"
        return "V\(version)"
    }
    
    private var displayName: String {
        let name = session.currentUser?.displayName ?? ""
        return name.isEmpty ? "管理员" : name
    }
    
    private var avatarText: String {
        guard let first = displayName.first else { return "管" }
        return String(first)
    }
    
    private var roleText: String {
        return session.currentUser?.roleName ?? "管理员"
    }
}

struct ProfileView_Previews: PreviewProvider {
    static var previews: some View {
        ProfileView()
    }
}
