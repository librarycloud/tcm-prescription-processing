import SwiftUI
import PhotosUI

@MainActor
public struct PrescriptionsView: View {
    @Bindable private var router = Router.shared
    var session = SessionManager.shared
    
    @State private var searchText = ""
    @State private var selectedStatus: Int? = nil // nil: 全部, 0: 进行中, 1: 已完成, 2: 已取消
    @State private var selectedDoctorId: Int? = nil
    @State private var selectedStoreId: Int? = nil
    
    @State private var doctors: [DoctorItem] = []
    @State private var stores: [StoreItem] = []
    @State private var prescriptions: [PrescriptionItem] = []
    @State private var isLoading = false
    @State private var errorMessage: String? = nil
    @State private var loadTask: Task<Void, Never>? = nil
    
    @State private var currentTaskID: UUID = UUID()
    // 操作状态
    @State private var planPrescription: PrescriptionItem? = nil
    @State private var itemToDelete: PrescriptionItem? = nil
    @State private var showDeleteAlert = false
    @Environment(\.horizontalSizeClass) private var sizeClass
    
    private var gridColumns: [GridItem] {
        if sizeClass == .regular {
            return [GridItem(.adaptive(minimum: 340, maximum: .infinity), spacing: 12)]
        } else {
            return [GridItem(.flexible())]
        }
    }
    
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
                            .scaledFont(13, weight: .semibold)
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 8))
                        }
                    }
                }
                
                // 2. 搜索栏
                SearchBarField(
                    text: $searchText,
                    placeholder: "搜索处方号、患者姓名或电话",
                    onSearch: {
                        startLoadPrescriptions()
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
                                    withAnimation(.easeInOut(duration: 0.15)) {
                                        selectedStatus = opt.val
                                        startLoadPrescriptions()
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
                                    startLoadPrescriptions()
                                }
                            )
                            ForEach(doctors) { doc in
                                SegmentedButton(
                                    label: doc.name,
                                    isSelected: selectedDoctorId == doc.id,
                                    action: {
                                        selectedDoctorId = doc.id
                                        startLoadPrescriptions()
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
                                    startLoadPrescriptions()
                                }
                            )
                            ForEach(stores) { store in
                                SegmentedButton(
                                    label: store.name,
                                    isSelected: selectedStoreId == store.id,
                                    action: {
                                        selectedStoreId = store.id
                                        startLoadPrescriptions()
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
            } else if let error = errorMessage, !error.isEmpty {
                Spacer()
                VStack(spacing: 8) {
                    Text(error).foregroundStyle(Color.danger).scaledFont(14).multilineTextAlignment(.center)
                    Button("点击重试") { startLoadPrescriptions() }.foregroundStyle(Color.appPrimary).scaledFont(14, weight: .bold)
                }
                .padding(.horizontal, 16)
                Spacer()
            } else if prescriptions.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "doc.text")
                        .scaledFont(48)
                        .foregroundStyle(Color.muted)
                    Text("暂无处方数据")
                        .scaledFont(15)
                        .foregroundStyle(Color.muted)
                }
                Spacer()
            } else {
                AppScrollView {
                    LazyVGrid(columns: gridColumns, spacing: 12) {
                        ForEach(prescriptions) { item in
                            PrescriptionCardView(
                                item: item,
                                isStoreStaff: isStoreStaff,
                                onAddPlan: { planPrescription = item },
                                onEdit: { router.navigate(to: .prescriptionEdit(id: item.id)) },
                                onDelete: { itemToDelete = item; showDeleteAlert = true },
                                onTap: { router.navigate(to: .prescriptionDetail(id: item.id)) }
                            )
                        }
                    }
                    .padding(16)
                }
                .refreshable {
                    ApiClient.shared.clearResponseCache()
                    await loadPrescriptions()
                }
                
                .background(Color.pageBackground)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("ListNeedsRefresh_Prescriptions"))) { _ in
            ApiClient.shared.clearResponseCache()
            startLoadPrescriptions()
        }
        .sheet(item: $planPrescription) { rx in
            ProcessingPlanFormView(initialPrescriptionId: rx.id)
        }
        .alert("删除处方", isPresented: $showDeleteAlert) {
            Button("确认删除", role: .destructive) {
                if let target = itemToDelete {
                    Task {
                        do {
                            try await ApiClient.shared.deletePrescription(id: target.id)
                            await loadPrescriptions()
                        } catch {
                            self.errorMessage = "删除失败: \(error.localizedDescription)"
                        }
                    }
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("确定要删除 \(itemToDelete?.patientName ?? "患者") 的处方吗？此操作不可撤销。")
        }
        .scrollDismissesKeyboard(.interactively)
        .task {
            // 三个请求完全并行：医生列表、门店列表、处方列表
            async let fetchedDocs = ApiClient.shared.fetchDoctors()
            async let fetchedStores = ApiClient.shared.fetchStores()
            async let prescriptionsTask: () = loadPrescriptions()
            if let docs = try? await fetchedDocs { self.doctors = docs }
            if let sts = try? await fetchedStores { self.stores = sts }
            await prescriptionsTask
        }
        .onDisappear {
            loadTask?.cancel()
        }
        .navigationTitle("处方管理")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func startLoadPrescriptions() {
        loadTask?.cancel()
        loadTask = Task { await loadPrescriptions() }
    }

    private func loadPrescriptions() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        do {
            let result = try await ApiClient.shared.fetchPrescriptions(
                status: selectedStatus,
                keyword: searchText,
                storeId: selectedStoreId,
                doctorId: selectedDoctorId
            )
            guard !Task.isCancelled else { return }
            self.prescriptions = result
        } catch {
            guard !Task.isCancelled else { return }
            self.errorMessage = error.localizedDescription
        }
        if currentTaskID == taskID { isLoading = false }
    }
}

