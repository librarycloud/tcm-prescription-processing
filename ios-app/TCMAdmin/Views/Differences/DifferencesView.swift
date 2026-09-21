import SwiftUI

// MARK: - 库存差异管理 (1:1 移植 Android DifferencesScreens)
@MainActor
public struct DifferencesView: View {
    @Environment(SessionManager.self) var session
    var isStoreStaff: Bool { session.currentUser?.role == 3 }
    
    @State private var selectedTab = 0 // 0: 当前差异商品, 1: 差异变动流水
    @State private var stats: DifferenceStatsModel = DifferenceStatsModel(more: 0, less: 0, total: 0)
    @State private var products: [DifferenceProductModel] = []
    @State private var logs: [DifferenceLogModel] = []
    
    @State private var productsPage = 1
    @State private var logsPage = 1
    @State private var hasMoreProducts = true
    @State private var hasMoreLogs = true
    
    @State private var isLoading = false
    @State private var isLoadingMore = false
    @State private var errorMessage: String? = nil
    @State private var currentTaskID: UUID = UUID()
    
    // 销账弹窗
    @State private var writeOffProduct: DifferenceProductModel? = nil
    @State private var writeOffType: String = "" // "WRITE_OFF_RECEIPT" or "WRITE_OFF_SHIPMENT"
    @State private var writeOffQuantityText: String = ""
    @State private var isWriteOffBusy = false
    
    // 登记差异弹窗
    @State private var isRegisterSheetShowing = false
    
    public init() {}
    
