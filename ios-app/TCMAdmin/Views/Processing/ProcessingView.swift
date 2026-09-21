import SwiftUI

@MainActor
public struct ProcessingView: View {
    @Bindable private var router = Router.shared
    var session = SessionManager.shared
    
    @State private var mode = "plans" // "plans": 加工计划, "pickup": 领取列表
    @State private var activeView = "today-all" // 计划视图切换
    @State private var pickupStatus: Int = 0 // 0: 等待药材, 1: 已领取药材
    @State private var searchText = ""
    @State private var selectedStoreId: Int? = nil
    
    @State private var plans: [ProcessingPlanItem] = []
    @State private var pickupPackages: [PackageModel] = []
    @State private var stores: [StoreItem] = []
    @State private var stats: ProcessingStatsModel? = nil
    
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var currentTaskID: UUID = UUID()
    
    // 操作弹窗
    @State private var isCreatingPlan = false
    @State private var planToEdit: ProcessingPlanItem? = nil
    @State private var planForPackage: ProcessingPlanItem? = nil
    
    private var isSuperAdmin: Bool {
        session.currentUser?.role == 0
    }
    
    // 动态统计面板数据 (加工计划模式)
    var statItems: [(String, String, String)] {
        [
            ("今日全部", "today-all", "\((stats?.waitingCount ?? 0) + (stats?.processingCount ?? 0))"),
            ("今日待加工", "today-waiting", "\(stats?.waitingCount ?? 0)"),
            ("逾期未开工", "overdue", "\(stats?.overdueCount ?? 0)"),
            ("加工中", "processing", "\(stats?.processingCount ?? 0)"),
            ("等待顾客", "notice", "\(stats?.waitingNoticeCount ?? 0)"),
            ("明日加工", "tomorrow", "\(stats?.tomorrowWaitingCount ?? 0)"),
            ("全部计划", "all", "\(stats?.processingPlanTotalCount ?? 0)")
        ]
    }
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: 0) {
            // 1. 顶部操作栏与模式切换
            VStack(spacing: 12) {
                // 扫码与新建按钮
                HStack(spacing: 10) {
                    Button(action: { router.isScannerPresented = true }) {
                        HStack(spacing: 6) {
                            Image(systemName: "qrcode.viewfinder")
                            Text("扫码作业")
                                .scaledFont(13, weight: .medium)
                        }
                        .foregroundStyle(Color.appPrimary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(Color.surface)
                        .clipShape(.rect(cornerRadius: 8))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.appPrimary.opacity(0.4), lineWidth: 1)
                        )
                    }
                    
                    if mode != "pickup" {
                        Button(action: { isCreatingPlan = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "plus")
                                Text("新建加工计划")
                                    .scaledFont(13, weight: .medium)
                            }
                            .foregroundStyle(Color.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 8))
                        }
                    }
                }
                
                // 模式切换: 加工计划 vs 领取列表
                HStack(spacing: 8) {
                    SegmentedButton(
                        label: "加工计划",
                        isSelected: mode == "plans",
                        action: {
                            withAnimation {
                                mode = "plans"
                                isLoading = false
                                Task { await loadData() }
                            }
                        }
                    )
                    .frame(maxWidth: .infinity)
                    
                    SegmentedButton(
                        label: "领取列表",
                        isSelected: mode == "pickup",
                        action: {
                            withAnimation {
                                mode = "pickup"
                                isLoading = false
                                Task { await loadData() }
                            }
                        }
                    )
                    .frame(maxWidth: .infinity)
                }
                
                // 加工计划模式下的统计网格
                if mode == "plans" {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(statItems, id: \.1) { item in
                                let isSelected = activeView == item.1
                                Button(action: {
                                    withAnimation {
                                        activeView = item.1
                                        Task { await loadData() }
                                    }
                                }) {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(item.2)
                                            .scaledFont(16, weight: .bold)
                                            .foregroundStyle(item.0.contains("逾期") ? Color.danger : (isSelected ? Color.appPrimary : Color.ink))
                                        Text(item.0)
                                            .scaledFont(11)
                                            .foregroundStyle(isSelected ? Color.appPrimary : Color.muted)
                                    }
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(isSelected ? Color.appPrimarySoft : Color.surface)
                                    .clipShape(.rect(cornerRadius: 8))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(isSelected ? Color.appPrimary : Color.cardBorder, lineWidth: 1)
                                    )
                                }
                            }
                        }
                        .padding(.horizontal, 2)
                    }
                } else {
                    // 领取列表模式下的统计卡片 (对齐 Android)
                    HStack(spacing: 10) {
                        statPickupCard(
                            label: "待领取",
                            value: "\(stats?.pickupWaitingCount ?? 0)",
                            isSelected: pickupStatus == 0,
                            color: .appPrimary,
                            softColor: .appPrimarySoft
                        ) {
                            pickupStatus = 0
                            Task { await loadData() }
                        }
                        
                        statPickupCard(
                            label: "已领取",
                            value: "",
                            isSelected: pickupStatus == 1,
                            color: .success,
                            softColor: .successSoft
                        ) {
                            pickupStatus = 1
                            Task { await loadData() }
                        }
                    }
                }
                
                // 搜索栏
                SearchBarField(
                    text: $searchText,
                    placeholder: mode == "plans" ? "搜索计划单号、患者姓名或备注" : "搜索取货码、患者姓名或手机号",
                    onSearch: {
                        Task { await loadData() }
                    },
                    onScan: { router.isScannerPresented = true }
                )
                
                // 超管门店筛选
                if isSuperAdmin && !stores.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(label: "全部门店", isSelected: selectedStoreId == nil) {
                                selectedStoreId = nil
                                Task { await loadData() }
                            }
                            ForEach(stores) { store in
                                SegmentedButton(label: store.name, isSelected: selectedStoreId == store.id) {
                                    selectedStoreId = store.id
                                    Task { await loadData() }
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 8)
            
            // 2. 列表内容展示
            if mode == "plans" {
                plansContentView
            } else {
                pickupContentView
            }
        }
        .onChange(of: activeView) { _, _ in NotificationCenter.default.post(name: NSNotification.Name("ScrollToTop"), object: nil) }
        .onChange(of: mode) { _, _ in NotificationCenter.default.post(name: NSNotification.Name("ScrollToTop"), object: nil) }
        .background(Color.pageBackground)
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("ListNeedsRefresh_Processing"))) { _ in
            ApiClient.shared.clearResponseCache()
            Task { await loadData() }
        }
        .task {
            if isSuperAdmin && stores.isEmpty {
                if let sts = try? await ApiClient.shared.fetchStores() {
                    self.stores = sts
                }
            }
            await loadData()
        }
        .sheet(isPresented: $isCreatingPlan) {
            ProcessingPlanFormView()
        }
        .sheet(item: $planToEdit) { plan in
            ProcessingPlanFormView(planToEdit: plan)
        }
        .sheet(item: $planForPackage) { plan in
            GeneratePackageDialog(plan: plan) {
                await loadData()
            }
        }
    }
    
    // MARK: - 计划模式列表视图
    @ViewBuilder
    private var plansContentView: some View {
        if isLoading && plans.isEmpty {
            Spacer()
            ProgressView("正在加载加工计划...")
            Spacer()
        } else if let error = errorMessage, !error.isEmpty {
            Spacer()
            Text(error).foregroundStyle(Color.danger).scaledFont(14).padding()
            Spacer()
        } else if plans.isEmpty {
            Spacer()
            VStack(spacing: 12) {
                Image(systemName: "doc.plaintext")
                    .scaledFont(48)
                    .foregroundStyle(Color.muted)
                Text("当前视图暂无加工任务")
                    .scaledFont(15)
                    .foregroundStyle(Color.muted)
            }
            Spacer()
        } else {
            AppScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(plans) { plan in
                        ProcessingPlanCard(
                            plan: plan,
                            onRxClick: {
                                let rxId = plan.prescriptionId ?? plan.prescription?.id ?? 0
                                if rxId > 0 {
                                    router.navigate(to: .prescriptionDetail(id: rxId))
                                }
                            },
                            onStartClick: {
                                Task {
                                    _ = try? await ApiClient.shared.transitionPlan(id: plan.id, status: 1)
                                    await loadData()
                                }
                            },
                            onDelayClick: {
                                Task {
                                    _ = try? await ApiClient.shared.delayPlan(id: plan.id, days: 1)
                                    await loadData()
                                }
                            },
                            onScanClick: {
                                router.isScannerPresented = true
                            },
                            onWorkflowClick: {
                                router.navigate(to: .workflowOperation(planId: plan.id, planCode: plan.planCode))
                            },
                            onGeneratePackageClick: {
                                planForPackage = plan
                            },
                            onEditClick: {
                                planToEdit = plan
                            },
                            onCancelClick: {
                                Task {
                                    _ = try? await ApiClient.shared.cancelPlan(id: plan.id)
                                    await loadData()
                                }
                            },
                            onTap: {
                                router.navigate(to: .workflowOperation(planId: plan.id, planCode: plan.planCode))
                            }
                        )
                    }
                }
                .padding(16)
            }
            .refreshable {
                ApiClient.shared.clearResponseCache()
                await loadData()
            }
            .id("plans_\(activeView)_\(selectedStoreId ?? -1)")
            .background(Color.pageBackground)
        }
    }
    
    // MARK: - 领取模式列表视图 (对标 Android pickupFlow)
    @ViewBuilder
    private var pickupContentView: some View {
        if isLoading && pickupPackages.isEmpty {
            Spacer()
            ProgressView("正在加载领取列表...")
            Spacer()
        } else if let error = errorMessage, !error.isEmpty {
            Spacer()
            Text(error).foregroundStyle(Color.danger).scaledFont(14).padding()
            Spacer()
        } else if pickupPackages.isEmpty {
            Spacer()
            VStack(spacing: 12) {
                Image(systemName: "bag")
                    .scaledFont(48)
                    .foregroundStyle(Color.muted)
                Text(pickupStatus == 0 ? "暂无待领取的药材" : "暂无已领取记录")
                    .scaledFont(15)
                    .foregroundStyle(Color.muted)
            }
            Spacer()
        } else {
            AppScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(pickupPackages) { pkg in
                        ProcessingPickupPackageCard(
                            pkg: pkg,
                            isSuperAdmin: isSuperAdmin,
                            onQuickVerify: {
                                Task {
                                    _ = try? await ApiClient.shared.verifyPackage(code: pkg.code, pickupMethod: 0)
                                    await loadData()
                                }
                            },
                            onTap: {
                                router.navigate(to: .packageDetail(id: pkg.id))
                            }
                        )
                    }
                }
                .padding(16)
            }
            .refreshable {
                ApiClient.shared.clearResponseCache()
                await loadData()
            }
            .id("pickups_\(pickupStatus)_\(selectedStoreId ?? -1)")
            .background(Color.pageBackground)
        }
    }
    
    // MARK: - 辅助卡片
    private func statPickupCard(label: String, value: String, isSelected: Bool, color: Color, softColor: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 3) {
                if !value.isEmpty {
                    Text(value)
                        .scaledFont(18, weight: .bold)
                        .foregroundStyle(color)
                }
                Text(label)
                    .scaledFont(value.isEmpty ? 15 : 12, weight: (isSelected || value.isEmpty) ? .bold : .regular)
                    .foregroundStyle(isSelected ? color : Color.muted)
            }
            .frame(maxWidth: .infinity, alignment: value.isEmpty ? .center : .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(isSelected ? softColor : Color.surface)
            .clipShape(.rect(cornerRadius: 8))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? color : Color.cardBorder, lineWidth: isSelected ? 1.5 : 1)
            )
        }
    }
    
    // MARK: - 数据请求
    private func loadData() async {
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
            async let fetchedStats = ApiClient.shared.fetchProcessingStats(storeId: selectedStoreId)
            
            if mode == "plans" {
                async let fetchedPlans = ApiClient.shared.fetchProcessingPlans(view: activeView, keyword: searchText, storeId: selectedStoreId)
                let (plansRes, statsRes) = try await (fetchedPlans, fetchedStats)
                guard !Task.isCancelled else { return }
                self.plans = plansRes
                self.stats = statsRes
            } else {
                async let fetchedPkgs = ApiClient.shared.fetchPackages(
                    status: pickupStatus,
                    keyword: searchText,
                    storeId: selectedStoreId,
                    sortBy: "createdAt"
                )
                let (pkgsRes, statsRes) = try await (fetchedPkgs, fetchedStats)
                guard !Task.isCancelled else { return }
                self.pickupPackages = pkgsRes
                self.stats = statsRes
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

// MARK: - 生成包裹弹窗 (对标 Android ProcessingListScreen.kt AlertDialog)
struct GeneratePackageDialog: View {
    let plan: ProcessingPlanItem
    let onDone: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var remark: String = ""
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    @State private var currentTaskID: UUID = UUID()
    
    
    init(plan: ProcessingPlanItem, onDone: @escaping () async -> Void) {
        self.plan = plan
        self.onDone = onDone
        _remark = State(initialValue: plan.processRemark ?? plan.remark ?? "")
    }
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("计划信息")) {
                    InfoRowItem(label: "患者", value: plan.patientName ?? "-")
                    InfoRowItem(label: "工艺", value: plan.method ?? "加工")
                    InfoRowItem(label: "剂数", value: "\(plan.totalDose ?? 0) 剂")
                }
                
                Section(
                    header: Text("包裹备注"),
                    footer: Text("该加工计划已完成，生成包裹后将通知顾客或安排物流取件。")
                ) {
                    TextField("填写包裹备注（如代煎规格、取货提醒）", text: $remark)
                }
                
                if let err = errorMessage {
                    Section {
                        Text(err).foregroundStyle(Color.danger).scaledFont(13)
                    }
                }
            }
            .navigationTitle("生成包裹")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("确认生成") {
                        isSubmitting = true
                        Task {
                            do {
                                try await ApiClient.shared.generatePlanPackage(
                                    id: plan.id,
                                    itemInfo: remark.trimmingCharacters(in: .whitespacesAndNewlines)
                                )
                                await onDone()
                                await MainActor.run {
                                    isSubmitting = false
                                    dismiss()
                                }
                            } catch {
                                await MainActor.run {
                                    isSubmitting = false
                                    errorMessage = error.localizedDescription
                                }
                            }
                        }
                    }
                    .scaledFont(14, weight: .bold)
                    .foregroundStyle(Color.appPrimary)
                    .disabled(isSubmitting)
                }
            }
        }
    }
}

