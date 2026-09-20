import SwiftUI

@MainActor
public struct ProcessingPlanFormView: View {
    @Environment(\.dismiss) private var dismiss
    
    public let initialPrescriptionId: Int?
    public let planToEdit: ProcessingPlanItem?
    
    // 真实业务字段
    @State private var availablePrescriptions: [PrescriptionItem] = []
    @State private var selectedPrescriptionId: Int = 0
    @State private var processType = "水煎煮"
    @State private var totalDose = "7"
    @State private var bagCount = "2"
    @State private var volumeMl = "200"
    @State private var usageMethod = "早晚温服，每次一袋"
    @State private var pickupMethod = 0 // 0: 自提, 1: 跑腿, 2: 快递
    @State private var expressAddress = ""
    @State private var scheduleType = 1 // 1: 指定日期, 2: 等待顾客通知
    @State private var processDate = Date()
    @State private var isUrgent = false // priority: 0 普通, 1 加急
    @State private var paymentStatus = 1 // 0: 未付款, 1: 已付款
    @State private var processRemark = ""
    @State private var remark = ""
    @State private var rxSearchText = ""
    @State private var showPrescriptionPicker = false
    
    @State private var isSubmitting = false
    @State private var successAlert = false
    @State private var errorMessage: String? = nil
    
    let processTypeOptions = ["水煎煮", "打粉", "泛丸", "切片", "浓缩膏滋"]
    
    public init(initialPrescriptionId: Int? = nil, planToEdit: ProcessingPlanItem? = nil) {
        self.initialPrescriptionId = initialPrescriptionId
        self.planToEdit = planToEdit
        
        if let plan = planToEdit {
            _selectedPrescriptionId = State(initialValue: plan.prescriptionId ?? plan.prescription?.id ?? 0)
            _processType = State(initialValue: plan.method ?? plan.processType?.name ?? "水煎煮")
            _totalDose = State(initialValue: "\(plan.totalDose ?? 7)")
            _bagCount = State(initialValue: "\(plan.bagCount ?? 2)")
            _volumeMl = State(initialValue: "\(plan.volumeMl ?? 200)")
            _pickupMethod = State(initialValue: plan.pickupMethod ?? 0)
            _isUrgent = State(initialValue: plan.isUrgent)
            _processRemark = State(initialValue: plan.processRemark ?? "")
            _remark = State(initialValue: plan.remark ?? "")
        } else if let pId = initialPrescriptionId {
            _selectedPrescriptionId = State(initialValue: pId)
        }
    }
    