    public var body: some View {
        AppScrollView {
            VStack(spacing: 16) {
                // 顶部标题与操作栏
                HStack(alignment: .center) {
                    SectionHeader(title: "库存差异", subtitle: "管理未入库/未销库的实货差异")
                    Spacer()
                    if !isStoreStaff {
                        Button(action: { isRegisterSheetShowing = true }) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus")
                                Text("登记差异")
                            }
                            .scaledFont(13, weight: .bold)
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 8))
                        }
                    }
                }
                
                // 概览统计卡片
                AppCard(padding: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("当前库存差异统计")
                                .scaledFont(15, weight: .bold)
                            Spacer()
                            if isLoading {
                                ProgressView()
                                    .scaleEffect(0.8)
                            }
                        }
                        
                        Divider().foregroundStyle(Color.cardBorder)
                        
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("实盘多货 (待入账)")
                                    .scaledFont(12)
                                    .foregroundStyle(Color.muted)
                                Text("\(stats.more ?? 0) 种品项")
                                    .scaledFont(16, weight: .bold)
                                    .foregroundStyle(Color.appPrimary)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 4) {
                                Text("实盘少货 (待损耗)")
                                    .scaledFont(12)
                                    .foregroundStyle(Color.muted)
                                Text("\(stats.less ?? 0) 种品项")
                                    .scaledFont(16, weight: .bold)
                                    .foregroundStyle(Color.danger)
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
                                .foregroundStyle(Color.danger)
                            Text(error)
                                .scaledFont(13)
                                .foregroundStyle(Color.danger)
                            Spacer()
                            Button("重试") {
                                Task { await loadData() }
                            }
                            .scaledFont(12, weight: .bold)
                            .foregroundStyle(Color.appPrimary)
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
                                    .scaledFont(36)
                                    .foregroundStyle(Color.success)
                                Text("暂无实货库存差异")
                                    .scaledFont(14, weight: .medium)
                                    .foregroundStyle(Color.muted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                    } else {
                        LazyVStack(spacing: 10) {
                            ForEach(products) { item in
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        Text("\(item.productCode ?? item.barcode ?? "-") · \(item.displayName)")
                                            .scaledFont(14, weight: .bold)
                                            .foregroundStyle(Color.ink)
                                        
                                        Text("规格：\(item.specification ?? "-")")
                                            .scaledFont(12)
                                            .foregroundStyle(Color.muted)
                                            
                                        HStack(spacing: 8) {
                                            if let preReceipt = item.preReceiptQuantity, preReceipt > 0 {
                                                HStack {
                                                    Text("先到货：+\(String(format: "%g", preReceipt)) \(item.unit ?? "g")")
                                                        .scaledFont(12, weight: .medium)
                                                        .foregroundStyle(Color.success)
                                                    Spacer()
                                                    if !isStoreStaff {
                                                        Button(action: {
                                                            writeOffProduct = item
                                                            writeOffType = "WRITE_OFF_RECEIPT"
                                                            writeOffQuantityText = "\(String(format: "%g", preReceipt))"
                                                        }) {
                                                            Text("入库销账")
                                                                .scaledFont(11, weight: .bold)
                                                                .foregroundStyle(Color.white)
                                                                .padding(.horizontal, 8)
                                                                .padding(.vertical, 4)
                                                                .background(Color.success)
                                                                .clipShape(.rect(cornerRadius: 4))
                                                        }
                                                    }
                                                }
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 6)
                                                .background(Color.surface)
                                                .clipShape(.rect(cornerRadius: 6))
                                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                                            }
                                            if let preShipment = item.preShipmentQuantity, preShipment > 0 {
                                                HStack {
                                                    Text("先出货：-\(String(format: "%g", preShipment)) \(item.unit ?? "g")")
                                                        .scaledFont(12, weight: .medium)
                                                        .foregroundStyle(Color.danger)
                                                    Spacer()
                                                    if !isStoreStaff {
                                                        Button(action: {
                                                            writeOffProduct = item
                                                            writeOffType = "WRITE_OFF_SHIPMENT"
                                                            writeOffQuantityText = "\(String(format: "%g", preShipment))"
                                                        }) {
                                                            Text("销库销账")
                                                                .scaledFont(11, weight: .bold)
                                                                .foregroundStyle(Color.white)
                                                                .padding(.horizontal, 8)
                                                                .padding(.vertical, 4)
                                                                .background(Color.danger)
                                                                .clipShape(.rect(cornerRadius: 4))
                                                        }
                                                    }
                                                }
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 6)
                                                .background(Color.surface)
                                                .clipShape(.rect(cornerRadius: 6))
                                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                                            }
                                        }
                                        
                                        if let remark = item.remark, !remark.isEmpty {
                                            Text("备注: \(remark)")
                                                .scaledFont(12)
                                                .foregroundStyle(Color.muted)
                                        }
                                    }
                                }
                            }
                            if hasMoreProducts {
                                Color.clear.frame(height: 10)
                                    .task { await loadMoreProducts() }
                            }
                        }
                    }
                } else {
                    // 差异流水明细
                    if logs.isEmpty && !isLoading {
                        AppCard(padding: 32) {
                            VStack(spacing: 8) {
                                Image(systemName: "doc.plaintext")
                                    .scaledFont(36)
                                    .foregroundStyle(Color.muted)
                                Text("暂无变动流水记录")
                                    .scaledFont(14, weight: .medium)
                                    .foregroundStyle(Color.muted)
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
                                                .scaledFont(14, weight: .bold)
                                                .foregroundStyle(Color.ink)
                                            Spacer()
                                            StatusPill(text: log.operationTypeLabel)
                                        }
                                        
                                        Text("\(formatDateOnly(log.businessDate)) · \(log.creator?.displayName ?? "-")")
                                            .scaledFont(12)
                                            .foregroundStyle(Color.muted)
                                            
                                        let qty = log.changeQuantity ?? 0.0
                                        Text("变动数量：\(String(format: "%g", qty)) \(log.product?.unit ?? "g")")
                                            .scaledFont(13, weight: .medium)
                                            .foregroundStyle(Color.appPrimary)
                                    }
                                }
                            }
                            if hasMoreLogs {
                                Color.clear.frame(height: 10)
                                    .task { await loadMoreLogs() }
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
            ApiClient.shared.clearResponseCache()
            await loadData()
        }
        .id("diff_\(selectedTab)")
        .sheet(item: $writeOffProduct) { prod in
            NavigationStack {
                VStack(spacing: 16) {
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("\(prod.productCode ?? "") · \(prod.displayName)")
                                .scaledFont(15, weight: .bold)
                            
                            Text("销账类型：\(writeOffType == "WRITE_OFF_RECEIPT" ? "入库销账" : "销库销账")")
                                .scaledFont(13)
                                .foregroundStyle(Color.muted)
                            
                            TextField("请输入销账数量 (\(prod.unit ?? "g"))", text: $writeOffQuantityText)
                                .keyboardType(.decimalPad)
                                .padding(.horizontal, 12)
                                .frame(height: 44)
                                .background(Color.surface)
                                .clipShape(.rect(cornerRadius: 8))
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
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 46)
                        .background(Color.appPrimary)
                        .clipShape(.rect(cornerRadius: 8))
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
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        productsPage = 1
        logsPage = 1
        hasMoreProducts = true
        hasMoreLogs = true
        
        do {
            async let fetchStats = ApiClient.shared.fetchDifferenceStats()
            async let fetchProds = ApiClient.shared.fetchDifferences(page: 1)
            async let fetchLgs = ApiClient.shared.fetchDifferenceLogs(page: 1)
            
            let (loadedStats, loadedProds, loadedLogs) = try await (fetchStats, fetchProds, fetchLgs)
            self.stats = loadedStats
            self.products = loadedProds
            self.logs = loadedLogs
            if loadedProds.count < 30 { hasMoreProducts = false }
            if loadedLogs.count < 20 { hasMoreLogs = false }
        } catch {
            errorMessage = error.localizedDescription
        }
        if currentTaskID == taskID { isLoading = false }
    }
    
    private func loadMoreProducts() async {
        guard hasMoreProducts, !isLoadingMore else { return }
        isLoadingMore = true
        productsPage += 1
        do {
            let newProds = try await ApiClient.shared.fetchDifferences(page: productsPage)
            if newProds.count < 30 { hasMoreProducts = false }
            self.products.append(contentsOf: newProds)
        } catch {
            hasMoreProducts = false
        }
        isLoadingMore = false
    }
    
    private func loadMoreLogs() async {
        guard hasMoreLogs, !isLoadingMore else { return }
        isLoadingMore = true
        logsPage += 1
        do {
            let newLogs = try await ApiClient.shared.fetchDifferenceLogs(page: logsPage)
            if newLogs.count < 20 { hasMoreLogs = false }
            self.logs.append(contentsOf: newLogs)
        } catch {
            hasMoreLogs = false
        }
        isLoadingMore = false
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
    @State private var currentTaskID: UUID = UUID()
    
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
                                .scaledFont(16, weight: .bold)
                            
                            Divider().foregroundStyle(Color.cardBorder)
                            
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
                                Text("选择商品 *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                TextField("搜索商品名称、编码或条码", text: $keyword)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .clipShape(.rect(cornerRadius: 8))
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
                                                        HighlightedText(
                                                            text: item.displayName,
                                                            keyword: keyword,
                                                            font: .system(size: (13) * ThemeManager.shared.fontScale),
                                                            regularColor: .ink,
                                                            highlightColor: .appPrimary,
                                                            weight: .semibold
                                                        )
                                                        HighlightedText(
                                                            text: "\(item.productCode ?? "") · 规格：\(item.specification ?? "-")",
                                                            keyword: keyword,
                                                            font: .system(size: (11) * ThemeManager.shared.fontScale),
                                                            regularColor: .muted,
                                                            highlightColor: .appPrimary
                                                        )
                                                    }
                                                    Spacer()
                                                    if selectedProduct?.id == item.id {
                                                        Image(systemName: "checkmark.circle.fill")
                                                            .foregroundStyle(Color.appPrimary)
                                                    }
                                                }
                                                .padding(10)
                                                .background(selectedProduct?.id == item.id ? Color.appPrimary.opacity(0.08) : Color.surface)
                                                .clipShape(.rect(cornerRadius: 6))
                                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(selectedProduct?.id == item.id ? Color.appPrimary : Color.cardBorder, lineWidth: 1))
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 差异数量
                            VStack(alignment: .leading, spacing: 6) {
                                Text("数量 (\(selectedProduct?.unit ?? "g")) *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                TextField("输入差异变动数量", text: $quantityText)
                                    .keyboardType(.decimalPad)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .clipShape(.rect(cornerRadius: 8))
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                        }
                    }
                    
                    if let error = errorMessage {
                        AppCard(padding: 12) {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Color.danger)
                                Text(error).scaledFont(13).foregroundStyle(Color.danger)
                            }
                        }
                    }
                    
                    Button(action: submitRegister) {
                        HStack {
                            if isSubmitting {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            }
                            Text("确认登记")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(canSubmit ? Color.appPrimary : Color.appPrimary.opacity(0.4))
                        .clipShape(.rect(cornerRadius: 10))
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

