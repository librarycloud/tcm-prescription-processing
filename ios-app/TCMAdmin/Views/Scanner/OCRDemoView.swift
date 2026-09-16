import SwiftUI

@MainActor
struct OCRDemoView: View {
    @State private var isShowingScanner = false
    @State private var recognizedText: String = "扫描结果将显示在这里..."
    @State private var isProcessing = false
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "text.viewfinder")
                .font(.system(size: (60) * ThemeManager.shared.fontScale))
                .foregroundColor(.appPrimary)
                .padding(.top, 40)
            
            Text("智能处方 OCR 识别")
                .font(.title2)
                .bold()
            
            Text("使用 Apple 原生 Vision 技术，无需联网，直接在设备上高速识别处方单或发票。")
                .font(.subheadline)
                .foregroundColor(.muted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            
            Spacer()
            
            // 结果展示区
            ScrollView {
                Text(recognizedText)
                    .font(.system(size: (14) * ThemeManager.shared.fontScale))
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color.surface)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.cardBorder, lineWidth: 1)
            )
            .padding(.horizontal)
            
            Spacer()
            
            // 扫描按钮
            Button(action: {
                isShowingScanner = true
            }) {
                HStack {
                    if isProcessing {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                        Text("正在解析文字...")
                    } else {
                        Image(systemName: "camera.viewfinder")
                        Text("拍照识别处方单")
                    }
                }
                .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(Color.appPrimary)
                .cornerRadius(12)
                .padding(.horizontal)
            }
            .disabled(isProcessing)
            .padding(.bottom, 20)
        }
        .background(Color.pageBackground.ignoresSafeArea())
        .sheet(isPresented: $isShowingScanner) {
            DocumentScannerView(
                onCancel: {
                    isShowingScanner = false
                },
                onScanCompleted: { texts in
                    isShowingScanner = false
                    isProcessing = true
                    
                    // 模拟处理动画以提升体验，实际上 Vision 会在后台极快完成
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        isProcessing = false
                        recognizedText = texts.joined(separator: "\n\n---\n\n")
                        if recognizedText.isEmpty {
                            recognizedText = "未能识别出任何文字内容。"
                        }
                    }
                },
                onError: { error in
                    isShowingScanner = false
                    print("Scanner error: \(error.localizedDescription)")
                }
            )
            .ignoresSafeArea()
        }
    }
}

struct OCRDemoView_Previews: PreviewProvider {
    static var previews: some View {
        OCRDemoView()
    }
}
