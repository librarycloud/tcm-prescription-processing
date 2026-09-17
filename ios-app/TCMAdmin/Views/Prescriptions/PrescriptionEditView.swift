import SwiftUI

@MainActor
public struct PrescriptionEditView: View {
    public let id: Int?
    @Environment(\.dismiss) private var dismiss
    
    // 基本信息
    @State private var customerName = ""
    @State private var phone = ""
    @State private var totalDose = "1"
    @State private var totalPrice = ""
    
    // 关联选择
    @State private var selectedDoctorId: Int? = nil
    @State private var selectedSourceId: Int? = nil
    @State private var selectedStoreId: Int? = nil
    
    // 外方处方
    @State private var isExternal = false
    @State private var externalHospital = ""
    @State private var externalDoctor = ""
    @State private var externalRemark = ""
    
    // 处方状态与备注
    @State private var status: Int = 0 // 0: 进行中, 1: 已完成, 2: 已取消
    @State private var remark = ""
    
    // 基础字典列表
    @State private var doctors: [DoctorItem] = []
    @State private var sources: [DictionaryItem] = []
    @State private var stores: [StoreItem] = []
    
    // 状态
    @State private var isLoading = false
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    
    private var isEdit: Bool {
        id != nil
    }
    
    private var isSuperAdmin: Bool {
        SessionManager.shared.currentUser?.role == 0
    }
    
    private var isFormValid: Bool {
        !customerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (Int(totalDose) ?? 0) > 0
    }
    
    public init(id: Int?) {
        self.id = id
    }
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                // 页面说明标头
                SectionHeader(
                    title: isEdit ? "编辑处方" : "新建处方",
                    subtitle: isEdit ? "修改处方基本信息、归属门店与状态" : "录入患者信息、关联医生与处方明细"
                )
                
