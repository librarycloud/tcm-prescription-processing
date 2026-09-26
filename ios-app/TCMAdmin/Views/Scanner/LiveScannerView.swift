import SwiftUI
import AVFoundation
import Vision

@MainActor
public struct LiveScannerView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable private var router = Router.shared
    
    @State private var isTorchOn = false
    @State private var isResolving = false
    @State private var resolvingMessage = "正在识别条码..."
    @State private var equipmentAlertData: EquipmentModel? = nil
    @State private var scanError: String? = nil
    
    var enableOCR: Bool = false
    
    public init(enableOCR: Bool = false) { self.enableOCR = enableOCR }
    
    public var body: some View {
        ZStack {
            // 相机层
            BarcodeScannerPreview(torchOn: isTorchOn, enableOCR: enableOCR) { code in
                handleScannedCode(code)
            }
            .ignoresSafeArea()
            
            // 扫描瞄准取景框
            VStack {
                HStack {
                    Button(action: { dismiss() }) {
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
                
                // 瞄准框
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.appPrimary, lineWidth: 3)
                        .frame(width: 260, height: 260)
                    
                    Rectangle()
                        .fill(Color.appPrimary.opacity(0.2))
                        .frame(width: 240, height: 2)
                        .shadow(color: .appPrimary, radius: 4)
                }
                
                Text("将条码/二维码放入框内即可自动识别")
                    .scaledFont(14, weight: .medium)
                    .foregroundStyle(Color.white.opacity(0.9))
                    .padding(.top, 24)
                
                Spacer()
                Spacer()
            }
            
            // 解析中遮罩弹窗
            if isResolving {
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
    }
    
    // MARK: - 核心 4 路分发路由 (1:1 还原 Android MainActivity.kt 扫码逻辑)
    private func handleScannedCode(_ rawCode: String) {
        let code = rawCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !code.isEmpty, !isResolving else { return }
        
        // 触感反馈（唯一触发点，由 isResolving 保证只振动一次）
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        
        

        if let onScanned = router.scannerOnScanned {
            dismiss()
            onScanned(code)
            router.scannerOnScanned = nil
            return
        }
        
        isResolving = true
        resolvingMessage = "正在识别条码..."
        
        Task {
            // 1. 取货码核销: TCM:PICKUP:1:xxxx
            if code.hasPrefix("TCM:PICKUP:1:") {
                await MainActor.run {
                    isResolving = false
                    dismiss()
                    router.navigate(to: .packageVerify(initialCode: code))
                }
                return
            }
            
            // 2. 加工计划二维码: TCM:PLAN:1:xxxx
            if code.hasPrefix("TCM:PLAN:1:") {
                await MainActor.run { resolvingMessage = "正在定位加工计划..." }
                do {
                    let plan = try await ApiClient.shared.fetchProcessingPlanByScan(code: code)
                    await MainActor.run {
                        isResolving = false
                        dismiss()
                        router.navigate(to: .workflowOperation(planId: plan.id, planCode: plan.planCode))
                    }
                } catch {
                    await MainActor.run {
                        isResolving = false
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
                            equipmentAlertData = equip
                        }
                    } else {
                        // 如果没查到，给一个兜底以避免卡住，或者给个提示(这里沿用Alert展示错误)
                        await MainActor.run {
                            isResolving = false
                            equipmentAlertData = EquipmentModel(id: 0, name: "设备未找到", typeName: "未知", equipmentNo: code, status: 0, currentUsage: nil)
                        }
                    }
                } catch {
                    await MainActor.run {
                        isResolving = false
                        equipmentAlertData = EquipmentModel(id: 0, name: "查询失败", typeName: error.localizedDescription, equipmentNo: code, status: 0, currentUsage: nil)
                    }
                }
                return
            }
            
            // 4. 其余所有扫码（药品条形码、商品SKU）-> 进入库存查询
            await MainActor.run { resolvingMessage = "正在查询库存..." }
            do {
                let items = try await ApiClient.shared.fetchInventory(keyword: code, storeId: nil)
                await MainActor.run {
                    isResolving = false
                    dismiss()
                    if items.count == 1, let firstItem = items.first {
                        NotificationCenter.default.post(name: NSNotification.Name("SearchInventoryByBarcode_DirectlyShowDetail"), object: ["code": code, "item": firstItem])
                    } else {
                        NotificationCenter.default.post(name: NSNotification.Name("SearchInventoryByBarcode"), object: code)
                    }
                }
            } catch {
                await MainActor.run {
                    isResolving = false
                    dismiss()
                    NotificationCenter.default.post(name: NSNotification.Name("SearchInventoryByBarcode"), object: code)
                }
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
    private let sessionQueue = DispatchQueue(label: "com.tcm.camera.session", qos: .userInitiated)
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var hasScanned = false
    private let scanStateQueue = DispatchQueue(label: "com.tcm.camera.scan-state")
    private var lastOcrScanTime: Date = Date.distantPast
    private var lastOcrResult: String? = nil
    private var ocrMatchCount: Int = 0
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
    }
    
    private func setupCamera() {
        let session = AVCaptureSession()
        // 提升采集分辨率，使小条码在不放大的情况下也能快速识别
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
            return
        }
        
        do {
            try videoDevice.lockForConfiguration()
            // 对于支持微距的虚拟多镜头系统，.near 会自动切换到超广角微距镜头，实现 10cm 内的极速对焦
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
        
        // 1. Metadata Output for standard barcodes
        let metadataOutput = AVCaptureMetadataOutput()
        if session.canAddOutput(metadataOutput) {
            session.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            metadataOutput.metadataObjectTypes = [
                .qr, .ean13, .ean8, .code128, .code39, .upce
            ]
        }
        
        // 2. Video Data Output for OCR Fallback (Optional)
        if enableOCR {
            let videoDataOutput = AVCaptureVideoDataOutput()
            videoDataOutput.videoSettings = [(kCVPixelBufferPixelFormatTypeKey as String): Int(kCVPixelFormatType_32BGRA)]
            videoDataOutput.alwaysDiscardsLateVideoFrames = true
            let videoQueue = DispatchQueue(label: "com.tcm.videoqueue", qos: .userInteractive)
            videoDataOutput.setSampleBufferDelegate(self, queue: videoQueue)
            if session.canAddOutput(videoDataOutput) {
                session.addOutput(videoDataOutput)
            }
        }
        
        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.layer.bounds
        view.layer.addSublayer(preview)
        
        self.previewLayer = preview
        self.captureSession = session
        
        sessionQueue.async {
            session.startRunning()
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
    


    // MARK: - OCR Video Frame Extraction
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard !isScanClaimed() else { return }
        
        let now = Date()
        // 限制 OCR 频率为 150ms 一次
        guard now.timeIntervalSince(lastOcrScanTime) > 0.15 else { return }
        lastOcrScanTime = now
        
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        
        let request = VNRecognizeTextRequest { [weak self] request, error in
            guard let self = self, !self.isScanClaimed() else { return }
            guard let observations = request.results as? [VNRecognizedTextObservation] else { return }
            
            if let sku = self.extractSku(observations: observations) {
                if sku == self.lastOcrResult {
                    self.ocrMatchCount += 1
                } else {
                    self.lastOcrResult = sku
                    self.ocrMatchCount = 1
                }
                
                if self.ocrMatchCount >= 2 {
                    guard self.claimScan() else { return }
                    DispatchQueue.main.async {
                        self.onScanned?(sku)
                        self.lastOcrResult = nil
                        self.ocrMatchCount = 0
                        
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                            self.releaseScan()
                        }
                    }
                }
            } else {
                self.ocrMatchCount = 0
                self.lastOcrResult = nil
            }
        }
        // Keep the 1080p camera stream, but limit OCR work to the aiming area.
        request.recognitionLevel = .fast
        request.usesLanguageCorrection = false
        request.recognitionLanguages = ["en-US"]
        request.regionOfInterest = CGRect(x: 0.1, y: 0.25, width: 0.8, height: 0.5)
        
        // 手机竖屏时的图像方向通常是 .right
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .right, options: [:])
        try? handler.perform([request])
    }
    
    private func extractSku(observations: [VNRecognizedTextObservation]) -> String? {
        func cleanDigits(_ str: String) -> String {
            var res = ""
            for char in str {
                if char.isNumber { res.append(char) }
                else if char == "O" || char == "o" { res.append("0") }
                else if char == "I" || char == "l" { res.append("1") }
                else if char == "Z" || char == "z" { res.append("2") }
                else if char == "S" || char == "s" { res.append("5") }
                else if char == "B" || char == "b" { res.append("8") }
            }
            return res
        }
        
        func has10PlusConsecutiveDigits(_ text: String) -> Bool {
            let regex = try? NSRegularExpression(pattern: "\\d{10,}")
            let nsText = text as NSString
            return regex?.firstMatch(in: text, range: NSRange(location: 0, length: nsText.length)) != nil
        }
        
        let labelRegex = try? NSRegularExpression(pattern: "(编码|编号|SKU|商品码|批号)", options: .caseInsensitive)
        let candidate9Regex = try? NSRegularExpression(pattern: "(?i)\\b[0-9A-Za-z]{9}\\b")
        let standalone9Regex = try? NSRegularExpression(pattern: "\\b\\d{9}\\b")
        let allSpaces = CharacterSet.whitespacesAndNewlines
        
        struct Candidate {
            let sku: String
            let distanceToCenter: CGFloat
        }
        var candidates: [Candidate] = []
        
        // VNRecognizedTextObservation boundingBox is in normalized coordinates (0.0 to 1.0)
        // Center of the screen is (0.5, 0.5). We calculate distance to center Y (0.5).
        let centerY: CGFloat = 0.5
        
        for (i, obs) in observations.enumerated() {
            guard let topCandidate = obs.topCandidates(1).first else { continue }
            let line = topCandidate.string
            let normalizedLine = line.components(separatedBy: allSpaces).joined()
            if normalizedLine.isEmpty { continue }
            
            // Vision's boundingBox origin is bottom-left
            let boxMidY = obs.boundingBox.origin.y + (obs.boundingBox.size.height / 2.0)
            let distance = abs(boxMidY - centerY)
            
            let nsLine = normalizedLine as NSString
            
            // 1. 标签匹配
            if let regex = labelRegex, regex.firstMatch(in: normalizedLine, range: NSRange(location: 0, length: nsLine.length)) != nil {
                for offset in 0...2 {
                    guard i + offset < observations.count else { break }
                    let nextObs = observations[i + offset]
                    guard let nextTop = nextObs.topCandidates(1).first else { continue }
                    
                    let checkLine = nextTop.string.components(separatedBy: allSpaces).joined()
                    if has10PlusConsecutiveDigits(checkLine) { continue }
                    
                    let nextBoxMidY = nextObs.boundingBox.origin.y + (nextObs.boundingBox.size.height / 2.0)
                    let nextDist = abs(nextBoxMidY - centerY)
                    
                    let c = cleanDigits(checkLine)
                    if c.count == 9 { candidates.append(Candidate(sku: c, distanceToCenter: nextDist)); continue }
                    
                    if let cRegex = candidate9Regex {
                        let nsCheck = checkLine as NSString
                        let matches = cRegex.matches(in: checkLine, range: NSRange(location: 0, length: nsCheck.length))
                        for match in matches {
                            let candidate = cleanDigits(nsCheck.substring(with: match.range))
                            if candidate.count == 9 { candidates.append(Candidate(sku: candidate, distanceToCenter: nextDist)) }
                        }
                    }
                }
            }
            
            // 2. 无标签的纯数字/容错匹配
            if has10PlusConsecutiveDigits(normalizedLine) { continue }
            
            if let sRegex = standalone9Regex, let match = sRegex.firstMatch(in: normalizedLine, range: NSRange(location: 0, length: nsLine.length)) {
                candidates.append(Candidate(sku: nsLine.substring(with: match.range), distanceToCenter: distance))
                continue
            }
            
            if let cRegex = candidate9Regex {
                let matches = cRegex.matches(in: normalizedLine, range: NSRange(location: 0, length: nsLine.length))
                for match in matches {
                    let candidate = cleanDigits(nsLine.substring(with: match.range))
                    if candidate.count == 9 { candidates.append(Candidate(sku: candidate, distanceToCenter: distance)) }
                }
            }
        }
        
        // Return the candidate closest to the center Y
        return candidates.sorted(by: { $0.distanceToCenter < $1.distanceToCenter }).first?.sku
    }

    private func isScanClaimed() -> Bool {
        scanStateQueue.sync { hasScanned }
    }

    private func claimScan() -> Bool {
        scanStateQueue.sync {
            guard !hasScanned else { return false }
            hasScanned = true
            return true
        }
    }

    private func releaseScan() {
        scanStateQueue.async { [weak self] in
            self?.hasScanned = false
        }
    }

    func stopScanner() {
        guard let session = captureSession else { return }
        // AVCaptureSession 激活时系统会强制阻止熄屏，结束后需手动恢复用户设置的值
        sessionQueue.async {
            if session.isRunning {
                session.stopRunning()
            }
            DispatchQueue.main.async {
                let keepAwake = UserDefaults.standard.bool(forKey: "keep_screen_awake")
                UIApplication.shared.isIdleTimerDisabled = keepAwake
            }
        }
    }
    
    deinit {
        stopScanner()
    }
    
    func setTorch(on: Bool) {
        let activeDevice = (captureSession?.inputs.first as? AVCaptureDeviceInput)?.device ?? AVCaptureDevice.default(for: .video)
        guard let device = activeDevice, device.hasTorch else { return }
        try? device.lockForConfiguration()
        device.torchMode = on ? .on : .off
        device.unlockForConfiguration()
    }
}
