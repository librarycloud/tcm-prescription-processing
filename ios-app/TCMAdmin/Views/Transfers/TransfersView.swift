import SwiftUI

// MARK: - 门店调拨列表 (1:1 移植 Android TransfersScreen)
@MainActor
public struct TransfersView: View {
    @ObservedObject private var router = Router.shared
    @ObservedObject private var session = SessionManager.shared
    
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
    
    public init() {}
    
    private var showStore: Bool {
        session.currentUser?.role == 0
    }
    
    public var body: some View {
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
                        .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(Color.appPrimary)
                        .cornerRadius(8)
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
                        Task { await loadTransfers() }
                    }
                    
                    statCardItem(
                        title: "部分归还",
                        value: "\(stats["partReturned"] ?? 0)",
                        isSelected: selectedStatus == 1 && !overdueOnly,
                        color: .warning
                    ) {
                        selectedStatus = 1
                        overdueOnly = false
                        Task { await loadTransfers() }
                    }
                    
                    statCardItem(
                        title: "已逾期",
                        value: "\(stats["overdue"] ?? 0)",
                        isSelected: overdueOnly,
                        color: .danger
                    ) {
                        selectedStatus = nil
                        overdueOnly = true
                        Task { await loadTransfers() }
                    }
                }
                
                // 搜索栏
                SearchBarField(
                    text: $searchText,
                    placeholder: "输入单号、门店、物品或批号",
                    onSearch: {
                        Task { await loadTransfers() }
                    }
                )
                .onChange(of: searchText) {
                    searchTask?.cancel()
                    searchTask = Task {
                        do {
                            try await Task.sleep(nanoseconds: 500_000_000)
                            if !Task.isCancelled {
                                await loadTransfers()
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
                            Task { await loadTransfers() }
                        }
                        SegmentedButton(label: "借出中", isSelected: selectedStatus == 0 && !overdueOnly) {
                            selectedStatus = 0
                            overdueOnly = false
                            Task { await loadTransfers() }
                        }
                        SegmentedButton(label: "部分归还", isSelected: selectedStatus == 1 && !overdueOnly) {
                            selectedStatus = 1
                            overdueOnly = false
                            Task { await loadTransfers() }
                        }
                        SegmentedButton(label: "已逾期", isSelected: overdueOnly) {
                            selectedStatus = nil
                            overdueOnly = true
                            Task { await loadTransfers() }
                        }
                    }
                }
                
                if showStore && stores.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(label: "全部门店", isSelected: selectedStoreId == nil) {
                                selectedStoreId = nil
                                Task {
                                    await loadTransfers()
                                    await loadStats()
                                }
                            }
                            ForEach(stores) { store in
                                SegmentedButton(label: store.name, isSelected: selectedStoreId == store.id) {
                                    selectedStoreId = store.id
                                    Task {
                                        await loadTransfers()
                                        await loadStats()
                                    }
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
                                    .foregroundColor(.danger)
                                Text(error)
                                    .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.danger)
                                Spacer()
                                Button("重试") {
                                    Task {
                                        await loadTransfers()
                                        await loadStats()
                                    }
                                }
                                .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(.appPrimary)
                            }
                        }
                    }
                    
                    if isLoading && transfers.isEmpty {
                        ProgressView("正在加载调拨记录...")
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                    } else if transfers.isEmpty {
                        AppCard(padding: 32) {
                            VStack(spacing: 8) {
                                Image(systemName: "arrow.triangle.swap")
                                    .font(.system(size: (36) * ThemeManager.shared.fontScale))
                                    .foregroundColor(.muted)
                                Text("暂无调拨记录")
                                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .medium))
                                    .foregroundColor(.muted)
                            }
                            .frame(maxWidth: .infinity)
                        }
                        .padding(.top, 20)
                    } else {
                        LazyVStack(spacing: 12) {
                            ForEach(transfers) { item in
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        HStack {
                                            Image(systemName: "arrow.triangle.swap")
                                                .foregroundColor(.appPrimary)
                                            Text(item.transferNo)
                                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                            Spacer()
                                            StatusPill(text: item.overdue == true ? "已逾期" : item.statusText)
                                        }
                                        
                                        HStack {
                                            Text(item.displaySourceStore)
                                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                                .foregroundColor(.ink)
                                            Image(systemName: "arrow.right")
                                                .foregroundColor(.appPrimary)
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .padding(.horizontal, 4)
                                            Text(item.displayTargetStore)
                                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                                .foregroundColor(.ink)
                                            Spacer()
                                        }
                                        .padding(12)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .background(Color.surface)
                                        .cornerRadius(8)
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                        
                                        VStack(spacing: 6) {
                                            InfoRowItem(label: "调拨物品", value: item.itemsDisplay)
                                            InfoRowItem(label: "调拨日期", value: formatDateOnly(item.transferDate) != "-" ? formatDateOnly(item.transferDate) : formatDateOnly(item.createdAt))
                                            
                                            let isItemOverdue = item.overdue == true || (item.expectedReturnDate != nil && item.expectedReturnDate! < String(Date().ISO8601Format().prefix(10)))
                                            InfoRowItem(
                                                label: "预计归还",
                                                value: formatDateOnly(item.expectedReturnDate),
                                                valueColor: isItemOverdue ? .danger : .ink,
                                                isBold: isItemOverdue
                                            )
                                        }
                                        .padding(.top, 4)
                                        
                                        if item.outboundStatus == 0 {
                                            Text("提示：调出方尚未确认出库")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.warning)
                                                .padding(.top, 4)
                                        }
                                    }
                                }
                                .onTapGesture {
                                    router.navigate(to: .transferDetail(id: item.id))
                                }
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color.pageBackground)
            .refreshable {
                await loadTransfers()
                await loadStats()
            }
        }
        .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
        .navigationTitle("门店调拨")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $isCreateSheetShowing) {
            TransferFormView(stores: stores) {
                Task {
                    await loadTransfers()
                    await loadStats()
                }
            }
        }
        .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
        .scrollDismissesKeyboard(.interactively)
        .task {
            stores = (try? await ApiClient.shared.fetchStores()) ?? []
            await loadStats()
            await loadTransfers()
        }
    }
    
    @ViewBuilder
    private func statCardItem(title: String, value: String, isSelected: Bool, color: Color, onClick: @escaping () -> Void) -> some View {
        Button(action: onClick) {
            VStack(spacing: 4) {
                Text(title)
                    .font(.system(size: (11) * ThemeManager.shared.fontScale))
                    .foregroundColor(isSelected ? color : .muted)
                Text(value)
                    .font(.system(size: (18) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(color)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(isSelected ? color.opacity(0.1) : Color.surface)
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? color : Color.cardBorder, lineWidth: isSelected ? 1.5 : 1)
            )
        }
    }
    
    private func loadStats() async {
        stats = (try? await ApiClient.shared.fetchTransferStats(storeId: selectedStoreId)) ?? [:]
    }
    
    private func loadTransfers() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        do {
            self.transfers = try await ApiClient.shared.fetchTransfers(
                keyword: searchText.trimmingCharacters(in: .whitespacesAndNewlines),
                status: selectedStatus,
                overdueOnly: overdueOnly,
                storeId: selectedStoreId
            )
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

// MARK: - 新建调拨表单 (1:1 移植 Android CreateTransferDialog)
@MainActor
public struct TransferFormView: View {
    public let stores: [StoreItem]
    public let onSaved: () -> Void
    
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var session = SessionManager.shared
    
    @State private var fromStoreId: Int = 0
    @State private var toStoreId: Int = 0
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
    
    private var isValid: Bool {
        fromStoreId > 0 && toStoreId > 0 && fromStoreId != toStoreId &&
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
                            
                            Divider().foregroundColor(Color.cardBorder)
                            
                            // 调出门店
                            VStack(alignment: .leading, spacing: 6) {
                                Text("调出门店 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(stores) { s in
                                            SegmentedButton(label: s.name, isSelected: fromStoreId == s.id) {
                                                fromStoreId = s.id
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 调入门店
                            VStack(alignment: .leading, spacing: 6) {
                                Text("调入门店 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(stores) { s in
                                            SegmentedButton(label: s.name, isSelected: toStoreId == s.id) {
                                                toStoreId = s.id
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 预计归还日期
                            VStack(alignment: .leading, spacing: 6) {
                                Text("预计归还日期 (YYYY-MM-DD)").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("例如：2026-09-23", text: $expectedReturnDate)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            Divider().foregroundColor(Color.cardBorder)
                            
                            Text("调拨物资信息").font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold)).foregroundColor(.ink)
                            
                            // 物品名称
                            VStack(alignment: .leading, spacing: 6) {
                                Text("物品名称 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("输入物资名称", text: $itemName)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            // 规格
                            VStack(alignment: .leading, spacing: 6) {
                                Text("规格（可选）").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                TextField("如：500g/包", text: $itemSpecification)
                                    .padding(.horizontal, 12)
                                    .frame(height: 44)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                            }
                            
                            // 数量与单位
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("数量 *").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                    TextField("数量", text: $itemQuantity)
                                        .keyboardType(.decimalPad)
                                        .padding(.horizontal, 12)
                                        .frame(height: 44)
                                        .background(Color.surface)
                                        .cornerRadius(8)
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                }
                                
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("单位").font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium)).foregroundColor(.ink)
                                    TextField("如：盒、件", text: $itemUnit)
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
                    
                    Button(action: createAction) {
                        HStack {
                            if isBusy {
                                ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                            }
                            Text(isBusy ? "正在创建..." : "确认创建调拨")
                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(isValid ? Color.appPrimary : Color.appPrimary.opacity(0.4))
                        .cornerRadius(10)
                    }
                    .disabled(!isValid)
                }
                .padding(16)
            }
            .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
            .navigationTitle("新建调拨")
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
    
    public init(id: Int) {
        self.id = id
    }
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let error = errorMessage, transfer != nil {
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
                
                if isLoading && transfer == nil {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(.top, 40)
                } else if transfer == nil {
                    VStack(spacing: 12) {
                        Image(systemName: "arrow.triangle.swap")
                            .font(.system(size: (36) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                        Text(errorMessage ?? "未能加载调拨详情")
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
                } else if let item = transfer {
                    // 头部卡片
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text(item.transferNo)
                                    .font(.system(size: (17) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                Spacer()
                                StatusPill(text: item.overdue == true ? "已逾期" : item.statusText)
                            }
                            
                            HStack {
                                Text(item.displaySourceStore)
                                    .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                    .foregroundColor(.ink)
                                Image(systemName: "arrow.right")
                                    .foregroundColor(.appPrimary)
                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                    .padding(.horizontal, 4)
                                Text(item.displayTargetStore)
                                    .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                    .foregroundColor(.ink)
                                Spacer()
                            }
                            .padding(10)
                            .background(Color.surface)
                            .cornerRadius(8)
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                        }
                    }
                    
                    // 调拨信息
                    SectionHeader(title: "调拨信息")
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            InfoRowItem(label: "调拨日期", value: formatDateOnly(item.transferDate) != "-" ? formatDateOnly(item.transferDate) : formatDateOnly(item.createdAt))
                            
                            let isItemOverdue = item.overdue == true
                            InfoRowItem(
                                label: "预计归还",
                                value: formatDateOnly(item.expectedReturnDate),
                                valueColor: isItemOverdue ? .danger : .ink,
                                isBold: isItemOverdue
                            )
                            InfoRowItem(label: "创建人", value: item.creator?.displayName ?? "-")
                            if let created = item.createdAt, !created.isEmpty {
                                InfoRowItem(label: "创建时间", value: formatDateTimeToMinute(created))
                            }
                            if let rem = item.remark, !rem.isEmpty {
                                InfoRowItem(label: "备注", value: rem)
                            }
                        }
                    }
                    
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
                    
                    // 调拨明细
                    if let items = item.items, !items.isEmpty {
                        SectionHeader(title: "调拨明细 (\(items.count) 项)")
                        ForEach(items) { tItem in
                            AppCard(padding: 14) {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(tItem.displayName)
                                                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                                .foregroundColor(.ink)
                                            if let spec = tItem.specification, !spec.isEmpty {
                                                Text("规格：\(spec)")
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                    .foregroundColor(.muted)
                                            }
                                        }
                                        Spacer()
                                        Text("\(String(format: "%g", tItem.quantity ?? 0.0)) \(tItem.unit ?? "")")
                                            .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                            .foregroundColor(.appPrimary)
                                    }
                                    
                                    Divider().foregroundColor(Color.cardBorder)
                                    
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
                                            }) {
                                                Text("申请归还")
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                                                    .foregroundColor(.appPrimary)
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
                    
                    // 归还记录
                    SectionHeader(title: "归还记录")
                    if let records = item.returnRecords, !records.isEmpty {
                        ForEach(records) { record in
                            AppCard(padding: 14) {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Text(record.itemName ?? "物资")
                                            .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .semibold))
                                        Spacer()
                                        StatusPill(text: record.status == 1 ? "已确认" : "待确认")
                                    }
                                    
                                    Divider().foregroundColor(Color.cardBorder)
                                    
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
                                    if record.status != 1 && item.permissions?.canConfirmReturn == true {
                                        HStack {
                                            Spacer()
                                            Button(action: {
                                                confirmReturnAction(returnId: record.id)
                                            }) {
                                                Text("确认归还")
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 14)
                                                    .padding(.vertical, 6)
                                                    .background(Color.success)
                                                    .cornerRadius(6)
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
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                                .frame(maxWidth: .infinity)
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
                                            .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                            .foregroundColor(.white)
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 46)
                                    .background(Color.appPrimary)
                                    .cornerRadius(8)
                                }
                                .disabled(isActionBusy)
                            }
                            
                            if item.permissions?.canCancel == true {
                                Button(action: cancelTransferAction) {
                                    Text("取消调拨")
                                        .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                        .foregroundColor(.danger)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 46)
                                        .background(Color.surface)
                                        .cornerRadius(8)
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
        .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
        .navigationTitle("调拨详情")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadDetail()
        }
        .refreshable {
            await loadDetail()
        }
        .sheet(item: $returnDialogItem) { item in
            NavigationStack {
                VStack(spacing: 16) {
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("归还物资：\(item.displayName)")
                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                            
                            Text("剩余可归还数量：\(String(format: "%g", item.availableReturnQuantity ?? 0.0)) \(item.unit ?? "")")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                .foregroundColor(.muted)
                            
                            TextField("请输入归还数量", text: $returnQuantityText)
                                .keyboardType(.decimalPad)
                                .padding(.horizontal, 12)
                                .frame(height: 44)
                                .background(Color.surface)
                                .cornerRadius(8)
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                        }
                    }
                    
                    Button(action: {
                        submitReturnAction(item: item)
                    }) {
                        Text("提交归还申请")
                            .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(Color.appPrimary)
                            .cornerRadius(8)
                    }
                    .disabled((Double(returnQuantityText) ?? 0.0) <= 0)
                    
                    Spacer()
                }
                .padding(16)
                .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
                .navigationTitle("申请归还")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("取消") { returnDialogItem = nil }
                    }
                }
            }
        }
    }
    
    private func loadDetail() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        do {
            self.transfer = try await ApiClient.shared.fetchTransferDetail(id: id)
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func confirmOutboundAction() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
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
    
    private func confirmReturnAction(returnId: Int) {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
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
    
    private func submitReturnAction(item: TransferItemModel) {
        guard let qty = Double(returnQuantityText), qty > 0 else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        returnDialogItem = nil
        isActionBusy = true
        
        Task {
            do {
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                let todayStr = formatter.string(from: Date())
                
                let payload: [String: Any] = [
                    "returnDate": todayStr,
                    "items": [
                        [
                            "transferItemId": item.id,
                            "quantity": qty
                        ]
                    ]
                ]
                try await ApiClient.shared.addTransferReturns(transferId: id, payload: payload)
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

