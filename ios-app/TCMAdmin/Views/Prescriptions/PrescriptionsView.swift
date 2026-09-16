import SwiftUI

@MainActor
public struct PrescriptionsView: View {
    @ObservedObject private var router = Router.shared
    @ObservedObject private var session = SessionManager.shared
    
    @State private var searchText = ""
    @State private var selectedStatus: Int? = nil // nil: 全部, 0: 进行中, 1: 已完成, 2: 已取消
    @State private var selectedDoctorId: Int? = nil
    @State private var selectedStoreId: Int? = nil
    
    @State private var doctors: [DoctorItem] = []
    @State private var stores: [StoreItem] = []
    @State private var prescriptions: [PrescriptionItem] = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    
    // 操作状态
    @State private var planPrescription: PrescriptionItem? = nil
    @State private var itemToDelete: PrescriptionItem? = nil
    @State private var showDeleteAlert = false
    
    let statusOptions = [
        (name: "全部", val: nil as Int?),
        (name: "进行中", val: 0 as Int?),
        (name: "已完成", val: 1 as Int?),
        (name: "已取消", val: 2 as Int?)
    ]
    
    private var isStoreStaff: Bool {
        session.currentUser?.role == 3
    }
    
    private var isSuperAdmin: Bool {
        session.currentUser?.role == 0
    }
    
    public init() {}
    
    public var body: some View {
        VStack(spacing: 0) {
            // 头部与过滤区
            VStack(spacing: 10) {
                // 1. 顶部 Header 与新建按钮
                HStack(alignment: .center) {
                    SectionHeader(title: "处方管理", subtitle: "患者处方、加工批次与原件")
                    Spacer()
                    if !isStoreStaff {
                        Button(action: { router.navigate(to: .prescriptionEdit(id: nil)) }) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus")
                                Text("新建处方")
                            }
                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary)
                            .cornerRadius(8)
                        }
                    }
                }
                
                // 2. 搜索栏
                SearchBarField(
                    text: $searchText,
                    placeholder: "搜索处方号、患者姓名或电话",
                    onSearch: {
                        Task { await loadPrescriptions() }
                    },
                    onScan: { router.isScannerPresented = true }
                )
                
