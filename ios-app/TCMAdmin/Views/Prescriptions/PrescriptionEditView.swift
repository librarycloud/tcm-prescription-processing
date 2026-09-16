import SwiftUI

@MainActor
public struct PrescriptionEditView: View {
    public let id: Int?
    @Environment(\.dismiss) private var dismiss
    
    @State private var patientName = ""
    @State private var patientPhone = ""
    @State private var diagnosis = ""
    
    @State private var isLoading = false
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    
    public init(id: Int?) {
        self.id = id
    }
    
    public var body: some View {
        Form {
            Section(header: Text("患者信息")) {
                TextField("患者姓名", text: $patientName)
                TextField("联系电话", text: $patientPhone)
                    .keyboardType(.phonePad)
                TextField("临床诊断", text: $diagnosis)
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
                .disabled(isSubmitting)
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
                diagnosis = rx.diagnosis ?? ""
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func submitForm() {
        isSubmitting = true
        errorMessage = nil
        Task {
            do {
                if let rxId = id {
                    try await ApiClient.shared.updatePrescriptionBasicInfo(id: rxId, patientName: patientName, patientPhone: patientPhone, diagnosis: diagnosis)
                } else {
                    let payload: [String: Any] = [
                        "patientName": patientName,
                        "patientPhone": patientPhone,
                        "diagnosis": diagnosis,
                        "items": []
                    ]
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
