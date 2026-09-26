import SwiftUI

struct HerbUnit: Identifiable {
    let id: String
    let type: String?
    let typeLabel: String
    let unitNo: Int?
    var locations: [HerbLocationItem]
}

@MainActor
struct HerbsView: View {
    @State private var searchText = ""
    @State private var type: String = ""
    @State private var selectedStoreId: Int? = nil
    @State private var stores: [StoreItem] = []
    @State private var data: HerbLocationData? = nil
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var currentTaskID: UUID = UUID()
    @State private var loadTask: Task<Void, Never>? = nil
    @Environment(\.horizontalSizeClass) private var sizeClass
    
    private var gridColumns: [GridItem] {
        if sizeClass == .regular {
            return [GridItem(.adaptive(minimum: 340, maximum: .infinity), spacing: 12)]
        } else {
            return [GridItem(.flexible())]
        }
    }
    
    private var isSuperAdmin: Bool {
        SessionManager.shared.currentUser?.role == 0
    }
    
    @State private var groupedUnits: [HerbUnit] = []
    @State private var updateTask: Task<Void, Never>? = nil
    
    private func updateGroupedUnits() {
        updateTask?.cancel()
        guard let locs = data?.locations else {
            groupedUnits = []
            return
        }
        
        let currentType = type
        let currentSearchText = searchText
        
        updateTask = Task.detached(priority: .userInitiated) {
            if Task.isCancelled { return }
            let locsByType = currentType.isEmpty ? locs : locs.filter { $0.type == currentType }
            let filtered: [HerbLocationItem]
            if currentSearchText.isEmpty {
                filtered = locsByType
            } else {
                let isSearchPinyin = currentSearchText.rangeOfCharacter(from: CharacterSet.letters.inverted) == nil
                filtered = locsByType.filter { loc in
                    (loc.code?.localizedCaseInsensitiveContains(currentSearchText) == true) ||
                    (loc.herbs?.contains(where: { herb in
                        if herb.name.localizedCaseInsensitiveContains(currentSearchText) { return true }
                        if herb.pinyin?.localizedCaseInsensitiveContains(currentSearchText) == true { return true }
                        if herb.code?.localizedCaseInsensitiveContains(currentSearchText) == true { return true }
                        if isSearchPinyin && herb.name.pinyinInitials.localizedCaseInsensitiveContains(currentSearchText) { return true }
                        return false
                    }) == true)
                }
            }
            
            if Task.isCancelled { return }
            var dict: [String: HerbUnit] = [:]
            for loc in filtered {
                let key = "\(loc.type ?? "")_\(loc.unitNo ?? 0)"
                if dict[key] == nil {
                    let tLabel: String
                    switch loc.type {
                    case "D": tLabel = "药斗"
                    case "G": tLabel = "药柜"
                    case "F": tLabel = "冰箱"
                    case "C": tLabel = "仓库"
                    default: tLabel = loc.type ?? ""
                    }
                    dict[key] = HerbUnit(id: key, type: loc.type, typeLabel: tLabel, unitNo: loc.unitNo, locations: [])
                }
                dict[key]?.locations.append(loc)
            }
            
            let sorted = dict.values.sorted { u1, u2 in
                if u1.type != u2.type { return (u1.type ?? "") < (u2.type ?? "") }
                return (u1.unitNo ?? 0) < (u2.unitNo ?? 0)
            }
            
            if Task.isCancelled { return }
            await MainActor.run {
                self.groupedUnits = sorted
            }
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 10) {
                SearchBarField(
                    text: $searchText,
                    placeholder: "搜索药材名称、拼音或位置编码",
                    onSearch: nil
                )
                
                if isSuperAdmin && !stores.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(
                                label: "全部门店",
                                isSelected: selectedStoreId == nil,
                                action: {
                                    selectedStoreId = nil
                                    startLoadData()
                                }
                            )
                            ForEach(stores) { store in
                                SegmentedButton(
                                    label: store.name,
                                    isSelected: selectedStoreId == store.id,
                                    action: {
                                        selectedStoreId = store.id
                                        startLoadData()
                                    }
                                )
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .onChange(of: selectedStoreId) { _, _ in NotificationCenter.default.post(name: NSNotification.Name("ScrollToTop"), object: nil) }
        .background(Color.pageBackground)
            
            if isLoading && data == nil {
                Spacer()
                ProgressView("正在加载斗谱货位...")
                Spacer()
            } else if let error = errorMessage {
                Spacer()
                Text(error).foregroundStyle(Color.danger)
                Button("重试") { startLoadData() }
                    .padding(.top, 8)
                Spacer()
            } else {
                AppScrollView {
                    VStack(spacing: 0) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("斗谱管理")
                                    .scaledFont(20, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("中药斗谱布局与货位药材维护")
                                    .scaledFont(13)
                                    .foregroundStyle(Color.muted)
                            }
                            Spacer()
                            Button(action: {
                                Router.shared.navigate(to: .herbLocationAssign(location: nil))
                            }) {
                                Text("配置药材")
                                    .scaledFont(13, weight: .bold)
                                    .foregroundStyle(Color.white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(Color.appPrimary)
                                    .clipShape(.rect(cornerRadius: 8))
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 16)
                        .padding(.bottom, 8)
                        
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                let types = [("" ,"全部区域"), ("D", "药斗"), ("G", "药柜"), ("F", "冰箱"), ("C", "仓库")]
                                ForEach(types, id: \.0) { item in
                                    SegmentedButton(
                                        label: item.1,
                                        isSelected: type == item.0,
                                        action: { type = item.0 }
                                    )
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 4)
                        }
                        
                        Group {
                            if sizeClass == .regular {
                                LazyVGrid(columns: [GridItem(.adaptive(minimum: 340), spacing: 12, alignment: .top)], spacing: 12) {
                                    ForEach(groupedUnits) { unit in
                                        unitCard(unit)
                                    }
                                }
                            } else {
                                LazyVStack(spacing: 12) {
                                    ForEach(groupedUnits) { unit in
                                        unitCard(unit)
                                    }
                                }
                            }
                        }
                        .padding(16)
                    } // End VStack
                }
                .refreshable {
                    ApiClient.shared.clearResponseCache()
                    await loadData()
                }
                
                .background(Color.pageBackground)
            }
        }
        .onChange(of: searchText) { _, _ in updateGroupedUnits() }
        .onChange(of: type) { _, _ in updateGroupedUnits() }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("ListNeedsRefresh_Herbs"))) { _ in
            ApiClient.shared.clearResponseCache()
            startLoadData()
        }
        .scrollDismissesKeyboard(.interactively)
        .task {
            if isSuperAdmin {
                async let storesTask: () = {
                    if let sts = try? await ApiClient.shared.fetchStores() {
                        if Task.isCancelled { return }
            await MainActor.run { self.stores = sts }
                    }
                }()
                async let dataTask: () = loadData()
                _ = await (storesTask, dataTask)
            } else {
                await loadData()
            }
        }
        .onDisappear {
            loadTask?.cancel()
        }
    }

    private func startLoadData() {
        loadTask?.cancel()
        loadTask = Task { await loadData() }
    }
    
    private func loadData() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        do {
            let result = try await ApiClient.shared.fetchHerbLocations(storeId: selectedStoreId)
            guard !Task.isCancelled else { return }
            self.data = result
            
            Task.detached(priority: .background) {
                for loc in result.locations ?? [] {
                    for herb in loc.herbs ?? [] {
                        _ = herb.name.pinyinInitials
                    }
                }
            }
            
            updateGroupedUnits()
        } catch {
            guard !Task.isCancelled else { return }
            self.errorMessage = error.localizedDescription
        }
        if currentTaskID == taskID { isLoading = false }
    }


    @ViewBuilder
    private func unitCard(_ unit: HerbUnit) -> some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Image(systemName: "shippingbox.fill")
                        .foregroundStyle(Color.appPrimary)
                    Text("\(unit.typeLabel) \(unit.unitNo ?? 0) 组")
                        .scaledFont(15, weight: .bold)
                        .foregroundStyle(Color.ink)
                    Spacer()
                    Text("共 \(unit.locations.count) 个位置")
                        .scaledFont(12)
                        .foregroundStyle(Color.muted)
                }
                
                ForEach(unit.locations) { loc in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            HighlightedText(
                                text: loc.code ?? "",
                                keyword: searchText,
                                font: .system(size: 13),
                                weight: .semibold
                            )
                            Spacer()
                            Text(positionLabel(for: loc))
                                .scaledFont(10)
                                .foregroundStyle(Color.appPrimaryDark)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.appPrimary.opacity(0.35), lineWidth: 1))
                        }
                        
                        Text(loc.typeLabel).scaledFont(11).foregroundStyle(Color.muted)
                        
                        if let herbs = loc.herbs, !herbs.isEmpty {
                            HighlightedText(
                                text: herbs.map { $0.name }.joined(separator: "、"),
                                keyword: searchText,
                                font: .system(size: 12),
                                weight: .medium
                            )
                            .padding(.top, 2)
                        } else {
                            Text("未配置药材（空置）").scaledFont(12).foregroundStyle(Color.muted)
                                .padding(.top, 2)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(Color.surface)
                    .clipShape(.rect(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                    .onTapGesture {
                        Router.shared.navigate(to: .herbLocationAssign(location: loc))
                    }
                }
            }
        }
    }

    private func positionLabel(for loc: HerbLocationItem) -> String {
        let type = loc.type ?? ""
        let unit = "\(loc.unitNo ?? 0)"
        let layer = "\(loc.layerNo ?? 0)"
        let column = "\(loc.columnNo ?? 0)"
        
        if type == "D" {
            let layerStr = layer == "0" ? "顶层" : "\(layer)行"
            return "斗\(unit) · \(layerStr) · \(column)列"
        } else {
            return "\(loc.typeLabel)\(unit) · \(layer) 层"
        }
    }
}