    public var body: some View {
        NavigationStack {
            Form {
                if let error = errorMessage {
                    Section {
                        Text(error).foregroundStyle(Color.red)
                    }
                }
                
                // 1. 处方关联
                Section(header: Text("处方信息")) {
                    if let rx = availablePrescriptions.first(where: { $0.id == selectedPrescriptionId }) {
                        Button(action: { showPrescriptionPicker = true }) {
                            HStack {
                                Text("关联处方")
                                    .foregroundStyle(Color.ink)
                                Spacer()
                                Text("\(rx.prescriptionNo ?? "") (\(rx.patientName ?? "未知"))")
                                    .foregroundStyle(Color.muted)
                            }
                        }
                    } else {
                        Button(action: { showPrescriptionPicker = true }) {
                            HStack {
                                Text("关联处方")
                                    .foregroundStyle(Color.ink)
                                Spacer()
                                Text(availablePrescriptions.isEmpty ? "加载中..." : "请选择")
                                    .foregroundStyle(Color.muted)
                            }
                        }
                    }
                }
                
                // 2. 工艺与规格
                Section(header: Text("工艺与分装规格")) {
                    Picker("加工工艺", selection: $processType) {
                        ForEach(processTypeOptions, id: \.self) {
                            Text($0).tag($0)
                        }
                    }
                    
                    HStack {
                        Text("处方付数")
                        Spacer()
                        TextField("付数", text: $totalDose)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                        Text("付")
                    }
                    
                    HStack {
                        Text("每付包数")
                        Spacer()
                        TextField("包数", text: $bagCount)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                        Text("包")
                    }
                    
                    HStack {
                        Text("每包容量")
                        Spacer()
                        TextField("容量", text: $volumeMl)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                        Text("ml")
                    }
                    
                    TextField("用法说明 (如：早晚饭后温服)", text: $usageMethod)
                }
                
                // 3. 排产与优先级
                Section(header: Text("排产调度与优先级")) {
                    Picker("排产方式", selection: $scheduleType) {
                        Text("指定日期生产").tag(1)
                        Text("等待顾客通知").tag(2)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    
                    if scheduleType == 1 {
                        DatePicker("加工日期", selection: $processDate, displayedComponents: .date)
                    }
                    
                    Toggle(isOn: $isUrgent) {
                        HStack {
                            Text("加急任务")
                            if isUrgent {
                                UrgentBadge(text: "加急")
                            }
                        }
                    }
                    
                    Picker("支付状态", selection: $paymentStatus) {
                        Text("未付款").tag(0)
                        Text("已付款").tag(1)
                    }
                }
                
                // 4. 交付方式
                Section(header: Text("取货交付")) {
                    Picker("领取方式", selection: $pickupMethod) {
                        Text("到店自提").tag(0)
                        Text("同城跑腿").tag(1)
                        Text("快递寄送").tag(2)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    
                    if pickupMethod == 2 {
                        TextField("收件人完整地址", text: $expressAddress)
                    }
                }
                
                // 5. 备注
                Section(header: Text("工艺与特别备注")) {
                    TextField("工艺特殊要求 (如先煎、后下)", text: $processRemark)
                    TextField("其他内部备注", text: $remark)
                }
            }
            .navigationTitle(planToEdit != nil ? "编辑加工计划" : "新建加工计划")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("取消") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: submitForm) {
                        if isSubmitting {
                            ProgressView()
                        } else {
                            Text(planToEdit != nil ? "保存修改" : "保存并排产")
                                .fontWeight(.bold)
                                .foregroundStyle(Color.appPrimary)
                        }
                    }
                    .disabled(isSubmitting || selectedPrescriptionId == 0)
                }
            }
            .alert(planToEdit != nil ? "修改成功" : "创建成功", isPresented: $successAlert) {
                Button("确定", role: .cancel) {
                    NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                    NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Prescriptions"), object: nil)
                    dismiss()
                }
            } message: {
                Text(planToEdit != nil ? "加工计划信息已更新" : "加工计划已生成并进入待排产队列")
            }
            .task {
                do {
                    let rx = try await ApiClient.shared.fetchPrescriptions(status: 0, pageSize: 50)
                    availablePrescriptions = rx
                    if selectedPrescriptionId == 0, let first = rx.first {
                        selectedPrescriptionId = first.id
                    }
                } catch {
                    errorMessage = "无法加载处方列表: \(error.localizedDescription)"
                }
            }
                .sheet(isPresented: $showPrescriptionPicker) {
                NavigationStack {
                    List {
                        let filtered = availablePrescriptions.filter { 
                            rxSearchText.isEmpty || 
                            ($0.patientName ?? "").contains(rxSearchText) || 
                            ($0.prescriptionNo ?? "").contains(rxSearchText) 
                        }
                        
                        ForEach(filtered) { rx in
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
                    .navigationTitle("选择关联处方")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("取消") { showPrescriptionPicker = false }
                        }
                    }
                }
            }
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
                        "processType": processType,
                        "totalDose": Int(totalDose) ?? 7,
                        "bagCount": Int(bagCount) ?? 2,
                        "volumeMl": Int(volumeMl) ?? 200,
                        "usageMethod": usageMethod,
                        "pickupMethod": pickupMethod,
                        "scheduleType": scheduleType,
                        "isUrgent": isUrgent,
                        "paymentStatus": paymentStatus,
                        "processRemark": processRemark,
                        "remark": remark
                    ]
                    if scheduleType == 1 {
                        let formatter = DateFormatter()
                        formatter.dateFormat = "yyyy-MM-dd"
                        payload["processDate"] = formatter.string(from: processDate)
                    }
                    if pickupMethod == 2 {
                        payload["expressAddress"] = expressAddress
                    }
                    try await ApiClient.shared.updateProcessingPlan(id: plan.id, payload: payload)
                } else {
                    try await ApiClient.shared.createProcessingPlan(
                        prescriptionId: selectedPrescriptionId,
                        processType: processType,
                        totalDose: Int(totalDose) ?? 7,
                        bagCount: Int(bagCount) ?? 2,
                        volumeMl: Int(volumeMl) ?? 200,
                        usageMethod: usageMethod,
                        pickupMethod: pickupMethod,
                        expressAddress: expressAddress,
                        scheduleType: scheduleType,
                        processDate: scheduleType == 1 ? processDate : nil,
                        isUrgent: isUrgent,
                        paymentStatus: paymentStatus,
                        processRemark: processRemark,
                        remark: remark
                    )
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
