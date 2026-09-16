import SwiftUI
import CoreImage.CIFilterBuiltins

// MARK: - 快递单号智能提取 (1:1 移植 Android extractExpressTrackingNo)
public func extractExpressTrackingNo(_ raw: String) -> String {
    let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    if text.isEmpty { return "" }
    
    // 匹配 SF 开头的单号 (如 SF1432567890123、SF-123456789012)
    if let match = text.range(of: #"(?i)(?:SF|sf)[\s-]*([0-9]{10,16})"#, options: .regularExpression) {
        let sub = String(text[match])
        let digits = sub.filter { $0.isNumber }
        return "SF" + digits
    }
    
    // 匹配 12-16 位纯数字顺丰或主流快递单号
    if let match = text.range(of: #"(?<!\d)(\d{12,16})(?!\d)"#, options: .regularExpression) {
        return String(text[match])
    }
    
    // 去除空格和短横线后的连续字母数字（>=10位）
    let cleaned = text.replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "-", with: "")
    if cleaned.count >= 10 && cleaned.allSatisfy({ $0.isLetter || $0.isNumber }) {
        return cleaned
    }
    return text
}

// MARK: - CoreImage 原生二维码生成视图
nonisolated(unsafe) private let qrSharedCIContext = CIContext()

@MainActor
public struct QRCodeView: View {
    public let content: String
    public let size: CGFloat
    
    @State private var generatedImage: UIImage? = nil
    
    public init(content: String, size: CGFloat = 140) {
        self.content = content
        self.size = size
    }
    
    public var body: some View {
        Group {
            if let image = generatedImage {
                Image(uiImage: image)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: size, height: size)
            } else {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.1))
                    .frame(width: size, height: size)
            }
        }
        .task(id: content) {
            // Generate QR asynchronously to prevent main thread blocking during transition
            let img = await generateQRCodeAsync(from: content)
            await MainActor.run { self.generatedImage = img }
        }
    }
    

    
    private func generateQRCodeAsync(from string: String) async -> UIImage? {
        guard !string.isEmpty else { return nil }
        return await Task.detached(priority: .userInitiated) {
            let context = qrSharedCIContext
            let filter = CIFilter.qrCodeGenerator()
            filter.message = Data(string.utf8)
            filter.correctionLevel = "M"
            
            if let outputImage = filter.outputImage {
                let transform = CGAffineTransform(scaleX: 10, y: 10)
                let scaledImage = outputImage.transformed(by: transform)
                if let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) {
                    return UIImage(cgImage: cgImage)
                }
            }
            return nil
        }.value
    }
    
    // Kept for backward compatibility if used synchronously elsewhere (but we replaced it)
    private func generateQRCode(from string: String) -> UIImage? {
        guard !string.isEmpty else { return nil }
        let context = qrSharedCIContext
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        
        if let outputImage = filter.outputImage {
            let transform = CGAffineTransform(scaleX: 10, y: 10)
            let scaledImage = outputImage.transformed(by: transform)
            if let cgImage = context.createCGImage(scaledImage, from: scaledImage.extent) {
                return UIImage(cgImage: cgImage)
            }
        }
        return nil
    }
}

// MARK: - 条码/单号轻量扫描弹窗
// MARK: - 包裹表单（新建 / 编辑 1:1 对齐 Android PackageFormScreen）
@MainActor
public struct PackageFormView: View {
    public let initial: PackageModel?
    public let onSaved: () -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var itemName: String = ""
    @State private var itemInfo: String = ""
    @State private var receiverName: String = ""
    @State private var receiverPhone: String = ""
    @State private var method: Int = 0 // 0: 自提, 1: 跑腿, 2: 快递
    @State private var tracking: String = ""
    
    @State private var isBusy = false
    @State private var errorMessage: String? = nil
    @State private var isScannerShowing = false
    
