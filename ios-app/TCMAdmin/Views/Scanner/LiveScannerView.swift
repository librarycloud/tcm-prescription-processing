import SwiftUI
import AVFoundation
import CoreImage
import UIKit

final class SharedCameraManager {
    static let shared = SharedCameraManager()
    
    let sessionQueue = DispatchQueue(label: "com.tcm.cameraqueue")
    var captureSession: AVCaptureSession?
    var metadataOutput: AVCaptureMetadataOutput?
    var videoDataOutput: AVCaptureVideoDataOutput?
    var isConfigured = false
    
    private init() {}
    
    func preload() {
        sessionQueue.async {
            guard !self.isConfigured else { return }
            
            let session = AVCaptureSession()
            session.beginConfiguration()
            
            if session.canSetSessionPreset(.hd1920x1080) {
                session.sessionPreset = .hd1920x1080
            }
            
            let deviceTypes: [AVCaptureDevice.DeviceType] = [
                .builtInDualWideCamera,
                .builtInTripleCamera,
                .builtInDualCamera,
                .builtInWideAngleCamera
            ]
            let discovery = AVCaptureDevice.DiscoverySession(deviceTypes: deviceTypes, mediaType: .video, position: .back)
            
            guard let videoDevice = discovery.devices.first ?? AVCaptureDevice.default(for: .video),
                  let videoInput = try? AVCaptureDeviceInput(device: videoDevice),
                  session.canAddInput(videoInput) else {
                session.commitConfiguration()
                return
            }
            
            do {
                try videoDevice.lockForConfiguration()
                if videoDevice.isAutoFocusRangeRestrictionSupported {
                    videoDevice.autoFocusRangeRestriction = .near
                }
                if videoDevice.isFocusModeSupported(.continuousAutoFocus) {
                    videoDevice.focusMode = .continuousAutoFocus
                }
                if videoDevice.isExposureModeSupported(.continuousAutoExposure) {
                    videoDevice.exposureMode = .continuousAutoExposure
                }
                videoDevice.unlockForConfiguration()
            } catch {
                print("Failed to optimize camera focus: \(error)")
            }
            
            session.addInput(videoInput)
            
            let mOutput = AVCaptureMetadataOutput()
            if session.canAddOutput(mOutput) {
                session.addOutput(mOutput)
                mOutput.metadataObjectTypes = [
                    .qr, .ean13, .ean8, .code128, .code39, .upce
                ]
                self.metadataOutput = mOutput
            }
            
            let vOutput = AVCaptureVideoDataOutput()
            vOutput.videoSettings = [(kCVPixelBufferPixelFormatTypeKey as String): Int(kCVPixelFormatType_32BGRA)]
            vOutput.alwaysDiscardsLateVideoFrames = true
            if session.canAddOutput(vOutput) {
                session.addOutput(vOutput)
                self.videoDataOutput = vOutput
            }
            
            session.commitConfiguration()
            self.captureSession = session
            self.isConfigured = true
            #if DEBUG
            print("AVCaptureSession globally pre-configured")
            #endif
        }
    }
    
    func start() {
        sessionQueue.async {
            self.captureSession?.startRunning()
        }
    }
    
    func stop() {
        sessionQueue.async {
            self.captureSession?.stopRunning()
        }
    }
}

@MainActor
final class SharedOCRManager {
    static let shared = SharedOCRManager()
    
    var engine: OCREngine?
    var isLoading: Bool = false
    private var isLoaded: Bool = false
    
    private init() {}
    
    func preload() {
        guard !isLoaded && !isLoading else { return }
        isLoading = true
        
        Task {
            do {
                let sessionManager = ORTSessionManager()
                var tuning = ORTSessionTuningOptions.default
                tuning.intraOpThreads = 4
                try await sessionManager.loadModels(executionProvider: .cpu, tuning: tuning)
                let engine = try OCREngine(sessionManager: sessionManager)
                
                await MainActor.run {
                    self.engine = engine
                    self.isLoaded = true
                    self.isLoading = false
                    #if DEBUG
                    print("PaddleOCR engine loaded globally (CPU EP with 4 threads)")
                    #endif
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    print("Failed to preload PaddleOCR engine globally: \(error)")
                }
            }
        }
    }
}

