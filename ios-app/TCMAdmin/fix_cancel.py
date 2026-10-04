import re

file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views/Transfers/TransfersView.swift"
with open(file_path, "r") as f:
    content = f.read()

# Let's wrap the fetch inside a Task.detached so it ignores UI cancellation
old_load = """    private func loadDetail() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        do {
            self.transfer = try await ApiClient.shared.fetchTransferDetail(id: id)
            print("loadDetail success for transfer id: \\(id)")
        } catch is CancellationError {
            print("loadDetail cancelled for transfer id: \\(id)")
            // ignore
        } catch {
            print("loadDetail error: \\(error)")
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
            // 使用独立任务，防止下拉刷新 UI 动画回弹导致任务被 SwiftUI 误杀
            let fetched = try await Task.detached(priority: .userInitiated) {
                try await ApiClient.shared.fetchTransferDetail(id: self.id)
            }.value
            
            self.transfer = fetched
        } catch is CancellationError {
            // ignore
        } catch {
            errorMessage = error.localizedDescription
        }
        if currentTaskID == taskID { isLoading = false }
    }"""

content = content.replace(old_load, new_load)

with open(file_path, "w") as f:
    f.write(content)
