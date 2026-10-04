file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views/Transfers/TransfersView.swift"
with open(file_path, "r") as f:
    content = f.read()

old_states = """    @State private var fromStoreId: Int = 0
    @State private var toStoreId: Int = 0"""

new_states = """    @State private var fromStoreId: Int = -1
    @State private var toStoreId: Int = -1"""

content = content.replace(old_states, new_states)

old_valid = """    private var isValid: Bool {
        fromStoreId > 0 && toStoreId > 0 && fromStoreId != toStoreId &&
        !itemName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (Double(itemQuantity) ?? 0.0) > 0 &&
        !isBusy
    }"""

new_valid = """    private var isValid: Bool {
        fromStoreId >= 0 && toStoreId >= 0 && fromStoreId != toStoreId &&
        !itemName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (Double(itemQuantity) ?? 0.0) > 0 &&
        !isBusy
    }"""

content = content.replace(old_valid, new_valid)

with open(file_path, "w") as f:
    f.write(content)