@MainActor
public struct LiveScannerView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable private var router = Router.shared
    
    @State private var isTorchOn = false
    @State private var isResolving = false
    @State private var showLoadingOverlay = false
    @State private var resolvingMessage = "正在识别条码..."
    @State private var equipmentAlertData: EquipmentModel? = nil
    @State private var scanError: String? = nil
    
    var enableOCR: Bool = false
    
    public init(enableOCR: Bool = false) { self.enableOCR = enableOCR }
    
    public var body: some View {
        ZStack {
            // 相机未就绪时，纯黑背景防止白条/灰色透出
            Color.black.ignoresSafeArea()
            
            // 相机层
            BarcodeScannerPreview(torchOn: isTorchOn, enableOCR: enableOCR) { code in
                handleScannedCode(code)
            }
            .ignoresSafeArea()
            
            // 扫描瞄准取景框
            VStack {
                HStack {
                    Button(action: {
                        // Update the cover binding explicitly; this still works when the
                        // presentation is owned by Router rather than the local dismiss action.
                        router.isScannerPresented = false
                        dismiss()
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .scaledFont(32)
                            .foregroundStyle(Color.white.opacity(0.8))
                            .padding(16)
                            .contentShape(Rectangle())
                    }
                    .padding(.leading, 4)
                    .padding(.top, 4)
                    
                    Spacer()
                    
                    Button(action: { isTorchOn.toggle() }) {
                        Image(systemName: isTorchOn ? "flashlight.on.fill" : "flashlight.off.fill")
                            .scaledFont(24)
                            .foregroundStyle(isTorchOn ? Color.yellow : Color.white)
                            .padding(10)
                            .background(Color.black.opacity(0.4))
                            .clipShape(Circle())
                    }
                    .padding(.trailing, 20)
                    .padding(.top, 20)
                }
                
                Spacer()
                
                if enableOCR {
                    // OCR 模式：显示 320x180 瞄准框与局部镂空遮罩
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.appPrimary, lineWidth: 3)
                            .frame(width: 320, height: 180)
                        
                        // 扫描线
                        Rectangle()
                            .fill(Color.appPrimary.opacity(0.2))
                            .frame(width: 300, height: 2)
                            .shadow(color: .appPrimary, radius: 4)
                    }
                    .background(
                        // 全屏遮罩带中间镂空
                        Color.black.opacity(0.55)
                            .frame(width: 4000, height: 4000)
                            .mask(
                                Rectangle().fill(Color.white)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 16)
                                            .frame(width: 320, height: 180)
                                            .blendMode(.destinationOut)
                                    )
                                    .compositingGroup()
                            )
                            .allowsHitTesting(false)
                    )
                    
                    Text("请将文字/条码放入框内")
                        .scaledFont(14, weight: .medium)
                        .foregroundStyle(Color.white.opacity(0.9))
                        .padding(.top, 24)
                } else {
                    // 纯扫码模式：全屏扫描，不加任何遮罩
                    Text("将条码/二维码放入屏幕内即可自动识别")
                        .scaledFont(14, weight: .medium)
                        .foregroundStyle(Color.white.opacity(0.9))
                        .padding(.top, 24)
                }
                
                Spacer()
                Spacer()
            }
            
            // 解析中遮罩弹窗
            if showLoadingOverlay {
                Color.black.opacity(0.5).ignoresSafeArea()
                
                VStack(spacing: 16) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .appPrimary))
                        .scaleEffect(1.3)
                    Text(resolvingMessage)
                        .scaledFont(15, weight: .medium)
                        .foregroundStyle(Color.ink)
                }
                .padding(24)
                .background(Color.surface)
                .clipShape(.rect(cornerRadius: 12))
                .shadow(radius: 10)
            }
        }
        .alert("设备信息", item: $equipmentAlertData) { equip in
            Button("前往加工管理") {
                dismiss()
                // 切换到加工管理 Tab
            }
            Button("关闭", role: .cancel) {}
        } message: { equip in
            Text("名称：\(equip.name)\n设备类型：\(equip.typeName ?? "通用设备")\n当前状态：\(equip.status == 1 ? "正常空闲" : "使用中")")
        }
        .alert("扫描提示", isPresented: Binding(get: { scanError != nil }, set: { if !$0 { scanError = nil } })) {
            Button("我知道了", role: .cancel) {}
        } message: {
            Text(scanError ?? "")
        }
        .onDisappear {
            router.isScannerPresented = false
        }
        .preferredColorScheme(.dark)
        .statusBarHidden(true)
    }
    
    private func closeScanner() {
        router.isScannerPresented = false
        dismiss()
    }
    
    // MARK: - 核心 4 路分发路由 (1:1 还原 Android MainActivity.kt 扫码逻辑)
    private func handleScannedCode(_ rawCode: String) {
        let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty, !isResolving else { return }
        
        // 触感反馈（唯一触发点，由 isResolving 保证只振动一次）
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        
        if let onScanned = router.scannerOnScanned {
            closeScanner()
            onScanned(code)
            router.scannerOnScanned = nil
            return
        }
        
        // 1. 取货码核销: TCM:PICKUP:1:xxxx (纯本地路由跳转，无需网络动画)
        if code.hasPrefix("TCM:PICKUP:1:") {
            isResolving = true // 只是防抖
            closeScanner()
            router.navigate(to: .packageVerify(initialCode: code))
            return
        }
        
        // 4. 其余所有扫码（药品条形码、商品SKU）-> 进入库存查询 (由库存页面自己负责展示骨架屏加载动画)
        if !code.hasPrefix("TCM:PLAN:1:") && !code.hasPrefix("TCM:EQUIPMENT:1:") {
            isResolving = true // 只是防抖
            closeScanner()
            NotificationCenter.default.post(name: NSNotification.Name("SearchInventoryByBarcode"), object: code)
            return
        }
        
        // 对于真正需要网络请求等待的（计划/设备），才弹出黑色的 Loading 遮罩
        isResolving = true
        showLoadingOverlay = true
        resolvingMessage = "正在查询..."
        
        Task {
            // 2. 加工计划二维码: TCM:PLAN:1:xxxx
            if code.hasPrefix("TCM:PLAN:1:") {
                await MainActor.run { resolvingMessage = "正在定位加工计划..." }
                do {
                    let plan = try await ApiClient.shared.fetchProcessingPlanByScan(code: code)
                    await MainActor.run {
                        isResolving = false
                        showLoadingOverlay = false
                        closeScanner()
                        router.navigate(to: .workflowOperation(planId: plan.id, planCode: plan.planCode))
                    }
                } catch {
                    await MainActor.run {
                        isResolving = false
                        showLoadingOverlay = false
                        scanError = "未找到对应加工计划"
                    }
                }
                return
            }
            
            // 3. 设备二维码: TCM:EQUIPMENT:1:xxxx
            if code.hasPrefix("TCM:EQUIPMENT:1:") {
                await MainActor.run { resolvingMessage = "正在查询设备..." }
                do {
                    if let equip = try await ApiClient.shared.fetchEquipmentByCode(code: code) {
                        await MainActor.run {
                            isResolving = false
                            showLoadingOverlay = false
                            equipmentAlertData = equip
                        }
                    } else {
                        // 如果没查到，给一个兜底以避免卡住，或者给个提示(这里沿用Alert展示错误)
                        await MainActor.run {
                            isResolving = false
                            showLoadingOverlay = false
                            equipmentAlertData = EquipmentModel(id: 0, name: "设备未找到", typeName: "未知", equipmentNo: code, status: 0, currentUsage: nil)
                        }
                    }
                } catch {
                    await MainActor.run {
                        isResolving = false
                        showLoadingOverlay = false
                        equipmentAlertData = EquipmentModel(id: 0, name: "查询失败", typeName: error.localizedDescription, equipmentNo: code, status: 0, currentUsage: nil)
                    }
                }
                return
            }
        }
    }
}

