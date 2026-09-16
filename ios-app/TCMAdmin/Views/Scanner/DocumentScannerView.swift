import SwiftUI
import VisionKit
import Vision

// MARK: - OCR 解析服务 (原生 Vision)
class OCRService {
    static let shared = OCRService()
    
    /// 从多张图片中提取文字
    func recognizeText(from images: [UIImage], completion: @escaping ([String]) -> Void) {
        DispatchQueue.global(qos: .userInitiated).async {
            var extractedTexts: [String] = []
            
            for image in images {
                guard let cgImage = image.cgImage else { continue }
                
                // 配置文字识别请求
                let request = VNRecognizeTextRequest { (request, error) in
                    guard error == nil,
                          let observations = request.results as? [VNRecognizedTextObservation] else {
                        return
                    }
                    
                    // 将一页纸里的多行文字拼接到一起
                    let recognizedStrings = observations.compactMap { observation in
                        // 取置信度最高的候选结果
                        observation.topCandidates(1).first?.string
                    }
                    
                    let pageText = recognizedStrings.joined(separator: "\n")
                    extractedTexts.append(pageText)
                }
                
                // 设置识别参数
                request.recognitionLevel = .accurate // 追求高精度
                request.usesLanguageCorrection = true // 使用语言纠错
                
                // iOS 14+ 必须明确指定支持中文
                if #available(iOS 14.0, *) {
                    request.recognitionLanguages = ["zh-Hans", "zh-Hant", "en-US"]
                }
                
                // 执行请求
                let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
                try? handler.perform([request])
            }
            
            DispatchQueue.main.async {
                completion(extractedTexts)
            }
        }
    }
}

// MARK: - iOS 原生文档扫描器 UI (VisionKit)
/// 这个 View 打开后会自动启用相机，寻找白纸/处方单的边缘并自动裁剪
struct DocumentScannerView: UIViewControllerRepresentable {
    var onCancel: () -> Void
    var onScanCompleted: ([String]) -> Void
    var onError: (Error) -> Void
    
    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let scannerViewController = VNDocumentCameraViewController()
        scannerViewController.delegate = context.coordinator
        return scannerViewController
    }
    
    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        var parent: DocumentScannerView
        
        init(_ parent: DocumentScannerView) {
            self.parent = parent
        }
        
        // 成功扫描到纸张
        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            // 将扫描到的页面转为 UIImage 数组
            var scannedImages: [UIImage] = []
            for pageIndex in 0..<scan.pageCount {
                let image = scan.imageOfPage(at: pageIndex)
                scannedImages.append(image)
            }
            
            // 拿到图片后，立刻调用 Vision OCR 进行识别
            OCRService.shared.recognizeText(from: scannedImages) { recognizedTexts in
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                self.parent.onScanCompleted(recognizedTexts)
            }
        }
        
        // 用户点击取消
        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            parent.onCancel()
        }
        
        // 出现错误
        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            parent.onError(error)
        }
    }
}