// MARK: - 加工计划卡片组件 (对标 Android ProcessingPlanCard)
struct ProcessingPlanCard: View {
    let plan: ProcessingPlanItem
    let onRxClick: () -> Void
    let onStartClick: () -> Void
    let onDelayClick: () -> Void
    let onScanClick: () -> Void
    let onWorkflowClick: () -> Void
    let onGeneratePackageClick: () -> Void
    let onEditClick: () -> Void
    let onCancelClick: () -> Void
    let onTap: () -> Void
    
    private var rxId: Int {
        plan.prescriptionId ?? plan.prescription?.id ?? 0
    }
    
    private var pickupMethodText: String {
        plan.pickupMethod == 1 ? "自提" : (plan.pickupMethod == 2 ? "快递" : "未指定")
    }
    
    var body: some View {
        AppCard(padding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                // 卡片 Header
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(alignment: .center, spacing: 6) {
                            Image(systemName: "gearshape.fill")
                                .foregroundStyle(Color.appPrimary)
                                .font(.system(size: 14))
                            Text("\(plan.patientName ?? "-") · \(plan.method ?? "加工")")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.ink)
                        }
                        
                        Text("\(maskPhone(plan.prescription?.phone)) · 医生：\(plan.doctorName ?? "-")")
                            .scaledFont(12)
                            .foregroundStyle(Color.muted)
                    }
                    
