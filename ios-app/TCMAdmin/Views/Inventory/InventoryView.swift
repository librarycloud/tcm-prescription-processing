import SwiftUI

@MainActor
struct InventoryView: View {
    @AppStorage("inventory_search_history") private var searchHistoryString: String = ""
    @State private var searchText = ""
    @State private var selectedStoreId: Int? = nil
    @State private var stores: [StoreItem] = []
    @State private var items: [InventoryItem] = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var searchTask: Task<Void, Never>? = nil
    
    private var searchHistory: [String] {
        searchHistoryString.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
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
        let hasLatinLetter = text.contains(where: { $0.isLetter && $0.isASCII })
        return chineseCount >= 2 || digitCount >= 4 || hasLatinLetter
    }

    private func clearSearchHistory() {
        searchHistoryString = ""
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // 1. 顶部搜索与扫码区
            VStack(spacing: 10) {
                SearchBarField(
                    text: $searchText,
                    placeholder: "输入拼音、名称或扫码...",
                    onSearch: {
                        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                        if term.isEmpty {
                            items = []
                            return
                        }
                        addSearchHistory(term)
                        Task { await loadInventory() }
                    },
                    onScan: {
                        Router.shared.presentScanner(enableOCR: true)
                    }
                )
                .onChange(of: searchText) {
                    searchTask?.cancel()
                    let term = searchText.trimmingCharacters(in: .whitespaces)
                    
                    if term.isEmpty {
                        self.items = []
                        return
                    }
                    
                    if shouldAutoSearchQuery(term) {
                        searchTask = Task {
                            do {
                                try await Task.sleep(nanoseconds: 500_000_000)
                                if !Task.isCancelled {
                                    addSearchHistory(term)
                                    await loadInventory()
                                }
                            } catch {}
                        }
                    }
                }
                
                // 搜索历史标签 (当搜索框为空且有历史记录时展示)
                if searchText.isEmpty && !searchHistory.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text("历史搜索")
                                .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .medium))
                                .foregroundColor(.muted)
                            Spacer()
                            Button(action: clearSearchHistory) {
                                Image(systemName: "trash")
                                    .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                            }
                        }
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(searchHistory, id: \.self) { historyTerm in
                                    Button(action: {
                                        searchText = historyTerm
                                        addSearchHistory(historyTerm)
                                        Task { await loadInventory() }
                                    }) {
                                        Text(historyTerm)
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.ink)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 4)
                                            .background(Color.surface)
                                            .cornerRadius(12)
                                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.cardBorder, lineWidth: 1))
                                    }
                                }
                            }
                        }
                    }
                    .padding(.top, 2)
                }
                
                // 2. 门店筛选区 (StoreChipsRow)
                if stores.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        SegmentedButton(
                            label: "全部门店",
                            isSelected: selectedStoreId == nil,
                            action: {
                                selectedStoreId = nil
                                let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                                if !term.isEmpty {
                                    Task { await loadInventory() }
                                }
                            }
                        )
                        
                        ForEach(stores) { store in
                            SegmentedButton(
                                label: store.name,
                                isSelected: selectedStoreId == store.id,
                                action: {
                                    selectedStoreId = store.id
                                    let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                                    if !term.isEmpty {
                                        Task { await loadInventory() }
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
            
            // 3. 列表区域
            if isLoading && items.isEmpty {
                Spacer()
                ProgressView("正在查询药品库存...")
                Spacer()
            } else if let error = errorMessage {
                Spacer()
                Text(error).foregroundColor(.danger).font(.system(size: (14) * ThemeManager.shared.fontScale)).padding()
                Spacer()
            } else if items.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "archivebox")
                        .font(.system(size: (48) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                    if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        Text("搜索或扫码查看商品库存与批次详情")
                            .font(.system(size: (15) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                    } else {
                        Text("暂无药品库存数据")
                            .font(.system(size: (15) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                        
                        Button(action: {
                            Router.shared.presentScanner(enableOCR: true)
                        }) {
                            Text("重新扫描")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                .foregroundColor(.appPrimary)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.appPrimary, lineWidth: 1))
                        }
                    }
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(items) { item in
                            InventoryRowView(item: item)
                        }
                    }
                    .padding(16)
                }
                .refreshable {
                    let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !term.isEmpty {
                        await loadInventory()
                    }
                }
            }
        }
        .background(Color.pageBackground)
        .scrollDismissesKeyboard(.interactively)
        .task {
            await loadInitialData()
        }
    }
    
    private func loadInitialData() async {
        // 独立获取门店，如果权限不足失败则忽略
        if let storesResult = try? await ApiClient.shared.fetchStores() {
            self.stores = storesResult
        }
        
        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if term.isEmpty {
            self.items = []
            return
        }
        await loadInventory()
    }
    
    private func loadInventory() async {
        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        if term.isEmpty {
            self.items = []
            self.errorMessage = nil
            return
        }
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        do {
            self.items = try await ApiClient.shared.fetchInventory(keyword: term, storeId: selectedStoreId)
        } catch {
            self.errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

// MARK: - 列表单行卡片
@MainActor
struct InventoryRowView: View {
    let item: InventoryItem
    
    var body: some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 8) {
                // Row 1: Icon, Code · Name, Price
                HStack(alignment: .top) {
                    Image(systemName: "cross.case.fill")
                        .foregroundColor(.appPrimary)
                        .font(.system(size: (14) * ThemeManager.shared.fontScale))
                        .padding(.top, 2)
                    
                    let code = item.productCode?.isEmpty == false ? item.productCode! : "-"
                    Text("\(code) · \(item.name)")
                        .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.ink)
                        .lineLimit(2)
                    
                    Spacer()
                    
                    if let price = item.retailPrice, price > 0 {
                        Text("¥\(String(format: "%.2f", price))")
                            .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                            .foregroundColor(.danger)
                    }
                }
                
                // Row 2: Spec, Unit
                HStack {
                    Text("规格：\(item.specification?.isEmpty == false ? item.specification! : "-")")
                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                    Spacer()
                    Text("单位：\(item.unit?.isEmpty == false ? item.unit! : "-")")
                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                }
                
                // Row 3: Manufacturer, Barcode
                HStack {
                    Text("厂家：\(item.manufacturer?.isEmpty == false ? item.manufacturer! : "-")")
                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    
                    Text("条码：\(item.barcode?.isEmpty == false ? item.barcode! : "无条码")")
                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                        .lineLimit(1)
                }
            }
        }
        .onTapGesture {
            Router.shared.navigate(to: .inventoryDetail(item: item))
        }
    }
}
