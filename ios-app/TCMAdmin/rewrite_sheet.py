import re

file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views/Transfers/TransfersView.swift"
with open(file_path, "r") as f:
    content = f.read()

# Fix CancellationError in loadDetail
old_load = """    private func loadDetail() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        do {
            self.transfer = try await ApiClient.shared.fetchTransferDetail(id: id)
        } catch {
            errorMessage = error.localizedDescription
        }
        if currentTaskID == taskID { isLoading = false }
    }"""

new_load = """    private func loadDetail() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        do {
            self.transfer = try await ApiClient.shared.fetchTransferDetail(id: id)
        } catch is CancellationError {
            // ignore
        } catch {
            errorMessage = error.localizedDescription
        }
        if currentTaskID == taskID { isLoading = false }
    }"""

content = content.replace(old_load, new_load)

# Add TransferReturnSheet struct to the file
sheet_struct = """
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
"""

content = content + "\n" + sheet_struct

# Now replace the sheet block in TransferDetailView
# We need to find the sheet and replace it.
sheet_pattern = re.compile(r"\.sheet\(item: \$returnDialogItem\) \{ item in\n\s*NavigationStack \{.*?\n\s*\}\n\s*\}\n\s*\.alert\(\"二次确认\"", re.DOTALL)

new_sheet_call = """.sheet(item: $returnDialogItem) { item in
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
        .alert("二次确认\""""

content = sheet_pattern.sub(new_sheet_call, content)

# And remove the submitReturnAction function from TransferDetailView because it's no longer used.
submit_pattern = re.compile(r"\n\s*private func submitReturnAction\(item: TransferItemModel\) \{.*?\n\s*\}\n\s*\}\n", re.DOTALL)
content = submit_pattern.sub("\n", content)

with open(file_path, "w") as f:
    f.write(content)