public struct MasonryLayout: Layout {
    public var minColumnWidth: CGFloat
    public var spacing: CGFloat
    
    public init(minColumnWidth: CGFloat = 340, spacing: CGFloat = 12) {
        self.minColumnWidth = minColumnWidth
        self.spacing = spacing
    }
    
    private func computeColumns(in boundsWidth: CGFloat) -> Int {
        let maxColumns = Int((boundsWidth + spacing) / (minColumnWidth + spacing))
        return max(1, maxColumns)
    }
    
    public func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.replacingUnspecifiedDimensions().width
        let columns = computeColumns(in: width)
        let columnWidth = (width - CGFloat(columns - 1) * spacing) / CGFloat(columns)
        var columnHeights = Array(repeating: CGFloat(0), count: columns)
        
        for subview in subviews {
            let shortestColumn = columnHeights.enumerated().min(by: { $0.element < $1.element })?.offset ?? 0
            let size = subview.sizeThatFits(ProposedViewSize(width: columnWidth, height: nil))
            columnHeights[shortestColumn] += size.height + spacing
        }
        
        let maxHeight = (columnHeights.max() ?? 0)
        return CGSize(width: width, height: max(0, maxHeight - (subviews.isEmpty ? 0 : spacing)))
    }
    
    public func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let columns = computeColumns(in: bounds.width)
        let columnWidth = (bounds.width - CGFloat(columns - 1) * spacing) / CGFloat(columns)
        var columnHeights = Array(repeating: bounds.minY, count: columns)
        
        for subview in subviews {
            let shortestColumn = columnHeights.enumerated().min(by: { $0.element < $1.element })?.offset ?? 0
            let x = bounds.minX + CGFloat(shortestColumn) * (columnWidth + spacing)
            let y = columnHeights[shortestColumn]
            
            let size = subview.sizeThatFits(ProposedViewSize(width: columnWidth, height: nil))
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(width: columnWidth, height: size.height))
            
            columnHeights[shortestColumn] += size.height + spacing
        }
    }
}
