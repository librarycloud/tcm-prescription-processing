import SwiftUI

@MainActor
public struct PrescriptionEditView: View {
    public let id: Int?
    @Environment(\.dismiss) private var dismiss
    
    @State private var patientName = ""
    @State private var patientPhone = ""
    @State private var totalDose = ""
    @State private var remark = ""
    
    @State private var isLoading = false
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    
    public init(id: Int?) {
        self.id = id
    }
    
    public var body: some View {
        Form {
            Section(header: Text("基本信息")) {
                TextField("患者姓名 *", text: $patientName)
                TextField("联系电话", text: $patientPhone)
                    .keyboardType(.phonePad)
                TextField("处方剂数 *", text: $totalDose)
                    .keyboardType(.numberPad)
                TextField("处方备注", text: $remark)
            }
            
            if let error = errorMessage {
                Section {
                    Text(error).foregroundColor(.danger).font(.system(size: (13) * ThemeManager.shared.fontScale))
                }
            }
            
            Section(footer: Text("提示：移动端目前仅支持修改部分信息，如需完整编辑药材，请前往 Web 管理后台。")) {
                Button(action: submitForm) {
                    HStack {
                        Spacer()
                        if isSubmitting {
                            ProgressView().padding(.trailing, 8)
                        }
                        Text("保存").bold()
                        Spacer()
                    }
                    .foregroundColor(.appPrimary)
                }
                .disabled(isSubmitting || patientName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || Int(totalDose) ?? 0 <= 0)
            }
        }
        .navigationTitle(id == nil ? "新建处方" : "编辑处方")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if let rxId = id {
                await loadDetail(rxId)
            }
        }
    }
    
    private func loadDetail(_ rxId: Int) async {
        guard !isLoading else { return }
        isLoading = true
        do {
            if let rx = try await ApiClient.shared.fetchPrescriptionDetail(id: rxId) {
                patientName = rx.patientName ?? ""
                patientPhone = rx.patientPhone ?? ""
                totalDose = rx.totalDose != nil ? "\(rx.totalDose!)" : ""
                remark = rx.remark ?? ""
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func submitForm() {
        isSubmitting = true
        errorMessage = nil
        
        let dose = Int(totalDose) ?? 0
        let pName = patientName.trimmingCharacters(in: .whitespacesAndNewlines)
        let pPhone = patientPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        let pRemark = remark.trimmingCharacters(in: .whitespacesAndNewlines)
        let payload: [String: Any] = [
            "customerName": pName,
            "phone": pPhone,
            "totalDose": dose,
            "remark": pRemark
        ]
        
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