                    Spacer()
                    
                    HStack(spacing: 6) {
                        if plan.isUrgent == true {
                            UrgentBadge()
                        }
                        StatusPill(text: plan.statusText)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                
                Divider().foregroundStyle(Color.cardBorder)
                
                // 卡片内容区
                VStack(alignment: .leading, spacing: 8) {
                    InfoRowItem(label: "批次剂数", value: "第 \(plan.batchNo ?? 1) 批 · \(plan.totalDose ?? 0) 剂")
                    if let bagCount = plan.bagCount, bagCount > 0 {
                        InfoRowItem(label: "代煎规格", value: "\(bagCount) 袋 · \(plan.volumeMl ?? 0)ml")
                    }
                    
                    InfoRowItem(label: "取货方式", value: pickupMethodText)
                    InfoRowItem(label: "计划开工", value: formatDateOnly(plan.processDate, defaultVal: "未安排"))
                    
                    if let startDate = plan.startDate {
                        InfoRowItem(label: "实际开工", value: formatDateTimeToMinute(startDate))
                    }
                    if let finishDate = plan.finishDate {
                        InfoRowItem(label: "完成时间", value: formatDateTimeToMinute(finishDate))
                    }
                    InfoRowItem(label: "创建时间", value: formatDateTimeToMinute(plan.createdAt))
                    if let remark = plan.remark, !remark.isEmpty {
                        InfoRowItem(label: "备注", value: remark)
                    }
                    if let processRemark = plan.processRemark, !processRemark.isEmpty {
                        InfoRowItem(label: "加工备注", value: processRemark)
                    }
                }
                .padding(16)
                
                Divider().foregroundStyle(Color.cardBorder)
                
                // 底部操作行
                FlowLayout(spacing: 8, lineSpacing: 8, alignment: .trailing) {
                    if rxId > 0 {
                        Button(action: onRxClick) {
                            Text("处方")
                                .scaledFont(12)
                                .foregroundStyle(Color.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color.surface)
                                .clipShape(.rect(cornerRadius: 6))
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                        }
                    }
                    
                    // 2. 待加工操作: 开始加工 / 延期明天
                    if plan.status == 0 {
                        Button(action: onStartClick) {
                            Text("开始加工")
                                .scaledFont(12, weight: .bold)
                                .foregroundStyle(Color.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color.appPrimary)
                                .clipShape(.rect(cornerRadius: 6))
                        }
                        
                        Button(action: onDelayClick) {
                            Text("延期明天")
                                .scaledFont(12)
                                .foregroundStyle(Color.appPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.appPrimary, lineWidth: 1))
                        }
                    }
                    
                    // 3. 加工中操作: 快捷扫码 / 工序流转
                    if plan.status == 1 {
                        Button(action: onScanClick) {
                            HStack(spacing: 2) {
                                Image(systemName: "qrcode.viewfinder")
                                Text("扫码")
                            }
                            .scaledFont(12, weight: .bold)
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 6))
                        }
                        