                // 错误提示条
                if let error = errorMessage {
                    AppCard(padding: 12) {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(Color.danger)
                            Text(error)
                                .scaledFont(13)
                                .foregroundStyle(Color.danger)
                            Spacer()
                            Button("关闭") { errorMessage = nil }
                                .scaledFont(12, weight: .bold)
                                .foregroundStyle(Color.appPrimary)
                        }
                    }
                }
                
                if isLoading {
                    ProgressView("正在加载处方基础数据...")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 32)
                } else {
                    // 主卡片：处方信息
                    AppCard(padding: 16) {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("处方基本信息")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.ink)
                            
                            Divider().foregroundStyle(Color.cardBorder)
                            
                            InputField(title: "患者姓名 *", placeholder: "请输入患者姓名", text: $customerName)
                            
                            InputField(title: "联系电话", placeholder: "请输入联系电话", text: $phone, keyboardType: .phonePad)
                            
                            HStack(spacing: 12) {
                                InputField(
                                    title: "处方剂数 *",
                                    placeholder: "剂数",
                                    text: Binding(
                                        get: { totalDose },
                                        set: { totalDose = $0.filter { $0.isNumber } }
                                    ),
                                    keyboardType: .numberPad
                                )
                                
                                InputField(
                                    title: "处方金额（可选）",
                                    placeholder: "¥ 0.00",
                                    text: $totalPrice,
                                    keyboardType: .decimalPad
                                )
                            }
                            
                            // 所属门店（仅超级管理员且拥有门店列表时展示）
                            if isSuperAdmin && !stores.isEmpty {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("所属门店")
                                        .scaledFont(13, weight: .medium)
                                        .foregroundStyle(Color.ink)
                                    
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 8) {
                                            ForEach(stores) { store in
                                                SegmentedButton(
                                                    label: store.name,
                                                    isSelected: selectedStoreId == store.id
                                                ) {
                                                    selectedStoreId = store.id
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(.top, 4)
                            }
                            
                            // 主治医生选择
                            if !doctors.isEmpty {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("主治医生")
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
                            }
                            
                            // 处方来源选择
                            if !sources.isEmpty {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("处方来源")
                                        .scaledFont(13, weight: .medium)
                                        .foregroundStyle(Color.ink)
                                    
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 8) {
                                            ForEach(sources) { src in
                                                SegmentedButton(
                                                    label: src.name,
                                                    isSelected: selectedSourceId == src.id
                                                ) {
                                                    selectedSourceId = src.id
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(.top, 4)
                            }
                            
                            // 外方处方
                            VStack(spacing: 10) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("外方处方")
                                            .scaledFont(14, weight: .medium)
                                            .foregroundStyle(Color.ink)
                                        Text("由外部医院或诊所开具")
                                            .scaledFont(12)
                                            .foregroundStyle(Color.muted)
                                    }
                                    Spacer()
                                    Toggle("", isOn: $isExternal)
                                        .labelsHidden()
                                }
                                
                                if isExternal {
                                    Divider().foregroundStyle(Color.cardBorder)
                                    InputField(title: "外方医院", placeholder: "请输入外方医院名称", text: $externalHospital)
                                    InputField(title: "外方医生", placeholder: "请输入外方医生姓名", text: $externalDoctor)
                                    InputField(title: "外方备注", placeholder: "请输入外方备注", text: $externalRemark)
                                }
                            }
                            .padding(12)
                            .background(Color.pageBackground.opacity(0.6))
                            .clipShape(.rect(cornerRadius: 10))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color.cardBorder.opacity(0.8), lineWidth: 1)
                            )
                            .padding(.top, 4)
                            
                            // 处方状态选择（编辑模式）
                            if isEdit {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("处方状态")
                                        .scaledFont(13, weight: .medium)
                                        .foregroundStyle(Color.ink)
                                    
                                    HStack(spacing: 8) {
                                        SegmentedButton(label: "进行中", isSelected: status == 0) {
                                            status = 0
                                        }
                                        SegmentedButton(label: "已完成", isSelected: status == 1) {
                                            status = 1
                                        }
                                        SegmentedButton(label: "已取消", isSelected: status == 2) {
                                            status = 2
                                        }
                                    }
                                }
                                .padding(.top, 4)
                            }
                            
                            // 处方备注
                            InputField(title: "处方备注", placeholder: "请输入处方备注（可选）", text: $remark)
                        }
                    }
                    
                    // 提交按钮
                    Button(action: submitForm) {
                        HStack(spacing: 8) {
                            Spacer()
                            if isSubmitting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            }
                            Text(isSubmitting ? "保存中..." : "确认保存处方")
                                .scaledFont(15, weight: .semibold)
                                .foregroundStyle(Color.white)
                            Spacer()
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(isFormValid && !isSubmitting ? Color.appPrimary : Color.gray.opacity(0.4))
                        .clipShape(.rect(cornerRadius: 10))
                    }
                    .disabled(!isFormValid || isSubmitting)
                    .padding(.top, 6)
                    .padding(.bottom, 24)
                }
            }
            .padding(16)
        }
        .background(Color.pageBackground.ignoresSafeArea())
        .navigationTitle(isEdit ? "编辑处方" : "新建处方")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadInitialData()
        }
    }
    
    private func loadInitialData() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        
        async let fetchDocs = ApiClient.shared.fetchDoctors()
        async let fetchSrcs = ApiClient.shared.fetchPrescriptionSources()
        async let fetchStrs: [StoreItem] = isSuperAdmin ? (try? await ApiClient.shared.fetchStores()) ?? [] : []
        
        if let docs = try? await fetchDocs {
            self.doctors = docs
        }
        if let srcs = try? await fetchSrcs {
            self.sources = srcs
        }
        self.stores = (await fetchStrs)
        
        if let rxId = id {
            do {
                if let rx = try await ApiClient.shared.fetchPrescriptionDetail(id: rxId) {
                    customerName = rx.customerName ?? ""
                    phone = rx.phone ?? ""
                    totalDose = "\(rx.totalDose ?? rx.dose ?? 1)"
                    if let price = rx.totalPrice {
                        totalPrice = String(format: "%.2f", price)
                    }
                    selectedDoctorId = rx.doctorId ?? rx.doctor?.id
                    selectedSourceId = rx.sourceId ?? rx.source?.id
                    selectedStoreId = rx.storeId ?? rx.store?.id
                    isExternal = rx.isExternal ?? false
                    externalHospital = rx.externalHospital ?? ""
                    externalDoctor = rx.externalDoctor ?? ""
                    externalRemark = rx.externalRemark ?? ""
                    status = rx.status ?? 0
                    remark = rx.remark ?? ""
                }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
        
        // 如果未选择，赋予首项默认值
        if selectedDoctorId == nil, let firstDoc = doctors.first {
            selectedDoctorId = firstDoc.id
        }
        if selectedSourceId == nil, let firstSrc = sources.first {
            selectedSourceId = firstSrc.id
        }
        if selectedStoreId == nil, let firstStore = stores.first {
            selectedStoreId = firstStore.id
        }
        
        isLoading = false
    }
    
    private func submitForm() {
        guard isFormValid && !isSubmitting else { return }
        isSubmitting = true
        errorMessage = nil
        
        let dose = Int(totalDose) ?? 1
        let pName = customerName.trimmingCharacters(in: .whitespacesAndNewlines)
        let pPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let pRemark = remark.trimmingCharacters(in: .whitespacesAndNewlines)
        
        var payload: [String: Any] = [
            "customerName": pName,
            "phone": pPhone,
            "totalDose": dose,
            "remark": pRemark,
            "isExternal": isExternal
        ]
        
        if let price = Double(totalPrice.trimmingCharacters(in: .whitespacesAndNewlines)) {
            payload["totalPrice"] = price
        }
        if let docId = selectedDoctorId {
            payload["doctorId"] = docId
        }
        if let srcId = selectedSourceId {
            payload["sourceId"] = srcId
        }
        if let strId = selectedStoreId {
            payload["storeId"] = strId
        }
        if isExternal {
            payload["externalHospital"] = externalHospital.trimmingCharacters(in: .whitespacesAndNewlines)
            payload["externalDoctor"] = externalDoctor.trimmingCharacters(in: .whitespacesAndNewlines)
            payload["externalRemark"] = externalRemark.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if isEdit {
            payload["status"] = status
        }
        
        Task {
            do {
                if let rxId = self.id {
                    try await ApiClient.shared.updatePrescription(id: rxId, payload: payload)
                } else {
                    _ = try await ApiClient.shared.createPrescription(payload: payload)
                }
                await MainActor.run {
                    isSubmitting = false
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isSubmitting = false
                }
            }
        }
    }
}
