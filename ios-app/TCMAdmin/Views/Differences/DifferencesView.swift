import SwiftUI

// MARK: - 库存差异管理 (1:1 移植 Android DifferencesScreens)
@MainActor
public struct DifferencesView: View {
    @State private var selectedTab = 0 // 0: 当前差异商品, 1: 差异变动流水
    @State private var stats: DifferenceStatsModel = DifferenceStatsModel(more: 0, less: 0, total: 0)
    @State private var products: [DifferenceProductModel] = []
    @State private var logs: [DifferenceLogModel] = []
    
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    
    // 销账弹窗
    @State private var writeOffProduct: DifferenceProductModel? = nil
    @State private var writeOffType: String = "" // "WRITE_OFF_RECEIPT" or "WRITE_OFF_SHIPMENT"
    @State private var writeOffQuantityText: String = ""
    @State private var isWriteOffBusy = false
    
    // 登记差异弹窗
    @State private var isRegisterSheetShowing = false
    
    public init() {}
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // 顶部标题与操作栏
                HStack(alignment: .center) {
                    SectionHeader(title: "库存差异", subtitle: "管理未入库/未销库的实货差异")
                    Spacer()
                    Button(action: { isRegisterSheetShowing = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                            Text("登记差异")
                        }
                        .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color.appPrimary)
                        .cornerRadius(8)
                    }
                }
                
                // 概览统计卡片
                AppCard(padding: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("当前库存差异统计")
                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                            Spacer()
                            if isLoading {
                                ProgressView()
                                    .scaleEffect(0.8)
                            }
                        }
                        
                        Divider().foregroundColor(Color.cardBorder)
                        
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("实盘多货 (待入账)")
                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                                Text("\(stats.more ?? 0) 种品项")
                                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.appPrimary)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 4) {
                                Text("实盘少货 (待损耗)")
                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                                Text("\(stats.less ?? 0) 种品项")
                                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.danger)
                            }
                        }
                    }
                }
                
                // Tab 切换
                HStack(spacing: 12) {
                    SegmentedButton(label: "当前差异商品 (\(products.count))", isSelected: selectedTab == 0) {
                        selectedTab = 0
                    }
                    SegmentedButton(label: "差异变动流水 (\(logs.count))", isSelected: selectedTab == 1) {
                        selectedTab = 1
                    }
                }
                
                if let error = errorMessage {
                    AppCard(padding: 12) {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.danger)
                            Text(error)
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.danger)
                            Spacer()
                            Button("重试") {
                                Task { await loadData() }
                            }
                            .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                            .foregroundColor(.appPrimary)
                        }
                    }
                }
                
                // 列表内容
                if selectedTab == 0 {
                    // 当前差异商品
                    if products.isEmpty && !isLoading {
                        AppCard(padding: 32) {
                            VStack(spacing: 8) {
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: (36) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.success)
                                Text("暂无实货库存差异")
                                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .medium))
                                    .foregroundColor(.muted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    } else {
                        LazyVStack(spacing: 10) {
                            ForEach(products) { item in
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        Text("\(item.productCode ?? item.barcode ?? "-") · \(item.displayName)")
                                            .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                            .foregroundColor(.ink)
                                        
                                        Text("规格：\(item.specification ?? "-")")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                            
                                        HStack(spacing: 8) {
                                            if let preReceipt = item.preReceiptQuantity, preReceipt > 0 {
                                                HStack {
                                                    Text("先到货：+\(String(format: "%g", preReceipt)) \(item.unit ?? "g")")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .medium))
                                                        .foregroundColor(.success)
                                                    Spacer()
                                                    Button(action: {
                                                        writeOffProduct = item
                                                        writeOffType = "WRITE_OFF_RECEIPT"
                                                        writeOffQuantityText = "\(String(format: "%g", preReceipt))"
                                                    }) {
                                                        Text("入库销账")
                                                            .font(.system(size: (11) * ThemeManager.shared.fontScale, weight: .bold))
                                                            .foregroundColor(.white)
                                                            .padding(.horizontal, 8)
                                                            .padding(.vertical, 4)
                                                            .background(Color.success)
                                                            .cornerRadius(4)
                                                    }
                                                }
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 6)
                                                .background(Color.surface)
                                                .cornerRadius(6)
                                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                                            }
                                            if let preShipment = item.preShipmentQuantity, preShipment > 0 {
                                                HStack {
                                                    Text("先出货：-\(String(format: "%g", preShipment)) \(item.unit ?? "g")")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .medium))
                                                        .foregroundColor(.danger)
                                                    Spacer()
                                                    Button(action: {
                                                        writeOffProduct = item
                                                        writeOffType = "WRITE_OFF_SHIPMENT"
                                                        writeOffQuantityText = "\(String(format: "%g", preShipment))"
                                                    }) {
                                                        Text("销库销账")
                                                            .font(.system(size: (11) * ThemeManager.shared.fontScale, weight: .bold))
                                                            .foregroundColor(.white)
                                                            .padding(.horizontal, 8)
                                                            .padding(.vertical, 4)
                                                            .background(Color.danger)
                                                            .cornerRadius(4)
                                                    }
                                                }
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 6)
                                                .background(Color.surface)
                                                .cornerRadius(6)
                                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                                            }
                                        }
                                        
                                        if let remark = item.remark, !remark.isEmpty {
                                            Text("备注: \(remark)")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 差异流水明细
                    if logs.isEmpty && !isLoading {
                        AppCard(padding: 32) {
                            VStack(spacing: 8) {
                                Image(systemName: "doc.plaintext")
                                    .font(.system(size: (36) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                                Text("暂无变动流水记录")
                                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .medium))
                                    .foregroundColor(.muted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    } else {
                        LazyVStack(spacing: 10) {
                            ForEach(logs) { log in
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        HStack {
                                            Text("\(log.product?.productCode ?? log.product?.barcode ?? "-") · \(log.product?.displayName ?? "商品")")
                                                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                                .foregroundColor(.ink)
                                            Spacer()
                                            StatusPill(text: log.operationTypeLabel)
                                        }
                                        
                                        Text("\(formatDateOnly(log.businessDate)) · \(log.creator?.displayName ?? "-")")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                            
                                        let qty = log.changeQuantity ?? 0.0
                                        Text("变动数量：\(String(format: "%g", qty)) \(log.product?.unit ?? "g")")
                                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                                            .foregroundColor(.appPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(Color.pageBackground)
        .navigationTitle("库存差异")
        .navigationBarTitleDisplayMode(.inline)
        .scrollDismissesKeyboard(.interactively)
        .task {
            await loadData()
        }
        .refreshable {
            await loadData()
        }
        .sheet(item: $writeOffProduct) { prod in
            NavigationStack {
                VStack(spacing: 16) {
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("\(prod.productCode ?? "") · \(prod.displayName)")
                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                            
                            Text("销账类型：\(writeOffType == "WRITE_OFF_RECEIPT" ? "入库销账" : "销库销账")")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                            
                            TextField("请输入销账数量 (\(prod.unit ?? "g"))", text: $writeOffQuantityText)
                                .keyboardType(.decimalPad)
                                .padding(.horizontal, 12)
                                .frame(height: 44)
                                .background(Color.surface)
                                .cornerRadius(8)
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                        }
                    }
                    
                    Button(action: {
                        submitWriteOffAction(product: prod)
                    }) {
                        HStack {
                            if isWriteOffBusy {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            }
                            Text("确认销账")
                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(Color.appPrimary)
                        .cornerRadius(8)
                    }
                    .disabled((Double(writeOffQuantityText) ?? 0.0) <= 0 || isWriteOffBusy)
                    
                    Spacer()
                }
                .padding(16)
                .background(Color.pageBackground)
                .navigationTitle(writeOffType == "WRITE_OFF_RECEIPT" ? "入库销账" : "销库销账")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("取消") { writeOffProduct = nil }
                    }
                }
            }
        }
        .sheet(isPresented: $isRegisterSheetShowing) {
            RegisterDifferenceSheet {
                Task { await loadData() }
            }
        }
    }
    
    private func loadData() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        do {
            async let fetchStats = ApiClient.shared.fetchDifferenceStats()
            async let fetchProds = ApiClient.shared.fetchDifferences()
            async let fetchLgs = ApiClient.shared.fetchDifferenceLogs()
            
            let (loadedStats, loadedProds, loadedLogs) = try await (fetchStats, fetchProds, fetchLgs)
            self.stats = loadedStats
            self.products = loadedProds
            self.logs = loadedLogs
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func submitWriteOffAction(product: DifferenceProductModel) {
        guard let qty = Double(writeOffQuantityText), qty > 0 else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isWriteOffBusy = true
        
        Task {
            do {
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                let todayStr = formatter.string(from: Date())
                
                let payload: [String: Any] = [
                    "productId": product.id,
                    "quantity": qty,
                    "businessDate": todayStr
                ]
                try await ApiClient.shared.writeOffDifference(payload: payload)
                await MainActor.run {
                    isWriteOffBusy = false
                    writeOffProduct = nil
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
                await loadData()
            } catch {
                await MainActor.run {
                    isWriteOffBusy = false
                    errorMessage = error.localizedDescription
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }
}

// MARK: - 登记差异表单弹窗 (1:1 对齐 Android 登记差异 Dialog)
public struct RegisterDifferenceSheet: View {
    public let onSaved: () -> Void
    
    @Environment(\.dismiss) private var dismiss
    @State private var registerType: String = "PRE_RECEIPT" // PRE_RECEIPT: 先到货, PRE_SHIPMENT: 先出货
    @State private var keyword: String = ""
    @State private var catalog: [DifferenceProductModel] = []
    @State private var selectedProduct: DifferenceProductModel? = nil
    @State private var quantityText: String = ""
    @State private var isLoadingCatalog = false
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    
    public init(onSaved: @escaping () -> Void) {
        self.onSaved = onSaved
    }
    
    private var filteredCatalog: [DifferenceProductModel] {
        let kw = keyword.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if kw.isEmpty {
            return Array(catalog.prefix(15))
        }
        return catalog.filter {
            $0.displayName.lowercased().contains(kw) ||
            ($0.productCode?.lowercased().contains(kw) == true) ||
            ($0.barcode?.lowercased().contains(kw) == true)
        }
    }
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 14) {
                            Text("登记实货差异")
                                .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                            
                            Divider().foregroundColor(Color.cardBorder)
                            
                            // 差异类型切换
                            HStack(spacing: 8) {
                                SegmentedButton(label: "先到货未入库", isSelected: registerType == "PRE_RECEIPT") {
                                    registerType = "PRE_RECEIPT"
                                }
                                SegmentedButton(label: "先出货未销库", isSelected: registerType == "PRE_SHIPMENT") {
                                    registerType = "PRE_SHIPMENT"
                                }
                            }
                            
                            // 搜索选择商品
                            VStack(alignment: .leading, spacing: 6) {
                                Text("选择商品 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("搜索商品名称、编码或条码", text: $keyword)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                
                                if isLoadingCatalog {
                                    ProgressView().padding(.vertical, 8)
                                } else {
                                    LazyVStack(spacing: 6) {
                                        ForEach(filteredCatalog) { item in
                                            Button(action: {
                                                selectedProduct = item
                                            }) {
                                                HStack {
                                                    VStack(alignment: .leading, spacing: 2) {
                                                        Text(item.displayName)
                                                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                                            .foregroundColor(.ink)
                                                        Text("\(item.productCode ?? "") · 规格：\(item.specification ?? "-")")
                                                            .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                                            .foregroundColor(.muted)
                                                    }
                                                    Spacer()
                                                    if selectedProduct?.id == item.id {
                                                        Image(systemName: "checkmark.circle.fill")
                                                            .foregroundColor(.appPrimary)
                                                    }
                                                }
                                                .padding(10)
                                                .background(selectedProduct?.id == item.id ? Color.appPrimary.opacity(0.08) : Color.surface)
                                                .cornerRadius(6)
                                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(selectedProduct?.id == item.id ? Color.appPrimary : Color.cardBorder, lineWidth: 1))
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 差异数量
                            VStack(alignment: .leading, spacing: 6) {
                                Text("数量 (\(selectedProduct?.unit ?? "g")) *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("输入差异变动数量", text: $quantityText)
                                    .keyboardType(.decimalPad)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
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
                    
                    Button(action: submitRegister) {
                        HStack {
                            if isSubmitting {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            }
                            Text("确认登记")
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
            .background(Color.pageBackground)
            .navigationTitle("登记差异")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { dismiss() }
                }
            }
            .task {
                await loadCatalog()
            }
        }
    }
    
    private var canSubmit: Bool {
        selectedProduct != nil && (Double(quantityText) ?? 0.0) > 0 && !isSubmitting
    }
    
    private func loadCatalog() async {
        isLoadingCatalog = true
        catalog = (try? await ApiClient.shared.fetchProductCatalog()) ?? []
        isLoadingCatalog = false
    }
    
    private func submitRegister() {
        guard let prod = selectedProduct, let qty = Double(quantityText), qty > 0 else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isSubmitting = true
        errorMessage = nil
        
        Task {
            do {
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                let todayStr = formatter.string(from: Date())
                
                let payload: [String: Any] = [
                    "operationType": registerType,
                    "businessDate": todayStr,
                    "items": [
                        [
                            "productId": prod.id,
                            "quantity": qty
                        ]
                    ]
                ]
                try await ApiClient.shared.registerDifference(payload: payload)
                await MainActor.run {
                    isSubmitting = false
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    onSaved()
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    isSubmitting = false
                    errorMessage = error.localizedDescription
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }
}

