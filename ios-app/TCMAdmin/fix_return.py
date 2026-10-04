import re

file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views/Transfers/TransfersView.swift"
with open(file_path, "r") as f:
    content = f.read()

old_button = """                    Button(action: {
                        submitReturnAction(item: item)
                    }) {
                        Text(returnRecordId != nil ? "保存修改" : "提交归还申请")
                            .scaledFont(15, weight: .bold)
                            .foregroundStyle(Color.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 8))
                    }
                    .disabled((Double(returnQuantityText) ?? 0.0) <= 0 || isActionBusy)"""

new_button = """                    if let error = errorMessage {
                        AppCard(padding: 12) {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Color.danger)
                                Text(error).scaledFont(13).foregroundStyle(Color.danger)
                            }
                        }
                    }

                    Button(action: {
                        submitReturnAction(item: item)
                    }) {
                        if isActionBusy {
                            ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                        } else {
                            Text(returnRecordId != nil ? "保存修改" : "提交归还申请")
                                .scaledFont(15, weight: .bold)
                                .foregroundStyle(Color.white)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .background(((Double(returnQuantityText.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")) ?? 0.0) <= 0 || isActionBusy) ? Color.appPrimary.opacity(0.5) : Color.appPrimary)
                    .clipShape(.rect(cornerRadius: 8))
                    .disabled((Double(returnQuantityText.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")) ?? 0.0) <= 0 || isActionBusy)"""

content = content.replace(old_button, new_button)

old_submit = """    private func submitReturnAction(item: TransferItemModel) {
        guard let qty = Double(returnQuantityText), qty > 0 else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        let rId = returnRecordId
        returnDialogItem = nil
        returnRecordId = nil
        isActionBusy = true
        
        Task {
            do {
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                let dateStr = formatter.string(from: returnDate)
                
                if let rId = rId {
                    let payload: [String: Any] = [
                        "returnDate": dateStr,
                        "quantity": qty,
                        "remark": returnRemark
                    ]
                    try await ApiClient.shared.updateTransferReturn(transferId: id, returnId: rId, payload: payload)
                } else {
                    let payload: [String: Any] = [
                        "returnDate": dateStr,
                        "items": [
                            [
                                "transferItemId": item.id,
                                "quantity": qty,
                                "remark": returnRemark
                            ]
                        ]
                    ]
                    try await ApiClient.shared.addTransferReturns(transferId: id, payload: payload)
                }
                
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
    }"""

new_submit = """    private func submitReturnAction(item: TransferItemModel) {
        let text = returnQuantityText.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
        guard let qty = Double(text), qty > 0 else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        let rId = returnRecordId
        errorMessage = nil
        isActionBusy = true
        
        Task {
            do {
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                let dateStr = formatter.string(from: returnDate)
                
                if let rId = rId {
                    let payload: [String: Any] = [
                        "returnDate": dateStr,
                        "quantity": qty,
                        "remark": returnRemark
                    ]
                    try await ApiClient.shared.updateTransferReturn(transferId: id, returnId: rId, payload: payload)
                } else {
                    let payload: [String: Any] = [
                        "returnDate": dateStr,
                        "items": [
                            [
                                "transferItemId": item.id,
                                "quantity": qty,
                                "remark": returnRemark
                            ]
                        ]
                    ]
                    try await ApiClient.shared.addTransferReturns(transferId: id, payload: payload)
                }
                
                await MainActor.run {
                    isActionBusy = false
                    returnDialogItem = nil
                    returnRecordId = nil
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
    }"""

content = content.replace(old_submit, new_submit)

# Also fix the DatePicker layout just in case it was blocking taps
old_date = """                            VStack(alignment: .leading, spacing: 6) {
                                Text("归还日期").scaledFont(13).foregroundStyle(Color.muted)
                                DatePicker("", selection: $returnDate, displayedComponents: .date)
                                    .labelsHidden()
                            }"""

new_date = """                            HStack {
                                Text("归还日期").scaledFont(13).foregroundStyle(Color.muted)
                                Spacer()
                                DatePicker("", selection: $returnDate, displayedComponents: .date)
                                    .labelsHidden()
                            }"""

content = content.replace(old_date, new_date)

with open(file_path, "w") as f:
    f.write(content)
