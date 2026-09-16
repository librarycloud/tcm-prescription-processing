import SwiftUI

@MainActor
public struct E6ImportsView: View {
    @ObservedObject private var router = Router.shared
    @State private var searchText = ""
    @State private var selectedStatus: Int? = nil
    @State private var orderDate: String = "" // "" for all, or yyyy-MM-dd
    @State private var e6Imports: [E6ImportItem] = []
    @State private var selectedIds: Set<Int> = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var successNotice: String? = nil
    
    // 驳回弹窗
    @State private var rejectTargetId: Int? = nil
    @State private var rejectionReason = ""
    @State private var isRejecting = false
    
    // 确认排产 / 合并排产 Sheet
    @State private var confirmTargetItems: [E6ImportItem]? = nil
    
    private let statusOptions: [(Int?, String)] = [
        (nil, "全部"), (0, "待确认"), (1, "待映射"), (2, "导入异常"),
        (3, "已生成处方"), (4, "已驳回"), (5, "已取消"), (6, "数据冲突"), (7, "处理中")
    ]
    
    private var todayString: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
    
    public init() {}
    
    public var body: some View {
        ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                // 顶部标题与搜索操作栏
                VStack(spacing: 10) {
                    SectionHeader(title: "E6诊所处方导入", subtitle: "核对E6订单，确认后生成处方与加工计划") {
                        Button(action: { Task { await loadE6Imports() } }) {
                            HStack(spacing: 4) {
                                Image(systemName: "arrow.triangle.2.circlepath")
                                Text("同步")
                            }
                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                            .foregroundColor(.appPrimary)
                        }
                    }
                    
                    SearchBarField(
                        text: $searchText,
                        placeholder: "搜索订单号、顾客、电话或医师编码",
                        onSearch: { Task { await loadE6Imports() } },
                        onScan: { router.isScannerPresented = true }
                    )
                    
                    // 日期快捷切换
                    HStack(spacing: 8) {
                        SegmentedButton(
                            label: "今日订单",
                            isSelected: orderDate == todayString,
                            action: {
                                orderDate = orderDate == todayString ? "" : todayString
                                Task { await loadE6Imports() }
                            }
                        )
                        SegmentedButton(
                            label: "全部日期",
                            isSelected: orderDate.isEmpty,
                            action: {
                                orderDate = ""
                                Task { await loadE6Imports() }
                            }
                        )
                        
                        Spacer()
                        
                        // 自定义日期选择器
                        DatePicker("", selection: Binding(
                            get: {
                                let formatter = DateFormatter()
                                formatter.dateFormat = "yyyy-MM-dd"
                                return formatter.date(from: orderDate) ?? Date()
                            },
                            set: { newValue in
                                let formatter = DateFormatter()
                                formatter.dateFormat = "yyyy-MM-dd"
                                orderDate = formatter.string(from: newValue)
                                Task { await loadE6Imports() }
                            }
                        ), displayedComponents: .date)
                        .labelsHidden()
                        .frame(width: 120)
                    }
                    
                    // 状态筛选 Chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(statusOptions, id: \.1) { opt in
                                SegmentedButton(
                                    label: opt.1,
                                    isSelected: selectedStatus == opt.0,
                                    action: {
                                        selectedStatus = opt.0
                                        Task { await loadE6Imports() }
                                    }
                                )
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 8)
                .background(Color.pageBackground)
                
                // 提示栏
                if let notice = successNotice {
                    Text(notice)
                        .font(.system(size: (13) * ThemeManager.shared.fontScale))
                        .foregroundColor(.success)
                        .padding(.vertical, 4)
                }
                
                // 导入订单列表
                if isLoading && e6Imports.isEmpty {
                    Spacer()
                    ProgressView("正在同步 E6 处方导入记录...")
                    Spacer()
                } else if let error = errorMessage, !error.isEmpty {
                    Spacer()
                    Text(error).foregroundColor(.danger).font(.system(size: (14) * ThemeManager.shared.fontScale)).padding()
                    Spacer()
                } else if e6Imports.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "icloud.and.arrow.down")
                            .font(.system(size: (48) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                        Text("暂无符合条件的 E6 处方单")
                            .font(.system(size: (15) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                    }
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(e6Imports) { item in
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 8) {
                                        HStack(alignment: .center) {
                                            // 多选勾选框
                                            if item.canReview {
                                                Button(action: {
                                                    if selectedIds.contains(item.id) {
                                                        selectedIds.remove(item.id)
                                                    } else {
                                                        selectedIds.insert(item.id)
                                                    }
                                                }) {
                                                    Image(systemName: selectedIds.contains(item.id) ? "checkmark.circle.fill" : "circle")
                                                        .foregroundColor(selectedIds.contains(item.id) ? .appPrimary : .muted)
                                                        .font(.system(size: (18) * ThemeManager.shared.fontScale))
                                                }
                                                .buttonStyle(PlainButtonStyle())
                                                .padding(.trailing, 2)
                                            }
                                            
                                            Text(item.displayCustomer)
                                                .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                                .foregroundColor(.ink)
                                            Spacer()
                                            HStack(spacing: 6) {
                                                StatusPill(text: item.isPaidBool ? "已付款" : "未付款")
                                                StatusPill(text: item.statusText)
                                            }
                                        }
                                        
                                        let phoneStr = item.displayPhone
                                        let maskedPhone = phoneStr.count == 11 ? "\(phoneStr.prefix(3))****\(phoneStr.suffix(4))" : phoneStr
                                        Text("\(maskedPhone)  ·  单号：\(item.displayOrderNo)")
                                            .font(.system(size: (12.5) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.ink)
                                        
                                        Text("\(formatDateOnly(item.displayDate))  ·  \(item.displayDose)剂  ·  ¥\(String(format: "%.2f", item.displayPrice))")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                        
                                        Text("操作员：\(item.displayOperator)  ·  系统医生：\(item.displayDoctor)")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                            .lineLimit(1)
                                        
                                        if let err = item.errorMessage, !err.isEmpty {
                                            HStack(alignment: .top, spacing: 4) {
                                                Image(systemName: "exclamationmark.triangle.fill")
                                                    .foregroundColor(.danger)
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                Text(err)
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                    .foregroundColor(.danger)
                                                    .lineLimit(2)
                                            }
                                            .padding(6)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .background(Color.dangerSoft)
                                            .cornerRadius(6)
                                        }
                                        
                                        // 操作栏按钮
                                        Divider().foregroundColor(Color.cardBorder).padding(.top, 2)
                                        
                                        HStack(spacing: 8) {
                                            if item.canReview {
                                                Button(action: {
                                                    rejectTargetId = item.id
                                                    rejectionReason = ""
                                                }) {
                                                    Text("驳回")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .medium))
                                                        .foregroundColor(.danger)
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 6)
                                                        .background(Color.dangerSoft)
                                                        .cornerRadius(6)
                                                }
                                            }
                                            
                                            if item.status == 2 || item.status == 6 {
                                                Button(action: { revalidate(id: item.id) }) {
                                                    Text("重新校验")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .medium))
                                                        .foregroundColor(.appPrimary)
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 6)
                                                        .background(Color.appPrimarySoft)
                                                        .cornerRadius(6)
                                                }
                                            }
                                            
