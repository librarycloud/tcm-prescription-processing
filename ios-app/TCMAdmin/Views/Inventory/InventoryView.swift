import SwiftUI

@MainActor
struct InventoryView: View {
    @AppStorage("inventory_search_history") private var searchHistoryString: String = ""
    @State private var searchText = ""
    @State private var selectedStoreId: Int? = nil
    @State private var stores: [StoreItem] = []
    @State private var items: [InventoryItem] = []
    @State private var selectedProduct: InventoryItem? = nil // 1:1 对齐 Android selectedProduct
    @State private var isLoading = false
    @State private var currentTaskID: UUID = UUID()
    @State private var errorMessage: String? = nil
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var hasAutoNavigated = false // 1:1 对齐 Android hasAutoNavigated
    @State private var lastSearchedTerm: String = ""
    @Environment(\.horizontalSizeClass) private var sizeClass
    
    private var gridColumns: [GridItem] {
        if sizeClass == .regular {
            return [GridItem(.adaptive(minimum: 340, maximum: .infinity), spacing: 12)]
        } else {
            return [GridItem(.flexible())]
        }
    }
    
    private var searchHistory: [String] {
        var seen = Set<String>()
        var result: [String] = []
        for item in searchHistoryString.components(separatedBy: ",") {
            let trimmed = item.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty && !seen.contains(trimmed) {
                seen.insert(trimmed)
                result.append(trimmed)
            }
        }
        return result
    }
    