                // 3. 状态筛选
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(statusOptions, id: \.name) { opt in
                            SegmentedButton(
                                label: opt.name,
                                isSelected: selectedStatus == opt.val,
                                action: {
                                    withAnimation {
                                        selectedStatus = opt.val
                                        Task { await loadPrescriptions() }
                                    }
                                }
                            )
                        }
                    }
                }
                
                // 4. 医生筛选
                if !doctors.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(
                                label: "全部医生",
                                isSelected: selectedDoctorId == nil,
                                action: {
                                    selectedDoctorId = nil
                                    Task { await loadPrescriptions() }
                                }
                            )
                            ForEach(doctors) { doc in
                                SegmentedButton(
                                    label: doc.name,
                                    isSelected: selectedDoctorId == doc.id,
                                    action: {
                                        selectedDoctorId = doc.id
                                        Task { await loadPrescriptions() }
                                    }
                                )
                            }
                        }
                    }
                }
                
                // 5. 门店筛选 (超管可见)
                if isSuperAdmin && !stores.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            SegmentedButton(
                                label: "全部门店",
                                isSelected: selectedStoreId == nil,
                                action: {
                                    selectedStoreId = nil
                                    Task { await loadPrescriptions() }
                                }
                            )
                            ForEach(stores) { store in
                                SegmentedButton(
                                    label: store.name,
                                    isSelected: selectedStoreId == store.id,
                                    action: {
                                        selectedStoreId = store.id
                                        Task { await loadPrescriptions() }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 12)
            .padding(.bottom, 8)
            .background(Color.pageBackground)
            
            // 处方列表
            if isLoading && prescriptions.isEmpty {
                Spacer()
                ProgressView("正在加载处方记录...")
                Spacer()
            } else if prescriptions.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "doc.text")
                        .font(.system(size: (48) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                    Text("暂无处方数据")
                        .font(.system(size: (15) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(prescriptions) { item in
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack(alignment: .top) {
                                        VStack(alignment: .leading, spacing: 4) {
                                            HStack(alignment: .center, spacing: 6) {
                                                Image(systemName: "cross.case.fill")
                                                    .foregroundColor(.appPrimary)
                                                    .font(.system(size: (14) * ThemeManager.shared.fontScale))
                                                Text(item.patientName ?? "-")
                                                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                                                    .foregroundColor(.ink)
                                            }
                                            Text(item.prescriptionNo ?? "-")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                        }
                                        Spacer()
                                        StatusPill(text: item.statusText)
                                    }
                                    
                                    Spacer().frame(height: 2)
                                    
                                    VStack(spacing: 6) {
                                        InfoRowItem(label: "联系电话", value: maskPhone(item.patientPhone))
                                        InfoRowItem(label: "主治医生", value: item.doctorName ?? "-")
                                        
                                        let taken = item.dose ?? 0
                                        let total = item.totalDose ?? 0
                                        InfoRowItem(label: "剂数进度", value: "\(taken) / \(total) 剂（余 \(max(0, total - taken)) 剂）")
                                        
                                        let planCount = item.plans?.count ?? 0
                                        InfoRowItem(label: "加工批次", value: "\(planCount) 批")
                                        
                                        if let sName = item.storeName {
                                            InfoRowItem(label: "所属门店", value: sName)
                                        }
                                    }
                                    
                                    // 底部操作按钮行 (对标 Android PrescriptionScreens.kt)
                                    if !isStoreStaff {
                                        Divider().foregroundColor(Color.cardBorder.opacity(0.6)).padding(.top, 4)
                                        
                                        HStack(spacing: 8) {
                                            Spacer()
                                            
                                            // 进行中处方可新增加工
                                            if item.status == 0 {
                                                Button(action: {
                                                    planPrescription = item
                                                }) {
                                                    Text("新增加工")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                                                        .foregroundColor(.white)
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 5)
                                                        .background(Color.appPrimary)
                                                        .cornerRadius(6)
                                                }
                                            }
                                            
                                            // 非已完成处方可编辑
                                            if item.status != 1 {
                                                Button(action: {
                                                    router.navigate(to: .prescriptionEdit(id: item.id))
                                                }) {
                                                    Text("编辑")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                        .foregroundColor(.ink)
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 5)
                                                        .background(Color.surface)
                                                        .cornerRadius(6)
                                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                                                }
                                            }
                                            
                                            // 无加工计划时可删除
                                            if (item.plans?.count ?? 0) == 0 {
                                                Button(action: {
                                                    itemToDelete = item
                                                    showDeleteAlert = true
                                                }) {
                                                    Text("删除")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                        .foregroundColor(.danger)
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 5)
                                                        .background(Color.surface)
                                                        .cornerRadius(6)
                                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.danger.opacity(0.4), lineWidth: 1))
                                                }
                                            }
                                        }
                                        .padding(.top, 2)
                                    }
                                }
                            }
                            .onTapGesture {
                                router.navigate(to: .prescriptionDetail(id: item.id))
                            }
                        }
                    }
                    .padding(16)
                }
                .refreshable {
                    await loadPrescriptions()
                }
                .background(Color.pageBackground)
            }
        }
        .sheet(item: $planPrescription) { rx in
            ProcessingPlanFormView(initialPrescriptionId: rx.id)
        }
        .alert(isPresented: $showDeleteAlert) {
            Alert(
                title: Text("删除处方"),
                message: Text("确定要删除 \(itemToDelete?.patientName ?? "患者") 的处方吗？此操作不可撤销。"),
                primaryButton: .destructive(Text("确认删除")) {
                    if let target = itemToDelete {
                        Task {
                            try? await ApiClient.shared.deletePrescription(id: target.id)
                            await loadPrescriptions()
                        }
                    }
                },
                secondaryButton: .cancel(Text("取消"))
            )
        }
        .scrollDismissesKeyboard(.interactively)
        .task {
            async let fetchedDocs = ApiClient.shared.fetchDoctors()
            async let fetchedStores = ApiClient.shared.fetchStores()
            if let docs = try? await fetchedDocs { self.doctors = docs }
            if let sts = try? await fetchedStores { self.stores = sts }
            await loadPrescriptions()
        }
        .navigationTitle("处方管理")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func loadPrescriptions() async {
        guard !isLoading else { return }
        isLoading = true
        do {
            self.prescriptions = try await ApiClient.shared.fetchPrescriptions(
                status: selectedStatus,
                keyword: searchText,
                storeId: selectedStoreId,
                doctorId: selectedDoctorId
            )
        } catch {
            self.errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}

// MARK: - 处方详情
@MainActor
public struct PrescriptionDetailView: View {
    public let id: Int
    @ObservedObject private var router = Router.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var prescription: PrescriptionItem? = nil
    @State private var isLoading = false
    @State private var isBusy = false
    @State private var errorMessage: String? = nil
    
    // 附件查看与上传状态
    @State private var attachmentData: Data? = nil
    @State private var isShowingCamera = false
    @State private var isShowingPhotoLibrary = false
    @State private var isShowingFullAttachment = false
    @State private var showDeleteConfirm = false
    @State private var isUploading = false
    @State private var uploadProgress: Double = 0.0
    @State private var showDeleteAttachmentConfirm = false
    
    // 加工批次操作状态
    @State private var planToCreateFor: PrescriptionItem? = nil
    @State private var planToEdit: ProcessingPlanItem? = nil
    @State private var planToDelete: ProcessingPlanItem? = nil
    @State private var showDeletePlanAlert = false
    @State private var confirmCancelPlanId: Int? = nil
    @State private var showCancelPlanDialog = false
    
    // 图片缩放状态控制
    @State private var currentScale: CGFloat = 1.0
    @State private var currentOffset: CGSize = .zero
    
    public init(id: Int) {
        self.id = id
    }
    
    public var body: some View {
        ZStack {
            Group {
                if isLoading && prescription == nil {
                    ProgressView("正在加载处方详情...")
                } else if let rx = prescription {
                    ScrollView {
                        VStack(spacing: 16) {
                            if let err = errorMessage {
                                AppCard(padding: 12) {
                                    HStack {
                                        Image(systemName: "exclamationmark.triangle.fill").foregroundColor(.danger)
                                        Text(err).font(.system(size: (13) * ThemeManager.shared.fontScale)).foregroundColor(.danger)
                                        Spacer()
                                        Button("关闭") { errorMessage = nil }
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                                    }
                                }
                            }
                            
                            // 1. 基本信息卡片
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack(alignment: .top) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(rx.patientName ?? "患者")
                                                .font(.system(size: (18) * ThemeManager.shared.fontScale, weight: .bold))
                                                .foregroundColor(.ink)
                                            Text(rx.prescriptionNo ?? "CF-\(rx.id)")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                        }
                                        Spacer()
                                        StatusPill(text: rx.statusText)
                                    }
                                    
                                    Divider().foregroundColor(Color.cardBorder).padding(.vertical, 2)
                                    
                                    let phoneStr = rx.patientPhone ?? ""
                                    let maskedPhone = phoneStr.count == 11 ? "\(phoneStr.prefix(3))****\(phoneStr.suffix(4))" : phoneStr
                                    InfoRowItem(label: "联系电话", value: maskedPhone.isEmpty ? "-" : maskedPhone)
                                    InfoRowItem(label: "所属门店", value: rx.storeName ?? "-")
                                    InfoRowItem(label: "主治医生", value: rx.doctorName ?? "-")
                                    InfoRowItem(label: "处方来源", value: rx.source?.name ?? "-")
                                    InfoRowItem(label: "处方类型", value: (rx.isExternal == true) ? "外方" : "本方")
                                    
                                    let taken = rx.dose ?? 0
                                    let total = rx.totalDose ?? 0
                                    let remaining = max(0, total - taken)
                                    InfoRowItem(label: "剂数进度", value: "\(taken) / \(total) 剂，剩余 \(remaining) 剂", valueColor: .appPrimaryDark, isBold: true)
                                    
                                    // 日期不带时间
                                    InfoRowItem(label: "录入时间", value: formatDateOnly(rx.createdAt))
                                    
                                    if let creatorName = rx.creator?.nickname ?? rx.creator?.username, !creatorName.isEmpty {
                                        InfoRowItem(label: "录入人", value: creatorName)
                                    }
                                    if let diag = rx.diagnosis, !diag.isEmpty {
                                        InfoRowItem(label: "临床诊断", value: diag)
                                    }
                                    if let rem = rx.remark, !rem.isEmpty {
                                        InfoRowItem(label: "医嘱备注", value: rem)
                                    }
                                }
                            }
                            
                            // 2. 处方原件模块
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 12) {
                                    HStack {
                                        Text("处方原件")
                                            .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                            .foregroundColor(.ink)
                                        Spacer()
                                        if isUploading {
                                            HStack(spacing: 6) {
                                                ProgressView().scaleEffect(0.8)
                                                if uploadProgress > 0 {
                                                    Text("上传中 \(Int(uploadProgress * 100))%")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                        .foregroundColor(.appPrimary)
                                                } else {
                                                    Text("准备上传...")
                                                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                        .foregroundColor(.appPrimary)
                                                }
                                            }
                                        } else if isBusy {
                                            ProgressView().scaleEffect(0.8)
                                        }
                                    }
                                    
                                    Divider().foregroundColor(Color.cardBorder)
                                    
                                    HStack(spacing: 12) {
                                        if rx.attachment != nil {
                                            Button(action: loadAndShowAttachment) {
                                                HStack(spacing: 6) {
                                                    Image(systemName: "doc.text.magnifyingglass")
                                                    Text("查看原件照片")
                                                }
                                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                                .foregroundColor(.appPrimary)
                                                .frame(maxWidth: .infinity)
                                                .frame(height: 38)
                                                .background(Color.appPrimarySoft)
                                                .cornerRadius(8)
                                            }
                                        }
                                        
                                        Button(action: { isShowingCamera = true }) {
                                            HStack(spacing: 6) {
                                                Image(systemName: "camera.fill")
                                                Text(rx.attachment != nil ? "重新拍照" : "拍照上传")
                                            }
                                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                            .foregroundColor(.white)
                                            .frame(maxWidth: .infinity)
                                            .frame(height: 38)
                                            .background(isUploading ? Color.muted : Color.appPrimary)
                                            .cornerRadius(8)
                                        }
                                        .disabled(isUploading || isBusy)
                                        
                                        Button(action: { isShowingPhotoLibrary = true }) {
                                            HStack(spacing: 6) {
                                                Image(systemName: "photo.on.rectangle")
                                                Text("相册")
                                            }
                                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                            .foregroundColor(.ink)
                                            .frame(maxWidth: .infinity)
                                            .frame(height: 38)
                                            .background(Color.surface)
                                            .cornerRadius(8)
                                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                        }
                                        .disabled(isUploading || isBusy)
                                        
                                        if rx.attachment != nil {
                                            Button(action: { showDeleteAttachmentConfirm = true }) {
                                                HStack(spacing: 6) {
                                                    Image(systemName: "trash.fill")
                                                    Text("删除")
                                                }
                                                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                                                .foregroundColor(.danger)
                                                .frame(maxWidth: .infinity)
                                                .frame(height: 38)
                                                .background(Color.surface)
                                                .cornerRadius(8)
                                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.danger.opacity(0.4), lineWidth: 1))
                                            }
                                            .disabled(isUploading || isBusy)
                                        }
                                    }
                                }
                            }
                            
                            // 3. 加工批次模块 (对齐 Android)
                            let plansList = rx.plans ?? []
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack {
                                        Text("加工批次")
                                            .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                            .foregroundColor(.ink)
                                        Text("共 \(plansList.count) 批")
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                        Spacer()
                                        
                                        if rx.status != 1 && rx.status != 2 {
                                            Button(action: {
                                                planToCreateFor = rx
                                            }) {
                                                Text("新增批次")
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .bold))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 10)
                                                    .padding(.vertical, 5)
                                                    .background(Color.appPrimary)
                                                    .cornerRadius(6)
                                            }
                                        }
                                    }
                                    
                                    if plansList.isEmpty {
                                        Text("暂无加工批次")
                                            .font(.system(size: (13) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                            .padding(.vertical, 4)
                                    } else {
                                        ForEach(Array(plansList.enumerated()), id: \.offset) { index, plan in
                                            planRowView(index: index, plan: plan)
                                        }
                                    }
                                }
                            }
                            
                            // 4. 领取记录 (对齐 Android)
                            let pickupPlans = plansList.filter { $0.package != nil || $0.status == 4 }
                            if !pickupPlans.isEmpty {
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        HStack {
                                            Text("领取记录")
                                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                                .foregroundColor(.ink)
                                            Text("共 \(pickupPlans.count) 批")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                            Spacer()
                                        }
                                        
                                        ForEach(Array(pickupPlans.enumerated()), id: \.offset) { index, plan in
                                            planRowView(index: index, plan: plan)
                                        }
                                    }
                                }
                            }
                            
                            // 5. E6导入处方明细 (对齐 Android)
                            if let e6List = rx.e6Imports, !e6List.isEmpty {
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        HStack {
                                            Text("E6导入处方明细")
                                                .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                                .foregroundColor(.ink)
                                            Text("共 \(e6List.count) 笔")
                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                            Spacer()
                                        }
                                        
                                        ForEach(e6List) { imp in
                                            VStack(alignment: .leading, spacing: 6) {
                                                HStack {
                                                    Text("订单号：\(imp.displayOrderNo)")
                                                        .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .bold))
                                                        .foregroundColor(.ink)
                                                    Spacer()
                                                    StatusPill(text: imp.isPaidBool ? "已付款" : "未付款")
                                                }
                                                
                                                // 日期不带时间
                                                Text("操作员：\(imp.displayOperator)  ·  订单时间：\(formatDateOnly(imp.displayDate))")
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                    .foregroundColor(.muted)
                                                
                                                Text("总价：¥\(String(format: "%.2f", imp.displayPrice))")
                                                    .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .semibold))
                                                    .foregroundColor(.danger)
                                                
                                                if let rawItems = imp.rawPayload?.items, !rawItems.isEmpty {
                                                    Divider().foregroundColor(Color.cardBorder.opacity(0.6)).padding(.vertical, 2)
                                                    
                                                    // 明细小表头
                                                    HStack {
                                                        Text("药材名称").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted).frame(maxWidth: .infinity, alignment: .leading)
                                                        Text("剂数").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted).frame(width: 40, alignment: .trailing)
                                                        Text("单剂量").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted).frame(width: 60, alignment: .trailing)
                                                        Text("总量").font(.system(size: (11) * ThemeManager.shared.fontScale)).foregroundColor(.muted).frame(width: 60, alignment: .trailing)
                                                    }
                                                    
                                                    ForEach(rawItems) { rItem in
                                                        HStack {
                                                            Text(rItem.name ?? "-")
                                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                                .foregroundColor(.ink)
                                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                            Text(String(format: "%g", rItem.doseCount ?? 0))
                                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                                .foregroundColor(.ink)
                                                                .frame(width: 40, alignment: .trailing)
                                                            Text("\(String(format: "%g", rItem.quantity ?? 0))\(rItem.unit ?? "")")
                                                                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                                                .foregroundColor(.ink)
                                                                .frame(width: 60, alignment: .trailing)
                                                            Text("\(String(format: "%g", rItem.totalQuantity ?? 0))\(rItem.unit ?? "")")
                                                                .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .semibold))
                                                                .foregroundColor(.ink)
                                                                .frame(width: 60, alignment: .trailing)
                                                        }
                                                        .padding(.vertical, 2)
                                                    }
                                                }
                                            }
                                            .padding(10)
                                            .background(Color.pageBackground)
                                            .cornerRadius(8)
                                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 0.5))
                                        }
                                    }
                                }
                            }
                            
                            // 6. 药材清单
                            if let herbs = rx.herbs, !herbs.isEmpty {
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 12) {
                                        Text("药材清单 (\(herbs.count)味)")
                                            .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                        
                                        Divider().foregroundColor(Color.cardBorder)
                                        
                                        ForEach(herbs) { herb in
                                            HStack {
                                                Text("\(herb.name) \(String(format: "%g", herb.dosage ?? 0))\(herb.unit ?? "g")\(herb.usageMethod.map { " (\($0))" } ?? "")")
                                                    .font(.system(size: (14) * ThemeManager.shared.fontScale))
                                                Spacer()
                                                if let amt = herb.amount {
                                                    Text("¥\(String(format: "%.2f", amt))")
                                                        .font(.system(size: (14) * ThemeManager.shared.fontScale))
                                                        .foregroundColor(.muted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // 7. 操作管理按钮
                            VStack(spacing: 10) {
                                Button(action: {
                                    router.navigate(to: .prescriptionEdit(id: rx.id))
                                }) {
                                    HStack {
                                        Image(systemName: "pencil")
                                        Text("编辑处方信息")
                                    }
                                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .semibold))
                                    .foregroundColor(.ink)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 46)
                                    .background(Color.surface)
                                    .cornerRadius(10)
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.cardBorder, lineWidth: 1))
                                }
                                
                                Button(action: { showDeleteConfirm = true }) {
                                    HStack {
                                        Image(systemName: "trash")
                                        Text("删除处方")
                                    }
                                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .semibold))
                                    .foregroundColor(.danger)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 46)
                                    .background(Color.surface)
                                    .cornerRadius(10)
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.danger.opacity(0.3), lineWidth: 1))
                                }
                            }
                        }
                        .padding(16)
                    }
                } else {
                    VStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: (36) * ThemeManager.shared.fontScale))
                            .foregroundColor(.muted)
                        Text("未能加载到处方信息")
                            .foregroundColor(.muted)
                    }
                }
            }
            
            // 处方全屏预览
            if isShowingFullAttachment, let data = attachmentData, let uiImage = UIImage(data: data) {
                Color.black.ignoresSafeArea()
                VStack {
                    HStack {
                        Spacer()
                        Button(action: { 
                            isShowingFullAttachment = false 
                            currentScale = 1.0
                            currentOffset = .zero
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: (28) * ThemeManager.shared.fontScale))
                                .foregroundColor(.white)
                                .padding()
                        }
                    }
                    Spacer()
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFit()
                        .scaleEffect(currentScale)
                        .offset(currentOffset)
                        .gesture(
                            MagnificationGesture()
                                .onChanged { value in
                                    currentScale = max(1.0, value)
                                }
                                .onEnded { _ in
                                    if currentScale < 1.0 { currentScale = 1.0 }
                                }
                        )
                        .simultaneousGesture(
                            DragGesture()
                                .onChanged { value in
                                    if currentScale > 1.0 {
                                        currentOffset = value.translation
                                    }
                                }
                                .onEnded { _ in
                                    if currentScale == 1.0 {
                                        currentOffset = .zero
                                    }
                                }
                        )
                    Spacer()
                }
            }
            

        }
        .background(Color.pageBackground.edgesIgnoringSafeArea(.all))
        .task {
            await reloadDetail()
        }
        .sheet(isPresented: $isShowingCamera) {
            ImagePickerView(sourceType: .camera) { image in
                uploadAttachment(image: image)
            }
        }
        .sheet(isPresented: $isShowingPhotoLibrary) {
            ImagePickerView(sourceType: .photoLibrary) { image in
                uploadAttachment(image: image)
            }
        }
        .sheet(item: $planToCreateFor) { rx in
            ProcessingPlanFormView(initialPrescriptionId: rx.id)
                .onDisappear { Task { await reloadDetail() } }
        }
        .sheet(item: $planToEdit) { plan in
            ProcessingPlanFormView(planToEdit: plan)
                .onDisappear { Task { await reloadDetail() } }
        }
        .alert(isPresented: $showDeletePlanAlert) {
            Alert(
                title: Text("删除计划"),
                message: Text("确认要删除该加工计划吗？"),
                primaryButton: .destructive(Text("确认删除")) {
                    if let p = planToDelete {
                        deletePlan(p.id)
                    }
                },
                secondaryButton: .cancel(Text("取消"))
            )
        }
        .alert("删除原件", isPresented: $showDeleteAttachmentConfirm) {
            Button("取消", role: .cancel) {}
            Button("确认删除", role: .destructive) { deleteAttachment() }
        } message: {
            Text("确认要删除该处方原件照片吗？不可恢复。")
        }
        .alert(isPresented: $showDeleteConfirm) {
            Alert(
                title: Text("删除处方确认"),
                message: Text("确认删除该处方吗？如果该处方已关联加工计划，可能无法删除。"),
                primaryButton: .destructive(Text("确认删除")) {
                    deleteCurrentPrescription()
                },
                secondaryButton: .cancel(Text("取消"))
            )
        }
        .background(Color.pageBackground)
        .navigationTitle("处方详情")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func reloadDetail() async {
        guard !isLoading else { return }
        isLoading = true
        prescription = try? await ApiClient.shared.fetchPrescriptionDetail(id: id)
        isLoading = false
    }
    
    private func loadAndShowAttachment() {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                let data = try await ApiClient.shared.fetchPrescriptionAttachment(id: id)
                await MainActor.run {
                    self.attachmentData = data
                    self.isShowingFullAttachment = true
                    self.isBusy = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = "加载处方原件失败: \(error.localizedDescription)"
                    self.isBusy = false
                }
            }
        }
    }
    
    private func uploadAttachment(image: UIImage) {
        guard let data = image.jpegData(compressionQuality: 0.8) else { return }
        isUploading = true
        uploadProgress = 0.0
        Task {
            do {
                let fileName = "prescription_\(id)_\(Int(Date().timeIntervalSince1970)).jpg"
                try await ApiClient.shared.uploadPrescriptionAttachment(id: id, fileName: fileName, mimeType: "image/jpeg", data: data) { progress in
                    DispatchQueue.main.async {
                        self.uploadProgress = max(0, min(1, progress))
                    }
                }
                await reloadDetail()
            } catch {
                errorMessage = "上传处方原件失败: \(error.localizedDescription)"
            }
            isUploading = false
        }
    }
    
    private func deleteCurrentPrescription() {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await ApiClient.shared.deletePrescription(id: id)
                await MainActor.run {
                    self.dismiss()
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = "删除处方失败: \(error.localizedDescription)"
                    self.isBusy = false
                }
            }
        }
    }
    
    private func deletePlan(_ planId: Int) {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await ApiClient.shared.deleteProcessingPlan(id: planId)
                await reloadDetail()
            } catch {
                await MainActor.run {
                    self.errorMessage = "删除计划失败: \(error.localizedDescription)"
                    self.isBusy = false
                }
            }
        }
    }
    
    @ViewBuilder
    private func planRowView(index: Int, plan: ProcessingPlanItem) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                let bNoStr = plan.batchNo != nil ? "\(plan.batchNo!)" : "\(index + 1)"
                let pName = plan.processType?.name ?? plan.method ?? "加工"
                Text("第 \(bNoStr) 批 · \(pName)")
                    .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(.ink)
                Spacer()
                StatusPill(text: plan.statusText)
            }
            
            // 日期不带时间
            let doseStr = plan.totalDose != nil ? "\(plan.totalDose!)" : "0"
            let fDate = formatDateOnly(plan.processDate, defaultVal: "等待安排")
            Text("剂数：\(doseStr) 剂  ·  安排：\(fDate)")
                .font(.system(size: (12) * ThemeManager.shared.fontScale))
                .foregroundColor(.ink)
            
            let isDecoction = (plan.processType?.name ?? "").contains("煎") || (plan.method ?? "").contains("煎")
            if isDecoction {
                let bagsStr = plan.bagCount != nil ? "\(plan.bagCount!)" : "0"
                let volStr = plan.volumeMl != nil ? "\(plan.volumeMl!)" : "0"
                Text("规格：\(bagsStr) 袋 · \(volStr) ml")
                    .font(.system(size: (12) * ThemeManager.shared.fontScale))
                    .foregroundColor(.muted)
            }
            
            if let pCode = plan.package?.code, !pCode.isEmpty, pCode != "-" {
                Text("取货码：\(pCode.formattedPickupCode)")
                    .font(.system(size: (12) * ThemeManager.shared.fontScale, weight: .semibold))
                    .foregroundColor(.appPrimary)
            }
            
            Divider().foregroundColor(Color.cardBorder.opacity(0.6)).padding(.top, 4)
            let pkgId: Int? = plan.package?.id
            let pStatus: Int = plan.status ?? 0
            HStack(spacing: 8) {
                Spacer()
                if pkgId != nil {
                    let pId = pkgId!
                    Button(action: {
                        router.navigate(to: .packageDetail(id: pId))
                    }) {
                        Text("包裹")
                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                            .foregroundColor(.appPrimary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.appPrimarySoft)
                            .cornerRadius(6)
                    }
                }
                
                if pStatus == 0 {
                    Button(action: {
                        self.planToEdit = plan
                    }) {
                        Text("编辑")
                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                            .foregroundColor(.ink)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.gray.opacity(0.1))
                            .cornerRadius(6)
                    }
                }
                
                if pStatus != 3 && pStatus != 4 {
                    Button(action: {
                        self.confirmCancelPlanId = plan.id
                        self.showCancelPlanDialog = true
                    }) {
                        Text("取消")
                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                            .foregroundColor(.danger)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.danger.opacity(0.1))
                            .cornerRadius(6)
                            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.danger.opacity(0.4), lineWidth: 1))
                    }
                }
            }
            .padding(.top, 2)
        }
        .padding(10)
        .background(Color.pageBackground)
        .cornerRadius(8)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 0.5))
    }

    private func deleteAttachment() {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await ApiClient.shared.deletePrescriptionAttachment(id: id)
                await reloadDetail()
            } catch {
                await MainActor.run {
                    self.errorMessage = "删除原件失败: \(error.localizedDescription)"
                    self.isBusy = false
                }
            }
        }
    }
}
