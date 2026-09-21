import SwiftUI

@MainActor
public struct SecurityPrivacyView: View {
    @State private var sessions: [SessionItem] = []
    @State private var isLoading = true
    @State private var isRevokingId: String? = nil
    @State private var showRevokeAllAlert = false
    
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
            sessions.removeAll { $0.jti == jti }
        } catch {
            print("Failed to revoke session: \(error)")
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
        DetailShell("安全与隐私") {
            ScrollView {
                VStack(spacing: 16) {
                    Text("这些是当前登录了你账号的设备。如果有不认识的设备，或者已经不再使用的设备，请将其退出登录。")
                        .font(.footnote)
                        .foregroundColor(ThemeManager.shared.current.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal)
                        .padding(.top, 8)
                    
                    if isLoading {
                        ProgressView()
                            .padding()
                    } else {
                        ForEach(sessions) { session in
                            SessionCard(
                                session: session,
                                isRevoking: isRevokingId == session.jti,
                                onRevoke: {
                                    Task {
                                        await revokeSession(jti: session.jti)
                                    }
                                }
                            )
                        }
                    }
                    
                    if !isLoading && sessions.filter({ !$0.isCurrent }).count > 0 {
                        Button(action: { showRevokeAllAlert = true }) {
                            Text("退出所有其他设备")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.red)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.red.opacity(0.1))
                                .cornerRadius(12)
                        }
                        .padding(.horizontal)
                        .padding(.top, 12)
                    }
                }
                .padding(.bottom, 32)
            }
        }
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
    }
}

@MainActor
struct SessionCard: View {
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
                            .foregroundColor(ThemeManager.shared.current.ink)
                        
                        if session.isCurrent {
                            Text("当前设备")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(ThemeManager.shared.current.success)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(ThemeManager.shared.current.successSoft)
                                .cornerRadius(4)
                        }
                    }
                    
                    HStack(spacing: 12) {
                        Label(session.ip.isEmpty ? "未知 IP" : session.ip, systemImage: "network")
                            .font(.caption)
                            .foregroundColor(ThemeManager.shared.current.muted)
                        
                        Label("活跃于\(timeText)", systemImage: "clock")
                            .font(.caption)
                            .foregroundColor(ThemeManager.shared.current.muted)
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
        .background(ThemeManager.shared.current.surface)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(ThemeManager.shared.current.outlineVariant, lineWidth: 1)
        )
        .padding(.horizontal)
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