    private func addSearchHistory(_ term: String) {
        let t = term.trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return }
        var list = [t] + searchHistory.filter { $0 != t }
        if list.count > 8 { list = Array(list.prefix(8)) }
        searchHistoryString = list.joined(separator: ",")
    }
    
    private func shouldAutoSearchQuery(_ value: String) -> Bool {
        let text = value.trimmingCharacters(in: .whitespaces)
        if text.isEmpty { return false }
        let chineseCount = text.filter { $0 >= "\u{4E00}" && $0 <= "\u{9FA5}" }.count
        let digitCount = text.filter { $0.isNumber }.count
        return chineseCount >= 2 || digitCount >= 4
    }

    private func clearSearchHistory() {
        searchHistoryString = ""
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // 1. 顶部搜索栏与扫码入口 (置顶固定，1:1 对齐 Android)
            VStack(spacing: 10) {
                SearchBarField(
                    text: $searchText,
                    placeholder: "输入拼音、名称或扫码...",
                    onSearch: {
                        searchTask?.cancel()
                        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                        if term.isEmpty {
                            self.items = []
                            self.isLoading = false
                            self.selectedProduct = nil
                            self.hasAutoNavigated = false
                            self.lastSearchedTerm = ""
                            return
                        }
                        ApiClient.shared.clearResponseCache()
                        addSearchHistory(term)
                        lastSearchedTerm = term
                        hasAutoNavigated = false
                        self.selectedProduct = nil
                        searchTask = Task { await loadInventory(allowAutoNavigate: true) }
                    },
                    onScan: {
                        ApiClient.shared.clearResponseCache()
                        Router.shared.presentScanner(enableOCR: true)
                    }
                )
                .onChange(of: searchText) {
                    let term = searchText.trimmingCharacters(in: .whitespaces)
                    
                    if term.isEmpty {
                        searchTask?.cancel()
                        self.items = []
                        self.selectedProduct = nil
                        self.hasAutoNavigated = false
                        self.lastSearchedTerm = ""
                        return
                    }
                    
                    // 已在 onReceive 或 onSearch 搜索过相同关键词则跳过
                    if term == lastSearchedTerm {
                        return
                    }
                    
                    self.hasAutoNavigated = false
                    searchTask?.cancel()
                    if shouldAutoSearchQuery(term) {
                        searchTask = Task {
                            do {
                                try await Task.sleep(nanoseconds: 300_000_000)
                                if !Task.isCancelled {
                                    ApiClient.shared.clearResponseCache()
                                    lastSearchedTerm = term
                                    await loadInventory(allowAutoNavigate: true)
                                }
                            } catch {}
                        }
                    }
                }
                
                // 搜索历史标签 (当搜索框为空且未选中商品时展示)
                if searchText.isEmpty && selectedProduct == nil && !searchHistory.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("历史搜索")
                                .scaledFont(12, weight: .medium)
                                .foregroundStyle(Color.muted)
                            Spacer()
                            Button(action: clearSearchHistory) {
                                Image(systemName: "trash")
                                    .scaledFont(11)
                                    .foregroundStyle(Color.muted)
                            }
                        }
                        FlowLayout(spacing: 8, lineSpacing: 8) {
                            ForEach(searchHistory, id: \.self) { historyTerm in
                                Button(action: {
                                    searchText = historyTerm
                                    addSearchHistory(historyTerm)
                                    hasAutoNavigated = false
                                    lastSearchedTerm = historyTerm
                                    selectedProduct = nil
                                    searchTask?.cancel()
                                    searchTask = Task { await loadInventory(allowAutoNavigate: true) }
                                }) {
                                    Text(historyTerm)
                                        .scaledFont(14)
                                        .foregroundStyle(Color.ink)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(Color.surface)
                                        .clipShape(.rect(cornerRadius: 16))
                                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.cardBorder, lineWidth: 1))
                                }
                            }
                        }
                    }
                    .padding(.top, 2)
                }
                
                // 2. 门店筛选区 (当 stores > 1 且未选中商品时展示)
                if stores.count > 1 && selectedProduct == nil {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(
                                label: "全部门店",
                                isSelected: selectedStoreId == nil,
                                action: {
                                    selectedStoreId = nil
                                    hasAutoNavigated = false
                                    let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                                    if !term.isEmpty {
                                        Task { await loadInventory(allowAutoNavigate: false) }
                                    }
                                }
                            )
                            
                            ForEach(stores) { store in
                                SegmentedButton(
                                    label: store.name,
                                    isSelected: selectedStoreId == store.id,
                                    action: {
                                        selectedStoreId = store.id
                                        hasAutoNavigated = false
                                        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                                        if !term.isEmpty {
                                            Task { await loadInventory(allowAutoNavigate: false) }
                                        }
                                    }
                                )
                            }
                        }
                        .padding(.horizontal, 2)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 8)
            .background(Color.pageBackground)
            
            // 3. 核心内容区域：详情展示 / 搜索结果列表 / 空状态 (可滚动，支持下拉刷新)
            ZStack(alignment: .top) {
                // 底层：搜索结果列表
                AppScrollView {
                    if isLoading && items.isEmpty {
                        VStack(spacing: 12) {
                            Spacer().frame(height: 60)
                            ProgressView("正在查询药品库存...")
                            Spacer().frame(height: 60)
                        }
                        .frame(maxWidth: .infinity)
                    } else if let error = errorMessage, !error.isEmpty {
                        VStack(spacing: 12) {
                            Spacer().frame(height: 40)
                            Text(error)
                                .foregroundStyle(Color.danger)
                                .scaledFont(14)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 16)
                            Button("点击重试") {
                                ApiClient.shared.clearResponseCache()
                                Task { await loadInventory(allowAutoNavigate: false) }
                            }
                            .scaledFont(14, weight: .bold)
                            .foregroundStyle(Color.appPrimaryDark)
                            Spacer().frame(height: 40)
                        }
                        .frame(maxWidth: .infinity)
                    } else if items.isEmpty {
                        VStack(spacing: 12) {
                            Spacer().frame(height: 50)
                            Image(systemName: "archivebox")
                                .scaledFont(48)
                                .foregroundStyle(Color.muted)
                            if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                Text("搜索或扫码查看商品库存与批次详情")
                                    .scaledFont(15)
                                    .foregroundStyle(Color.muted)
                            } else {
                                Text("暂无药品库存数据")
                                    .scaledFont(15)
                                    .foregroundStyle(Color.muted)
                                
                                Button(action: {
                                    ApiClient.shared.clearResponseCache()
                                    Router.shared.presentScanner(enableOCR: true)
                                }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "qrcode.viewfinder")
                                        Text("重新扫描")
                                    }
                                    .scaledFont(13, weight: .semibold)
                                    .foregroundStyle(Color.appPrimaryDark)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.appPrimary, lineWidth: 1))
                                }
                            }
                            Spacer().frame(height: 50)
                        }
                        .frame(maxWidth: .infinity)
                    } else {
                        LazyVGrid(columns: gridColumns, spacing: 12) {
                            ForEach(items) { item in
                                InventoryRowView(item: item, keyword: searchText)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                                        withAnimation(.easeInOut(duration: 0.2)) {
                                            self.selectedProduct = item
                                        }
                                    }
                            }
                        }
                        .padding(16)
                    }
                }
                .allowsHitTesting(selectedProduct == nil)
                
                // 顶层：详情页
                if let product = selectedProduct {
                    AppScrollView {
                        productDetailSection(for: product)
                    }
                    .background(Color.pageBackground)
                    .transition(.move(edge: .trailing).combined(with: .opacity))
                    .zIndex(1)
                }
            }
            .refreshable {
                ApiClient.shared.clearResponseCache()
                if let product = selectedProduct {
                    if let keyword = product.productCode ?? product.barcode {
                        if let res = try? await ApiClient.shared.fetchInventory(keyword: keyword, storeId: selectedStoreId) {
                            if let updated = res.first(where: { $0.id == product.id }) {
                                self.selectedProduct = updated
                            }
                        }
                    }
                } else {
                    await loadInventory(allowAutoNavigate: false)
                }
            }
        }
        .background(Color.pageBackground)
        .scrollDismissesKeyboard(.interactively)
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("SearchInventoryByBarcode"))) { notif in
            if let code = notif.object as? String, !code.isEmpty {
                ApiClient.shared.clearResponseCache()
                self.selectedProduct = nil
                self.hasAutoNavigated = false
                self.lastSearchedTerm = code
                self.searchText = code
                self.addSearchHistory(code)
                self.searchTask?.cancel()
                self.searchTask = Task {
                    await self.loadInventory(allowAutoNavigate: true)
                }
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("SearchInventoryByBarcode_DirectlyShowDetail"))) { notif in
            if let dict = notif.object as? [String: Any],
               let code = dict["code"] as? String,
               let item = dict["item"] as? InventoryItem {
                ApiClient.shared.clearResponseCache()
                self.hasAutoNavigated = true
                self.lastSearchedTerm = code
                self.searchText = code
                self.addSearchHistory(code)
                self.items = [item]
                self.selectedProduct = item
            }
        }
        .task {
            await loadInitialData()
        }
    }
    
    // MARK: - 商品详情展示区 (1:1 对齐 Android 页面内展开)
    private func productDetailSection(for item: InventoryItem) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            // 返回列表按钮栏
            HStack(alignment: .center) {
                SectionHeader(title: "商品信息")
                Spacer()
                
                HStack(spacing: 4) {
                    Image(systemName: "arrow.backward")
                        .scaledFont(12)
                    Text("返回列表")
                        .scaledFont(12, weight: .medium)
                }
                .foregroundStyle(Color.appPrimaryDark)
                // 增大点击热区，保持 onTapGesture 以防 ScrollView 延迟
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color.appPrimarySoft)
                .clipShape(.rect(cornerRadius: 6))
                .contentShape(Rectangle())
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selectedProduct = nil
                    }
                }
            }
            .padding(.top, 4)
            
            // 基本信息卡片
            AppCard(padding: 16) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(alignment: .top) {
                        Text(item.name)
                            .scaledFont(16, weight: .bold)
                            .foregroundStyle(Color.ink)
                        Spacer()
                        if let price = item.retailPrice, price > 0 {
                            Text("¥\(String(format: "%.2f", price))")
                                .scaledFont(16, weight: .bold)
                                .foregroundStyle(Color.danger)
                        }
                    }
                    
                    Divider().foregroundStyle(Color.cardBorder)
                    
                    InfoRowItem(label: "商品编码", value: item.productCode?.isEmpty == false ? item.productCode! : "-")
                    InfoRowItem(label: "商品条码", value: item.barcode?.isEmpty == false ? item.barcode! : "无条码")
                    
                    HStack {
                        Text("规格：\(item.specification?.isEmpty == false ? item.specification! : "-")")
                            .scaledFont(13)
                            .foregroundStyle(Color.muted)
                        Spacer()
                        Text("单位：\(item.unit?.isEmpty == false ? item.unit! : "-")")
                            .scaledFont(13)
                            .foregroundStyle(Color.muted)
                    }
                    
                    HStack {
                        Text("生产厂商")
                            .scaledFont(13)
                            .foregroundStyle(Color.muted)
                        Spacer()
                        Text(item.manufacturer?.isEmpty == false ? item.manufacturer! : "-")
                            .scaledFont(13)
                            .foregroundStyle(Color.muted)
                    }
                    
                    // 高亮总库存 Banner
                    HStack(alignment: .firstTextBaseline) {
                        Text("总库存：")
                            .scaledFont(13)
                            .foregroundStyle(Color.ink)
                        Text("\(String(format: "%g", item.displayStock))")
                            .scaledFont(24, weight: .bold)
                            .foregroundStyle(item.displayStock > 0 ? Color.appPrimaryDark : Color.danger)
                        Text(item.displayUnit)
                            .scaledFont(13)
                            .foregroundStyle(Color.ink)
                        
                        Spacer()
                        
                        Text("共 ")
                            .scaledFont(13)
                            .foregroundStyle(Color.ink)
                        Text("\(item.inventories?.count ?? 0)")
                            .scaledFont(20, weight: .bold)
                            .foregroundStyle(Color.appPrimaryDark)
                        Text(" 个库存批次")
                            .scaledFont(13)
                            .foregroundStyle(Color.ink)
                    }
                    .padding(.vertical, 10)
                    .padding(.horizontal, 12)
                    .background(Color.appPrimarySoft)
                    .clipShape(.rect(cornerRadius: 8))
                }
            }
            
            // 批次信息
            Text("库存批次明细")
                .scaledFont(14, weight: .bold)
                .foregroundStyle(Color.ink)
                .padding(.top, 8)
            
            if let batches = item.inventories, !batches.isEmpty {
                LazyVGrid(columns: gridColumns, spacing: 12) {
                    ForEach(batches) { batch in
                        AppCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack(alignment: .center) {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text("批号：\(batch.batchNo?.isEmpty == false ? batch.batchNo! : "-")")
                                            .scaledFont(14, weight: .semibold)
                                            .foregroundStyle(Color.ink)
                                        
                                        HStack(spacing: 0) {
                                            Text("货位：")
                                                .scaledFont(14)
                                                .foregroundStyle(Color.muted)
                                            
                                            let locName = batch.locationName ?? ""
                                            let locCode = batch.locationCode ?? ""
                                            let loc = !locCode.isEmpty && !locName.isEmpty ? "\(locCode)-\(locName)" : (!locCode.isEmpty ? locCode : (!locName.isEmpty ? locName : ""))
                                            Text(loc.isEmpty ? "未分配" : loc)
                                                .scaledFont(16, weight: .black)
                                                .foregroundStyle(loc.isEmpty ? Color.muted : Color.appPrimaryDark)
                                                .padding(.horizontal, loc.isEmpty ? 0 : 6)
                                                .padding(.vertical, loc.isEmpty ? 0 : 2)
                                                .background(loc.isEmpty ? Color.clear : Color.appPrimarySoft)
                                                .clipShape(.rect(cornerRadius: 4))
                                        }
                                    }
                                    
                                    Spacer()
                                    
                                    VStack(alignment: .trailing, spacing: 2) {
                                        HStack(alignment: .firstTextBaseline, spacing: 3) {
                                            let qty = batch.quantity ?? 0.0
                                            Text(String(format: "%g", qty))
                                                .scaledFont(24, weight: .bold)
                                                .foregroundStyle(qty <= 0 ? Color.danger : Color.appPrimaryDark)
                                            Text(item.displayUnit)
                                                .scaledFont(12, weight: .medium)
                                                .foregroundStyle(Color.muted)
                                        }
                                    }
                                }
                                
                                let pDate = formatDateOnly(batch.productionDate)
                                let eDate = formatDateOnly(batch.expiryDate)
                                let iDate = formatDateOnly(batch.inboundDate)
                                
                                if pDate != "-" || eDate != "-" || iDate != "-" {
                                    Divider().foregroundStyle(Color.cardBorder).padding(.top, 4)
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("生产日期").scaledFont(9).foregroundStyle(Color.muted)
                                            Text(pDate).scaledFont(10).foregroundStyle(Color.ink).lineLimit(1)
                                        }.frame(maxWidth: .infinity, alignment: .leading)
                                        
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("有效期至").scaledFont(9).foregroundStyle(Color.muted)
                                            Text(eDate).scaledFont(10).foregroundStyle(Color.ink).lineLimit(1)
                                        }.frame(maxWidth: .infinity, alignment: .leading)
                                        
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("入库日期").scaledFont(9).foregroundStyle(Color.muted)
                                            Text(iDate).scaledFont(10).foregroundStyle(Color.muted).lineLimit(1)
                                        }.frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                    .padding(.top, 2)
                                }
                            }
                        }
                    }
                }
            } else {
                AppCard(padding: 24) {
                    HStack {
                        Spacer()
                        VStack(spacing: 8) {
                            Image(systemName: "archivebox")
                                .scaledFont(32)
                                .foregroundStyle(Color.muted)
                            Text("该商品暂无库存批次")
                                .scaledFont(14)
                                .foregroundStyle(Color.muted)
                        }
                        Spacer()
                    }
                }
            }
        }
        .padding(16)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 30, coordinateSpace: .local)
                .onEnded { value in
                    // 从左侧边缘 (X<40) 往右滑 (translation>50)
                    if value.startLocation.x < 40 && value.translation.width > 50 {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            selectedProduct = nil
                        }
                    }
                }
        )
    }
    
    private func loadInitialData() async {
        if let storesResult = try? await ApiClient.shared.fetchStores() {
            self.stores = storesResult
            if storesResult.count == 1 && selectedStoreId == nil {
                self.selectedStoreId = storesResult.first?.id
            }
        }
        guard items.isEmpty else { return }
        
        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if term.isEmpty {
            return
        }
        await loadInventory(allowAutoNavigate: false)
    }
    
    private func loadInventory(allowAutoNavigate: Bool = true) async {
        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if term.isEmpty {
            self.items = []
            self.errorMessage = nil
            self.selectedProduct = nil
            return
        }
        
        let taskID = UUID()
        currentTaskID = taskID
        
        isLoading = true
        errorMessage = nil
        defer { 
            if currentTaskID == taskID {
                isLoading = false
            }
        }
        
        do {
            let res = try await ApiClient.shared.fetchInventory(keyword: term, storeId: selectedStoreId)
            guard !Task.isCancelled else { return }
            guard currentTaskID == taskID else { return }
            if allowAutoNavigate && res.count == 1 && !term.isEmpty && !hasAutoNavigated && selectedProduct == nil {
                hasAutoNavigated = true
                self.items = res
                self.selectedProduct = res.first
            } else {
                self.items = res
                if res.isEmpty {
                    self.selectedProduct = nil
                }
            }
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled else { return }
            let desc = error.localizedDescription
            if !desc.lowercased().contains("cancel") && !desc.isEmpty {
                self.errorMessage = desc
            }
        }
    }
}

