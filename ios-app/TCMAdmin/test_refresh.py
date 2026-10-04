import re

file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views/Transfers/TransfersView.swift"
with open(file_path, "r") as f:
    content = f.read()

# Let's add a debug print to loadDetail
old_load = """    private func loadDetail() async {
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

new_load = """    private func loadDetail() async {
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

content = content.replace(old_load, new_load)

with open(file_path, "w") as f:
    f.write(content)
