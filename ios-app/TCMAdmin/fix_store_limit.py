import re

file_path = "/Users/yunfei/Desktop/tcm/ios-app/TCMAdmin/Views/Transfers/TransfersView.swift"
with open(file_path, "r") as f:
    content = f.read()

# Add isSuperAdmin and userStoreId
old_init = """    public init(stores: [StoreItem], onSaved: @escaping () -> Void) {
        self.stores = stores
        self.onSaved = onSaved
    }
    
    private var isValid: Bool {"""

new_init = """    public init(stores: [StoreItem], onSaved: @escaping () -> Void) {
        self.stores = stores
        self.onSaved = onSaved
    }
    
    private var isSuperAdmin: Bool {
        SessionManager.shared.currentUser?.role == 0
    }
    
    private var userStoreId: Int {
        SessionManager.shared.currentUser?.store?.id ?? 0
    }
    
    private var isValid: Bool {"""

content = content.replace(old_init, new_init)

# Replace the From and To store selection blocks
old_selection_blocks = """                            // 调出门店
                            VStack(alignment: .leading, spacing: 6) {
                                Text("调出门店 *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(stores) { s in
                                            SegmentedButton(label: s.name, isSelected: fromStoreId == s.id) {
                                                fromStoreId = s.id
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 调入门店
                            VStack(alignment: .leading, spacing: 6) {
                                Text("调入门店 *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        ForEach(stores) { s in
                                            SegmentedButton(label: s.name, isSelected: toStoreId == s.id) {
                                                toStoreId = s.id
                                            }
                                        }
                                    }
                                }
                            }"""

new_selection_blocks = """                            // 调出门店 (提供方)
                            VStack(alignment: .leading, spacing: 6) {
                                Text("调出门店 (提供方) *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                ScrollView(.horizontal, showsIndicators: false) {
                                    HStack(spacing: 8) {
                                        let availableFrom = isSuperAdmin ? stores.filter { $0.id != toStoreId } : stores.filter { $0.id != userStoreId }
                                        ForEach(availableFrom) { s in
                                            SegmentedButton(label: s.name, isSelected: fromStoreId == s.id) {
                                                fromStoreId = s.id
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 调入门店 (申请方)
                            VStack(alignment: .leading, spacing: 6) {
                                Text("调入门店 (申请方) *").scaledFont(13, weight: .medium).foregroundStyle(Color.ink)
                                if !isSuperAdmin && userStoreId > 0 {
                                    let myStoreName = stores.first(where: { $0.id == userStoreId })?.name ?? SessionManager.shared.currentUser?.store?.name ?? "当前门店"
                                    Text(myStoreName)
                                        .scaledFont(13, weight: .bold)
                                        .foregroundStyle(Color.appPrimary)
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 7)
                                        .background(Color.appPrimary.opacity(0.1))
                                        .clipShape(Capsule())
                                        .overlay(Capsule().stroke(Color.appPrimary.opacity(0.3), lineWidth: 1))
                                } else {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 8) {
                                            let availableTo = stores.filter { $0.id != fromStoreId }
                                            ForEach(availableTo) { s in
                                                SegmentedButton(label: s.name, isSelected: toStoreId == s.id) {
                                                    toStoreId = s.id
                                                }
                                            }
                                        }
                                    }
                                }
                            }"""

content = content.replace(old_selection_blocks, new_selection_blocks)

# Add .onAppear to set default toStoreId
old_body_end = """                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: createAction) {
                        if isBusy {
                            ProgressView()
                        } else {
                            Text("提交")
                        }
                    }
                    .disabled(!isValid)
                }
                .padding(16)
            }
            .background(Color.pageBackground.ignoresSafeArea(.all))
            .navigationTitle("新建调拨")"""

# Wait, the padding(16) belongs to a different block. Let's just find the end of the view correctly.
# Search for .navigationTitle("新建调拨")
# and add .onAppear before or after it
old_nav_title = """            .background(Color.pageBackground.ignoresSafeArea(.all))
            .navigationTitle("新建调拨")"""

new_nav_title = """            .background(Color.pageBackground.ignoresSafeArea(.all))
            .navigationTitle("新建调拨")
            .onAppear {
                if !isSuperAdmin && userStoreId > 0 {
                    toStoreId = userStoreId
                }
            }"""

content = content.replace(old_nav_title, new_nav_title)

with open(file_path, "w") as f:
    f.write(content)
