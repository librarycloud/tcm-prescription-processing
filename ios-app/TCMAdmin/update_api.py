file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Network/ApiClient.swift"
with open(file_path, "r") as f:
    content = f.read()

new_func = """    // MARK: - 5. 门店列表
    public func fetchStores() async throws -> [StoreItem] {
        let isSuperAdmin = await MainActor.run { SessionManager.shared.currentUser?.role == 0 }
        guard isSuperAdmin else { return [] }
        return try await request(path: "/stores", queryParams: ["status": "1"])
    }
    
    // 调拨专属门店列表（所有员工可用）
    public func fetchTransferStores() async throws -> [StoreItem] {
        return try await request(path: "/admin/store-transfers/stores")
    }"""

# Find the existing fetchStores
old_func_pattern = """    // MARK: - 5. 门店列表
    public func fetchStores() async throws -> [StoreItem] {
        let isSuperAdmin = await MainActor.run { SessionManager.shared.currentUser?.role == 0 }
        guard isSuperAdmin else { return [] }
        return try await request(path: "/stores", queryParams: ["status": "1"])
    }"""

content = content.replace(old_func_pattern, new_func)

with open(file_path, "w") as f:
    f.write(content)