// MARK: - 处方详情
@MainActor
public struct PrescriptionDetailView: View {
    public let id: Int
    @Bindable private var router = Router.shared
    @Environment(\.dismiss) private var dismiss
    
    @Environment(SessionManager.self) var session
    private var isStoreStaff: Bool { session.currentUser?.role == 3 }
    
    @State private var prescription: PrescriptionItem? = nil
    @State private var isLoading = false
    @State private var isBusy = false
    @State private var errorMessage: String? = nil
    
    // 附件查看与上传状态
    @State private var attachmentData: Data? = nil
    @State private var isShowingCamera = false
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    @State private var isShowingFullAttachment = false
    @State private var showDeleteConfirm = false
    @State private var isUploading = false
    @State private var uploadProgress: Double = 0.0
    @State private var uploadTask: Task<Void, Never>? = nil
    @State private var showDeleteAttachmentConfirm = false
    @State private var attachmentToDelete: Int? = nil
    
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
    @State private var currentTaskID: UUID = UUID()
    
    public init(id: Int) {
        self.id = id
    }
    
    public var body: some View {
        ZStack {
            Group {
                if isLoading && prescription == nil {
                    ProgressView("正在加载处方详情...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if prescription == nil {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle")
                            .scaledFont(36)
                            .foregroundStyle(Color.muted)
                        Text(errorMessage ?? "未能加载到处方信息")
                            .scaledFont(14)
                            .foregroundStyle(Color.muted)
                            .multilineTextAlignment(.center)
                        Button("点击重试") {
                            Task { await reloadDetail() }
                        }
                        .scaledFont(14, weight: .bold)
                        .foregroundStyle(Color.appPrimary)
                        .padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(16)
                } else if let rx = prescription {
                    AppScrollView {
                        VStack(spacing: 16) {
                            if let err = errorMessage {
                                AppCard(padding: 12) {
                                    HStack {
                                        Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Color.danger)
                                        Text(err).scaledFont(13).foregroundStyle(Color.danger)
                                        Spacer()
                                        Button("关闭") { errorMessage = nil }
                                            .scaledFont(12, weight: .bold)
                                    }
                                }
                            }
                            
                            // 1. 基本信息卡片
                            AppCard(padding: 16) {
                                VStack(alignment: .leading, spacing: 10) {
                                    HStack(alignment: .top) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(rx.patientName ?? "患者")
                                                .scaledFont(18, weight: .bold)
                                                .foregroundStyle(Color.ink)
                                            Text(rx.prescriptionNo ?? "CF-\(rx.id)")
                                                .scaledFont(12)
                                                .foregroundStyle(Color.muted)
                                        }
                                        Spacer()
                                        HStack(alignment: .center, spacing: 8) {
                                            if !isStoreStaff && rx.status != 1 {
                                                Button(action: {
                                                    router.navigate(to: .prescriptionEdit(id: rx.id))
                                                }) {
                                                    Text("编辑")
                                                        .scaledFont(12, weight: .medium)
                                                        .foregroundStyle(Color.ink)
                                                        .padding(.horizontal, 10)
                                                        .padding(.vertical, 4)
                                                        .background(Color.gray.opacity(0.12))
                                                        .clipShape(.rect(cornerRadius: 6))
                                                }
                                            }
                                            StatusPill(text: rx.statusText)
                                        }
                                    }
                                    
                                    Divider().foregroundStyle(Color.cardBorder).padding(.vertical, 2)
                                    
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
                                    InfoRowItem(label: "录入时间", value: formatDateTimeToMinute(rx.createdAt))
                                    
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
                                            .scaledFont(15, weight: .bold)
                                            .foregroundStyle(Color.ink)
                                        Spacer()
                                        if isUploading {
                                            HStack(spacing: 6) {
                                                ProgressView().scaleEffect(0.8)
                                                if uploadProgress > 0 {
                                                    Text("上传中 \(Int(uploadProgress * 100))%")
                                                        .scaledFont(12)
                                                        .foregroundStyle(Color.appPrimary)
                                                } else {
                                                    Text("准备上传...")
                                                        .scaledFont(12)
                                                        .foregroundStyle(Color.appPrimary)
                                                }
                                            }
                                        } else if isBusy {
                                            ProgressView().scaleEffect(0.8)
                                        }
                                    }
                                    
                                    Divider().foregroundStyle(Color.cardBorder)
                                    
                                    VStack(spacing: 8) {
                                        if let attachments = rx.attachments, !attachments.isEmpty {
                                            ForEach(Array(attachments.enumerated()), id: \.element.id) { index, att in
                                                HStack(spacing: 12) {
                                                    Button(action: {
                                                        if let attId = att.id { loadAndShowAttachment(attachmentId: attId) }
                                                    }) {
                                                        HStack(spacing: 6) {
                                                            Image(systemName: "doc.text.magnifyingglass")
                                                            Text(attachments.count > 1 ? "查看原件 \(index + 1)" : "查看原件照片")
                                                        }
                                                        .scaledFont(13, weight: .semibold)
                                                        .foregroundStyle(Color.appPrimary)
                                                        .frame(maxWidth: .infinity)
                                                        .frame(height: 38)
                                                        .background(Color.appPrimarySoft)
                                                        .clipShape(.rect(cornerRadius: 8))
                                                    }
                                                    
                                                    Button(action: {
                                                        attachmentToDelete = att.id
                                                        showDeleteAttachmentConfirm = true 
                                                    }) {
                                                        Image(systemName: "trash.fill")
                                                            .scaledFont(16)
                                                            .foregroundStyle(Color.danger)
                                                            .frame(width: 44, height: 38)
                                                            .background(Color.surface)
                                                            .clipShape(.rect(cornerRadius: 8))
                                                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.danger.opacity(0.4), lineWidth: 1))
                                                    }
                                                    .disabled(isUploading || isBusy)
                                                }
                                            }
                                        }
                                        
                                        HStack(spacing: 12) {
                                            Button(action: { isShowingCamera = true }) {
                                                HStack(spacing: 6) {
                                                    Image(systemName: "camera.fill")
                                                    Text((rx.attachments?.isEmpty ?? true) ? "拍照上传" : "继续拍照上传")
                                                }
                                                .scaledFont(13, weight: .semibold)
                                                .foregroundStyle(Color.white)
                                                .frame(maxWidth: .infinity)
                                                .frame(height: 38)
                                                .background(isUploading ? Color.muted : Color.appPrimary)
                                                .clipShape(.rect(cornerRadius: 8))
                                            }
                                            .disabled(isUploading || isBusy)
                                            
                                            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                                                HStack(spacing: 6) {
                                                    Image(systemName: "photo.on.rectangle")
                                                    Text("相册")
                                                }
                                                .scaledFont(13, weight: .semibold)
                                                .foregroundStyle(Color.ink)
                                                .frame(maxWidth: .infinity)
                                                .frame(height: 38)
                                                .background(Color.surface)
                                                .clipShape(.rect(cornerRadius: 8))
                                                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 1))
                                            }
                                            .disabled(isUploading || isBusy)
                                        }
                                    }
                                        .onChange(of: selectedPhotoItem) { _, newItem in
                                            Task {
                                                if let data = try? await newItem?.loadTransferable(type: Data.self), let uiImage = downsampledImage(from: data, maxPixelSize: 5712) {
                                                    uploadAttachment(image: uiImage)
                                                }
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
                                            .scaledFont(15, weight: .bold)
                                            .foregroundStyle(Color.ink)
                                        Text("共 \(plansList.count) 批")
                                            .scaledFont(12)
                                            .foregroundStyle(Color.muted)
                                        Spacer()
                                        
                                        if rx.status != 1 && rx.status != 2 {
                                            Button(action: {
                                                planToCreateFor = rx
                                            }) {
                                                Text("新增批次")
                                                    .scaledFont(12, weight: .bold)
                                                    .foregroundStyle(Color.white)
                                                    .padding(.horizontal, 10)
                                                    .padding(.vertical, 5)
                                                    .background(Color.appPrimary)
                                                    .clipShape(.rect(cornerRadius: 6))
                                            }
                                        }
                                    }
                                    
                                    if plansList.isEmpty {
                                        Text("暂无加工批次")
                                            .scaledFont(13)
                                            .foregroundStyle(Color.muted)
                                            .padding(.vertical, 4)
                                    } else {
                                        ForEach(Array(plansList.enumerated()), id: \.offset) { index, plan in
                                            planRowView(index: index, plan: plan)
                                        }
                                    }
                                }
                            }
                            
                            let pickupPlans = plansList.filter { $0.package != nil || $0.status == 4 }
                            if !pickupPlans.isEmpty {
                                AppCard(padding: 16) {
                                    VStack(alignment: .leading, spacing: 10) {
                                        HStack {
                                            Text("领取记录")
                                                .scaledFont(15, weight: .bold)
                                                .foregroundStyle(Color.ink)
                                            Text("共 \(pickupPlans.count) 批")
                                                .scaledFont(12)
                                                .foregroundStyle(Color.muted)
                                            Spacer()
                                        }
                                        
                                        ForEach(Array(pickupPlans.enumerated()), id: \.offset) { index, plan in
                                            pickupRowView(index: index, plan: plan)
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
                                                .scaledFont(15, weight: .bold)
                                                .foregroundStyle(Color.ink)
                                            Text("共 \(e6List.count) 笔")
                                                .scaledFont(12)
                                                .foregroundStyle(Color.muted)
                                            Spacer()
                                        }
                                        
                                        ForEach(e6List) { imp in
                                            VStack(alignment: .leading, spacing: 6) {
                                                HStack {
                                                    Text("订单号：\(imp.displayOrderNo)")
                                                        .scaledFont(13, weight: .bold)
                                                        .foregroundStyle(Color.ink)
                                                    Spacer()
                                                    StatusPill(text: imp.isPaidBool ? "已付款" : "未付款")
                                                }
                                                
                                                // 日期不带时间
                                                Text("操作员：\(imp.displayOperator)  ·  订单时间：\(formatDateTimeToMinute(imp.displayDate))")
                                                    .scaledFont(12)
                                                    .foregroundStyle(Color.muted)
                                                
                                                Text("总价：¥\(String(format: "%.2f", imp.displayPrice))")
                                                    .scaledFont(12, weight: .semibold)
                                                    .foregroundStyle(Color.danger)
                                                
                                                if let rawItems = imp.rawPayload?.items, !rawItems.isEmpty {
                                                    Divider().foregroundStyle(Color.cardBorder.opacity(0.6)).padding(.vertical, 2)
                                                    
                                                    // 明细小表头
                                                    HStack {
                                                        Text("药材名称").scaledFont(11).foregroundStyle(Color.muted).frame(maxWidth: .infinity, alignment: .leading)
                                                        Text("剂数").scaledFont(11).foregroundStyle(Color.muted).frame(width: 40, alignment: .trailing)
                                                        Text("单剂量").scaledFont(11).foregroundStyle(Color.muted).frame(width: 60, alignment: .trailing)
                                                        Text("总量").scaledFont(11).foregroundStyle(Color.muted).frame(width: 60, alignment: .trailing)
                                                    }
                                                    
                                                    ForEach(rawItems) { rItem in
                                                        HStack {
                                                            Text(rItem.name ?? "-")
                                                                .scaledFont(12)
                                                                .foregroundStyle(Color.ink)
                                                                .frame(maxWidth: .infinity, alignment: .leading)
                                                            Text(String(format: "%g", rItem.doseCount ?? 0))
                                                                .scaledFont(12)
                                                                .foregroundStyle(Color.ink)
                                                                .frame(width: 40, alignment: .trailing)
                                                            Text("\(String(format: "%g", rItem.quantity ?? 0))\(rItem.unit ?? "")")
                                                                .scaledFont(12)
                                                                .foregroundStyle(Color.ink)
                                                                .frame(width: 60, alignment: .trailing)
                                                            Text("\(String(format: "%g", rItem.totalQuantity ?? 0))\(rItem.unit ?? "")")
                                                                .scaledFont(12, weight: .semibold)
                                                                .foregroundStyle(Color.ink)
                                                                .frame(width: 60, alignment: .trailing)
                                                        }
                                                        .padding(.vertical, 2)
                                                    }
                                                }
                                            }
                                            .padding(10)
                                            .background(Color.pageBackground)
                                            .clipShape(.rect(cornerRadius: 8))
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
                                            .scaledFont(15, weight: .bold)
                                        
                                        Divider().foregroundStyle(Color.cardBorder)
                                        
                                        ForEach(herbs) { herb in
                                            HStack {
                                                Text("\(herb.name) \(String(format: "%g", herb.dosage ?? 0))\(herb.unit ?? "g")\(herb.usageMethod.map { " (\($0))" } ?? "")")
                                                    .scaledFont(14)
                                                Spacer()
                                                if let amt = herb.amount {
                                                    Text("¥\(String(format: "%.2f", amt))")
                                                        .scaledFont(14)
                                                        .foregroundStyle(Color.muted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            
            if isShowingFullAttachment {
                ZStack {
                    Color.black.ignoresSafeArea()
                    
                    if let data = attachmentData, let uiImage = downsampledImage(from: data, maxPixelSize: 5712) {
                        ZoomableImageView(image: uiImage)
                            .ignoresSafeArea()
                    } else {
                        ProgressView()
                            .scaleEffect(1.5)
                            .tint(.white)
                    }
                    
                    VStack {
                        HStack {
                            Spacer()
                            Button(action: { 
                                isShowingFullAttachment = false 
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .scaledFont(28)
                                    .foregroundStyle(Color.white)
                                    .padding()
                            }
                        }
                        Spacer()
                    }
                }
            }
            

        }
        .background(Color.pageBackground.ignoresSafeArea(.all))
        .task {
            await reloadDetail()
        }
        .fullScreenCover(isPresented: $isShowingCamera) {
            ImagePickerView(sourceType: .camera) { image in
                uploadAttachment(image: image)
            }
            .ignoresSafeArea()
        }
        
        .sheet(item: $planToCreateFor) { rx in
            ProcessingPlanFormView(initialPrescriptionId: rx.id)
                .onDisappear { Task { await reloadDetail() } }
        }
        .sheet(item: $planToEdit) { plan in
            ProcessingPlanFormView(planToEdit: plan)
                .onDisappear { Task { await reloadDetail() } }
        }
        .alert("删除计划", isPresented: $showDeletePlanAlert) {
            Button("确认删除", role: .destructive) {
                if let p = planToDelete {
                    deletePlan(p.id)
                }
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("确认要删除该加工计划吗？")
        }
        .alert("删除原件", isPresented: $showDeleteAttachmentConfirm) {
            Button("取消", role: .cancel) {}
            Button("确认删除", role: .destructive) {
                if let attId = attachmentToDelete {
                    deleteAttachment(attId)
                }
            }
        } message: {
            Text("确认要删除该处方原件照片吗？不可恢复。")
        }
        .alert("删除处方确认", isPresented: $showDeleteConfirm) {
            Button("确认删除", role: .destructive) {
                deleteCurrentPrescription()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("确认删除该处方吗？如果该处方已关联加工计划，可能无法删除。")
        }
        .background(Color.pageBackground)
        .navigationTitle("处方详情")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func reloadDetail() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        ApiClient.shared.clearResponseCache()
        prescription = try? await ApiClient.shared.fetchPrescriptionDetail(id: id)
        if currentTaskID == taskID { isLoading = false }
    }
    
    private func loadAndShowAttachment(attachmentId: Int) {
        self.isShowingFullAttachment = true
        self.attachmentData = nil
        Task {
            do {
                let data = try await ApiClient.shared.fetchPrescriptionAttachment(id: id, attachmentId: attachmentId)
                await MainActor.run {
                    self.attachmentData = data
                }
            } catch {
                await MainActor.run {
                    self.isShowingFullAttachment = false
                    self.errorMessage = "加载原件失败: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func uploadAttachment(image: UIImage) {
        uploadTask?.cancel()
        isUploading = true
        uploadProgress = 0.0
        uploadTask = Task { @MainActor in
            defer {
                isUploading = false
                uploadTask = nil
            }
            do {
                guard let cgImage = image.cgImage else {
                    errorMessage = "无法读取照片内容"
                    return
                }
                let orientation = cgImagePropertyOrientation(from: image.imageOrientation)
                let data = await Task.detached(priority: .userInitiated) {
                    jpegDataForUpload(from: cgImage, orientation: orientation)
                }.value
                guard !Task.isCancelled, let data else { return }
                let fileName = "prescription_\(id)_\(Int(Date().timeIntervalSince1970)).jpg"
                try await ApiClient.shared.uploadPrescriptionAttachment(id: id, fileName: fileName, mimeType: "image/jpeg", data: data) { progress in
                    DispatchQueue.main.async {
                        self.uploadProgress = max(0, min(1, progress))
                    }
                }
                await reloadDetail()
            } catch is CancellationError {
                return
            } catch {
                errorMessage = "上传处方原件失败: \(error.localizedDescription)"
            }
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
            defer { 
                Task { @MainActor in self.isBusy = false } 
            }
            do {
                try await ApiClient.shared.deleteProcessingPlan(id: planId)
                await reloadDetail()
            } catch {
                await MainActor.run {
                    self.errorMessage = "删除计划失败: \(error.localizedDescription)"
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
                    .scaledFont(14, weight: .bold)
                    .foregroundStyle(Color.ink)
                Spacer()
                StatusPill(text: plan.statusText)
            }
            
            // 日期不带时间
            let doseStr = plan.totalDose != nil ? "\(plan.totalDose!)" : "0"
            let fDate = formatDateOnly(plan.processDate, defaultVal: "等待安排")
            Text("剂数：\(doseStr) 剂  ·  安排：\(fDate)")
                .scaledFont(12)
                .foregroundStyle(Color.ink)
            
            let isDecoction = (plan.processType?.name ?? "").contains("煎") || (plan.method ?? "").contains("煎")
            if isDecoction {
                let bagsStr = plan.bagCount != nil ? "\(plan.bagCount!)" : "0"
                let volStr = plan.volumeMl != nil ? "\(plan.volumeMl!)" : "0"
                Text("规格：\(bagsStr) 袋 · \(volStr) ml")
                    .scaledFont(12)
                    .foregroundStyle(Color.muted)
            }
            
            if let pCode = plan.package?.code, !pCode.isEmpty, pCode != "-" {
                Text("取货码：\(pCode.formattedPickupCode)")
                    .scaledFont(12, weight: .semibold)
                    .foregroundStyle(Color.appPrimary)
            }
            
            Divider().foregroundStyle(Color.cardBorder.opacity(0.6)).padding(.top, 4)
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
                            .scaledFont(12)
                            .foregroundStyle(Color.appPrimary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.appPrimarySoft)
                            .clipShape(.rect(cornerRadius: 6))
                    }
                }
                
                if pStatus == 0 {
                    Button(action: {
                        self.planToEdit = plan
                    }) {
                        Text("编辑")
                            .scaledFont(12)
                            .foregroundStyle(Color.ink)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.gray.opacity(0.1))
                            .clipShape(.rect(cornerRadius: 6))
                    }
                }
                
                if pStatus == 0 || pStatus == 1 {
                    Button(action: {
                        self.confirmCancelPlanId = plan.id
                        self.showCancelPlanDialog = true
                    }) {
                        Text("取消")
                            .scaledFont(12)
                            .foregroundStyle(Color.danger)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.danger.opacity(0.1))
                            .clipShape(.rect(cornerRadius: 6))
                            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.danger.opacity(0.4), lineWidth: 1))
                    }
                }
            }
            .padding(.top, 2)
        }
        .padding(10)
        .background(Color.pageBackground)
        .clipShape(.rect(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 0.5))
        .onTapGesture {
            router.navigate(to: .workflowOperation(planId: plan.id, planCode: plan.planCode))
        }
    }

    @ViewBuilder
    private func pickupRowView(index: Int, plan: ProcessingPlanItem) -> some View {
        let isPicked = plan.status == 4
        let statusText = isPicked ? "已领取" : "待领取"
        let bNoStr = plan.batchNo != nil ? "\(plan.batchNo!)" : "\(index + 1)"
        let pName = plan.processType?.name ?? "加工"
        let rawCode = plan.package?.code ?? "-"
        
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("第 \(bNoStr) 批 · \(pName)")
                    .scaledFont(14, weight: .bold)
                    .foregroundStyle(Color.ink)
                Spacer()
                Text(statusText)
                    .scaledFont(12)
                    .foregroundStyle(isPicked ? Color.success : Color.warning)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(isPicked ? Color.success.opacity(0.1) : Color.warning.opacity(0.1))
                    .clipShape(.rect(cornerRadius: 4))
            }
            
            Text("剂数：\(plan.totalDose ?? 0) 剂  ·  取货码：\(rawCode.formattedPickupCode)")
                .scaledFont(12)
                .foregroundStyle(Color.ink.opacity(0.8))
            

            Text("完成时间：\(formatDateTimeToMinute(plan.finishDate, defaultVal: "-"))")
                .scaledFont(12)
                .foregroundStyle(Color.muted)
                .padding(.top, 2)
                
            if let pkg = plan.package {
                HStack {
                    Spacer()
                    Button(action: {
                        router.navigate(to: .packageDetail(id: pkg.id ?? 0))
                    }) {
                        Text("包裹详情")
                            .scaledFont(12)
                            .foregroundStyle(Color.ink)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.surface)
                            .clipShape(.rect(cornerRadius: 6))
                            .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                    }
                }
                .padding(.top, 4)
            }
        }
        .padding(10)
        .background(Color.pageBackground)
        .clipShape(.rect(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.cardBorder, lineWidth: 0.5))
    }
    private func deleteAttachment(_ attachmentId: Int) {
        guard !isBusy else { return }
        isBusy = true
        Task {
            defer { 
                Task { @MainActor in self.isBusy = false } 
            }
            do {
                try await ApiClient.shared.deletePrescriptionAttachment(id: id, attachmentId: attachmentId)
                await reloadDetail()
            } catch {
                await MainActor.run {
                    self.errorMessage = "删除原件失败: \(error.localizedDescription)"
                }
            }
        }
    }
}


struct PrescriptionCardView: View {
    let item: PrescriptionItem
    let isStoreStaff: Bool
    let onAddPlan: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void
    let onTap: () -> Void
    
    var body: some View {
        Button(action: {
            hideKeyboard()
            onTap()
        }) {
            AppCard(padding: 16) {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(alignment: .center, spacing: 6) {
                                Image(systemName: "cross.case.fill")
                                    .foregroundStyle(Color.appPrimary)
                                    .scaledFont(14)
                                Text(item.patientName ?? "-")
                                    .scaledFont(16, weight: .bold)
                                    .foregroundStyle(Color.ink)
                            }
                            Text(item.prescriptionNo ?? "-")
                                .scaledFont(12)
                                .foregroundStyle(Color.muted)
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
                    
                    if !isStoreStaff {
                        Divider().foregroundStyle(Color.cardBorder.opacity(0.6)).padding(.top, 4)
                        
                        HStack(spacing: 8) {
                            Spacer()
                            
                            if item.status == 0 {
                                Button(action: onAddPlan) {
                                    Text("新增加工")
                                        .scaledFont(12, weight: .bold)
                                        .foregroundStyle(Color.white)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(Color.appPrimary)
                                        .clipShape(.rect(cornerRadius: 6))
                                }
                            }
                            
                            if item.status != 1 {
                                Button(action: onEdit) {
                                    Text("编辑")
                                        .scaledFont(12)
                                        .foregroundStyle(Color.ink)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(Color.surface)
                                        .clipShape(.rect(cornerRadius: 6))
                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.cardBorder, lineWidth: 1))
                                }
                            }
                            
                            if (item.plans?.count ?? 0) == 0 {
                                Button(action: onDelete) {
                                    Text("删除")
                                        .scaledFont(12)
                                        .foregroundStyle(Color.danger)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(Color.surface)
                                        .clipShape(.rect(cornerRadius: 6))
                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.danger.opacity(0.4), lineWidth: 1))
                                }
                            }
                        }
                        .padding(.top, 2)
                    }
                }
            }
        }
        .buttonStyle(PlainButtonStyle())
    }
}