                        Button(action: onWorkflowClick) {
                            Text("工序流转")
                                .scaledFont(12, weight: .bold)
                                .foregroundStyle(Color.appPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.appPrimary, lineWidth: 1))
                        }
                    }
                    
                    // 4. 加工完成待生成包裹
                    if plan.status == 2 && plan.package?.code == nil {
                        Button(action: onGeneratePackageClick) {
                            Text("生成包裹")
                                .scaledFont(12, weight: .bold)
                                .foregroundStyle(Color.white)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color.success)
                                .clipShape(.rect(cornerRadius: 6))
                        }
                    }
                    
                    // 5. 编辑 / 取消 (状态 0 或 1)
                    if plan.status == 0 || plan.status == 1 {
                        Button(action: onEditClick) {
                            Text("编辑")
                                .scaledFont(12)
                                .foregroundStyle(Color.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color.surface)
                                .clipShape(.rect(cornerRadius: 6))
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                        }
                        
                        Button(action: onCancelClick) {
                            Text("取消")
                                .scaledFont(12)
                                .foregroundStyle(Color.danger)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.danger, lineWidth: 1))
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
        }
        .onTapGesture(perform: onTap)
    }
}

// MARK: - 领取模式包裹卡片组件 (对标 Android ProcessingPickupPackageCard)
struct ProcessingPickupPackageCard: View {
    let pkg: PackageModel
    let isSuperAdmin: Bool
    let onQuickVerify: () -> Void
    let onTap: () -> Void
    