                                            Spacer()
                                            
                                            if item.canConfirm {
                                                Button(action: {
                                                    confirmTargetItems = [item]
                                                }) {
                                                    HStack(spacing: 4) {
                                                        Image(systemName: "checkmark.circle.fill")
                                                        Text("确认导入并排产")
                                                    }
                                                    .font(.system(size: (12.5) * ThemeManager.shared.fontScale, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 12)
                                                    .padding(.vertical, 6)
                                                    .background(Color.appPrimary)
                                                    .cornerRadius(6)
                                                }
                                            }
                                        }
                                    }
                                }
                                .onTapGesture {
                                    router.navigate(to: .e6ImportDetail(id: item.id))
                                }
                            }
                        }
                        .padding(16)
                        .padding(.bottom, selectedIds.isEmpty ? 0 : 64)
                    }
                    .refreshable {
                        await loadE6Imports()
                    }
                    .background(Color.pageBackground)
                }
            }
            
            // 底部多选批量合并浮动条
            if !selectedIds.isEmpty {
                VStack {
                    HStack {
                        Text("已选择 \(selectedIds.count) 项")
                            .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        Button(action: {
                            let items = e6Imports.filter { selectedIds.contains($0.id) }
                            confirmTargetItems = items
                        }) {
                            Text("合并为单处方并排产")
                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .bold))
                                .foregroundColor(.appPrimary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Color.white)
                                .cornerRadius(8)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Color.appPrimary)
                    .cornerRadius(12)
                    .shadow(radius: 6)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
                }
            }
        }
        .sheet(item: Binding<IdentifiableIntWrapper?>(
            get: { rejectTargetId.map { IdentifiableIntWrapper(id: $0) } },
            set: { rejectTargetId = $0?.id }
        )) { wrapper in
            NavigationStack {
                Form {
                    Section(header: Text("驳回原因"), footer: Text("驳回后该订单将无法自动导入排产")) {
                        TextField("请输入驳回原因", text: $rejectionReason)
                    }
                }
                .navigationTitle("驳回 E6 订单")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { rejectTargetId = nil }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("确认驳回") {
                            submitReject(id: wrapper.id)
                        }
                        .foregroundColor(.danger)
                        .disabled(rejectionReason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isRejecting)
                    }
                }
            }
        }
        .sheet(item: Binding<E6ConfirmItemsWrapper?>(
            get: { confirmTargetItems.map { E6ConfirmItemsWrapper(items: $0) } },
            set: { confirmTargetItems = $0?.items }
        )) { wrapper in
            E6ConfirmFormSheet(items: wrapper.items) {
                selectedIds.removeAll()
                confirmTargetItems = nil
                Task { await loadE6Imports() }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .task {
            await loadE6Imports()
        }
        .navigationTitle("E6诊所处方导入")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func loadE6Imports() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let res = try await ApiClient.shared.fetchE6Imports(
                keyword: searchText.trimmingCharacters(in: .whitespacesAndNewlines),
                status: selectedStatus,
                orderDate: orderDate
            )
            guard !Task.isCancelled else { return }
            self.e6Imports = res
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
    
    private func revalidate(id: Int) {
        Task {
            do {
                try await ApiClient.shared.revalidateE6Import(id: id)
                await MainActor.run {
                    successNotice = "已完成重新校验"
                }
                await loadE6Imports()
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    private func submitReject(id: Int) {
        isRejecting = true
        Task {
            do {
                try await ApiClient.shared.rejectE6Import(id: id, reason: rejectionReason)
                await MainActor.run {
                    isRejecting = false
                    rejectTargetId = nil
                    successNotice = "已成功驳回订单"
                }
                await loadE6Imports()
            } catch {
                await MainActor.run {
                    isRejecting = false
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}

// 辅助包装器
private struct IdentifiableIntWrapper: Identifiable {
    let id: Int
}

private struct E6ConfirmItemsWrapper: Identifiable {
    var id: String { items.map { "\($0.id)" }.joined(separator: "_") }
    let items: [E6ImportItem]
}

// MARK: - E6 确认排产 / 合并排产表单 Sheet (对齐 Android E6ImportConfirmScreen)
struct BatchDraft: Identifiable {
    let id = UUID()
    var index: Int
    var totalDose: String
    var scheduleType: Int
    var processDate: Date = Date()
}

struct E6ConfirmFormSheet: View {
    @Environment(\.dismiss) private var dismiss
    let items: [E6ImportItem]
    let onDone: () -> Void
    
    @State private var customerName = ""
    @State private var phone = ""
    @State private var totalDoseStr = "1"
    @State private var batchCountStr = "1"
    @State private var selectedDoctorId = 0
    @State private var selectedProcessTypeId = 0
    @State private var autoAllocationEnabled = true
    @State private var bagsPerDose = "2"
    @State private var volumeMl = "200"
    @State private var pickupMethod = 0
    @State private var batchDrafts: [BatchDraft] = []
    
    @State private var doctors: [DoctorItem] = []
    @State private var processTypes: [ProcessTypeItem] = []
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    
    private var isMerge: Bool { items.count > 1 }
    
    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("顾客与处方信息")) {
                    TextField("顾客姓名", text: $customerName)
                    TextField("手机号", text: $phone)
                        .keyboardType(.phonePad)
                    
                    HStack {
                        Text("总剂数 *")
                        Spacer()
                        TextField("剂数", text: $totalDoseStr)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .onChange(of: totalDoseStr) { _, _ in generateDrafts() } // Xcode warns about iOS 17 deprecation, but we keep it for iOS 15 support
                    }
                    
                    HStack {
                        Text("批次数")
                        Spacer()
                        TextField("批次", text: $batchCountStr)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .onChange(of: batchCountStr) { _, _ in generateDrafts() }
                    }
                }
                
                Section(header: Text("加工排产配置")) {
                    Toggle("自动分配至可用设备", isOn: $autoAllocationEnabled)
                    
                    Picker("加工方式", selection: $selectedProcessTypeId) {
                        Text("请选择").tag(0)
                        ForEach(processTypes) { pt in
                            Text(pt.name).tag(pt.id)
                        }
                    }
                    
                    HStack {
                        Text("每付包数")
                        Spacer()
                        TextField("包数", text: $bagsPerDose)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                    
                    HStack {
                        Text("每包容量 (ml)")
                        Spacer()
                        TextField("容量", text: $volumeMl)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                    
                    Picker("取货方式", selection: $pickupMethod) {
                        Text("到店自提").tag(0)
                        Text("同城跑腿").tag(1)
                        Text("快递寄送").tag(2)
                    }
                }
                
                if !batchDrafts.isEmpty {
                    Section(header: Text("批次排产明细编辑")) {
                        ForEach($batchDrafts) { $draft in
                            VStack(spacing: 8) {
                                HStack {
                                    Text("第 \(draft.index + 1) 批")
                                        .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                                    Spacer()
                                    Picker("排产方式", selection: $draft.scheduleType) {
                                        Text("即刻排产").tag(1)
                                        Text("等待通知").tag(2)
                                    }
                                    .pickerStyle(SegmentedPickerStyle())
                                    .frame(width: 160)
                                }
                                HStack {
                                    Text("剂数:")
                                        .font(.system(size: (14) * ThemeManager.shared.fontScale))
                                    TextField("剂数", text: $draft.totalDose)
                                        .keyboardType(.numberPad)
                                        .textFieldStyle(RoundedBorderTextFieldStyle())
                                }
                                if draft.scheduleType == 1 {
                                    DatePicker("加工日期", selection: $draft.processDate, displayedComponents: .date)
                                        .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
                
                Section(header: Text("系统医生")) {
                    Picker("指定医生", selection: $selectedDoctorId) {
                        Text("未指定").tag(0)
                        ForEach(doctors) { doc in
                            Text(doc.name).tag(doc.id)
                        }
                    }
                }
                
                if let err = errorMessage {
                    Section {
                        Text(err).foregroundColor(.danger).font(.system(size: (13) * ThemeManager.shared.fontScale))
                    }
                }
            }
            .navigationTitle(isMerge ? "合并订单并排产 (\(items.count)单)" : "确认导入并排产")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: submit) {
                        if isSubmitting { ProgressView() }
                        else { Text("提交排产").bold() }
                    }
                    .disabled(isSubmitting)
                }
            }
            .task {
                initDefaults()
                await loadBaseData()
            }
        }
    }
    
    private func initDefaults() {
        if let first = items.first {
            customerName = first.displayCustomer
            phone = first.displayPhone
            if let dId = first.doctorMapping?.doctorId ?? first.doctorMapping?.doctor?.id, dId > 0 {
                selectedDoctorId = dId
            }
        }
        let sumDoses = items.reduce(0) { $0 + $1.displayDose }
        totalDoseStr = "\(max(sumDoses, 1))"
        generateDrafts()
    }
    
    private func generateDrafts() {
        let totalDose = max(Int(totalDoseStr) ?? 1, 1)
        let batchCount = max(Int(batchCountStr) ?? 1, 1)
        var newDrafts: [BatchDraft] = []
        let baseDose = totalDose / batchCount
        let remainder = totalDose % batchCount
        
        for i in 0..<batchCount {
            let bDose = baseDose + (i < remainder ? 1 : 0)
            let existingSchedule = i < batchDrafts.count ? batchDrafts[i].scheduleType : 1
            let existingDate = i < batchDrafts.count ? batchDrafts[i].processDate : Date()
            newDrafts.append(BatchDraft(index: i, totalDose: "\(bDose)", scheduleType: existingSchedule, processDate: existingDate))
        }
        self.batchDrafts = newDrafts
    }
    
    private func loadBaseData() async {
        do {
            async let d = ApiClient.shared.fetchDoctors()
            async let pt = ApiClient.shared.fetchProcessTypes()
            let (docList, ptList) = try await (d, pt)
            self.doctors = docList
            self.processTypes = ptList
            if selectedProcessTypeId == 0, let firstPt = ptList.first {
                self.selectedProcessTypeId = firstPt.id
            }
            if selectedDoctorId == 0, let firstDoc = docList.first {
                self.selectedDoctorId = firstDoc.id
            }
        } catch {}
    }
    
    private func submit() {
        guard let totalDose = Int(totalDoseStr), totalDose > 0 else {
            errorMessage = "总剂数必须大于0"
            return
        }
        isSubmitting = true
        errorMessage = nil
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        
        var batches: [[String: Any]] = []
        for draft in batchDrafts {
            let bDose = Int(draft.totalDose) ?? 1
            var b: [String: Any] = [
                "totalDose": bDose,
                "scheduleType": draft.scheduleType
            ]
            if draft.scheduleType == 1 {
                b["processDate"] = dateFormatter.string(from: draft.processDate)
            }
            batches.append(b)
        }
        
        let firstBatch = batchDrafts.first
        let firstScheduleType = firstBatch?.scheduleType ?? 1
        let firstProcessDate = (firstScheduleType == 1 && firstBatch != nil) ? dateFormatter.string(from: firstBatch!.processDate) : nil
        
        var payload: [String: Any] = [
            "customerName": customerName,
            "phone": phone,
            "totalDose": totalDose,
            "doseCount": totalDose,
            "autoAllocationEnabled": autoAllocationEnabled,
            "bagsPerDose": Int(bagsPerDose) ?? 2,
            "volumeMl": Int(volumeMl) ?? 200,
            "pickupMethod": pickupMethod,
            "scheduleType": firstScheduleType,
            "batches": batches
        ]
        if let fDate = firstProcessDate {
            payload["processDate"] = fDate
        }
        if selectedDoctorId > 0 { payload["doctorId"] = selectedDoctorId }
        if selectedProcessTypeId > 0 { payload["processTypeId"] = selectedProcessTypeId }
        
        Task {
            do {
                if isMerge {
                    let ids = items.map { $0.id }
                    payload["ids"] = ids
                    payload["importIds"] = ids
                    try await ApiClient.shared.mergeE6Imports(payload: payload)
                } else if let single = items.first {
                    try await ApiClient.shared.confirmE6Import(id: single.id, payload: payload)
                }
                await MainActor.run {
                    isSubmitting = false
                    dismiss()
                    onDone()
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

// MARK: - E6 导入确认详情 (对齐 Android E6ImportDetailScreen)
@MainActor
public struct E6ImportDetailView: View {
    public let id: Int
    @ObservedObject private var router = Router.shared
    @Environment(\.dismiss) private var dismiss
    @State private var isSuccess = false
    @State private var isLoading = false
    @State private var isConfirming = false
    @State private var isShowingConfirmSheet = false
    @State private var detail: E6ImportItem? = nil
    @State private var errorMessage: String? = nil
    
    public init(id: Int) {
        self.id = id
    }
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                if isLoading && detail == nil {
                    ProgressView("正在加载订单详情...")
                        .frame(maxWidth: .infinity)
                        .padding(.top, 40)
                } else if detail == nil {
                    VStack(spacing: 12) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.system(size: (36) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                        Text(errorMessage ?? "未能加载订单详情")
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
                } else if let item = detail {
                    if let err = errorMessage {
                        AppCard(padding: 12) {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill").foregroundColor(.danger)
                                Text(err).font(.system(size: (13) * ThemeManager.shared.fontScale)).foregroundColor(.danger)
                                Spacer()
                                Button("关闭") { errorMessage = nil }.font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                            }
                        }
                    }
                    
                    // 1. 订单基本信息卡片
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("订单信息")
                                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                Spacer()
                                StatusPill(text: item.statusText)
                            }
                            
                            Divider().foregroundColor(Color.cardBorder)
                            
                            InfoRowItem(label: "E6订单号", value: item.displayOrderNo)
                            // 日期不带时间
                            InfoRowItem(label: "订单时间", value: formatDateOnly(item.displayDate))
                            InfoRowItem(label: "顾客", value: item.displayCustomer)
                            
                            let phoneStr = item.displayPhone
                            let maskedPhone = phoneStr.count == 11 ? "\(phoneStr.prefix(3))****\(phoneStr.suffix(4))" : phoneStr
                            InfoRowItem(label: "手机号", value: maskedPhone.isEmpty ? "-" : maskedPhone)
                            InfoRowItem(label: "操作员", value: item.displayOperator)
                            InfoRowItem(label: "系统医生", value: item.displayDoctor)
                            
                            if let docCode = item.e6DoctorCode, !docCode.isEmpty {
                                InfoRowItem(label: "医师编码", value: docCode)
                            }
                            InfoRowItem(label: "剂数", value: "\(item.displayDose)剂")
                            InfoRowItem(label: "付款", value: item.isPaidBool ? "已付款" : "未付款", valueColor: item.isPaidBool ? .success : .orange, isBold: true)
                            InfoRowItem(label: "总价", value: "¥\(String(format: "%.2f", item.displayPrice))", valueColor: .danger, isBold: true)
                            
                            if let rem = item.remark, !rem.isEmpty {
                                InfoRowItem(label: "备注", value: rem)
                            }
                            if let err = item.errorMessage, !err.isEmpty {
                                InfoRowItem(label: "错误信息", value: err, valueColor: .danger, isBold: true)
                            }
                        }
                    }
                    
                    // 2. 对应处方卡片 (如果已有对应处方)
                    if let rx = item.prescription {
                        AppCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("对应处方")
                                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                
                                Divider().foregroundColor(Color.cardBorder)
                                
                                InfoRowItem(label: "处方号", value: rx.prescriptionNo ?? "CF-\(rx.id)")
                                InfoRowItem(label: "状态", value: rx.statusText)
                                
                                Button(action: {
                                    router.navigate(to: .prescriptionDetail(id: rx.id))
                                }) {
                                    Text("查看对应处方")
                                        .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 38)
                                        .background(Color.appPrimary)
                                        .cornerRadius(8)
                                }
                                .padding(.top, 4)
                            }
                        }
                    }
                    
                    // 3. 加工计划卡片 (如果已有加工计划)
                    if let plan = item.processingPlan {
                        AppCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("加工计划")
                                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                
                                Divider().foregroundColor(Color.cardBorder)
                                
                                InfoRowItem(label: "计划状态", value: plan.statusText)
                                InfoRowItem(label: "加工剂数", value: "\(plan.totalDose ?? 0)剂")
                                if let pRem = plan.processRemark, !pRem.isEmpty {
                                    InfoRowItem(label: "加工备注", value: pRem)
                                }
                            }
                        }
                    }
                    
                    // 4. E6处方明细卡片
                    if let rawItems = item.rawPayload?.items, !rawItems.isEmpty {
                        AppCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("E6处方明细（\(rawItems.count)项）")
                                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.ink)
                                
                                Divider().foregroundColor(Color.cardBorder)
                                
                                // 表头
                                HStack {
                                    Text("药材名称").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted).frame(maxWidth: .infinity, alignment: .leading)
                                    Text("剂数").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted).frame(width: 40, alignment: .trailing)
                                    Text("单剂量").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted).frame(width: 60, alignment: .trailing)
                                    Text("总量").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted).frame(width: 60, alignment: .trailing)
                                }
                                
                                ForEach(Array(rawItems.enumerated()), id: \.offset) { index, rItem in
                                    HStack {
                                        Text("\(index + 1). \(rItem.name ?? "-")")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.ink)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                        Text(String(format: "%g", rItem.doseCount ?? 0))
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.ink)
                                            .frame(width: 40, alignment: .trailing)
                                        Text("\(String(format: "%g", rItem.quantity ?? 0))\(rItem.unit ?? "")")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.ink)
                                            .frame(width: 60, alignment: .trailing)
                                        Text("\(String(format: "%g", rItem.totalQuantity ?? 0))\(rItem.unit ?? "")")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .semibold))
                                            .foregroundColor(.ink)
                                            .frame(width: 60, alignment: .trailing)
                                    }
                                    .padding(.vertical, 3)
                                }
                            }
                        }
                    }
                    
                    // 5. 确认操作按钮 (弹出完整排产表单)
                    if item.status == 0 {
                        Button(action: { isShowingConfirmSheet = true }) {
                            HStack {
                                Text(item.prescriptionId == nil ? "确认导入并生成加工计划" : "重新生成加工计划")
                                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color.appPrimary)
                            .cornerRadius(10)
                        }
                        .padding(.top, 6)
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
        .navigationTitle("订单详情")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $isShowingConfirmSheet) {
            if let item = detail {
                E6ConfirmFormSheet(items: [item]) {
                    Task { await loadDetail() }
                }
            }
        }
        .task { await loadDetail() }
        .refreshable { await loadDetail() }
    }
    
    private func loadDetail() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let res = try await ApiClient.shared.fetchE6ImportDetail(id: id)
            guard !Task.isCancelled else { return }
            self.detail = res
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
    
    private func confirmAction() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isConfirming = true
        errorMessage = nil
        Task {
            do {
                try await ApiClient.shared.confirmE6Import(id: id)
                await MainActor.run {
                    isConfirming = false
                    isSuccess = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        dismiss()
                    }
                }
            } catch {
                await MainActor.run {
                    isConfirming = false
                    errorMessage = "导入失败: \(error.localizedDescription)"
                }
            }
        }
    }
}
