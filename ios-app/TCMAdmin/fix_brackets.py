file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views/Transfers/TransfersView.swift"
with open(file_path, "r") as f:
    content = f.read()

# Replace the extra brackets at the end of executeConfirmReturn
old_text = """    private func executeConfirmReturn(returnId: Int) {
        isActionBusy = true
        Task {
            do {
                try await ApiClient.shared.confirmReturn(transferId: id, returnId: returnId)
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
    }
        }
    }
}


// MARK: - 申请/修改归还独立弹窗"""

new_text = """    private func executeConfirmReturn(returnId: Int) {
        isActionBusy = true
        Task {
            do {
                try await ApiClient.shared.confirmReturn(transferId: id, returnId: returnId)
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
    }
}

// MARK: - 申请/修改归还独立弹窗"""

content = content.replace(old_text, new_text)

with open(file_path, "w") as f:
    f.write(content)
