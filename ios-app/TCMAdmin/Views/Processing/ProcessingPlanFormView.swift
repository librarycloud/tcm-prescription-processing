import SwiftUI

fileprivate struct ProcessingBatchDraft: Identifiable {
    let id: UUID
    var dose: String
    var processTypeId: Int = 0
    var scheduleType: Int
    var processDate: Date
    var isUrgent: Bool = false
}

@MainActor
public struct ProcessingPlanFormView: View {
    @Environment(\.dismiss) private var dismiss
    
    public let initialPrescriptionId: Int?
    public let planToEdit: ProcessingPlanItem?
    
    // 基础字典
    @State private var availablePrescriptions: [PrescriptionItem] = []
    @State private var processTypes: [DictionaryItem] = []
    @State private var notifyTypes: [DictionaryItem] = []
    
    // 真实业务字段
    @State private var selectedPrescriptionId: Int = 0
    @State private var processTypeId: Int = 0
    @State private var totalDose = "7"
    @State private var bagCount = "2"
    @State private var volumeMl = "200"
    
    @State private var batchCountStr = "1"
    @State private var batchDrafts: [ProcessingBatchDraft] = []
    
    @State private var usageMethod = ""
    @State private var pickupMethod = 0 // 0: 自提, 1: 跑腿, 2: 快递
    @State private var expressAddress = ""
    @State private var scheduleType = 1 // 1: 指定日期, 2: 等待顾客通知
    @State private var processDate = Date()
    @State private var isUrgent = false
    @State private var notifyType = 0
    @State private var notifyStatus = 0
    @State private var paymentStatus = 1
    @State private var processRemark = ""
    @State private var remark = ""
    @State private var rxSearchText = ""
    @State private var showPrescriptionPicker = false
    
    @State private var isLoadingDicts = true
    @State private var isSubmitting = false
    @State private var successAlert = false
    @State private var errorMessage: String? = nil
    
    private var isDecoction: Bool {
        processTypes.first { $0.id == processTypeId }?.code == "DECOCTION" || 
        processTypes.first { $0.id == processTypeId }?.name.contains("煎煮") == true
    }
    
    public init(initialPrescriptionId: Int? = nil, planToEdit: ProcessingPlanItem? = nil) {
        self.initialPrescriptionId = initialPrescriptionId
        self.planToEdit = planToEdit
        
        if let plan = planToEdit {
            _selectedPrescriptionId = State(initialValue: plan.prescription?.id ?? 0)
            _processTypeId = State(initialValue: plan.processType?.id ?? 0)
            _totalDose = State(initialValue: "\(plan.totalDose ?? 7)")
            _bagCount = State(initialValue: "\(plan.bagCount ?? 2)")
            _volumeMl = State(initialValue: "\(plan.volumeMl ?? 200)")
            _usageMethod = State(initialValue: plan.usageMethod ?? "")
            _pickupMethod = State(initialValue: plan.pickupMethod ?? 0)
            _scheduleType = State(initialValue: plan.scheduleType ?? 1)
            if let sd = plan.processDate, let d = DateFormatter.yyyyMMdd.date(from: sd) {
                _processDate = State(initialValue: d)
            }
            _isUrgent = State(initialValue: (plan.priority ?? 0) > 0)
            _paymentStatus = State(initialValue: plan.paymentStatus ?? 1)
            _notifyType = State(initialValue: plan.notifyType?.id ?? 0)
            _notifyStatus = State(initialValue: plan.notifyStatus ?? 0)
            _processRemark = State(initialValue: plan.processRemark ?? "")
            _remark = State(initialValue: plan.remark ?? "")
        } else if let rxId = initialPrescriptionId {
            _selectedPrescriptionId = State(initialValue: rxId)
        }
    }
    
