import SwiftUI

// MARK: - 门店调拨列表 (1:1 移植 Android TransfersScreen)
@MainActor
public struct TransfersView: View {
    @Bindable private var router = Router.shared
    var session = SessionManager.shared
    
    @State private var searchText = ""
    @State private var searchTask: Task<Void, Error>? = nil
    @State private var selectedStatus: Int? = nil // nil: 全部, 0: 借出中, 1: 部分归还, 2: 已调平
    @State private var overdueOnly = false
    @State private var transfers: [TransferModel] = []
    @State private var stats: [String: Int] = [:]
    @State private var stores: [StoreItem] = []
    @State private var selectedStoreId: Int? = nil
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var isCreateSheetShowing = false
    @State private var currentTaskID: UUID = UUID()
    
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var gridColumns: [GridItem] {
        if sizeClass == .regular {
            return [GridItem(.adaptive(minimum: 340, maximum: .infinity), spacing: 12, alignment: .top)]
        } else {
            return [GridItem(.flexible())]
        }
    }
    @State private var loadTask: Task<Void, Never>? = nil
    @State private var splitSelectedDetailId: Int? = nil
    
    public init() {}
    
    public var body: some View {
        Group {
            if sizeClass == .regular {
                HStack(spacing: 0) {
                    mainListContent
                        .frame(maxWidth: .infinity)
                    
                    if let detailId = splitSelectedDetailId {
                        Divider().ignoresSafeArea()
                        VStack(spacing: 0) {
                            HStack {
                                Text("调拨详情")
                                    .font(.headline)
                                    .foregroundStyle(Color.ink)
                                Spacer()
                                Button(action: { splitSelectedDetailId = nil }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.system(size: 24))
                                        .foregroundStyle(Color.muted)
                                }
                            }
                            .padding()
                            .background(Color.pageBackground)
                            
                            Divider()
                            
                            TransferDetailView(id: detailId)
                                .id(detailId)
                        }
                        .frame(width: 420)
                        .transition(.move(edge: .trailing))
                    }
                }
            } else {
                mainListContent
            }
        }
    }
    
    private var showStore: Bool {
        session.currentUser?.role == 0
    }
    
    @ViewBuilder
    private var mainListContent: some View {
        VStack(spacing: 0) {
            // 头部与过滤区
            VStack(spacing: 12) {
                HStack(alignment: .center) {
                    SectionHeader(title: "门店调拨", subtitle: "跨门店物资借调与归还跟踪")
                    Spacer()
                    Button(action: { isCreateSheetShowing = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                            Text("新建调拨")
                        }
                        .scaledFont(13, weight: .bold)
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color.appPrimary)
                        .clipShape(.rect(cornerRadius: 8))
                    }
                }
                
                // 顶部统计指标卡片 (支持点击过滤)
                HStack(spacing: 10) {
                    statCardItem(
                        title: "借出中",
                        value: "\(stats["borrowing"] ?? 0)",
                        isSelected: selectedStatus == 0 && !overdueOnly,
                        color: .appPrimary
                    ) {
                        selectedStatus = 0
                        overdueOnly = false
                        startLoadTransfers()
                    }
                    
                    statCardItem(
                        title: "待确认",
                        value: "\(stats["pendingConfirm"] ?? 0)",
                        isSelected: selectedStatus == 99 && !overdueOnly,
                        color: .warning
                    ) {
                        selectedStatus = 99
                        overdueOnly = false
                        startLoadTransfers()
                    }
                    
                    statCardItem(
                        title: "已逾期",
                        value: "\(stats["overdue"] ?? 0)",
                        isSelected: overdueOnly,
                        color: .danger
                    ) {
                        selectedStatus = nil
                        overdueOnly = true
                        startLoadTransfers()
                    }
                }
                
                // 搜索栏
                SearchBarField(
                    text: $searchText,
                    placeholder: "输入单号、门店、物品或批号",
                    onSearch: {
                        startLoadTransfers()
                    }
                )
                .onChange(of: searchText) {
                    searchTask?.cancel()
                    if searchText.shouldSkipAutoSearch { return }
                    searchTask = Task {
                        do {
                            try await Task.sleep(nanoseconds: 300_000_000)
                            if !Task.isCancelled {
                                startLoadTransfers()
                            }
                        } catch {}
                    }
                }
                
                // 状态过滤标签
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        SegmentedButton(label: "全部状态", isSelected: selectedStatus == nil && !overdueOnly) {
                            selectedStatus = nil
                            overdueOnly = false
                            startLoadTransfers()
                        }
                        SegmentedButton(label: "借出中", isSelected: selectedStatus == 0 && !overdueOnly) {
                            selectedStatus = 0
                            overdueOnly = false
                            startLoadTransfers()
                        }
                        SegmentedButton(label: "待确认", isSelected: selectedStatus == 99 && !overdueOnly) {
                            selectedStatus = 99
                            overdueOnly = false
                            startLoadTransfers()
                        }
                        SegmentedButton(label: "已逾期", isSelected: overdueOnly) {
                            selectedStatus = nil
                            overdueOnly = true
                            startLoadTransfers()
                        }
                    }
                }
                
                if showStore && stores.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(label: "全部门店", isSelected: selectedStoreId == nil) {
                                selectedStoreId = nil
                                startReloadTransfers()
                            }
                            ForEach(stores) { store in
                                SegmentedButton(label: store.name, isSelected: selectedStoreId == store.id) {
                                    selectedStoreId = store.id
                                    startReloadTransfers()
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 8)
            
            ScrollView {
                VStack(spacing: 12) {
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
                                    startReloadTransfers()
                                }
                                .scaledFont(12, weight: .bold)
                                .foregroundStyle(Color.appPrimary)
                            }
                        }
                    }
                    
                    if isLoading && transfers.isEmpty {
                        ProgressView("正在加载调拨记录...")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.pageBackground.ignoresSafeArea(.all))
                    } else if transfers.isEmpty {
                        AppCard(padding: 32) {
                            VStack(spacing: 8) {
                                Image(systemName: "arrow.triangle.swap")
                                    .scaledFont(36)
                                    .foregroundStyle(Color.muted)
                                Text("暂无调拨记录")
                                    .scaledFont(14, weight: .medium)
                                    .foregroundStyle(Color.muted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .padding(.top, 20)
                    } else {
                        LazyVGrid(columns: self.gridColumns, spacing: 12) {
                            ForEach(transfers) { item in
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        HStack {
                                            Image(systemName: "arrow.triangle.swap")
                                                .foregroundStyle(Color.appPrimary)
                                            Text(item.transferNo)
                                                .scaledFont(15, weight: .bold)
                                            Spacer()
                                            HStack(spacing: 4) {
                                                ForEach(item.statusTags, id: \.self) { tag in
                                                    StatusPill(text: tag)
                                                }
                                            }
                                        }
                                        
                                        HStack {
                                            Text(item.displaySourceStore)
                                                .scaledFont(13, weight: .semibold)
                                                .foregroundStyle(Color.ink)
                                            Image(systemName: "arrow.right")
                                                .foregroundStyle(Color.appPrimary)
                                                .scaledFont(12)
                                                .padding(.horizontal, 4)
                                            Text(item.displayTargetStore)
                                                .scaledFont(13, weight: .semibold)
                                                .foregroundStyle(Color.ink)
                                            Spacer()
                                        }
                                        .padding(12)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .background(Color.surface)
                                        .clipShape(.rect(cornerRadius: 8))
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                        
                                        VStack(spacing: 6) {
                                            InfoRowItem(label: "调拨物品", value: item.itemsDisplay)
                                            InfoRowItem(label: "调拨日期", value: formatDateOnly(item.transferDate) != "-" ? formatDateOnly(item.transferDate) : formatDateOnly(item.createdAt))
                                            
                                            let isItemOverdue = item.overdue == true || (item.expectedReturnDate != nil && item.expectedReturnDate! < String(Date().ISO8601Format().prefix(10)))
                                            if item.status != 2 && item.status != 3 {
                                                InfoRowItem(
                                                    label: "预计归还",
                                                    value: formatDateOnly(item.expectedReturnDate),
                                                    valueColor: isItemOverdue ? .danger : .ink,
                                                    isBold: isItemOverdue
                                                )
                                            }
                                            if let rem = item.remark, !rem.isEmpty {
                                                InfoRowItem(label: "备注", value: rem)
                                            }
                                        }
                                        .padding(.top, 4)
                                        
                                        if item.outboundStatus == 0 {
                                            Text("提示：调出方尚未确认出库")
                                                .scaledFont(12)
                                                .foregroundStyle(Color.warning)
                                                .padding(.top, 4)
                                        }
                                    }
                                }
                                .onTapGesture {
                                    hideKeyboard()
                                    if sizeClass == .regular {
                                        splitSelectedDetailId = item.id
                                    } else {
                                        router.navigate(to: .transferDetail(id: item.id))
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color.pageBackground.ignoresSafeArea(.all))
            .refreshable {
                ApiClient.shared.clearResponseCache()
                await loadTransfers()
                await loadStats()
            }
            
        }
        .background(Color.pageBackground.ignoresSafeArea(.all))
        .navigationTitle("门店调拨")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $isCreateSheetShowing) {
            TransferFormView(stores: stores) {
                startReloadTransfers()
            }
        }
        .background(Color.pageBackground.ignoresSafeArea(.all))
        .scrollDismissesKeyboard(.interactively)
        .task {
            // 三个请求完全并行：门店列表、统计数据、调拨记录
            async let fetchedStores = (try? ApiClient.shared.fetchTransferStores()) ?? []
            async let statsTask: () = loadStats()
            async let transfersTask: () = loadTransfers()
            
            let (storesResult, _, _) = await (fetchedStores, statsTask, transfersTask)
            self.stores = storesResult
        }
        .onDisappear {
            searchTask?.cancel()
            loadTask?.cancel()
        }
    }
    
    @ViewBuilder
    private func statCardItem(title: String, value: String, isSelected: Bool, color: Color, onClick: @escaping () -> Void) -> some View {
        Button(action: onClick) {
            VStack(spacing: 4) {
                Text(title)
                    .scaledFont(11)
                    .foregroundStyle(isSelected ? color : Color.muted)
                Text(value)
                    .scaledFont(18, weight: .bold)
                    .foregroundStyle(color)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(isSelected ? color.opacity(0.1) : Color.surface)
            .clipShape(.rect(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? color : Color.cardBorder, lineWidth: isSelected ? 1.5 : 1)
            )
        }
    }
    
    private func startLoadTransfers() {
        loadTask?.cancel()
        loadTask = Task { await loadTransfers() }
    }

    private func startReloadTransfers() {
        loadTask?.cancel()
        loadTask = Task {
            async let transfersTask: () = loadTransfers()
            async let statsTask: () = loadStats()
            _ = await (transfersTask, statsTask)
        }
    }

    private func loadStats() async {
        stats = (try? await ApiClient.shared.fetchTransferStats(storeId: selectedStoreId)) ?? [:]
    }
    
    private func loadTransfers() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        do {
            let result = try await ApiClient.shared.fetchTransfers(
                keyword: searchText.trimmingCharacters(in: .whitespacesAndNewlines),
                status: selectedStatus,
                overdueOnly: overdueOnly,
                storeId: selectedStoreId
            )
            guard !Task.isCancelled else { return }
            guard currentTaskID == taskID else { return }
            self.transfers = result
        } catch {
            guard !Task.isCancelled else { return }
            errorMessage = error.localizedDescription
        }
        if currentTaskID == taskID { isLoading = false }
    }
}

// MARK: - 新建调拨表单 (1:1 移植 Android CreateTransferDialog)
@MainActor
public struct TransferFormView: View {
    public let stores: [StoreItem]
    public let onSaved: () -> Void
    
    @Environment(\.dismiss) private var dismiss
    var session = SessionManager.shared
    
    @State private var fromStoreId: Int = -1
    @State private var toStoreId: Int = -1
    @State private var expectedReturnDate: String = ""
    @State private var itemName: String = ""
    @State private var itemSpecification: String = ""
    @State private var itemQuantity: String = "1"
    @State private var itemUnit: String = "件"
    @State private var isBusy = false
    @State private var errorMessage: String? = nil
    
    public init(stores: [StoreItem], onSaved: @escaping () -> Void) {
        self.stores = stores
        self.onSaved = onSaved
    }
    
    private var isSuperAdmin: Bool {
        SessionManager.shared.currentUser?.role == 0
    }
    
    private var userStoreId: Int {
        SessionManager.shared.currentUser?.store?.id ?? 0
    }
    
    private var isValid: Bool {
        fromStoreId >= 0 && toStoreId >= 0 && fromStoreId != toStoreId &&
        !itemName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (Double(itemQuantity) ?? 0.0) > 0 &&
        !isBusy
    }
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 14) {
                            SectionHeader(title: "新建门店调拨", subtitle: "选择出入库门店与物品清单")
                            
                            Divider().foregroundStyle(Color.cardBorder)
                            
                            // 调出门店 (提供方)
                            VStack(alignment: .leading, spacing: 6) {
                                Text("调出门店 (提供方) *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        let availableFrom = isSuperAdmin ? stores.filter { $0.id != toStoreId } : stores.filter { $0.id != userStoreId }
                                        ForEach(availableFrom) { s in
                                            SegmentedButton(label: s.name, isSelected: fromStoreId == s.id) {
                                                fromStoreId = s.id
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 调入门店 (申请方)
                            VStack(alignment: .leading, spacing: 6) {
                                Text("调入门店 (申请方) *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                if !isSuperAdmin && userStoreId > 0 {
                                    let myStoreName = stores.first(where: { $0.id == userStoreId })?.name ?? SessionManager.shared.currentUser?.store?.name ?? "当前门店"
                                    Text(myStoreName)
                                        .scaledFont(13, weight: .bold)
                                        .foregroundStyle(Color.appPrimary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 7)
                                        .background(Color.appPrimary.opacity(0.1))
                                        .clipShape(Capsule())
                                        .overlay(Capsule().stroke(Color.appPrimary.opacity(0.3), lineWidth: 1))
                                } else {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 8) {
                                            let availableTo = stores.filter { $0.id != fromStoreId }
                                            ForEach(availableTo) { s in
                                                SegmentedButton(label: s.name, isSelected: toStoreId == s.id) {
                                                    toStoreId = s.id
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 预计归还日期
                            VStack(alignment: .leading, spacing: 6) {
                                Text("预计归还日期 (YYYY-MM-DD)").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                TextField("例如：2026-09-23", text: $expectedReturnDate)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .clipShape(.rect(cornerRadius: 8))
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            Divider().foregroundStyle(Color.cardBorder)
                            
                            Text("调拨物资信息").scaledFont(14, weight: .bold).foregroundStyle(Color.ink)
                            
                            // 物品名称
                            VStack(alignment: .leading, spacing: 6) {
                                Text("物品名称 *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                TextField("输入物资名称", text: $itemName)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .clipShape(.rect(cornerRadius: 8))
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            // 规格
                            VStack(alignment: .leading, spacing: 6) {
                                Text("规格（可选）").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                TextField("如：500g/包", text: $itemSpecification)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .clipShape(.rect(cornerRadius: 8))
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            // 数量与单位
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("数量 *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                    TextField("数量", text: $itemQuantity)
                                        .keyboardType(.decimalPad)
                                        .padding(.horizontal, 12)
                                        .frame(height: 44)
                                        .background(Color.surface)
                                        .clipShape(.rect(cornerRadius: 8))
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                        .expandTapTarget()
                                }
                                
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("单位").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                    TextField("如：盒、件", text: $itemUnit)
                                        .padding(.horizontal, 12)
                                        .frame(height: 44)
                                        .background(Color.surface)
                                        .clipShape(.rect(cornerRadius: 8))
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                }
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
                    
                    Button(action: createAction) {
                        HStack {
                            if isBusy {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            }
                            Text(isBusy ? "正在创建..." : "确认创建调拨")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(isValid ? Color.appPrimary : Color.appPrimary.opacity(0.4))
                        .clipShape(.rect(cornerRadius: 10))
                    }
                    .disabled(!isValid)
                }
                .padding(16)
            }
            .background(Color.pageBackground.ignoresSafeArea(.all))
            .navigationTitle("新建调拨")
            .onAppear {
                if !isSuperAdmin && userStoreId > 0 {
                    toStoreId = userStoreId
                }
            }
            .navigationBarTitleDisplayMode(.inline)
                                .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { dismiss() }
                }
            }
        .onAppear {
                if let userStoreId = session.currentUser?.storeId, userStoreId > 0 {
                    fromStoreId = userStoreId
                } else if let firstStore = stores.first {
                    fromStoreId = firstStore.id
                }
                if let secondStore = stores.first(where: { $0.id != fromStoreId }) {
                    toStoreId = secondStore.id
                }
                
                // 默认 7 天后归还
                let future = Calendar.current.date(byAdding: .day, value: 7, to: Date()) ?? Date()
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                expectedReturnDate = formatter.string(from: future)
            }
        }
    }
    
    private func createAction() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        guard !isBusy else { return }
        isBusy = true
        errorMessage = nil
        
        Task {
            do {
                let qty = Double(itemQuantity) ?? 1.0
                let itemObj: [String: Any] = [
                    "itemName": itemName.trimmingCharacters(in: .whitespacesAndNewlines),
                    "specification": itemSpecification.trimmingCharacters(in: .whitespacesAndNewlines),
                    "quantity": qty,
                    "unit": itemUnit.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "件" : itemUnit.trimmingCharacters(in: .whitespacesAndNewlines)
                ]
                
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                let todayStr = formatter.string(from: Date())
                
                let payload: [String: Any] = [
                    "fromStoreId": fromStoreId,
                    "toStoreId": toStoreId,
                    "transferDate": todayStr,
                    "expectedReturnDate": expectedReturnDate.trimmingCharacters(in: .whitespacesAndNewlines),
                    "items": [itemObj]
                ]
                
                _ = try await ApiClient.shared.createTransfer(payload: payload)
                
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

// MARK: - 调拨详情 (1:1 移植 Android TransferDetailScreen)
@MainActor
public struct TransferDetailView: View {
    public let id: Int
    @Environment(\.dismiss) private var dismiss
    @State private var transfer: TransferModel? = nil
    @State private var isLoading = false
    @State private var isActionBusy = false
    @State private var errorMessage: String? = nil
    
    // 申请归还弹窗
    @State private var returnDialogItem: TransferItemModel? = nil
    @State private var returnQuantityText: String = ""
    @State private var returnRecordId: Int? = nil
    @State private var returnDate: Date = Date()
    @State private var returnRemark: String = ""
    @State private var currentTaskID: UUID = UUID()
    
    @Environment(\.horizontalSizeClass) private var sizeClass
    private var gridColumns: [GridItem] {
        if sizeClass == .regular {
            return [GridItem(.adaptive(minimum: 340, maximum: .infinity), spacing: 12, alignment: .top)]
        } else {
            return [GridItem(.flexible())]
        }
    }
    
    // 二次确认弹窗
    @State private var showActionConfirm = false
    @State private var confirmActionMessage = ""
    @State private var confirmActionBlock: (() -> Void)? = nil
    
    public init(id: Int) {
        self.id = id
    }
    
    public var body: some View {
        ScrollView {
            LazyVGrid(columns: self.gridColumns, spacing: 16) {
                if let error = errorMessage, transfer != nil {
                    AppCard(padding: 12) {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(Color.danger)
                            Text(error)
                                .scaledFont(13)
                                .foregroundStyle(Color.danger)
                            Spacer()
                            Button("重试") {
                                Task { await loadDetail() }
                            }
                            .scaledFont(12, weight: .bold)
                            .foregroundStyle(Color.appPrimary)
                        }
                    }
                }
                
                if isLoading && transfer == nil {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.top, 40)
                } else if transfer == nil {
                    VStack(spacing: 12) {
                        Image(systemName: "arrow.triangle.swap")
                            .scaledFont(36)
                            .foregroundStyle(Color.muted)
                        Text(errorMessage ?? "未能加载调拨详情")
                            .scaledFont(14)
                            .foregroundStyle(Color.muted)
                            .multilineTextAlignment(.center)
                        Button("点击重试") {
                            Task { await loadDetail() }
                        }
                        .scaledFont(14, weight: .bold)
                        .foregroundStyle(Color.appPrimary)
                        .padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 40)
                } else if let item = transfer {
                    // 头部卡片
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text(item.transferNo)
                                    .scaledFont(17, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Spacer()
                                HStack(spacing: 4) {
                                    ForEach(item.statusTags, id: \.self) { tag in
                                        StatusPill(text: tag)
                                    }
                                }
                            }
                            
                            HStack {
                                Text(item.displaySourceStore)
                                    .scaledFont(13, weight: .semibold)
                                    .foregroundStyle(Color.ink)
                                Image(systemName: "arrow.right")
                                    .foregroundStyle(Color.appPrimary)
                                    .scaledFont(12)
                                    .padding(.horizontal, 4)
                                Text(item.displayTargetStore)
                                    .scaledFont(13, weight: .semibold)
                                    .foregroundStyle(Color.ink)
                                Spacer()
                            }
                            .padding(10)
                            .background(Color.surface)
                            .clipShape(.rect(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                        }
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                    // 调拨信息
                    SectionHeader(title: "调拨信息")
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            InfoRowItem(label: "调拨日期", value: formatDateOnly(item.transferDate) != "-" ? formatDateOnly(item.transferDate) : formatDateOnly(item.createdAt))
                            
                            let isItemOverdue = item.overdue == true
                            if item.status != 2 && item.status != 3 {
                                InfoRowItem(
                                    label: "预计归还",
                                    value: formatDateOnly(item.expectedReturnDate),
                                    valueColor: isItemOverdue ? .danger : .ink,
                                    isBold: isItemOverdue
                                )
                            }
                            InfoRowItem(label: "创建人", value: item.creator?.displayName ?? "-")
                            if let created = item.createdAt, !created.isEmpty {
                                InfoRowItem(label: "创建时间", value: formatDateTimeToMinute(created))
                            }
                            if let rem = item.remark, !rem.isEmpty {
                                InfoRowItem(label: "备注", value: rem)
                            }
                        }
                    }
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                    // 借出确认
                    SectionHeader(title: "借出确认")
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            let confirmed = item.outboundStatus == 1
                            InfoRowItem(
                                label: "调出状态",
                                value: confirmed ? "已确认调出" : "待确认调出",
                                valueColor: confirmed ? .success : .warning,
                                isBold: true
                            )
                            if confirmed {
                                InfoRowItem(label: "确认人", value: item.outboundConfirmer?.displayName ?? "-")
                                if let confTime = item.outboundConfirmedAt, !confTime.isEmpty {
                                    InfoRowItem(label: "确认时间", value: formatDateTimeToMinute(confTime))
                                }
                            }
                    }
                        }
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                    // 调拨明细
                    if let items = item.items, !items.isEmpty {
                        SectionHeader(title: "调拨明细 (\(items.count) 项)")
                        ForEach(items) { tItem in
                            AppCard(padding: 14) {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(tItem.displayName)
                                                .scaledFont(14, weight: .bold)
                                                .foregroundStyle(Color.ink)
                                            if let spec = tItem.specification, !spec.isEmpty {
                                                Text("规格：\(spec)")
                                                    .scaledFont(12)
                                                    .foregroundStyle(Color.muted)
                                            }
                                        }
                                        Spacer()
                                        Text("\(String(format: "%g", tItem.quantity ?? 0.0)) \(tItem.unit ?? "")")
                                            .scaledFont(14, weight: .bold)
                                            .foregroundStyle(Color.appPrimary)
                                    }
                                    
                                    Divider().foregroundStyle(Color.cardBorder)
                                    
                                    InfoRowItem(label: "已确认归还", value: "\(String(format: "%g", tItem.returnedQuantity ?? 0.0)) \(tItem.unit ?? "")")
                                    if let pending = tItem.pendingReturnQuantity, pending > 0 {
                                        InfoRowItem(label: "待确认归还", value: "\(String(format: "%g", pending)) \(tItem.unit ?? "")", valueColor: .warning)
                                    }
                                    if let remaining = tItem.remainingQuantity {
                                        InfoRowItem(label: "剩余待归还", value: "\(String(format: "%g", remaining)) \(tItem.unit ?? "")", isBold: true)
                                    }
                                    
                                    // 申请归还按钮
                                    if item.permissions?.canSubmitReturn == true && (tItem.availableReturnQuantity ?? 0) > 0 {
                                        HStack {
                                            Spacer()
                                            Button(action: {
                                                returnDialogItem = tItem
                                                returnQuantityText = "\(String(format: "%g", tItem.availableReturnQuantity ?? 1.0))"
                                                returnRecordId = nil
                                                returnDate = Date()
                                                returnRemark = ""
                                            }) {
                                                Text("申请归还")
                                                    .scaledFont(12, weight: .bold)
                                                    .foregroundStyle(Color.appPrimary)
                                                    .padding(.horizontal, 12)
                                                    .padding(.vertical, 5)
                                                    .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.appPrimary, lineWidth: 1))
                                            }
                                        }
                                        .padding(.top, 4)
                                    }
                                }
                            }
                        }
                    }
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                    // 归还记录
                    SectionHeader(title: "归还记录")
                    if let records = item.returnRecords, !records.isEmpty {
                        ForEach(records) { record in
                            AppCard(padding: 14) {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Text(record.itemName ?? "物资")
                                            .scaledFont(14, weight: .semibold)
                                        Spacer()
                                        StatusPill(text: record.status == 1 ? "已确认" : "待确认")
                                    }
                                    
                                    Divider().foregroundStyle(Color.cardBorder)
                                    
                                    InfoRowItem(label: "归还数量", value: "\(String(format: "%g", record.quantity ?? 0.0))")
                                    if let rDate = record.returnDate {
                                        InfoRowItem(label: "归还日期", value: formatDateOnly(rDate))
                                    }
                                    if let op = record.operator {
                                        InfoRowItem(label: "发起人", value: op.displayName)
                                    }
                                    if let conf = record.confirmer {
                                        InfoRowItem(label: "确认人", value: conf.displayName)
                                    }
                                    if let confAt = record.confirmedAt {
                                        InfoRowItem(label: "确认时间", value: formatDateTimeToMinute(confAt))
                                    }
                                    if let rem = record.remark, !rem.isEmpty {
                                        InfoRowItem(label: "备注", value: rem)
                                    }
                                    
                                    // 确认归还操作按钮
                                    if record.status != 1 {
                                        HStack(spacing: 8) {
                                            Spacer()
                                            if item.permissions?.canSubmitReturn == true {
                                                Button(action: {
                                                    if let items = item.items, let rItem = items.first(where: { $0.id == record.transferItemId }) {
                                                        returnDialogItem = rItem
                                                        returnRecordId = record.id
                                                        returnQuantityText = "\(String(format: "%g", record.quantity ?? 1.0))"
                                                        if let dateStr = record.returnDate {
                                                            let formatter = DateFormatter()
                                                            formatter.dateFormat = "yyyy-MM-dd"
                                                            returnDate = formatter.date(from: dateStr) ?? Date()
                                                        }
                                                        returnRemark = record.remark ?? ""
                                                    }
                                                }) {
                                                    Text("修改")
                                                        .scaledFont(12, weight: .bold)
                                                        .foregroundStyle(Color.appPrimary)
                                                        .padding(.horizontal, 14)
                                                        .padding(.vertical, 6)
                                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.appPrimary, lineWidth: 1))
                                                }
                                                Button(action: {
                                                    cancelReturnAction(returnId: record.id)
                                                }) {
                                                    Text("取消")
                                                        .scaledFont(12, weight: .bold)
                                                        .foregroundStyle(Color.danger)
                                                        .padding(.horizontal, 14)
                                                        .padding(.vertical, 6)
                                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.danger, lineWidth: 1))
                                                }
                                            }
                                            if item.permissions?.canConfirmReturn == true {
                                                Button(action: {
                                                    confirmReturnAction(returnId: record.id)
                                                }) {
                                                    Text("确认")
                                                        .scaledFont(12, weight: .bold)
                                                        .foregroundStyle(Color.white)
                                                        .padding(.horizontal, 14)
                                                        .padding(.vertical, 6)
                                                        .background(Color.success)
                                                        .clipShape(.rect(cornerRadius: 6))
                                                }
                                            }
                                        }
                                        .padding(.top, 4)
                                    }
                                }
                            }
                        }
                    } else {
                        AppCard(padding: 20) {
                            Text("暂无归还记录")
                                .scaledFont(13)
                                .foregroundStyle(Color.muted)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    }
                    
                    // 底部主操作区 (确认调出 / 取消调拨)
                    if item.permissions?.canConfirmOutbound == true || item.permissions?.canCancel == true {
                        HStack(spacing: 12) {
                            if item.permissions?.canConfirmOutbound == true {
                                Button(action: confirmOutboundAction) {
                                    HStack {
                                        if isActionBusy {
                                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        }
                                        Text("确认调出")
                                            .scaledFont(14, weight: .bold)
                                            .foregroundStyle(Color.white)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 46)
                                    .background(Color.appPrimary)
                                    .clipShape(.rect(cornerRadius: 8))
                                }
                                .disabled(isActionBusy)
                            }
                            
                            if item.permissions?.canCancel == true {
                                Button(action: cancelTransferAction) {
                                    Text(item.status == 4 ? "确认取消" : "取消调拨")
                                        .scaledFont(14, weight: .bold)
                                        .foregroundStyle(Color.danger)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 46)
                                        .background(Color.surface)
                                        .clipShape(.rect(cornerRadius: 8))
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.danger, lineWidth: 1))
                                }
                                .disabled(isActionBusy)
                            }
                        }
                        .padding(.top, 8)
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.pageBackground.ignoresSafeArea(.all))
        .navigationTitle("调拨详情")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadDetail(showSpinner: true)
        }
        .refreshable {
            // 下拉刷新不设 isLoading，避免触发 UI 结构重建导致 SwiftUI 误杀任务
            ApiClient.shared.clearResponseCache()
            await loadDetail(showSpinner: false)
        }
        .sheet(item: $returnDialogItem) { item in
            TransferReturnSheet(
                item: item,
                transferId: id,
                recordId: returnRecordId,
                qty: returnQuantityText,
                date: returnDate,
                remark: returnRemark,
                onDismiss: { returnDialogItem = nil; returnRecordId = nil },
                onSuccess: {
                    returnDialogItem = nil
                    returnRecordId = nil
                    Task { await loadDetail() }
                }
            )
        }
        .alert("二次确认", isPresented: $showActionConfirm) {
            Button("取消", role: .cancel) { }
            Button("确定") {
                confirmActionBlock?()
            }
        } message: {
            Text(confirmActionMessage)
        }
    }
    
    private func loadDetail(showSpinner: Bool = true) async {
        let taskID = UUID()
        currentTaskID = taskID
        if showSpinner { isLoading = true }
        errorMessage = nil
        do {
            let fetched = try await ApiClient.shared.fetchTransferDetail(id: id)
            guard currentTaskID == taskID else { return }
            self.transfer = fetched
        } catch is CancellationError {
            // ignore
        } catch {
            guard currentTaskID == taskID else { return }
            errorMessage = error.localizedDescription
        }
        if currentTaskID == taskID { isLoading = false }
    }
    
    private func confirmOutboundAction() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        confirmActionMessage = "确定要确认调出该订单吗？确认后物品将正式发出。"
        confirmActionBlock = {
            self.executeConfirmOutbound()
        }
        showActionConfirm = true
    }
    
    private func executeConfirmOutbound() {
        isActionBusy = true
        Task {
            do {
                try await ApiClient.shared.confirmOutbound(id: id)
                await MainActor.run {
                    isActionBusy = false
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
                await loadDetail()
            } catch {
                await MainActor.run {
                    isActionBusy = false
                    errorMessage = error.localizedDescription
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }
    
    private func cancelTransferAction() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isActionBusy = true
        Task {
            do {
                try await ApiClient.shared.cancelTransfer(id: id, reason: "iOS端取消调拨")
                await MainActor.run {
                    isActionBusy = false
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    isActionBusy = false
                    errorMessage = error.localizedDescription
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }
    
    
    private func cancelReturnAction(returnId: Int) {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        confirmActionMessage = "确定要取消这条归还申请吗？"
        confirmActionBlock = {
            self.executeCancelReturn(returnId: returnId)
        }
        showActionConfirm = true
    }
    
    private func executeCancelReturn(returnId: Int) {
        isActionBusy = true
        Task {
            do {
                try await ApiClient.shared.cancelTransferReturn(transferId: id, returnId: returnId)
                await MainActor.run {
                    isActionBusy = false
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
                await loadDetail()
            } catch {
                await MainActor.run {
                    isActionBusy = false
                    errorMessage = error.localizedDescription
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }

    private func confirmReturnAction(returnId: Int) {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        confirmActionMessage = "确定要确认归还这笔物资吗？"
        confirmActionBlock = {
            self.executeConfirmReturn(returnId: returnId)
        }
        showActionConfirm = true
    }
    
    private func executeConfirmReturn(returnId: Int) {
        isActionBusy = true
        Task {
            do {
                try await ApiClient.shared.confirmReturn(transferId: id, returnId: returnId)
                await MainActor.run {
                    isActionBusy = false
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                }
                await loadDetail()
            } catch {
                await MainActor.run {
                    isActionBusy = false
                    errorMessage = error.localizedDescription
                    UINotificationFeedbackGenerator().notificationOccurred(.error)
                }
            }
        }
    }
}

// MARK: - 申请/修改归还独立弹窗
@MainActor
struct TransferReturnSheet: View {
    let item: TransferItemModel
    let transferId: Int
    let recordId: Int?
    
    @State private var quantityText: String
    @State private var date: Date
    @State private var remark: String
    
    let onDismiss: () -> Void
    let onSuccess: () -> Void
    
    @State private var isBusy = false
    @State private var errorMessage: String? = nil
    
    init(item: TransferItemModel, transferId: Int, recordId: Int?, qty: String, date: Date, remark: String, onDismiss: @escaping () -> Void, onSuccess: @escaping () -> Void) {
        self.item = item
        self.transferId = transferId
        self.recordId = recordId
        self._quantityText = State(initialValue: qty)
        self._date = State(initialValue: date)
        self._remark = State(initialValue: remark)
        self.onDismiss = onDismiss
        self.onSuccess = onSuccess
    }
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                AppCard(padding: 16) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("归还物资：\(item.displayName)")
                            .scaledFont(15, weight: .bold)
                        
                        Text("剩余可归还数量：\(String(format: "%g", item.availableReturnQuantity ?? 0.0)) \(item.unit ?? "")")
                            .scaledFont(13)
                            .foregroundStyle(Color.muted)
                        
                        TextField("请输入归还数量", text: $quantityText)
                            .keyboardType(.decimalPad)
                            .padding(.horizontal, 12)
                            .frame(height: 44)
                            .background(Color.surface)
                            .clipShape(.rect(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                        
                        HStack {
                            Text("归还日期").scaledFont(13).foregroundStyle(Color.muted)
                            Spacer()
                            DatePicker("", selection: $date, displayedComponents: .date)
                                .labelsHidden()
                        }
                        
                        TextField("备注 (选填)", text: $remark)
                            .padding(.horizontal, 12)
                            .frame(height: 44)
                            .background(Color.surface)
                            .clipShape(.rect(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
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

                Button(action: submit) {
                    if isBusy {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text(recordId != nil ? "保存修改" : "提交归还申请")
                            .scaledFont(15, weight: .bold)
                            .foregroundStyle(Color.white)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background(((Double(quantityText.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")) ?? 0.0) <= 0 || isBusy) ? Color.appPrimary.opacity(0.5) : Color.appPrimary)
                .clipShape(.rect(cornerRadius: 8))
                .disabled((Double(quantityText.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")) ?? 0.0) <= 0 || isBusy)
                
                Spacer()
            }
            .padding(16)
            .background(Color.pageBackground.ignoresSafeArea(.all))
            .navigationTitle(recordId != nil ? "修改归还" : "申请归还")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { onDismiss() }
                }
            }
        }
    }
    
    private func submit() {
        let text = quantityText.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
        guard let qty = Double(text), qty > 0 else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        errorMessage = nil
        isBusy = true
        
        Task {
            do {
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                let dateStr = formatter.string(from: date)
                
                if let rId = recordId {
                    let payload: [String: Any] = [
                        "returnDate": dateStr,
                        "quantity": qty,
                        "remark": remark
                    ]
                    try await ApiClient.shared.updateTransferReturn(transferId: transferId, returnId: rId, payload: payload)
                } else {
                    let payload: [String: Any] = [
                        "returnDate": dateStr,
                        "items": [
                            [
                                "transferItemId": item.id,
                                "quantity": qty,
                                "remark": remark
                            ]
                        ]
                    ]
                    try await ApiClient.shared.addTransferReturns(transferId: transferId, payload: payload)
                }
                
                await MainActor.run {
                    isBusy = false
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    onSuccess()
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
