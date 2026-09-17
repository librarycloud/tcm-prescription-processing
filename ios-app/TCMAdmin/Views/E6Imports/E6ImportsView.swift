import SwiftUI

@MainActor
public struct E6ImportsView: View {
    @Bindable private var router = Router.shared
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
                            .scaledFont(13, weight: .semibold)
                            .foregroundStyle(Color.appPrimary)
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
                        .scaledFont(13)
                        .foregroundStyle(Color.success)
                        .padding(.vertical, 4)
                }
                
                // 导入订单列表
                if isLoading && e6Imports.isEmpty {
                    Spacer()
                    ProgressView("正在同步 E6 处方导入记录...")
                    Spacer()
                } else if let error = errorMessage, !error.isEmpty {
                    Spacer()
                    Text(error).foregroundStyle(Color.danger).scaledFont(14).padding()
                    Spacer()
                } else if e6Imports.isEmpty {
                    Spacer()
                    VStack(spacing: 12) {
                        Image(systemName: "icloud.and.arrow.down")
                            .scaledFont(48)
                            .foregroundStyle(Color.muted)
                        Text("暂无符合条件的 E6 处方单")
                            .scaledFont(15)
                            .foregroundStyle(Color.muted)
                    }
                    Spacer()
                } else {
                    AppScrollView {
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
                                                        .foregroundStyle(selectedIds.contains(item.id) ? .appPrimary : .muted)
                                                        .scaledFont(18)
                                                }
                                                .buttonStyle(PlainButtonStyle())
                                                .padding(.trailing, 2)
                                            }
                                            
                                            Text(item.displayCustomer)
                                                .scaledFont(16, weight: .bold)
                                                .foregroundStyle(Color.ink)
                                            Spacer()
                                            HStack(spacing: 6) {
                                                StatusPill(text: item.isPaidBool ? "已付款" : "未付款")
                                                StatusPill(text: item.statusText)
                                            }
                                        }
                                        
                                        let phoneStr = item.displayPhone
                                        let maskedPhone = phoneStr.count == 11 ? "\(phoneStr.prefix(3))****\(phoneStr.suffix(4))" : phoneStr
                                        Text("\(maskedPhone)  ·  单号：\(item.displayOrderNo)")
                                            .scaledFont(12.5)
                                            .foregroundStyle(Color.ink)
                                        
                                        Text("\(formatDateOnly(item.displayDate))  ·  \(item.displayDose)剂  ·  ¥\(String(format: "%.2f", item.displayPrice))")
                                            .scaledFont(12)
                                            .foregroundStyle(Color.muted)
                                        
                                        Text("操作员：\(item.displayOperator)  ·  系统医生：\(item.displayDoctor)")
                                            .scaledFont(12)
                                            .foregroundStyle(Color.muted)
                                            .lineLimit(1)
                                        
                                        if let err = item.errorMessage, !err.isEmpty {
                                            HStack(alignment: .top, spacing: 4) {
                                                Image(systemName: "exclamationmark.triangle.fill")
                                                    .foregroundStyle(Color.danger)
                                                    .scaledFont(12)
                                                Text(err)
                                                    .scaledFont(12)
                                                    .foregroundStyle(Color.danger)
                                                    .lineLimit(2)
                                            }
                                            .padding(6)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .background(Color.dangerSoft)
                                            .clipShape(.rect(cornerRadius: 6))
                                        }
                                        
                                        // 操作栏按钮
                                        Divider().foregroundStyle(Color.cardBorder).padding(.top, 2)
                                        
                                        HStack(spacing: 8) {
                                            if item.canReview {
                                                Button(action: {
                                                    rejectTargetId = item.id
                                                    rejectionReason = ""
                                                }) {
                                                    Text("驳回")
                                                        .scaledFont(12, weight: .medium)
                                                        .foregroundStyle(Color.danger)
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 6)
                                                        .background(Color.dangerSoft)
                                                        .clipShape(.rect(cornerRadius: 6))
                                                }
                                            }
                                            
                                            if item.status == 2 || item.status == 6 {
                                                Button(action: { revalidate(id: item.id) }) {
                                                    Text("重新校验")
                                                        .scaledFont(12, weight: .medium)
                                                        .foregroundStyle(Color.appPrimary)
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 6)
                                                        .background(Color.appPrimarySoft)
                                                        .clipShape(.rect(cornerRadius: 6))
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
                                                    .scaledFont(12.5, weight: .bold)
                                                    .foregroundStyle(Color.white)
                                                    .padding(.horizontal, 12)
                                                    .padding(.vertical, 6)
                                                    .background(Color.appPrimary)
                                                    .clipShape(.rect(cornerRadius: 6))
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
                            .scaledFont(14, weight: .bold)
                            .foregroundStyle(Color.white)
                        Spacer()
                        Button(action: {
                            let items = e6Imports.filter { selectedIds.contains($0.id) }
                            confirmTargetItems = items
                        }) {
                            Text("合并为单处方并排产")
                                .scaledFont(13, weight: .bold)
                                .foregroundStyle(Color.appPrimary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(Color.white)
                                .clipShape(.rect(cornerRadius: 8))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Color.appPrimary)
                    .clipShape(.rect(cornerRadius: 12))
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
                        .foregroundStyle(Color.danger)
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
    let id: UUID
    var dose: String
    var scheduleType: Int // 1: 指定日期, 2: 等待通知
    var processDate: Date
    
    init(id: UUID = UUID(), dose: String = "1", scheduleType: Int = 1, processDate: Date = Date()) {
        self.id = id
        self.dose = dose
        self.scheduleType = scheduleType
        self.processDate = processDate
    }
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
    @State private var bagsPerDose = "2"
    @State private var volumeMl = "200"
    @State private var pickupMethod = 0
    @State private var batchDrafts: [BatchDraft] = []
    
    @State private var doctors: [DoctorItem] = []
    @State private var processTypes: [ProcessTypeItem] = []
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    
    private var isMerge: Bool { items.count > 1 }
    private var hasPrescription: Bool {
        items.first?.prescriptionId != nil && (items.first?.prescriptionId ?? 0) > 0
    }
    
    private var selectedProcessType: ProcessTypeItem? {
        processTypes.first { $0.id == selectedProcessTypeId }
    }
    
    private var isDecoction: Bool {
        guard let pt = selectedProcessType else { return true }
        let code = pt.code?.uppercased() ?? ""
        let name = pt.name
        return code == "DECOCTION" || name.contains("代煎") || name.contains("煎")
    }
    
    private var totalDose: Int {
        Int(totalDoseStr) ?? 0
    }
    
    private var allocatedDose: Int {
        batchDrafts.reduce(0) { $0 + (Int($1.dose) ?? 0) }
    }
    
    private var validBatches: Bool {
        totalDose > 0 &&
        allocatedDose == totalDose &&
        !batchDrafts.isEmpty &&
        batchDrafts.allSatisfy { (Int($0.dose) ?? 0) > 0 }
    }
    
    private var canSubmit: Bool {
        !isSubmitting &&
        !customerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (hasPrescription || selectedDoctorId > 0) &&
        selectedProcessTypeId > 0 &&
        validBatches &&
        (!isDecoction || ((Int(bagsPerDose) ?? 0) > 0 && (Int(volumeMl) ?? 0) > 0))
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    // 顶部标题
                    SectionHeader(
                        title: isMerge ? "合并订单并生成处方" : "确认导入并生成加工计划",
                        subtitle: isMerge ? "已选择 \(items.count) 个E6订单" : "核对信息后提交，生成处方和加工计划"
                    )
                    
                    if let err = errorMessage {
                        AppCard(padding: 12) {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundStyle(Color.danger)
                                Text(err)
                                    .scaledFont(13)
                                    .foregroundStyle(Color.danger)
                                Spacer()
                                Button("关闭") { errorMessage = nil }
                                    .scaledFont(12, weight: .bold)
                                    .foregroundStyle(Color.appPrimary)
                            }
                        }
                    }
                    
                    // 1. 顾客与处方卡片
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("顾客与处方")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.ink)
                            
                            Divider().foregroundStyle(Color.cardBorder)
                            
                            InputField(title: "顾客姓名 *", placeholder: "请输入顾客姓名", text: $customerName)
                            
                            InputField(title: "手机号", placeholder: "请输入手机号", text: $phone, keyboardType: .phonePad)
                            
                            HStack(spacing: 12) {
                                InputField(
                                    title: "总剂数 *",
                                    placeholder: "剂数",
                                    text: Binding(
                                        get: { totalDoseStr },
                                        set: { val in
                                            let filtered = val.filter { $0.isNumber }
                                            totalDoseStr = filtered
                                            autoAllocateBatches(total: Int(filtered), count: Int(batchCountStr))
                                        }
                                    ),
                                    keyboardType: .numberPad
                                )
                                
                                InputField(
                                    title: "批次数",
                                    placeholder: "批次",
                                    text: Binding(
                                        get: { batchCountStr },
                                        set: { val in
                                            let filtered = val.filter { $0.isNumber }
                                            batchCountStr = filtered
                                            autoAllocateBatches(total: Int(totalDoseStr), count: Int(filtered))
                                        }
                                    ),
                                    keyboardType: .numberPad
                                )
                            }
                            
                            // 系统医生选择
                            VStack(alignment: .leading, spacing: 6) {
                                Text("系统医生\(hasPrescription ? "" : " *")")
                                    .scaledFont(13, weight: .medium)
                                    .foregroundStyle(Color.ink)
                                
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(doctors) { doc in
                                            SegmentedButton(
                                                label: doc.name,
                                                isSelected: selectedDoctorId == doc.id
                                            ) {
                                                selectedDoctorId = doc.id
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(.top, 4)
                            
                            // 加工方式选择
                            VStack(alignment: .leading, spacing: 6) {
                                Text("加工方式 *")
                                    .scaledFont(13, weight: .medium)
                                    .foregroundStyle(Color.ink)
                                
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(processTypes) { pt in
                                            SegmentedButton(
                                                label: pt.name,
                                                isSelected: selectedProcessTypeId == pt.id
                                            ) {
                                                selectedProcessTypeId = pt.id
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(.top, 4)
                        }
                    }
                    
                    // 2. 加工批次卡片
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack(alignment: .center) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("加工批次")
                                        .scaledFont(15, weight: .bold)
                                        .foregroundStyle(Color.ink)
                                    Text("系统已自动分配，可逐批设置剂数、日期或等待通知")
                                        .scaledFont(12)
                                        .foregroundStyle(Color.muted)
                                }
                                Spacer()
                                Button(action: {
                                    autoAllocateBatches(total: Int(totalDoseStr), count: batchDrafts.count)
                                }) {
                                    Text("重新自动分配")
                                        .scaledFont(12, weight: .semibold)
                                        .foregroundStyle(Color.appPrimary)
                                }
                            }
                            
                            Divider().foregroundStyle(Color.cardBorder)
                            
                            ForEach(Array(batchDrafts.enumerated()), id: \.element.id) { index, draft in
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack(spacing: 8) {
                                        Text("第\(index + 1)批")
                                            .scaledFont(13, weight: .bold)
                                            .foregroundStyle(Color.ink)
                                            .frame(minWidth: 46, alignment: .leading)
                                        
                                        HStack {
                                            Text("剂数:")
                                                .scaledFont(13)
                                                .foregroundStyle(Color.muted)
                                            TextField("剂数", text: $batchDrafts[index].dose)
                                                .keyboardType(.numberPad)
                                                .scaledFont(14, weight: .semibold)
                                                .foregroundStyle(Color.ink)
                                        }
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.surface)
                                        .clipShape(.rect(cornerRadius: 8))
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                        
                                        if batchDrafts.count > 1 {
                                            Button(action: {
                                                deleteBatch(at: index)
                                            }) {
                                                Text("删除")
                                                    .scaledFont(12, weight: .medium)
                                                    .foregroundStyle(Color.danger)
                                                    .padding(.horizontal, 8)
                                                    .padding(.vertical, 6)
                                            }
                                        }
                                    }
                                    
                                    HStack(spacing: 8) {
                                        SegmentedButton(
                                            label: "指定日期",
                                            isSelected: batchDrafts[index].scheduleType == 1
                                        ) {
                                            batchDrafts[index].scheduleType = 1
                                        }
                                        .frame(maxWidth: .infinity)
                                        
                                        SegmentedButton(
                                            label: "等待通知",
                                            isSelected: batchDrafts[index].scheduleType == 2
                                        ) {
                                            batchDrafts[index].scheduleType = 2
                                        }
                                        .frame(maxWidth: .infinity)
                                    }
                                    
                                    if batchDrafts[index].scheduleType == 1 {
                                        DatePicker(
                                            "加工日期",
                                            selection: $batchDrafts[index].processDate,
                                            displayedComponents: .date
                                        )
                                        .scaledFont(13)
                                        .padding(.horizontal, 6)
                                    }
                                }
                                .padding(10)
                                .background(Color.pageBackground.opacity(0.6))
                                .clipShape(.rect(cornerRadius: 8))
                            }
                            
                            HStack {
                                Text("已分配 \(allocatedDose) / \(totalDose) 剂")
                                    .scaledFont(13, weight: .bold)
                                    .foregroundStyle(allocatedDose == totalDose ? Color.success : Color.danger)
                                
                                Spacer()
                                
                                if totalDose > 0 && batchDrafts.count < totalDose {
                                    Button(action: addBatch) {
                                        HStack(spacing: 4) {
                                            Image(systemName: "plus.circle.fill")
                                            Text("新增批次")
                                        }
                                        .scaledFont(13, weight: .semibold)
                                        .foregroundStyle(Color.appPrimary)
                                    }
                                }
                            }
                            .padding(.top, 4)
                        }
                    }
                    
                    // 3. 代煎参数卡片 (当工艺为代煎时)
                    if isDecoction {
                        AppCard(padding: 16) {
                            VStack(alignment: .leading, spacing: 12) {
                                Text("代煎参数")
                                    .scaledFont(15, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                
                                Divider().foregroundStyle(Color.cardBorder)
                                
                                HStack(spacing: 12) {
                                    InputField(
                                        title: "每剂袋数 *",
                                        placeholder: "默认2袋",
                                        text: $bagsPerDose,
                                        keyboardType: .numberPad
                                    )
                                    
                                    InputField(
                                        title: "每袋毫升 *",
                                        placeholder: "默认200ml",
                                        text: $volumeMl,
                                        keyboardType: .numberPad
                                    )
                                }
                            }
                        }
                    }
                    
                    // 4. 取货方式卡片
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("取货方式 *")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.ink)
                            
                            HStack(spacing: 8) {
                                let methods = ["自提", "跑腿", "快递"]
                                ForEach(Array(methods.enumerated()), id: \.offset) { idx, name in
                                    SegmentedButton(
                                        label: name,
                                        isSelected: pickupMethod == idx
                                    ) {
                                        pickupMethod = idx
                                    }
                                }
                            }
                        }
                    }
                    
                    // 5. 提交按钮
                    Button(action: submit) {
                        HStack {
                            if isSubmitting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .padding(.trailing, 4)
                            }
                            Text(isMerge ? "确认合并并生成" : "确认导入并生成加工计划")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(canSubmit ? Color.appPrimary : Color.muted.opacity(0.4))
                        .clipShape(.rect(cornerRadius: 10))
                    }
                    .disabled(!canSubmit)
                    .padding(.top, 6)
                    .padding(.bottom, 20)
                }
                .padding(16)
                .frame(maxWidth: .infinity)
            }
            .background(Color.pageBackground)
            .navigationTitle(isMerge ? "合并订单并排产" : "确认导入并排产")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
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
        batchCountStr = "1"
        autoAllocateBatches(total: max(sumDoses, 1), count: 1)
    }
    
    private func autoAllocateBatches(total: Int? = nil, count: Int? = nil) {
        let tDose = max(total ?? (Int(totalDoseStr) ?? 1), 1)
        let bCount = min(max(count ?? (Int(batchCountStr) ?? 1), 1), tDose)
        let base = tDose / bCount
        let remainder = tDose % bCount
        var curDate = Date()
        var newBatches: [BatchDraft] = []
        
        for i in 0..<bCount {
            let bDose = base + (i < remainder ? 1 : 0)
            let bDate = curDate
            newBatches.append(BatchDraft(
                id: UUID(),
                dose: "\(bDose)",
                scheduleType: 1,
                processDate: bDate
            ))
            // 顺延天数：按照前一批剂数递增日期，贴合患者服药周期
            curDate = Calendar.current.date(byAdding: .day, value: bDose, to: curDate) ?? curDate
        }
        self.batchDrafts = newBatches
        self.batchCountStr = "\(bCount)"
    }
    
    private func addBatch() {
        var nextDate = Date()
        if let last = batchDrafts.last {
            let lastDose = Int(last.dose) ?? 1
            nextDate = Calendar.current.date(byAdding: .day, value: lastDose, to: last.processDate) ?? Date()
        }
        batchDrafts.append(BatchDraft(
            id: UUID(),
            dose: "1",
            scheduleType: 1,
            processDate: nextDate
        ))
        batchCountStr = "\(batchDrafts.count)"
    }
    
    private func deleteBatch(at index: Int) {
        guard batchDrafts.count > 1 else { return }
        batchDrafts.remove(at: index)
        batchCountStr = "\(batchDrafts.count)"
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
        guard canSubmit else { return }
        isSubmitting = true
        errorMessage = nil
        
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        
        let bags = Int(bagsPerDose) ?? 2
        let volume = Int(volumeMl) ?? 200
        
        var batches: [[String: Any]] = []
        for draft in batchDrafts {
            let bDose = Int(draft.dose) ?? 1
            var b: [String: Any] = [
                "totalDose": bDose,
                "scheduleType": draft.scheduleType
            ]
            if draft.scheduleType == 1 {
                b["processDate"] = dateFormatter.string(from: draft.processDate)
            }
            if isDecoction {
                b["bagCount"] = bDose * bags
                b["volumeMl"] = volume
            }
            batches.append(b)
        }
        
        let firstBatch = batchDrafts.first
        let firstScheduleType = firstBatch?.scheduleType ?? 1
        let firstProcessDate = (firstScheduleType == 1 && firstBatch != nil) ? dateFormatter.string(from: firstBatch!.processDate) : nil
        
        var payload: [String: Any] = [
            "customerName": customerName.trimmingCharacters(in: .whitespacesAndNewlines),
            "phone": phone.trimmingCharacters(in: .whitespacesAndNewlines),
            "totalDose": totalDose,
            "doseCount": totalDose,
            "pickupMethod": pickupMethod,
            "scheduleType": firstScheduleType,
            "batches": batches
        ]
        if let fDate = firstProcessDate {
            payload["processDate"] = fDate
        }
        if isDecoction {
            payload["bagsPerDose"] = bags
            payload["volumeMl"] = volume
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
    @Bindable private var router = Router.shared
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
                            .scaledFont(36)
                            .foregroundStyle(Color.muted)
                        Text(errorMessage ?? "未能加载订单详情")
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
                } else if let item = detail {
                    if let err = errorMessage {
                        AppCard(padding: 12) {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Color.danger)
                                Text(err).scaledFont(13).foregroundStyle(Color.danger)
                                Spacer()
                                Button("关闭") { errorMessage = nil }.scaledFont(12, weight: .bold)
                            }
                        }
                    }
                    
                    // 1. 订单基本信息卡片
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("订单信息")
                                    .scaledFont(15, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Spacer()
                                StatusPill(text: item.statusText)
                            }
                            
                            Divider().foregroundStyle(Color.cardBorder)
                            
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
                                    .scaledFont(15, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                
                                Divider().foregroundStyle(Color.cardBorder)
                                
                                InfoRowItem(label: "处方号", value: rx.prescriptionNo ?? "CF-\(rx.id)")
                                InfoRowItem(label: "状态", value: rx.statusText)
                                
                                Button(action: {
                                    router.navigate(to: .prescriptionDetail(id: rx.id))
                                }) {
                                    Text("查看对应处方")
                                        .scaledFont(13, weight: .semibold)
                                        .foregroundStyle(Color.white)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 38)
                                        .background(Color.appPrimary)
                                        .clipShape(.rect(cornerRadius: 8))
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
                                    .scaledFont(15, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                
                                Divider().foregroundStyle(Color.cardBorder)
                                
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
                                    .scaledFont(15, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                
                                Divider().foregroundStyle(Color.cardBorder)
                                
                                // 表头
                                HStack {
                                    Text("药材名称").scaledFont(11).foregroundStyle(Color.muted).frame(maxWidth: .infinity, alignment: .leading)
                                    Text("剂数").scaledFont(11).foregroundStyle(Color.muted).frame(width: 40, alignment: .trailing)
                                    Text("单剂量").scaledFont(11).foregroundStyle(Color.muted).frame(width: 60, alignment: .trailing)
                                    Text("总量").scaledFont(11).foregroundStyle(Color.muted).frame(width: 60, alignment: .trailing)
                                }
                                
                                ForEach(Array(rawItems.enumerated()), id: \.offset) { index, rItem in
                                    HStack {
                                        Text("\(index + 1). \(rItem.name ?? "-")")
                                            .scaledFont(12)
                                            .foregroundStyle(Color.ink)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                        Text(String(format: "%g", rItem.doseCount ?? 0))
                                            .scaledFont(12)
                                            .foregroundStyle(Color.ink)
                                            .frame(width: 40, alignment: .trailing)
                                        Text("\(String(format: "%g", rItem.quantity ?? 0))\(rItem.unit ?? "")")
                                            .scaledFont(12)
                                            .foregroundStyle(Color.ink)
                                            .frame(width: 60, alignment: .trailing)
                                        Text("\(String(format: "%g", rItem.totalQuantity ?? 0))\(rItem.unit ?? "")")
                                            .scaledFont(12, weight: .semibold)
                                            .foregroundStyle(Color.ink)
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
                                    .scaledFont(15, weight: .bold)
                                    .foregroundStyle(Color.white)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 10))
                        }
                        .padding(.top, 6)
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.pageBackground.ignoresSafeArea(.all))
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