// MARK: - AVFoundation 相机底层实现
struct BarcodeScannerPreview: UIViewControllerRepresentable {
    var torchOn: Bool
    var enableOCR: Bool = false
    var onScanned: (String) -> Void
    
    func makeUIViewController(context: Context) -> BarcodeScannerViewController {
        let vc = BarcodeScannerViewController()
        vc.enableOCR = enableOCR
        vc.onScanned = onScanned
        return vc
    }
    
    func updateUIViewController(_ uiViewController: BarcodeScannerViewController, context: Context) {
        uiViewController.setTorch(on: torchOn)
    }
    
    static func dismantleUIViewController(_ uiViewController: BarcodeScannerViewController, coordinator: ()) {
        uiViewController.stopScanner()
    }
}

class BarcodeScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onScanned: ((String) -> Void)?
    var enableOCR: Bool = false
    private var captureSession: AVCaptureSession?
    // 统一使用 SharedCameraManager 的队列，避免多队列竞争同一个 session 导致死锁/卡住
    private var sessionQueue: DispatchQueue { SharedCameraManager.shared.sessionQueue }
    private var previewLayer: AVCaptureVideoPreviewLayer?
    nonisolated(unsafe) private var hasScanned = false
    private let scanStateQueue = DispatchQueue(label: "com.tcm.camera.scan-state")
    nonisolated(unsafe) private var hasFadedIn = false
    nonisolated(unsafe) private let scannerOpenTime = Date()
    nonisolated(unsafe) private var lastOcrScanTime: Date = Date.distantPast
    nonisolated(unsafe) private var consecutiveEmptyFrames: Int = 0
    nonisolated(unsafe) private var lastOcrResult: String? = nil
    nonisolated(unsafe) private var ocrMatchCount: Int = 0
    /// 对标 Android ocrInFlight：防止单次推理 > 500ms 时任务叠加
    nonisolated(unsafe) private var ocrInFlight: Bool = false
    nonisolated(unsafe) private var isStopped = false

    // PaddleOCR Engine
    nonisolated(unsafe) private var ocrEngine: OCREngine?
    private var isOcrEngineLoading = false
    // 复用 CIContext，创建代价极高，绝对不能每帧 new 一个
    private let ciContext = CIContext(options: [.useSoftwareRenderer: false])

    // MARK: - Scan state (被误删的原始实现)

    @discardableResult
    func claimScan() -> Bool {
        var claimed = false
        scanStateQueue.sync {
            if !hasScanned { hasScanned = true; claimed = true }
        }
        return claimed
    }

    func releaseScan() {
        scanStateQueue.sync { hasScanned = false }
    }

    nonisolated func isScanClaimed() -> Bool {
        scanStateQueue.sync { hasScanned }
    }

    // MARK: - Camera control

    func setTorch(on: Bool) {
        guard let device = AVCaptureDevice.default(for: .video), device.hasTorch else { return }
        try? device.lockForConfiguration()
        device.torchMode = on ? .on : .off
        device.unlockForConfiguration()
    }

    func stopScanner() {
        isStopped = true
        ocrInFlight = false
        scanStateQueue.sync { hasScanned = false }
        sessionQueue.async { [weak self] in
            guard let self else { return }
            self.captureSession?.stopRunning()
            self.captureSession?.outputs.forEach { output in
                if let videoOutput = output as? AVCaptureVideoDataOutput {
                    videoOutput.setSampleBufferDelegate(nil, queue: nil)
                }
                if let metadataOutput = output as? AVCaptureMetadataOutput {
                    metadataOutput.setMetadataObjectsDelegate(nil, queue: nil)
                }
            }
        }
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
    }
    
    private func setupCamera() {
        isStopped = false
        if enableOCR {
            Task { @MainActor [weak self] in
                guard let self = self else { return }
                if SharedOCRManager.shared.engine == nil {
                    self.isOcrEngineLoading = true
                    SharedOCRManager.shared.preload()
                    while SharedOCRManager.shared.engine == nil && SharedOCRManager.shared.isLoading {
                        try? await Task.sleep(nanoseconds: 100_000_000)
                    }
                }
                self.ocrEngine = SharedOCRManager.shared.engine
                self.isOcrEngineLoading = false
            }
        }
        
        SharedCameraManager.shared.sessionQueue.async { [weak self] in
            guard let self = self else { return }
            
            // Wait for configuration to complete if it's still running
            while !SharedCameraManager.shared.isConfigured {
                Thread.sleep(forTimeInterval: 0.05)
            }
            
            guard let session = SharedCameraManager.shared.captureSession else { return }
            
            // Re-bind delegates for the current scanner instance
            if let mOutput = SharedCameraManager.shared.metadataOutput {
                mOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            }
            if let vOutput = SharedCameraManager.shared.videoDataOutput {
                let videoQueue = DispatchQueue(label: "com.tcm.videoqueue", qos: .userInteractive)
                // 强制绑定 delegate，以便捕获第一帧进行淡入动画
                vOutput.setSampleBufferDelegate(self, queue: videoQueue)
            }
            
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                
                let preview = AVCaptureVideoPreviewLayer(session: session)
                preview.videoGravity = .resizeAspectFill
                preview.frame = self.view.layer.bounds
                preview.opacity = 0 // 初始透明度为 0，等第一帧到来时淡入
                self.view.layer.insertSublayer(preview, at: 0)
                
                self.previewLayer = preview
                self.captureSession = session
                
                SharedCameraManager.shared.start()
            }
        }
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }
    
    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let metadataObj = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let stringValue = metadataObj.stringValue else {
            return
        }
        guard claimScan() else { return }
        onScanned?(stringValue)
        
        // 延迟 1.5 秒后允许下一次扫描，避免重复触发
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            self?.releaseScan()
        }
    }
    


    nonisolated private static let tokenCharPattern = try! NSRegularExpression(pattern: #"[\s:：#\-_/|]+"#)
    nonisolated private static let skuLabelRegex = try! NSRegularExpression(pattern: #"(?i)(?:^|[^a-zA-Z0-9\x{4e00}-\x{9fa5}])(?:SKU|SHU|SU|5KU|5HU|5U|S0|SK0|SH0|SK|SH|KU|HU|编号|编码|商品码|批号|货号)(?::|：|#|\s|$)"#)
    // Swift ICU正则中中文算word字符，\b无法匹配“中文+数字”的边界。改用负向前瞻/后顾
    nonisolated private static let candidate9Pattern = try! NSRegularExpression(pattern: #"(?i)(?<![0-9A-Za-z])[0-9A-Za-z|!〇\s.\-_]{8,24}(?![0-9A-Za-z])"#)
    nonisolated private static let standalone9Pattern = try! NSRegularExpression(pattern: #"(?<!\d)\d{9}(?!\d)"#)
    nonisolated private static let excludeLinePattern = try! NSRegularExpression(pattern: "(?i)(phone|tel|电话|联系|日期|date|time|时间|网点|门店)")


    private struct LogicalRow {
        let text: String
        let top: Float
        let centerY: Float
    }

    nonisolated private func buildLogicalRows(_ elements: [OCRResult]) -> [String] {
        if elements.isEmpty { return [] }
        
        struct RawElement {
            let text: String
            let left: Float
            let top: Float
            let height: Float
            let centerY: Float
        }
        
        let rawElements = elements.map { res -> RawElement in
            let p = res.polygon
            let ys = p.map { Float($0[1]) }
            let xs = p.map { Float($0[0]) }
            let top = ys.min() ?? 0
            let bottom = ys.max() ?? 0
            let left = xs.min() ?? 0
            let height = bottom - top
            let centerY = top + height / 2.0
            return RawElement(text: normalizeOcrText(res.text), left: left, top: top, height: height, centerY: centerY)
        }.sorted { $0.centerY < $1.centerY }
        
        var clusters: [[RawElement]] = []
        for elem in rawElements {
            var matched = false
            for i in 0..<clusters.count {
                let cluster = clusters[i]
                let avgCenterY = cluster.map { $0.centerY }.reduce(0, +) / Float(cluster.count)
                let avgHeight = cluster.map { $0.height }.reduce(0, +) / Float(cluster.count)
                let threshold = max(avgHeight, elem.height) * 0.75
                if abs(elem.centerY - avgCenterY) <= threshold {
                    clusters[i].append(elem)
                    matched = true
                    break
                }
            }
            if !matched {
                clusters.append([elem])
            }
        }
        
        let rows = clusters.map { cluster -> LogicalRow in
            let sortedCluster = cluster.sorted { $0.left < $1.left }
            let joinedText = sortedCluster.map { $0.text }.joined(separator: " ")
            let avgTop = cluster.map { $0.top }.min() ?? 0
            let avgCenterY = cluster.map { $0.centerY }.reduce(0, +) / Float(cluster.count)
            return LogicalRow(text: joinedText, top: avgTop, centerY: avgCenterY)
        }.sorted { $0.top < $1.top }
        
        return rows.map { $0.text }
    }

    nonisolated private func extractSku(from texts: [String]) -> String? {
        let tokenCharPattern = Self.tokenCharPattern
        let skuLabelRegex = Self.skuLabelRegex
        let candidate9Pattern = Self.candidate9Pattern
        let standalone9Pattern = Self.standalone9Pattern
        let excludeLinePattern = Self.excludeLinePattern

        var skuCandidates: [String] = []

        for (i, rawLine) in texts.enumerated() {
            // 1. 带标签匹配
            if skuLabelRegex.firstMatch(in: rawLine, range: NSRange(rawLine.startIndex..., in: rawLine)) != nil {
                let afterLabel = skuLabelRegex.stringByReplacingMatches(in: rawLine, range: NSRange(rawLine.startIndex..., in: rawLine), withTemplate: " ")
                let cleaned = cleanDigits(afterLabel)
                if cleaned.count == 9 { skuCandidates.append(cleaned) }
                let matches = candidate9Pattern.matches(in: afterLabel, range: NSRange(afterLabel.startIndex..., in: afterLabel))
                for match in matches {
                    let c = cleanDigits((afterLabel as NSString).substring(with: match.range))
                    if c.count == 9 { skuCandidates.append(c) }
                }
                // 向下看 1-2 行
                for offset in 1...2 {
                    guard i + offset < texts.count else { break }
                    let next = texts[i + offset]
                    if excludeLinePattern.firstMatch(in: next, range: NSRange(next.startIndex..., in: next)) != nil { continue }
                    let nc = cleanDigits(next)
                    if nc.count == 9 { skuCandidates.append(nc) }
                    for m in candidate9Pattern.matches(in: next, range: NSRange(next.startIndex..., in: next)) {
                        let c = cleanDigits((next as NSString).substring(with: m.range))
                        if c.count == 9 { skuCandidates.append(c) }
                    }
                }
            }
        }

        // 2. 独立纯 9 位数字（兜底）
        if skuCandidates.isEmpty {
            for rawLine in texts {
                if excludeLinePattern.firstMatch(in: rawLine, range: NSRange(rawLine.startIndex..., in: rawLine)) != nil { continue }
                let collapsed = tokenCharPattern.stringByReplacingMatches(in: rawLine, range: NSRange(rawLine.startIndex..., in: rawLine), withTemplate: "")
                if (try? NSRegularExpression(pattern: #"\d{10,}"#))?.firstMatch(in: collapsed, range: NSRange(collapsed.startIndex..., in: collapsed)) != nil { continue }
                for m in standalone9Pattern.matches(in: collapsed, range: NSRange(collapsed.startIndex..., in: collapsed)) {
                    skuCandidates.append((collapsed as NSString).substring(with: m.range))
                }
                for m in candidate9Pattern.matches(in: collapsed, range: NSRange(collapsed.startIndex..., in: collapsed)) {
                    let c = cleanDigits((collapsed as NSString).substring(with: m.range))
                    if c.count == 9 { skuCandidates.append(c) }
                }
            }
        }

        return skuCandidates.first(where: { $0.count == 9 })
    }


    nonisolated private func normalizeOcrText(_ text: String) -> String {
        var res = ""
        for char in text {
            if char == "\u{3000}" || char == "\u{00A0}" {
                res.append(" ")
            } else if char == "〇" {
                res.append("0")
            } else if let scalar = char.unicodeScalars.first, scalar.value >= 0xFF10 && scalar.value <= 0xFF19 {
                // Fullwidth numbers ０..９
                res.append(Character(UnicodeScalar(scalar.value - 0xFF10 + 0x0030)!))
            } else if let scalar = char.unicodeScalars.first, scalar.value >= 0xFF21 && scalar.value <= 0xFF3A {
                // Fullwidth letters Ａ..Ｚ
                res.append(Character(UnicodeScalar(scalar.value - 0xFF21 + 0x0041)!))
            } else if let scalar = char.unicodeScalars.first, scalar.value >= 0xFF41 && scalar.value <= 0xFF5A {
                // Fullwidth letters ａ..ｚ
                res.append(Character(UnicodeScalar(scalar.value - 0xFF41 + 0x0061)!))
            } else {
                res.append(char)
            }
        }
        return res
    }

    nonisolated private func cleanDigits(_ str: String) -> String {
        var res = ""
        for char in str {
            switch char {
            case "O", "o", "C", "c", "D", "d", "Q", "q", "〇": res.append("0")
            case "I", "l", "|", "i", "!", "J", "j", "L": res.append("1")
            case "Z", "z": res.append("2")
            case "E": res.append("3")
            case "S", "s", "$": res.append("5")
            case "b", "G": res.append("6")
            case "T", "t": res.append("7")
            case "B", "R", "r": res.append("8")
            case "g": res.append("9")
            default:
                if char.isNumber { res.append(char) }
            }
        }
        return res
    }

    // MARK: - OCR Video Frame Extraction
    nonisolated func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        if !hasFadedIn {
            hasFadedIn = true
            DispatchQueue.main.async {
                guard let layer = self.previewLayer else { return }
                CATransaction.begin()
                CATransaction.setAnimationDuration(0.12)
                layer.opacity = 1.0
                CATransaction.commit()
            }
        }
        
        guard enableOCR else { return }
        
        autoreleasepool {
            guard !isScanClaimed() else { return }
            guard !isStopped else { return }

            let now = Date()
            
            // 等待页面完全弹出后再开始 OCR 推理 (0.15s 动画期间不吃 CPU，保障页面顺滑)
            guard now.timeIntervalSince(scannerOpenTime) > 0.15 else { return }
            
            // --- 动态自适应限流 (对标 Android) ---
            let throttleMs: TimeInterval
            if consecutiveEmptyFrames >= 6 {
                throttleMs = 0.250 // 空白视野：主动拉长至 250ms
            } else if consecutiveEmptyFrames >= 3 {
                throttleMs = 0.120 // 过渡阶段
            } else {
                throttleMs = 0.0   // 发现目标文字：满速识别，零延迟响应
            }
            guard now.timeIntervalSince(lastOcrScanTime) >= throttleMs else { return }
            // 对标 Android ocrInFlight：推理未结束时跳过本帧，防止任务叠加
            guard !ocrInFlight else { return }
            lastOcrScanTime = now

            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
            guard let engine = self.ocrEngine else {
                #if DEBUG
                print("PaddleOCR frame skipped: engine is not ready")
                #endif
                return
            }

            // --- ROI 裁剪：只送取景框区域入模型（对标 Android restrictScanningToRect）---
            let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
            // 手机竖屏时相机帧是横向的，先右转 90 度变成竖屏 (比如 1080x1920)
            let rotated = ciImage.oriented(.right)
            // oriented 后 origin 可能为负，必须平移回 (0,0) 才能正常计算 crop
            let translated = rotated.transformed(by: CGAffineTransform(translationX: -rotated.extent.origin.x, y: -rotated.extent.origin.y))
            
            let frameW = translated.extent.width   // 竖屏宽，约 1080
            let frameH = translated.extent.height  // 竖屏高，约 1920
            
            // 取景框 ROI：横向居中占 80%，纵向偏上占 60%
            let roiX = frameW * 0.1
            let roiY = frameH * 0.2
            let roiW = frameW * 0.8
            let roiH = frameH * 0.6
            let roiRect = CGRect(x: roiX, y: roiY, width: roiW, height: roiH)
            
            let cropped = translated.cropped(to: roiRect)
            
            // --- 终极核弹级优化：在交由 CPU/GPU 渲染和推理前，直接把图像长宽缩小一半 (面积缩小 4 倍) ---
            // 这保证了即使底层 YAML 配置没生效，送入 OCR 引擎的图像也只有 432x576，彻底告别 1.3秒的漫长推理！
            let scaled = cropped.transformed(by: CGAffineTransform(scaleX: 0.5, y: 0.5))
            
            guard let cgImage = ciContext.createCGImage(scaled, from: scaled.extent) else { return }

            ocrInFlight = true
            #if DEBUG
            print("PaddleOCR inference started: \(cgImage.width)x\(cgImage.height)")
            #endif
            Task.detached(priority: .userInitiated) { [weak self] in
                guard let self = self else { return }
                defer { 
                    Task { @MainActor in
                        self.ocrInFlight = false 
                    }
                }
            guard !self.isScanClaimed() else { return }

            do {
                let result = try await engine.run(cgImage) { [weak self] results in
                    guard let self = self else { return false }
                    return self.extractSku(from: results.map { $0.text }) != nil
                }
                let texts = result.results.map(\.text)
                
                #if DEBUG
                print("PaddleOCR inference finished: total=\(Int(result.totalTime * 1000))ms det=\(Int(result.detectionTime * 1000))ms rec=\(Int(result.recognitionTime * 1000))ms text=\(texts)")
                #endif
                
                if texts.isEmpty {
                    self.consecutiveEmptyFrames += 1
                    return
                } else {
                    self.consecutiveEmptyFrames = 0
                }
                
                guard let sku = self.extractSku(from: texts) else {
                    DispatchQueue.main.async {
                        self.ocrMatchCount = 0
                        self.lastOcrResult = nil
                    }
                    return
                }

                DispatchQueue.main.async {
                    if sku == self.lastOcrResult {
                        self.ocrMatchCount += 1
                    } else {
                        self.lastOcrResult = sku
                        self.ocrMatchCount = 1
                    }
                    if self.ocrMatchCount >= 2 {
                        guard self.claimScan() else { return }
                        let finalSku = sku
                        DispatchQueue.main.async {
                            self.onScanned?(finalSku)
                        }
                        self.lastOcrResult = nil
                        self.ocrMatchCount = 0
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                            self.releaseScan()
                        }
                    }
                }
            } catch {
                print("PaddleOCR engine run failed: \(error.localizedDescription)")
            }
        }
        }
    }
}
