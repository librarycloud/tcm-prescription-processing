import SwiftUI

@MainActor
public struct PackagesView: View {
    @Bindable private var router = Router.shared
    var session = SessionManager.shared
    
    @State private var selectedStatus: Int? = nil // nil: 全部, 0: 待取件, 1: 已完成
    @State private var selectedSortBy: String = "createdAt" // createdAt, pickedAt
    @State private var searchText = ""
    @State private var searchTask: Task<Void, Error>? = nil
    @State private var loadTask: Task<Void, Never>? = nil
    @State private var packages: [PackageModel] = []
    @State private var stores: [StoreItem] = []
    @State private var selectedStoreId: Int? = nil
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var isCreateSheetShowing = false
    @State private var currentTaskID: UUID = UUID()
    @Environment(\.horizontalSizeClass) private var sizeClass
    
    private var gridColumns: [GridItem] {
        if sizeClass == .regular {
            return [GridItem(.adaptive(minimum: 340, maximum: .infinity), spacing: 12)]
        } else {
            return [GridItem(.flexible())]
        }
    }
    
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
                            .scaledFont(13, weight: .semibold)
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 8))
                        }
                        
                        Button(action: { router.navigate(to: .packageVerify(initialCode: "")) }) {
                            HStack(spacing: 4) {
                                Image(systemName: "qrcode.viewfinder")
                                Text("取件核销")
                            }
                            .scaledFont(13, weight: .semibold)
                            .foregroundStyle(Color.appPrimary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.appPrimarySoft)
                            .clipShape(.rect(cornerRadius: 8))
                        }
                    }
                }
                
                SearchBarField(
                    text: $searchText,
                    placeholder: "输入收件人、取货码、手机号或单号查询",
                    onSearch: {
                        startLoadPackages()
                    },
                    onScan: { router.isScannerPresented = true }
                )
                .onChange(of: searchText) {
                    searchTask?.cancel()
                    searchTask = Task {
                        do {
                            try await Task.sleep(nanoseconds: 350_000_000)
                            if !Task.isCancelled {
                                isLoading = false
                                startLoadPackages()
                            }
                        } catch {}
                    }
                }
                
                // 状态过滤标签
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        SegmentedButton(label: "全部包裹", isSelected: selectedStatus == nil) {
                            selectedStatus = nil
                            startLoadPackages()
                        }
                        SegmentedButton(label: "待取件", isSelected: selectedStatus == 0) {
                            selectedStatus = 0
                            startLoadPackages()
                        }
                        SegmentedButton(label: "已完成", isSelected: selectedStatus == 1) {
                            selectedStatus = 1
                            startLoadPackages()
                        }
                    }
                }
                
                // 排序过滤标签
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        SegmentedButton(label: "按入库时间排序", isSelected: selectedSortBy == "createdAt") {
                            selectedSortBy = "createdAt"
                            startLoadPackages()
                        }
                        SegmentedButton(label: "按取件时间排序", isSelected: selectedSortBy == "pickedAt") {
                            selectedSortBy = "pickedAt"
                            startLoadPackages()
                        }
                    }
                }
                
                if showStore && !stores.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(label: "全部门店", isSelected: selectedStoreId == nil) {
                                selectedStoreId = nil
                                startLoadPackages()
                            }
                            ForEach(stores) { store in
                                SegmentedButton(label: store.name, isSelected: selectedStoreId == store.id) {
                                    selectedStoreId = store.id
                                startLoadPackages()
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
                        .foregroundStyle(Color.danger)
                        .scaledFont(14)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 16)
                    Button("点击重试") {
                        startLoadPackages()
                    }
                    .scaledFont(14, weight: .bold)
                    .foregroundStyle(Color.appPrimary)
                }
                .frame(maxWidth: .infinity)
                Spacer()
            } else if packages.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "shippingbox")
                        .scaledFont(48)
                        .foregroundStyle(Color.muted)
                    Text("暂无匹配包裹")
                        .scaledFont(15)
                        .foregroundStyle(Color.muted)
                }
                Spacer()
            } else {
                AppScrollView {
                    LazyVGrid(columns: gridColumns, spacing: 12) {
                        ForEach(packages) { pkg in
                            PackageRowCard(pkg: pkg, showStore: showStore) {
                                router.navigate(to: .packageVerify(initialCode: pkg.code))
                            } onTap: {
                                router.navigate(to: .packageDetail(id: pkg.id))
                            }
                        }
                    }
                    .padding(16)
                }
                .refreshable {
                    ApiClient.shared.clearResponseCache()
                    await loadPackages()
                }
                .safeAreaInset(edge: .bottom) { Color.clear.frame(height: 8) }
                .background(Color.pageBackground)
            }
        }
        .sheet(isPresented: $isCreateSheetShowing) {
            PackageFormView {
                startLoadPackages()
            }
        }
        .scrollDismissesKeyboard(.interactively)
        .task {
            // 并行加载门店列表与包裹数据，减少首屏等待时间
            if showStore && stores.isEmpty {
                async let storesTask: () = {
                    if let sts = try? await ApiClient.shared.fetchStores() {
                        await MainActor.run { self.stores = sts }
                    }
                }()
                async let packagesTask: () = loadPackages()
                _ = await (storesTask, packagesTask)
            } else {
                await loadPackages()
            }
        }
        .onDisappear {
            searchTask?.cancel()
            loadTask?.cancel()
        }
    }
    
    private func startLoadPackages() {
        loadTask?.cancel()
        loadTask = Task { await loadPackages() }
    }

    private func loadPackages() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        defer {
            if currentTaskID == taskID {
                isLoading = false
            }
        }
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


// MARK: - 包裹行卡片（独立子视图，减少 LazyVGrid 中不必要的重渲染）
private struct PackageRowCard: View {
    let pkg: PackageModel
    let showStore: Bool
    let onVerify: () -> Void
    let onTap: () -> Void
    
    var body: some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Image(systemName: "shippingbox")
                        .foregroundStyle(Color.appPrimary)
                    Text(pkg.name)
                        .scaledFont(16, weight: .bold)
                        .foregroundStyle(Color.ink)
                    Spacer()
                    StatusPill(text: pkg.method)
                    StatusPill(text: pkg.statusText)
                }
                
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("取货码").scaledFont(11).foregroundStyle(Color.muted)
                        Text(pkg.code.formattedPickupCode).scaledFont(18, weight: .bold).foregroundStyle(Color.appPrimaryDark)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("收件人").scaledFont(11).foregroundStyle(Color.muted)
                        Text("\(pkg.customer) · \(maskPhone(pkg.phone))")
                            .scaledFont(13, weight: .semibold)
                            .foregroundStyle(Color.ink)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.surface)
                .clipShape(.rect(cornerRadius: 8))
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
                        Button(action: onVerify) {
                            Text("快速核销")
                                .scaledFont(12, weight: .bold)
                                .foregroundStyle(Color.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 5)
                                .background(Color.success)
                                .clipShape(.rect(cornerRadius: 6))
                        }
                    }
                    .padding(.top, 4)
                }
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            hideKeyboard()
            onTap()
        }
    }
}
