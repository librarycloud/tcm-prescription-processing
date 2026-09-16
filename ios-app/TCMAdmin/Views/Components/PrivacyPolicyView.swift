import SwiftUI
import WebKit

struct InlineWebView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> WKWebView {
        return WKWebView()
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        uiView.load(URLRequest(url: url))
    }
}

struct PrivacyPolicyView: View {
    var onAgree: () -> Void
    var onDisagree: () -> Void
    
    @State private var webUrlToShow: URL?

    var body: some View {
        ZStack {
            Color.black.opacity(0.4).ignoresSafeArea()

            VStack(spacing: 20) {
                Text("隐私政策与用户协议")
                    .font(.headline)
                    .fontWeight(.bold)

                Text("感谢您使用本应用！我们非常重视您的个人信息和隐私保护。在您使用本应用前，请仔细阅读[《隐私政策》](https://yourdomain.com/privacy.html)和[《用户协议》](https://yourdomain.com/agreement.html)。\n\n我们将在获得您的明确同意后，收集必要的设备信息、网络信息等，并初始化相关第三方 SDK 以提供服务。")
                    .font(.body)
                    .tint(.blue)
                    .environment(\.openURL, OpenURLAction { url in
                        self.webUrlToShow = url
                        return .handled
                    })

                HStack(spacing: 40) {
                    Button(action: onDisagree) {
                        Text("暂不同意/退出")
                            .foregroundColor(.gray)
                    }

                    Button(action: onAgree) {
                        Text("同 意")
                            .bold()
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                    }
                }
            }
            .padding(24)
            .background(Color(UIColor.systemBackground))
            .cornerRadius(16)
            .padding(32)
        }
        .sheet(item: Binding<URL?>(
            get: { webUrlToShow },
            set: { webUrlToShow = $0 }
        )) { url in
            NavigationView {
                InlineWebView(url: url)
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
}

// Wrapper for URL to be Identifiable
extension URL: Identifiable {
    public var id: String { self.absoluteString }
}
