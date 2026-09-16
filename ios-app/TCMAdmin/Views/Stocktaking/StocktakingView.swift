import SwiftUI

@MainActor
public struct StocktakingView: View {
    @ObservedObject private var router = Router.shared
    @State private var stocktakings: [StocktakingModel] = []
    @State private var stores: [StoreItem] = []
    @State private var selectedStoreId: Int? = nil
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var isCreateSheetPresented = false
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: 0) {
            // 顶部标题与操作栏
            VStack(spacing: 12) {
                SectionHeader(title: "商品盘点", subtitle: "商品盘点计划与差异录入") {
                    Button(action: { isCreateSheetPresented = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus.circle.fill")
                            Text("新建盘点")
                        }
                        .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color.appPrimary)
                        .cornerRadius(8)
                    }
                }
                
                // 门店筛选
                if stores.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(
                                label: "全部门店",
                                isSelected: selectedStoreId == nil,
                                action: {
                                    selectedStoreId = nil
                                    Task { await loadStocktakings() }
                                }
                            )
                            
                            ForEach(stores) { st in
                                SegmentedButton(
                                    label: st.name,
                                    isSelected: selectedStoreId == st.id,
                                    action: {
                                        selectedStoreId = st.id
                                        Task { await loadStocktakings() }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 14)
            .padding(.bottom, 8)
            .background(Color.pageBackground)
            
            // 列表
            if isLoading && stocktakings.isEmpty {
                Spacer()
                ProgressView("正在加载盘点任务...")
                    .frame(maxWidth: .infinity)
                Spacer()
            } else if let error = errorMessage {
                Spacer()
                Text(error).foregroundColor(.danger).font(.system(size: (14) * ThemeManager.shared.fontScale)).padding()
                Spacer()
            } else if stocktakings.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "checklist")
                        .font(.system(size: (48) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                    Text("暂无盘点任务")
                        .font(.system(size: (15) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(stocktakings) { item in
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack {
                                        Image(systemName: "checklist.checked")
                                            .foregroundColor(.appPrimary)
                                        Text(item.displayCheckNo)
                                            .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                            .foregroundColor(.ink)
                                        Spacer()
                                        StatusPill(text: item.statusText)
                                    }
                                    
                                    Text(item.checkName)
                                        .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .semibold))
                                        .foregroundColor(.ink)
                                    
                                    let total = item.summary?.total ?? item.totalCount ?? 0
                                    let counted = item.summary?.counted ?? item.checkedCount ?? 0
                                    let diff = item.summary?.adjustment ?? item.diffCount ?? 0
                                    let progress = total > 0 ? Double(counted) / Double(total) : 0.0
                                    
                                    // 3 统计指标
                                    HStack(spacing: 8) {
                                        MetricCellView(title: "总条目", value: "\(total)")
                                        MetricCellView(title: "已盘点", value: "\(counted)")
                                        MetricCellView(title: "有差异", value: "\(diff)", isWarning: diff > 0)
                                    }
                                    
                                    // 进度条
                                    ProgressView(value: min(max(progress, 0.0), 1.0))
                                        .progressViewStyle(LinearProgressViewStyle(tint: Color.appPrimary))
                                        .padding(.vertical, 2)
                                    
                                    HStack {
                                        Text(item.storeName ?? "-")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                        Spacer()
                                        Text(formatDateOnly(item.createdAt ?? ""))
                                            .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                    }
                                }
                            }
                            .onTapGesture {
                                router.navigate(to: .stocktakingDetail(checkId: item.id))
                            }
                        }
                    }
                    .padding(16)
                }
                .refreshable {
                    await loadStocktakings()
                }
            }
        }
        .sheet(isPresented: $isCreateSheetPresented) {
            CreateGoodsCheckSheet(stores: stores) {
                Task { await loadStocktakings() }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .task {
            async let fetchedStores: () = loadStores()
            async let fetchedChecks: () = loadStocktakings()
            _ = await (fetchedStores, fetchedChecks)
        }
        .background(Color.pageBackground)
        .navigationTitle("商品盘点")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func loadStores() async {
        do {
            self.stores = try await ApiClient.shared.fetchStores()
        } catch {}
    }
    
    private func loadStocktakings() async {
        guard !isLoading else { return }
        isLoading = true
        do {
            self.stocktakings = try await ApiClient.shared.fetchStocktakings(storeId: selectedStoreId)
        } catch {
            self.errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

// MARK: - 指标小单元格
private struct MetricCellView: View {
    let title: String
    let value: String
    var isWarning: Bool = false
    
    var body: some View {
        VStack(spacing: 3) {
            Text(title)
                .font(.system(size: (11) * ThemeManager.shared.fontScale))
                .foregroundColor(.muted)
            Text(value)
                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                .foregroundColor(isWarning ? .danger : .ink)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(isWarning ? Color.dangerSoft : Color.surfaceVariant)
        .cornerRadius(6)
    }
}

// MARK: - 新建盘点单 Sheet
struct CreateGoodsCheckSheet: View {
    @Environment(\.dismiss) private var dismiss
    let stores: [StoreItem]
    let onCreated: () -> Void
    
    @State private var checkName = ""
    @State private var checkType = 1 // 1: 日盘, 2: 月盘, 3: 抽盘
    @State private var selectedStoreId: Int? = nil
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    
    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("盘点单信息")) {
                    TextField("盘点单名称 (如：2026年3月全店盘点)", text: $checkName)
                    
                    Picker("盘点类型", selection: $checkType) {
                        Text("日盘").tag(1)
                        Text("月盘").tag(2)
                        Text("抽盘").tag(3)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    
                    if !stores.isEmpty {
                        Picker("盘点门店", selection: $selectedStoreId) {
                            Text("默认当前门店").tag(nil as Int?)
                            ForEach(stores) { st in
                                Text(st.name).tag(st.id as Int?)
                            }
                        }
                    }
                }
                
                if let err = errorMessage {
                    Section {
                        Text(err).foregroundColor(.danger).font(.system(size: (13) * ThemeManager.shared.fontScale))
                    }
                }
            }
            .navigationTitle("新建盘点单")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: submit) {
                        if isSubmitting {
                            ProgressView()
                        } else {
                            Text("创建").bold()
                        }
                    }
                    .disabled(checkName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSubmitting)
                }
            }
        }
    }
    
    private func submit() {
        isSubmitting = true
        errorMessage = nil
        Task {
            do {
                try await ApiClient.shared.createGoodsCheck(
                    name: checkName.trimmingCharacters(in: .whitespacesAndNewlines),
                    type: checkType,
                    storeId: selectedStoreId
                )
                await MainActor.run {
                    isSubmitting = false
                    dismiss()
                    onCreated()
                }
            } catch {
                await MainActor.run {
                    isSubmitting = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

// MARK: - 盘点单明细 (1:1 对齐 Android StocktakingDetailScreen)
@MainActor
public struct StocktakingDetailView: View {
    public let checkId: Int
    @Environment(\.dismiss) private var dismiss
    @State private var detail: StocktakingModel? = nil
    @State private var isLoading = false
    @State private var itemFilter = "all" // all, counted, mine, missing, recount, diff
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var errorMessage: String? = nil
    @State private var isFinishing = false
    @State private var showFinishAlert = false
    
    // 初盘/复盘录入状态
    @State private var candidateKeyword = ""
    @State private var candidates: [StocktakingCandidateModel] = []
    @State private var selectedCandidate: StocktakingCandidateModel? = nil
    @State private var countInputQty = ""
    @State private var isAddingItem = false
    @State private var recountTargetItem: StocktakingItemModel? = nil
    @State private var recountInputQty = ""
    @State private var isRecounting = false
    
    // 批次/货位编辑状态
    @State private var editTargetItem: StocktakingItemModel? = nil
    @State private var editBatchNo = ""
    @State private var editLocationCode = ""
    @State private var isUpdatingLocation = false
    
    public init(checkId: Int) {
        self.checkId = checkId
    }
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                if isLoading && detail == nil {
                    ProgressView("正在加载盘点明细...")
                        .frame(maxWidth: .infinity)
                        .padding(.top, 60)
                } else if let err = errorMessage, detail == nil {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.system(size: 44))
                            .foregroundColor(.orange)
                        Text("加载盘点明细失败")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.ink)
                        Text(err)
                            .font(.system(size: 13))
                            .foregroundColor(.muted)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                        Button(action: {
                            Task { await loadDetail() }
                        }) {
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.clockwise")
                                Text("点击重试")
                            }
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                            .background(Color.appPrimary)
                            .cornerRadius(8)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 60)
                } else if let item = detail {
                    if let err = errorMessage {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.circle.fill")
                                .foregroundColor(.danger)
                            Text(err)
                                .font(.system(size: 12))
                                .foregroundColor(.danger)
                            Spacer()
                            Button(action: { errorMessage = nil }) {
                                Image(systemName: "xmark")
                                    .foregroundColor(.muted)
                            }
                        }
                        .padding(10)
                        .background(Color.danger.opacity(0.1))
                        .cornerRadius(8)
                    }
                    
                    // 1. 顶部单据信息卡片
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text(item.checkName)
                                    .font(.system(size: (17) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                Spacer()
                                StatusPill(text: item.statusText)
                            }
                            
                            Text("单号：\(item.displayCheckNo)  ·  门店：\(item.storeName ?? "-")")
                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                            
                            Divider().foregroundColor(Color.cardBorder)
                            
                            // 6 维指标切换器
                            let summary = item.summary
                            let total = summary?.total ?? item.totalCount ?? 0
                            let counted = summary?.counted ?? item.checkedCount ?? 0
                            let missing = summary?.missing ?? 0
                            let recount = summary?.pendingRecount ?? 0
                            let diff = summary?.adjustment ?? item.diffCount ?? 0
                            let mine = summary?.mine ?? 0
                            
                            VStack(spacing: 8) {
                                HStack(spacing: 8) {
                                    filterCell(title: "总项数", count: total, key: "all")
                                    filterCell(title: "已盘", count: counted, key: "counted")
                                    filterCell(title: "我的记录", count: mine, key: "mine")
                                }
                                HStack(spacing: 8) {
                                    filterCell(title: "漏盘", count: missing, key: "missing", isWarning: missing > 0)
                                    filterCell(title: "待复盘", count: recount, key: "recount", isWarning: recount > 0)
                                    filterCell(title: "有差异", count: diff, key: "diff", isDanger: diff > 0)
                                }
                            }
                        }
                    }
                    
                    // 2. 扫码 / 录入初盘卡片
                    if item.status != 2 {
                        AppCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("快速录入盘点实货")
                                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                
                                SearchBarField(
                                    text: $candidateKeyword,
                                    placeholder: "搜索药材名称/编码/条码...",
                                    onSearch: { searchCandidates() },
                                    onScan: { Router.shared.presentScanner(enableOCR: true) }
                                )
                                .onChange(of: candidateKeyword) {
                                    searchTask?.cancel()
                                    let term = candidateKeyword.trimmingCharacters(in: .whitespaces)
                                    if term.isEmpty {
                                        candidates = []
                                        return
                                    }
                                    searchTask = Task {
                                        do {
                                            try await Task.sleep(nanoseconds: 500_000_000)
                                            guard !Task.isCancelled else { return }
                                            searchCandidates()
                                        } catch {}
                                    }
                                }
                                
                                // 候选药品下拉/选择
                                if !candidates.isEmpty {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text("匹配到 \(candidates.count) 个商品，请选择：")
                                            .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                        
                                         ForEach(Array(candidates.prefix(4))) { cand in
                                             CandidateRowView(
                                                 cand: cand,
                                                 isSelected: selectedCandidate?.id == cand.id,
                                                 onSelect: {
                                                     selectedCandidate = cand
                                                     countInputQty = ""
                                                 }
                                             )
                                         }
                                    }
                                }
                                
                                // 实盘数量录入
                                if let selected = selectedCandidate {
                                    HStack(spacing: 8) {
                                        TextField("输入实盘数量", text: $countInputQty)
                                            .keyboardType(.decimalPad)
                                            .textFieldStyle(RoundedBorderTextFieldStyle())
                                        
                                        Text(selected.unit ?? "")
                                            .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                        
                                        Button(action: submitInitialCount) {
                                            HStack(spacing: 4) {
                                                if isAddingItem { ProgressView() }
                                                Text("确认录入").bold()
                                            }
                                            .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 7)
                                            .background(Color.appPrimary)
                                            .cornerRadius(6)
                                        }
                                        .disabled(countInputQty.isEmpty || isAddingItem)
                                    }
                                    .padding(.top, 4)
                                }
                            }
                        }
                    }
                    
                    // 3. 盘点条目明细列表
                    let items = item.items ?? []
                    VStack(alignment: .leading, spacing: 8) {
                        SectionHeader(
                            title: "盘点条目明细",
                            subtitle: "共 \(items.count) 项"
                        )
                        
                        if items.isEmpty {
                            AppCard(padding: 20) {
                                HStack {
                                    Spacer()
                                    Text("当前筛选条件下无盘点条目")
                                        .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                        .foregroundColor(.muted)
                                    Spacer()
                                }
                            }
                        } else {
                            ForEach(items) { row in
                                AppCard(padding: 14) {
                                    VStack(alignment: .leading, spacing: 8) {
                                        HStack {
                                            Text(row.productName)
                                                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                                .foregroundColor(.ink)
                                            Spacer()
                                            let loc = row.displayLocation
                                            if !loc.isEmpty {
                                                Text(loc)
                                                    .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                                    .foregroundColor(.appPrimaryDark)
                                                    .padding(.horizontal, 6)
                                                    .padding(.vertical, 2)
                                                    .background(Color.appPrimarySoft)
                                                    .cornerRadius(4)
                                            }
                                        }
                                        
                                        HStack {
                                            Text("规格：\(row.productSpec)")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                            
                                            Text("·")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                            
                                            Text("批号：\(row.batchNo ?? "-")")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                                
                                            Spacer()
                                            
                                            if item.status != 2 && row.itemId != nil {
                                                Button(action: {
                                                    editTargetItem = row
                                                    editBatchNo = row.batchNo ?? ""
                                                    editLocationCode = row.displayLocation
                                                }) {
                                                    Image(systemName: "pencil")
                                                        .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                                        .foregroundColor(.appPrimary)
                                                        .padding(4)
                                                }
                                            }
                                        }
                                        
                                        Divider().foregroundColor(Color.cardBorder)
                                        
                                        HStack(spacing: 12) {
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("系统账面").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                                Text("\(formatQty(row.systemQty)) \(row.productUnit)").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                                            }
                                            Spacer()
                                            VStack(alignment: .leading, spacing: 2) {
                                                Text("初盘实物").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                                Text(row.firstCountQty.map { "\(formatQty($0)) \(row.productUnit)" } ?? "-")
                                                    .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                                            }
                                            Spacer()
                                            if row.recountQty != nil {
                                                VStack(alignment: .leading, spacing: 2) {
                                                    Text("复盘实物").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                                    Text("\(formatQty(row.recountQty)) \(row.productUnit)")
                                                        .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                                                }
                                                Spacer()
                                            }
                                            VStack(alignment: .trailing, spacing: 2) {
                                                Text("差异").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                                let diff = row.displayDiff
                                                Text("\(diff > 0 ? "+" : "")\(formatQty(diff))")
                                                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                                    .foregroundColor(diff == 0 ? .success : .danger)
                                            }
                                        }
                                        
                                        // 复盘操作入口
                                        if item.status != 2 && row.displayDiff != 0 && row.itemId != nil {
                                            Divider().foregroundColor(Color.cardBorder)
                                            HStack {
                                                Spacer()
                                                Button(action: {
                                                    recountTargetItem = row
                                                    recountInputQty = ""
                                                }) {
                                                    HStack(spacing: 4) {
                                                        Image(systemName: "arrow.triangle.2.circlepath")
                                                        Text("复盘录入")
                                                    }
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .semibold))
                                                    .foregroundColor(.appPrimary)
                                                    .padding(.horizontal, 10)
                                                    .padding(.vertical, 5)
                                                    .background(Color.appPrimarySoft)
                                                    .cornerRadius(6)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // 4. 完成盘点单按钮
                    if item.status != 2 {
                        Button(action: { showFinishAlert = true }) {
                            HStack {
                                if isFinishing { ProgressView().padding(.trailing, 6) }
                                Text("完成并结束本次盘点").bold()
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color.appPrimary)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                        }
                        .disabled(isFinishing)
                        .padding(.top, 8)
                    }
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "tray")
                            .font(.system(size: 40))
                            .foregroundColor(.muted)
                        Text("未找到盘点明细")
                            .font(.system(size: 14))
                            .foregroundColor(.muted)
                        Button("重新加载") {
                            Task { await loadDetail() }
                        }
                        .font(.system(size: 13))
                        .foregroundColor(.appPrimary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 60)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(16)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .alert("完成盘点确认", isPresented: $showFinishAlert) {
            Button("取消", role: .cancel) {}
            Button("确认完成", role: .destructive) { finishCheck() }
        } message: {
            Text("确认后将封存本次盘点数据，是否继续？")
        }
        .sheet(item: $editTargetItem) { target in
            NavigationStack {
                Form {
                    Section(header: Text("商品信息")) {
                        Text(target.productName).font(.headline)
                        Text("规格：\(target.productSpec)").foregroundColor(.muted)
                    }
                    
                    Section(header: Text("编辑批次与货位")) {
                        TextField("批号", text: $editBatchNo)
                        TextField("盘点货位", text: $editLocationCode)
                    }
                }
                .navigationTitle("修改信息")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { editTargetItem = nil }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") {
                            submitLocationUpdate(targetId: target.id)
                        }
                        .disabled(isUpdatingLocation)
                    }
                }
            }
        }
        .sheet(item: $recountTargetItem) { target in
            NavigationStack {
                Form {
                    Section(header: Text("复盘商品")) {
                        Text(target.productName).font(.headline)
                        Text("账面库存：\(formatQty(target.systemQty)) \(target.productUnit)").foregroundColor(.muted)
                        Text("初盘数量：\(target.firstCountQty.map { "\(formatQty($0)) \(target.productUnit)" } ?? "-")").foregroundColor(.muted)
                    }
                    
                    Section(header: Text("录入复盘实物数")) {
                        TextField("输入复盘数量", text: $recountInputQty)
                            .keyboardType(.decimalPad)
                    }
                }
                .navigationTitle("复盘实录")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { recountTargetItem = nil }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("提交") {
                            submitRecount(targetId: target.id)
                        }
                        .disabled(recountInputQty.isEmpty || isRecounting)
                    }
                }
            }
        }
        .background(Color.pageBackground)
        .navigationTitle("盘点明细")
        .navigationBarTitleDisplayMode(.inline)
        .task { await loadDetail() }
        .refreshable { await loadDetail() }
    }
    
    private func filterCell(title: String, count: Int, key: String, isWarning: Bool = false, isDanger: Bool = false) -> some View {
        Button(action: {
            itemFilter = key
            Task { await loadDetail() }
        }) {
            VStack(spacing: 2) {
                Text(title).font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                Text("\(count)").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(isDanger ? .danger : (isWarning ? .warning : (itemFilter == key ? .appPrimary : .ink)))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
            .background(itemFilter == key ? Color.appPrimarySoft : Color.surfaceVariant)
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(itemFilter == key ? Color.appPrimary : Color.clear, lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func loadDetail() async {
        guard !isLoading else { return }
        isLoading = true
        do {
            let filterParam = itemFilter == "all" ? "" : (itemFilter == "diff" ? "adjustment" : itemFilter)
            self.detail = try await ApiClient.shared.fetchStocktakingDetail(id: checkId, status: filterParam)
        } catch {
            self.errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func searchCandidates() {
        guard !candidateKeyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        Task {
            candidates = (try? await ApiClient.shared.fetchGoodsCheckCandidates(checkId: checkId, keyword: candidateKeyword)) ?? []
        }
    }
    
    private func submitInitialCount() {
        guard let cand = selectedCandidate, let qty = Double(countInputQty) else { return }
        isAddingItem = true
        Task {
            do {
                try await ApiClient.shared.addGoodsCheckItem(
                    checkId: checkId,
                    productId: cand.productId,
                    quantity: qty,
                    locationCode: cand.locationCode,
                    batchNo: cand.batchNo
                )
                await MainActor.run {
                    isAddingItem = false
                    selectedCandidate = nil
                    countInputQty = ""
                    candidates = []
                    candidateKeyword = ""
                }
                await loadDetail()
            } catch {
                await MainActor.run {
                    isAddingItem = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    private func submitRecount(targetId: Int) {
        guard targetId > 0 else { return }
        guard let qty = Double(recountInputQty) else { return }
        isRecounting = true
        Task {
            do {
                try await ApiClient.shared.recountGoodsCheckItem(itemId: targetId, quantity: qty)
                await MainActor.run {
                    isRecounting = false
                    recountTargetItem = nil
                    recountInputQty = ""
                }
                await loadDetail()
            } catch {
                await MainActor.run {
                    isRecounting = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    private func submitLocationUpdate(targetId: Int) {
        guard targetId > 0 else { return }
        isUpdatingLocation = true
        Task {
            do {
                try await ApiClient.shared.updateGoodsCheckLocation(itemId: targetId, batchNo: editBatchNo, locationCode: editLocationCode)
                await MainActor.run {
                    isUpdatingLocation = false
                    editTargetItem = nil
                }
                await loadDetail()
            } catch {
                await MainActor.run {
                    isUpdatingLocation = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    private func finishCheck() {
        isFinishing = true
        Task {
            do {
                try await ApiClient.shared.finishGoodsCheck(id: checkId)
                await MainActor.run {
                    isFinishing = false
                }
                await loadDetail()
            } catch {
                await MainActor.run {
                    isFinishing = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    private func formatQty(_ val: Double?) -> String {
        guard let v = val else { return "0" }
        return String(format: "%g", v)
    }
}

// MARK: - 候选商品行
private struct CandidateRowView: View {
    let cand: StocktakingCandidateModel
    let isSelected: Bool
    let onSelect: () -> Void
    
    var body: some View {
        Button(action: onSelect) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(cand.name)
                        .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                        .foregroundColor(.ink)
                    
                    let spec = cand.specification ?? "-"
                    let loc = cand.locationCode ?? "-"
                    let stock = cand.currentStock.map { "  ·  库存: \(String(format: "%g", $0))" } ?? ""
                    Text("规格: \(spec)  ·  货位: \(loc)\(stock)")
                        .font(.system(size: (11) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                }
                Spacer()
                if isSelected {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.appPrimary)
                }
            }
            .padding(8)
            .background(isSelected ? Color.appPrimarySoft : Color.surfaceVariant)
            .cornerRadius(6)
        }
        .buttonStyle(PlainButtonStyle())
    }
}


