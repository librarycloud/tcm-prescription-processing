import SwiftUI

@MainActor
public struct PackagesView: View {
    @ObservedObject private var router = Router.shared
    @ObservedObject private var session = SessionManager.shared
    
    @State private var selectedStatus: Int? = nil // nil: 全部, 0: 待取件, 1: 已完成
    @State private var selectedSortBy: String = "createdAt" // createdAt, pickedAt
    @State private var searchText = ""
    @State private var searchTask: Task<Void, Error>? = nil
    @State private var packages: [PackageModel] = []
    @State private var stores: [StoreItem] = []
    @State private var selectedStoreId: Int? = nil
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var isCreateSheetShowing = false
    
    public init() {}
    
    private var showStore: Bool {
        session.currentUser?.role == 0
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // 头部与搜索过滤区
            VStack(spacing: 10) {
                HStack(alignment: .center) {
                    SectionHeader(title: "包裹管理", subtitle: "取件记录与物流登记")
                    Spacer()
                    HStack(spacing: 8) {
                        Button(action: { isCreateSheetShowing = true }) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus")
                                Text("新建")
                            }
                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary)
                            .cornerRadius(8)
                        }
                        
                        Button(action: { router.navigate(to: .packageVerify(initialCode: "")) }) {
                            HStack(spacing: 4) {
                                Image(systemName: "qrcode.viewfinder")
                                Text("取件核销")
                            }
                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                            .foregroundColor(.appPrimary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary.opacity(0.12))
                            .cornerRadius(8)
                        }
                    }
                }
                
                SearchBarField(
                    text: $searchText,
                    placeholder: "搜索包裹号、处方号、收件人或手机号",
                    onSearch: {
                        Task { await loadPackages() }
                    },
                    onScan: {
                        router.isScannerPresented = true
                    }
                )
                .onChange(of: searchText) {
                    searchTask?.cancel()
                    searchTask = Task {
                        do {
                            try await Task.sleep(nanoseconds: 500_000_000)
                            if !Task.isCancelled {
                                await loadPackages()
                            }
                        } catch {}
                    }
                }
                
                // 状态过滤标签
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        SegmentedButton(label: "全部包裹", isSelected: selectedStatus == nil) {
                            selectedStatus = nil
                            Task { await loadPackages() }
                        }
                        SegmentedButton(label: "待取件", isSelected: selectedStatus == 0) {
                            selectedStatus = 0
                            Task { await loadPackages() }
                        }
                        SegmentedButton(label: "已完成", isSelected: selectedStatus == 1) {
                            selectedStatus = 1
                            Task { await loadPackages() }
                        }
                    }
                }
                
                // 排序过滤标签
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        SegmentedButton(label: "按入库时间排序", isSelected: selectedSortBy == "createdAt") {
                            selectedSortBy = "createdAt"
                            Task { await loadPackages() }
                        }
                        SegmentedButton(label: "按取件时间排序", isSelected: selectedSortBy == "pickedAt") {
                            selectedSortBy = "pickedAt"
                            Task { await loadPackages() }
                        }
                    }
                }
                
                if showStore && !stores.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(label: "全部门店", isSelected: selectedStoreId == nil) {
                                selectedStoreId = nil
                                Task { await loadPackages() }
                            }
                            ForEach(stores) { store in
                                SegmentedButton(label: store.name, isSelected: selectedStoreId == store.id) {
                                    selectedStoreId = store.id
                                    Task { await loadPackages() }
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 8)
            .background(Color.pageBackground)
            
            if isLoading && packages.isEmpty {
                Spacer()
                ProgressView("正在查询包裹...")
                Spacer()
            } else if let error = errorMessage, !error.isEmpty {
                Spacer()
                VStack(spacing: 8) {
                    Text(error)
                        .foregroundColor(.danger)
                        .font(.system(size: (14) * ThemeManager.shared.fontScale))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                    Button("点击重试") {
                        Task { await loadPackages() }
                    }
                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(.appPrimary)
                }
                .frame(maxWidth: .infinity)
                Spacer()
            } else if packages.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "shippingbox")
                        .font(.system(size: (48) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                    Text("暂无匹配包裹")
                        .font(.system(size: (15) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(packages) { pkg in
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Image(systemName: "shippingbox")
                                            .foregroundColor(.appPrimary)
                                        Text(pkg.name)
                                            .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                            .foregroundColor(.ink)
                                        Spacer()
                                        StatusPill(text: pkg.method)
                                        StatusPill(text: pkg.statusText)
                                    }
                                    
                                    HStack {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text("取货码").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                            Text(pkg.code.formattedPickupCode).font(.system(size: (18) * ThemeManager.shared.fontScale, weight: .bold)).foregroundColor(.appPrimaryDark)
                                        }
                                        Spacer()
                                        VStack(alignment: .trailing, spacing: 4) {
                                            Text("收件人").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted)
                                            Text("\(pkg.customer) · \(maskPhone(pkg.phone))")
                                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                                .foregroundColor(.ink)
                                        }
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(Color.surface)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                    
                                    if showStore && !pkg.store.isEmpty {
                                        InfoRowItem(label: "所属门店", value: pkg.store)
                                    }
                                    let rawTime = pkg.time.isEmpty ? (pkg.pickedAt.isEmpty ? "" : pkg.pickedAt) : pkg.time
                                    let displayTime = (rawTime.isEmpty || rawTime == "未领取") ? "未领取" : formatDateTimeToMinute(rawTime)
                                    InfoRowItem(label: "领取时间", value: displayTime)
                                    if !pkg.info.isEmpty {
                                        InfoRowItem(label: "备注", value: pkg.info)
                                    }
                                    
                                    if pkg.statusCode == 0 {
                                        HStack {
                                            Spacer()
                                            Button(action: {
                                                router.navigate(to: .packageVerify(initialCode: pkg.code))
                                            }) {
                                                Text("快速核销")
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 14)
                                                    .padding(.vertical, 5)
                                                    .background(Color.success)
                                                    .cornerRadius(6)
                                            }
                                        }
                                        .padding(.top, 4)
                                    }
                                }
                            }
                            .onTapGesture {
                                router.navigate(to: .packageDetail(id: pkg.id))
                            }
                        }
                    }
                    .padding(16)
                }
                .refreshable {
                    await loadPackages()
                }
                .safeAreaInset(edge: .bottom) { Color.clear.frame(height: 8) }
                .background(Color.pageBackground)
            }
        }
        .sheet(isPresented: $isCreateSheetShowing) {
            PackageFormView {
                Task { await loadPackages() }
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .task {
            if showStore && stores.isEmpty {
                if let sts = try? await ApiClient.shared.fetchStores() {
                    self.stores = sts
                }
            }
            await loadPackages()
        }
    }
    
    private func loadPackages() async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let res = try await ApiClient.shared.fetchPackages(
                status: selectedStatus,
                keyword: searchText.trimmingCharacters(in: .whitespacesAndNewlines),
                storeId: selectedStoreId,
                sortBy: selectedSortBy
            )
            guard !Task.isCancelled else { return }
            self.packages = res
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled else { return }
            let desc = error.localizedDescription
            if !desc.lowercased().contains("cancel") && !desc.isEmpty {
                self.errorMessage = desc
            }
        }
    }
}