    private var displayTime: String {
        let rawTime = pkg.time.isEmpty ? (pkg.pickedAt.isEmpty ? "" : pkg.pickedAt) : pkg.time
        return (rawTime.isEmpty || rawTime == "未领取") ? "未领取" : formatDateTimeToMinute(rawTime)
    }
    
    var body: some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "shippingbox")
                        .foregroundStyle(Color.appPrimary)
                    Text(pkg.name)
                        .scaledFont(16, weight: .bold)
                        .foregroundStyle(Color.ink)
                    Spacer()
                    StatusPill(text: pkg.method)
                    StatusPill(text: pkg.statusText)
                }
                
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("取货码").scaledFont(11).foregroundStyle(Color.muted)
                        Text(pkg.code.formattedPickupCode).scaledFont(18, weight: .bold).foregroundStyle(Color.appPrimaryDark)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("收件人").scaledFont(11).foregroundStyle(Color.muted)
                        Text("\(pkg.customer) · \(maskPhone(pkg.phone))")
                            .scaledFont(13, weight: .semibold)
                            .foregroundStyle(Color.ink)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.surface)
                .clipShape(.rect(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                
                if isSuperAdmin && !pkg.store.isEmpty {
                    InfoRowItem(label: "所属门店", value: pkg.store)
                }
                InfoRowItem(label: "领取时间", value: displayTime)
                if !pkg.info.isEmpty {
                    InfoRowItem(label: "备注", value: pkg.info)
                }
                
                if pkg.statusCode == 0 {
                    HStack {
                        Spacer()
                        Button(action: onQuickVerify) {
                            Text("快速核销")
                                .scaledFont(12, weight: .bold)
                                .foregroundStyle(Color.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 5)
                                .background(Color.success)
                                .clipShape(.rect(cornerRadius: 6))
                        }
                    }
                    .padding(.top, 4)
                }
            }
        }
        .onTapGesture(perform: onTap)
    }
}