    private func autoAllocateBatches() {
        if planToEdit != nil { return }
        let tDose = max(Int(totalDose) ?? 1, 1)
        let bCount = min(max(Int(batchCountStr) ?? 1, 1), tDose)
        let base = tDose / bCount
        let remainder = tDose % bCount
        var curDate = processDate
        var newBatches: [ProcessingBatchDraft] = []
        
        for i in 0..<bCount {
            let bDose = base + (i < remainder ? 1 : 0)
            let bDate = curDate
            newBatches.append(ProcessingBatchDraft(
                id: UUID(),
                dose: "\(bDose)",
                scheduleType: scheduleType,
                processDate: bDate,
                isUrgent: isUrgent
            ))
            curDate = Calendar.current.date(byAdding: .day, value: bDose, to: curDate) ?? curDate
        }
        self.batchDrafts = newBatches
    }
    
    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if let err = errorMessage {
                        Text(err)
                            .foregroundStyle(Color.danger)
                            .font(.subheadline)
                            .padding()
                            .frame(maxWidth: .infinity)
                            .background(Color.dangerSoft)
                            .clipShape(.rect(cornerRadius: 8))
                    }
                    
                    if isLoadingDicts {
                        ProgressView("正在加载基础数据...")
                            .padding()
                    } else {
                        card1Prescription()
                        card2Batches()
                        card3Settings()
                    }
                }
                .padding(16)
            }
            .background(Color.pageBackground.ignoresSafeArea())
            .navigationTitle(planToEdit != nil ? "编辑加工计划" : "新建加工计划")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: submitForm) {
                        if isSubmitting {
                            ProgressView()
                        } else {
                            Text("保存")
                                .fontWeight(.bold)
                                .foregroundStyle(Color.appPrimary)
                        }
                    }
                    .disabled(isSubmitting || selectedPrescriptionId == 0 || isLoadingDicts)
                }
            }
            .alert(planToEdit != nil ? "修改成功" : "创建成功", isPresented: $successAlert) {
                Button("确定", role: .cancel) {
                    NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                    NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Prescriptions"), object: nil)
                    dismiss()
                }
            } message: {
                Text(planToEdit != nil ? "加工计划信息已更新" : "加工计划已生成并进入待处理队列")
            }
            .task {
                do {
                    async let d1 = ApiClient.shared.fetchDictionaries(type: "ProcessType")
                    async let d2 = ApiClient.shared.fetchDictionaries(type: "NotifyType")
                    let (pts, nts) = try await (d1, d2)
                    self.processTypes = pts
                    self.notifyTypes = nts
                    
                    if let plan = planToEdit {
                        self.selectedPrescriptionId = plan.prescriptionId ?? 0
                        self.processTypeId = plan.processType?.id ?? 0
                        if self.processTypeId == 0, let first = pts.first {
                            self.processTypeId = first.id
                        }
                        self.totalDose = plan.totalDose != nil ? "\(plan.totalDose!)" : ""
                        self.pickupMethod = plan.pickupMethod ?? 0
                        self.scheduleType = plan.scheduleType ?? 1
                        self.isUrgent = (plan.priority == 1)
                        self.notifyType = plan.notifyType?.id ?? 0
                        self.notifyStatus = plan.notifyStatus ?? 1
                        self.paymentStatus = plan.paymentStatus ?? 0
                        self.usageMethod = plan.usageMethod ?? ""
                        self.processRemark = plan.processRemark ?? ""
                        self.remark = plan.remark ?? ""
                        
                        if let dateStr = plan.processDate, let d = DateFormatter.yyyyMMdd.date(from: dateStr) {
                            self.processDate = d
                        }
                    } else {
                        if let first = pts.first {
                            self.processTypeId = first.id
                        }
                        if let firstNt = nts.first {
                            self.notifyType = firstNt.id
                        }
                        try await fetchAvailablePrescriptions(keyword: "")
                        autoAllocateBatches()
                    }
                    self.isLoadingDicts = false
                } catch {
                    self.errorMessage = "加载基础数据失败: \(error.localizedDescription)"
                }
            }
            .sheet(isPresented: $showPrescriptionPicker) {
                prescriptionPickerSheet()
            }
        }
    }


    @ViewBuilder
    private func prescriptionPickerSheet() -> some View {
        NavigationStack {
            List {
                ForEach(availablePrescriptions) { rx in
                    Button(action: {
                        selectedPrescriptionId = rx.id
                        showPrescriptionPicker = false
                    }) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(rx.prescriptionNo ?? "")")
                                    .scaledFont(14, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("\(rx.patientName ?? "未知") · \(rx.patientPhone ?? "")")
                                    .scaledFont(12)
                                    .foregroundStyle(Color.muted)
                            }
                            Spacer()
                            if selectedPrescriptionId == rx.id {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(Color.appPrimary)
                            }
                        }
                    }
                }
            }
            .searchable(text: $rxSearchText, prompt: "搜索处方号或患者姓名")
            .onChange(of: rxSearchText) { _, newVal in
                Task {
                    do {
                        try await fetchAvailablePrescriptions(keyword: newVal)
                    } catch { }
                }
            }
            .navigationTitle("选择关联处方")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") { showPrescriptionPicker = false }
                }
            }
        }
    }

    @ViewBuilder
    private func card1Prescription() -> some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 16) {
                Text("关联处方")
                    .scaledFont(15, weight: .bold)
                    .foregroundStyle(Color.ink)
                
                Button(action: {
                    if planToEdit == nil {
                        showPrescriptionPicker = true
                    }
                }) {
                    HStack {
                        if selectedPrescriptionId != 0, let rx = availablePrescriptions.first(where: { $0.id == selectedPrescriptionId }) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("\(rx.prescriptionNo ?? "")")
                                    .scaledFont(14, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("\(rx.patientName ?? "未知") · \(rx.patientPhone ?? "")")
                                    .scaledFont(12)
                                    .foregroundStyle(Color.muted)
                            }
                        } else {
                            Text("请选择关联处方")
                                .scaledFont(14)
                                .foregroundStyle(Color.muted)
                        }
                        Spacer()
                        if planToEdit == nil {
                            Image(systemName: "chevron.right")
                                .foregroundStyle(Color.muted)
                        }
                    }
                    .padding(12)
                    .background(Color.pageBackground)
                    .clipShape(.rect(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                }
                .disabled(planToEdit != nil)
                
                Divider().foregroundStyle(Color.cardBorder)
                
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("总付数 *")
                            .scaledFont(13, weight: .medium)
                            .foregroundStyle(Color.ink)
                        TextField("付数", text: Binding(
                            get: { totalDose },
                            set: { val in
                                totalDose = val.filter { $0.isNumber }
                                autoAllocateBatches()
                            }
                        ))
                        .keyboardType(.numberPad)
                        .padding(10)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("批次数")
                            .scaledFont(13, weight: .medium)
                            .foregroundStyle(Color.ink)
                        TextField("分几批", text: Binding(
                            get: { batchCountStr },
                            set: { val in
                                batchCountStr = val.filter { $0.isNumber }
                                autoAllocateBatches()
                            }
                        ))
                        .keyboardType(.numberPad)
                        .padding(10)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                    }
                }
                
                if isDecoction {
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("代煎袋数")
                                .scaledFont(13, weight: .medium)
                            TextField("每付几袋", text: $bagCount)
                                .keyboardType(.numberPad)
                                .padding(10)
                                .background(Color.pageBackground)
                                .clipShape(.rect(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                        }
                        VStack(alignment: .leading, spacing: 8) {
                            Text("单袋容量(ml)")
                                .scaledFont(13, weight: .medium)
                            TextField("如：200", text: $volumeMl)
                                .keyboardType(.numberPad)
                                .padding(10)
                                .background(Color.pageBackground)
                                .clipShape(.rect(cornerRadius: 8))
                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                        }
                    }
                }
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("用法说明 (可选)")
                        .scaledFont(13, weight: .medium)
                    TextField("输入用法", text: $usageMethod)
                        .padding(10)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                }
            }
        }
    }

    @ViewBuilder
    private func card2Batches() -> some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("加工方式 *")
                        .scaledFont(13, weight: .medium)
                        .foregroundStyle(Color.ink)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(processTypes) { pt in
                                SegmentedButton(
                                    label: pt.name,
                                    isSelected: processTypeId == pt.id
                                ) {
                                    processTypeId = pt.id
                                    for i in batchDrafts.indices {
                                        batchDrafts[i].processTypeId = 0
                                    }
                                }
                            }
                        }
                    }
                }
                
                if planToEdit == nil {
                    Divider().foregroundStyle(Color.cardBorder)
                    
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("加工批次")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.ink)
                            Text("自动分配，可逐批设置剂数与工艺")
                                .scaledFont(12)
                                .foregroundStyle(Color.muted)
                        }
                        Spacer()
                        Button(action: {
                            autoAllocateBatches()
                        }) {
                            Text("重新分配")
                                .scaledFont(12, weight: .semibold)
                                .foregroundStyle(Color.appPrimary)
                        }
                    }
                    
                    ForEach(Array(batchDrafts.enumerated()), id: \.element.id) { index, draft in
                        batchRowView(index: index, draft: draft)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func batchRowView(index: Int, draft: ProcessingBatchDraft) -> some View {
        if index < batchDrafts.count {
            VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("第\(index + 1)批")
                    .scaledFont(13, weight: .bold)
                    .foregroundStyle(Color.ink)
                    .frame(minWidth: 46, alignment: .leading)
                
                HStack {
                    Text("付数:")
                        .scaledFont(13)
                    .environment(\.locale, Locale(identifier: "zh_CN"))
                        .foregroundStyle(Color.muted)
                    TextField("付数", text: $batchDrafts[index].dose)
                        .keyboardType(.numberPad)
                        .scaledFont(14, weight: .semibold)
                        .foregroundStyle(Color.ink)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color.pageBackground)
                .clipShape(.rect(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
            }
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(processTypes, id: \.id) { type in
                        let isSelected = batchDrafts[index].processTypeId == type.id || (batchDrafts[index].processTypeId == 0 && processTypeId == type.id)
                        SegmentedButton(
                            label: type.name.isEmpty ? "工艺" : type.name,
                            isSelected: isSelected
                        ) {
                            batchDrafts[index].processTypeId = type.id
                        }
                    }
                }
            }
            .padding(.top, 4)
            
            HStack(spacing: 8) {
                SegmentedButton(label: "指定日期", isSelected: batchDrafts[index].scheduleType == 1) {
                    batchDrafts[index].scheduleType = 1
                }
                .frame(maxWidth: .infinity)
                SegmentedButton(label: "等待通知", isSelected: batchDrafts[index].scheduleType == 2) {
                    batchDrafts[index].scheduleType = 2
                }
                .frame(maxWidth: .infinity)
            }
            
            if batchDrafts[index].scheduleType == 1 {
                DatePicker("开工日期", selection: $batchDrafts[index].processDate, displayedComponents: .date)
                    .scaledFont(13)
                    .environment(\.locale, Locale(identifier: "zh_CN"))
                    .padding(.horizontal, 6)
            }
            
            Toggle("加急处理 (优先安排)", isOn: $batchDrafts[index].isUrgent)
                .scaledFont(13, weight: .medium)
                .tint(Color.warning)
                .padding(.horizontal, 6)
        }
        .padding(10)
        .background(Color.pageBackground.opacity(0.6))
        .clipShape(.rect(cornerRadius: 8))
        } else {
            EmptyView()
        }
    }

    @ViewBuilder
    private func card3Settings() -> some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("支付状态")
                        .scaledFont(13, weight: .medium)
                    HStack(spacing: 8) {
                        SegmentedButton(label: "未支付", isSelected: paymentStatus == 0) { paymentStatus = 0 }
                        SegmentedButton(label: "已支付", isSelected: paymentStatus == 1) { paymentStatus = 1 }
                        SegmentedButton(label: "免单", isSelected: paymentStatus == 2) { paymentStatus = 2 }
                    }
                }
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("消息通知设置")
                        .scaledFont(13, weight: .medium)
                    HStack(spacing: 8) {
                        SegmentedButton(label: "不通知", isSelected: notifyStatus == 0) { notifyStatus = 0 }
                        SegmentedButton(label: "开启通知", isSelected: notifyStatus == 1) { notifyStatus = 1 }
                    }
                    if notifyStatus == 1 {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(notifyTypes) { nt in
                                    SegmentedButton(label: nt.name, isSelected: notifyType == nt.id) { notifyType = nt.id }
                                }
                            }
                        }
                    }
                }
                
                Divider().foregroundStyle(Color.cardBorder)
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("取货方式")
                        .scaledFont(13, weight: .medium)
                    HStack(spacing: 8) {
                        SegmentedButton(label: "自提", isSelected: pickupMethod == 0) { pickupMethod = 0 }
                        SegmentedButton(label: "跑腿", isSelected: pickupMethod == 1) { pickupMethod = 1 }
                        SegmentedButton(label: "快递", isSelected: pickupMethod == 2) { pickupMethod = 2 }
                    }
                    if pickupMethod == 1 || pickupMethod == 2 {
                        TextField("收件人完整地址 (选填)", text: $expressAddress)
                            .padding(10)
                            .background(Color.pageBackground)
                            .clipShape(.rect(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                    }
                }
                
                Divider().foregroundStyle(Color.cardBorder)
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("工艺特殊要求 (如先煎、后下)")
                        .scaledFont(13, weight: .medium)
                    TextField("输入工艺备注", text: $processRemark)
                        .padding(10)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                }
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("其他内部备注")
                        .scaledFont(13, weight: .medium)
                    TextField("输入内部备注", text: $remark)
                        .padding(10)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                }
            }
        }
    }

    private func fetchAvailablePrescriptions(keyword: String) async throws {
        let formatter = DateFormatter.yyyyMMdd
        let today = formatter.string(from: Date())
        
        let rx = try await ApiClient.shared.fetchPrescriptions(
            status: nil,
            keyword: keyword,
            createdDate: keyword.isEmpty ? today : nil,
            pageSize: 50
        )
        self.availablePrescriptions = rx
        if selectedPrescriptionId == 0, let first = rx.first {
            selectedPrescriptionId = first.id
        }
    }
    
    private func submitForm() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isSubmitting = true
        errorMessage = nil
        
        Task {
            do {
                if let plan = planToEdit {
                    var payload: [String: Any] = [
                        "processTypeId": processTypeId,
                        "totalDose": Int(totalDose) ?? 7,
                        "pickupMethod": pickupMethod,
                        "scheduleType": scheduleType,
                        "priority": isUrgent ? 1 : 0,
                        "notifyType": notifyType > 0 ? notifyType : NSNull(),
                        "notifyStatus": notifyStatus,
                        "paymentStatus": paymentStatus
                    ]
                    if isDecoction {
                        payload["bagCount"] = Int(bagCount) ?? 2
                        payload["volumeMl"] = Int(volumeMl) ?? 200
                    }
                    if !usageMethod.isEmpty { payload["usageMethod"] = usageMethod }
                    if !processRemark.isEmpty { payload["processRemark"] = processRemark }
                    if !remark.isEmpty { payload["remark"] = remark }
                    
                    if scheduleType == 1 {
                        payload["processDate"] = DateFormatter.yyyyMMdd.string(from: processDate)
                    }
                    if pickupMethod == 1 || pickupMethod == 2 {
                        payload["expressAddress"] = expressAddress
                    }
                    try await ApiClient.shared.updateProcessingPlan(id: plan.id, payload: payload)
                } else {
                    let batches = batchDrafts.isEmpty ? [ProcessingBatchDraft(id: UUID(), dose: totalDose, scheduleType: scheduleType, processDate: processDate, isUrgent: isUrgent)] : batchDrafts
                    
                    for (i, draft) in batches.enumerated() {
                        var payload: [String: Any] = [
                            "prescriptionId": selectedPrescriptionId,
                            "processTypeId": draft.processTypeId != 0 ? draft.processTypeId : processTypeId,
                            "totalDose": Int(draft.dose) ?? 1,
                            "pickupMethod": pickupMethod,
                            "scheduleType": draft.scheduleType,
                            "priority": draft.isUrgent ? 1 : 0,
                            "notifyType": notifyType > 0 ? notifyType : NSNull(),
                            "notifyStatus": notifyStatus,
                            "paymentStatus": paymentStatus
                        ]
                        if batches.count > 1 {
                            payload["batchNo"] = i + 1
                        }
                        if isDecoction {
                            payload["bagCount"] = Int(bagCount) ?? 2
                            payload["volumeMl"] = Int(volumeMl) ?? 200
                        }
                        if !usageMethod.isEmpty { payload["usageMethod"] = usageMethod }
                        if !processRemark.isEmpty { payload["processRemark"] = processRemark }
                        if !remark.isEmpty { payload["remark"] = remark }
                        
                        if draft.scheduleType == 1 {
                            payload["processDate"] = DateFormatter.yyyyMMdd.string(from: draft.processDate)
                        }
                        if pickupMethod == 1 || pickupMethod == 2 {
                            payload["expressAddress"] = expressAddress
                        }
                        try await ApiClient.shared.createProcessingPlan(payload: payload)
                        await MainActor.run {
                            if !self.batchDrafts.isEmpty {
                                self.batchDrafts.removeAll(where: { $0.id == draft.id })
                            }
                        }
                    }
                }
                await MainActor.run {
                    isSubmitting = false
                    successAlert = true
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