// MARK: - 列表单行卡片
@MainActor
struct InventoryRowView: View {
    let item: InventoryItem
    var keyword: String = ""
    
    var body: some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 8) {
                // Row 1: Icon, Code · Name, Price
                HStack(alignment: .top) {
                    Image(systemName: "cross.case.fill")
                        .foregroundStyle(Color.appPrimaryDark)
                        .scaledFont(14)
                        .padding(.top, 2)
                    
                    let code = item.productCode?.isEmpty == false ? item.productCode! : "-"
                    HighlightedText(
                        text: "\(code) · \(item.name)",
                        keyword: keyword,
                        font: .system(size: (14) * ThemeManager.shared.fontScale),
                        regularColor: .ink,
                        highlightColor: .appPrimary,
                        weight: .bold
                    )
                    .lineLimit(2)
                    
                    Spacer()
                    
                    if let price = item.retailPrice, price > 0 {
                        Text("¥\(String(format: "%.2f", price))")
                            .scaledFont(15, weight: .bold)
                            .foregroundStyle(Color.danger)
                    }
                }
                
                // Row 2: Spec, Unit
                HStack {
                    HighlightedText(
                        text: "规格：\(item.specification?.isEmpty == false ? item.specification! : "-")",
                        keyword: keyword,
                        font: .system(size: (12) * ThemeManager.shared.fontScale),
                        regularColor: .muted,
                        highlightColor: .appPrimary
                    )
                    Spacer()
                    Text("单位：\(item.unit?.isEmpty == false ? item.unit! : "-")")
                        .scaledFont(12)
                        .foregroundStyle(Color.muted)
                }
                
                // Row 3: Manufacturer, Barcode
                HStack {
                    HighlightedText(
                        text: "厂家：\(item.manufacturer?.isEmpty == false ? item.manufacturer! : "-")",
                        keyword: keyword,
                        font: .system(size: (12) * ThemeManager.shared.fontScale),
                        regularColor: .muted,
                        highlightColor: .appPrimary
                    )
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    
                    HighlightedText(
                        text: "条码：\(item.barcode?.isEmpty == false ? item.barcode! : "无条码")",
                        keyword: keyword,
                        font: .system(size: (12) * ThemeManager.shared.fontScale),
                        regularColor: .muted,
                        highlightColor: .appPrimary
                    )
                    .lineLimit(1)
                }
            }
        }
    }
}