    public init(initial: PackageModel? = nil, onSaved: @escaping () -> Void) {
        self.initial = initial
        self.onSaved = onSaved
        _itemName = State(initialValue: initial?.name ?? "")
        _itemInfo = State(initialValue: initial?.info ?? "")
        _receiverName = State(initialValue: initial?.customer ?? "")
        _receiverPhone = State(initialValue: (initial?.phone == "-" ? "" : initial?.phone) ?? "")
        _method = State(initialValue: initial?.methodCode ?? 0)
        _tracking = State(initialValue: initial?.expressTrackingNo ?? "")
    }
    
    private var isEdit: Bool {
        initial != nil && (initial?.id ?? 0) > 0
    }
    
    private var canSubmit: Bool {
        !itemName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !receiverName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !isBusy &&
        (method != 2 || !tracking.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 14) {
                            SectionHeader(
                                title: isEdit ? "编辑包裹信息" : "创建自提/代发包裹",
                                subtitle: "录入物品、备注与收件人联系方式"
                            )
                            
                            Divider().foregroundColor(Color.cardBorder)
                            
                            // 物品名称
                            VStack(alignment: .leading, spacing: 6) {
                                Text("物品名称 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("如：中药汤剂 14袋", text: $itemName)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            // 备注
                            VStack(alignment: .leading, spacing: 6) {
                                Text("备注说明").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("如：请冷藏、分装或配送说明", text: $itemInfo, axis: .vertical)
                                    .lineLimit(2...4)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            // 收件人姓名
                            VStack(alignment: .leading, spacing: 6) {
                                Text("收件人姓名 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("输入收件人姓名", text: $receiverName)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            // 收件人手机号
                            VStack(alignment: .leading, spacing: 6) {
                                Text("收件人手机号").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("输入收件人手机号", text: $receiverPhone)
                                    .keyboardType(.phonePad)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            // 取货方式
                            VStack(alignment: .leading, spacing: 8) {
                                Text("取货方式 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                HStack(spacing: 8) {
                                    SegmentedButton(label: "自提", isSelected: method == 0) { method = 0 }
                                    SegmentedButton(label: "跑腿", isSelected: method == 1) { method = 1 }
                                    SegmentedButton(label: "快递", isSelected: method == 2) { method = 2 }
                                }
                            }
                            
                            // 快递单号 (仅快递时展示)
                            if method == 2 {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("快递单号 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                    HStack {
                                        TextField("输入或扫描快递单号", text: $tracking)
                                        Button(action: { isScannerShowing = true }) {
                                            Image(systemName: "qrcode.viewfinder")
                                                .foregroundColor(.appPrimary)
                                                .font(.system(size: (18) * ThemeManager.shared.fontScale))
                                        }
                                    }
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                }
                            }
                        }
                    }
                    
                    if let error = errorMessage {
                        AppCard(padding: 12) {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill").foregroundColor(.danger)
                                Text(error).font(.system(size: (13) * ThemeManager.shared.fontScale)).foregroundColor(.danger)
                            }
                        }
                    }
                    
                    Button(action: saveAction) {
                        HStack {
                            if isBusy {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            }
                            Text(isBusy ? "正在保存..." : (isEdit ? "确认修改" : "保存并生成取货码"))
                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(canSubmit ? Color.appPrimary : Color.appPrimary.opacity(0.4))
                        .cornerRadius(10)
                    }
                    .disabled(!canSubmit)
                }
                .padding(16)
            }
            .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
            .navigationTitle(isEdit ? "编辑包裹" : "新建包裹")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { dismiss() }
                }
            }

        }
    }
    
    private func saveAction() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        guard !isBusy else { return }
        isBusy = true
        errorMessage = nil
        
        Task {
            do {
                let payload: [String: Any] = [
                    "itemName": itemName.trimmingCharacters(in: .whitespacesAndNewlines),
                    "itemInfo": itemInfo.trimmingCharacters(in: .whitespacesAndNewlines),
                    "receiverName": receiverName.trimmingCharacters(in: .whitespacesAndNewlines),
                    "receiverPhone": receiverPhone.trimmingCharacters(in: .whitespacesAndNewlines),
                    "pickupMethod": method,
                    "expressTrackingNo": tracking.trimmingCharacters(in: .whitespacesAndNewlines)
                ]
                
                if isEdit, let pkg = initial {
                    _ = try await ApiClient.shared.updatePackage(id: pkg.id, payload: payload)
                } else {
                    _ = try await ApiClient.shared.createPackage(payload: payload)
                }
                
                await MainActor.run {
                    isBusy = false
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    onSaved()
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    isBusy = false
                    errorMessage = error.localizedDescription
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }
}

// MARK: - 取货码核销 (1:1 对齐 Android PackageVerifyScreen)
@MainActor
public struct PackageVerifyView: View {
    public let initialCode: String
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var router = Router.shared
    
    @State private var codeText = ""
    @State private var signedQrContent: String? = nil
    @State private var selectedMethod = 0 // 0: 自提, 1: 跑腿, 2: 快递
    @State private var expressTrackingNo = ""
    @State private var isVerifying = false
    @State private var verifiedPackage: PackageModel? = nil
    @State private var errorMessage: String? = nil
    @State private var isScannerShowing = false
    
    private var rawPickupCode: String {
        codeText.filter { $0.isNumber }
    }
    
    public init(initialCode: String = "") {
        self.initialCode = initialCode
    }
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // 核销输入卡片
                AppCard(padding: 20) {
                    VStack(spacing: 16) {
                        HStack {
                            Text("取货凭证核销")
                                .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                            Spacer()
                            Button(action: { isScannerShowing = true }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "qrcode.viewfinder")
                                    Text("扫码填入")
                                }
                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                                .foregroundColor(.appPrimary)
                            }
                        }
                        
                        Divider().foregroundColor(Color.cardBorder)
                        
                        // 取货码输入框
                        HStack {
                            Image(systemName: "number")
                                .foregroundColor(.muted)
                            TextField("请输入 6 位取货码", text: $codeText)
                                .font(.system(size: (18) * ThemeManager.shared.fontScale, weight: .bold))
                                .keyboardType(.numberPad)
                                .onChange(of: codeText) {
                                    let raw = codeText.filter { $0.isNumber }.prefix(6)
                                    if raw.count > 3 {
                                        let start = raw.startIndex
                                        let mid = raw.index(start, offsetBy: 3)
                                        codeText = "\(raw[start..<mid])-\(raw[mid...])"
                                    } else {
                                        codeText = String(raw)
                                    }
                                }
                            
                            if !codeText.isEmpty {
                                Button(action: {
                                    codeText = ""
                                    signedQrContent = nil
                                }) {
                                    Image(systemName: "xmark.circle.fill").foregroundColor(.muted)
                                }
                            }
                        }
                        .padding(.horizontal, 14)
                        .frame(height: 52)
                        .background(Color.surface)
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder, lineWidth: 1))
                        
                        // 领取方式切换
                        VStack(alignment: .leading, spacing: 8) {
                            Text("确认最终取货方式")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                            
                            HStack(spacing: 8) {
                                SegmentedButton(label: "自提", isSelected: selectedMethod == 0) { selectedMethod = 0 }
                                SegmentedButton(label: "跑腿", isSelected: selectedMethod == 1) { selectedMethod = 1 }
                                SegmentedButton(label: "快递", isSelected: selectedMethod == 2) { selectedMethod = 2 }
                            }
                        }
                        
                        if selectedMethod == 2 {
                            HStack {
                                TextField("输入或扫描快递单号 *", text: $expressTrackingNo)
                                    .font(.system(size: (14) * ThemeManager.shared.fontScale))
                                Button(action: { isScannerShowing = true }) {
                                    Image(systemName: "barcode.viewfinder")
                                        .foregroundColor(.appPrimary)
                                }
                            }
                            .padding(.horizontal, 14)
                            .frame(height: 44)
                            .background(Color.surface)
                            .cornerRadius(8)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                        }
                        
                        if let error = errorMessage {
                            Text(error)
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.danger)
                        }
                        
                        Button(action: verifyAction) {
                            HStack {
                                if isVerifying {
                                    ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                }
                                Text(isVerifying ? "正在核销..." : "确认核销出库")
                                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(rawPickupCode.count == 6 && (selectedMethod != 2 || !expressTrackingNo.isEmpty) ? Color.success : Color.success.opacity(0.4))
                            .cornerRadius(10)
                        }
                        .disabled(rawPickupCode.count != 6 || isVerifying || (selectedMethod == 2 && expressTrackingNo.isEmpty))
                    }
                }
                
                // 核销成功展示区
                if let pkg = verifiedPackage {
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Image(systemName: "checkmark.seal.fill")
                                    .foregroundColor(.success)
                                    .font(.system(size: (20) * ThemeManager.shared.fontScale))
                                Text("核销出库成功！")
                                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.success)
                                Spacer()
                                StatusPill(text: "已完成")
                            }
                            
                            Divider().foregroundColor(Color.cardBorder)
                            
                            InfoRowItem(label: "包裹名称", value: pkg.name)
                            InfoRowItem(label: "客户姓名", value: pkg.customer)
                            if !pkg.phone.isEmpty {
                                InfoRowItem(label: "联系电话", value: maskPhone(pkg.phone))
                            }
                            InfoRowItem(label: "取货单号", value: pkg.code.formattedPickupCode)
                            InfoRowItem(label: "核销时间", value: "刚刚")
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
        .navigationTitle("取件核销")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            parseInitialCode(initialCode)
        }

    }
    
    private func parseInitialCode(_ raw: String) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        
        if trimmed.hasPrefix("TCM:PICKUP:1:") {
            signedQrContent = trimmed
            // 提取6位取货码正则: TCM:PICKUP:1:\d+:(\d{6}):...
            if let regex = try? NSRegularExpression(pattern: #"^TCM:PICKUP:1:\d+:(\d{6}):"#, options: []),
               let match = regex.firstMatch(in: trimmed, options: [], range: NSRange(location: 0, length: trimmed.utf16.count)),
               let range = Range(match.range(at: 1), in: trimmed) {
                codeText = String(trimmed[range])
                return
            }
        }
        let digits = trimmed.filter { $0.isNumber }
        codeText = String(digits.prefix(6))
    }
    
    private func verifyAction() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isVerifying = true
        errorMessage = nil
        
        Task {
            do {
                let code = rawPickupCode
                let tracking = selectedMethod == 2 ? expressTrackingNo.trimmingCharacters(in: .whitespacesAndNewlines) : ""
                let res = try await ApiClient.shared.verifyPackage(
                    code: code,
                    pickupMethod: selectedMethod,
                    expressTrackingNo: tracking,
                    pickupQrContent: signedQrContent
                )
                await MainActor.run {
                    self.verifiedPackage = res
                    self.isVerifying = false
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isVerifying = false
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }
}

// MARK: - 包裹详情 (1:1 对齐 Android PackageDetailPage)
@MainActor
public struct PackageDetailView: View {
    public let id: Int
    @ObservedObject private var router = Router.shared
    @State private var package: PackageModel? = nil
    @State private var isLoading = true
    @State private var errorMessage: String? = nil
    @State private var isEditSheetShowing = false
    
    public init(id: Int) {
        self.id = id
    }
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let error = errorMessage, package != nil {
                    AppCard(padding: 12) {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.danger)
                            Text(error)
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.danger)
                            Spacer()
                            Button("重试") {
                                Task { await loadDetail() }
                            }
                            .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                            .foregroundColor(.appPrimary)
                        }
                    }
                }
                
                if isLoading && package == nil {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.top, 40)
                } else if package == nil {
                    VStack(spacing: 12) {
                        Image(systemName: "shippingbox")
                            .font(.system(size: (36) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                        Text(errorMessage ?? "未能加载包裹详情")
                            .font(.system(size: (14) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                            .multilineTextAlignment(.center)
                        Button("点击重试") {
                            Task { await loadDetail() }
                        }
                        .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.appPrimary)
                        .padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 40)
                } else if let pkg = package {
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text(pkg.name)
                                    .font(.system(size: (18) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                Spacer()
                                StatusPill(text: pkg.method)
                                StatusPill(text: pkg.statusText)
                            }
                            
                            Divider().foregroundColor(Color.cardBorder)
                            
                            // 二维码与大取货码展示区
                            VStack(spacing: 8) {
                                let qrData = pkg.pickupQrContent.isEmpty ? pkg.code : pkg.pickupQrContent
                                QRCodeView(content: qrData, size: 140)
                                    .padding(8)
                                    .background(Color.white)
                                    .cornerRadius(8)
                                    .shadow(color: Color.black.opacity(0.04), radius: 4)
                                
                                Text("取货码：\(pkg.code.formattedPickupCode)")
                                    .font(.system(size: (22) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.appPrimaryDark)
                                
                                Text("请向工作人员出示此取货码或二维码")
                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.surface)
                            .cornerRadius(8)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            
                            InfoRowItem(label: "收件客户", value: pkg.customer)
                            if !pkg.phone.isEmpty {
                                InfoRowItem(label: "联系电话", value: maskPhone(pkg.phone))
                            }
                            InfoRowItem(label: "取货方式", value: pkg.method)
                            if !pkg.store.isEmpty {
                                InfoRowItem(label: "所属门店", value: pkg.store)
                            }
                            if !pkg.expressTrackingNo.isEmpty {
                                InfoRowItem(label: "快递单号", value: pkg.expressTrackingNo)
                            }
                            InfoRowItem(label: "包裹状态", value: pkg.statusText)
                            if !pkg.createdAt.isEmpty {
                                InfoRowItem(label: "录入时间", value: formatDateTimeToMinute(pkg.createdAt))
                            }
                            if !pkg.pickedAt.isEmpty {
                                InfoRowItem(label: "取货时间", value: formatDateTimeToMinute(pkg.pickedAt))
                            }
                            if !pkg.creatorName.isEmpty {
                                InfoRowItem(label: "录入人", value: pkg.creatorName)
                            }
                            if !pkg.verifierName.isEmpty {
                                InfoRowItem(label: "核销人", value: pkg.verifierName)
                            }
                            
                            if !pkg.info.isEmpty {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("备注说明")
                                        .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                        .foregroundColor(.muted)
                                    Text(pkg.info)
                                        .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                        .foregroundColor(.ink)
                                        .padding(8)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .background(Color.surface)
                                        .cornerRadius(6)
                                }
                            }
                        }
                    }
                    
                    // 待领取状态操作按钮
                    if pkg.statusCode == 0 {
                        HStack(spacing: 12) {
                            Button(action: {
                                isEditSheetShowing = true
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "pencil")
                                    Text("编辑包裹")
                                }
                                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .semibold))
                                .foregroundColor(.ink)
                                .frame(maxWidth: .infinity)
                                .frame(height: 46)
                                .background(Color.surface)
                                .cornerRadius(8)
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            Button(action: {
                                router.navigate(to: .packageVerify(initialCode: pkg.code))
                            }) {
                                HStack(spacing: 6) {
                                    Image(systemName: "checkmark.circle.fill")
                                    Text("立即核销")
                                }
                                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 46)
                                .background(Color.success)
                                .cornerRadius(8)
                            }
                        }
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
        .navigationTitle("包裹详情")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadDetail()
        }
        .refreshable {
            await loadDetail()
        }
        .sheet(isPresented: $isEditSheetShowing) {
            if let pkg = package {
                PackageFormView(initial: pkg) {
                    Task { await loadDetail() }
                }
            }
        }
    }
    
    private func loadDetail() async {
        if isLoading && package != nil { return }
        isLoading = true
        errorMessage = nil
        do {
            self.package = try await ApiClient.shared.fetchPackageDetail(id: id)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

