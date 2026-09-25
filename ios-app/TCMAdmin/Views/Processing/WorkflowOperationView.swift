import SwiftUI
import PhotosUI

@MainActor
public struct WorkflowOperationView: View {
    public let planId: Int
    public let planCode: String
    @Bindable private var router = Router.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var workflow: WorkflowDetailModel? = nil
    @State private var isLoading = false
    @State private var isBusy = false
    @State private var errorMessage: String? = nil
    
    // 照片相关状态
    @State private var selectedPhotoData: Data? = nil
    @State private var isShowingPhoto = false
    @State private var isShowingCamera = false
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    @State private var isUploadingPhoto = false
    @State private var uploadProgress: Double = 0.0
    @State private var uploadTask: Task<Void, Never>? = nil
    @State private var photoViewerScale: CGFloat = 1.0
    @State private var photoViewerOffset: CGSize = .zero
    
    // 设备扫码/手动录入弹窗
    @State private var isInputtingEquipment = false
    @State private var equipmentInputPrompt = ""
    @State private var equipmentInputCode = ""
    @State private var pendingScanAction: String? = nil // "soaking", "decoction_<portion>", "packaging_<usageId>", "fault_swap"
    
    // 异常处理状态
    @State private var exceptionTargetUsage: WorkflowEquipmentUsageItem? = nil
    @State private var showExceptionActionSheet = false
    @State private var showVoidDialog = false
    @State private var showFaultDialog = false
    @State private var exceptionReason = ""
    @State private var faultNewEquipmentCode = ""
    
    // 完成加工与生成包裹弹窗
    @State private var showFinishDialog = false
    @State private var createPackageImmediately = true
    @State private var showGeneratePackageDialog = false
    
    // 设备占用提示
    @State private var showOccupiedDialog = false
    @State private var occupiedEquipmentInfo: EquipmentModel? = nil
    @State private var currentTaskID: UUID = UUID()
    
    public init(planId: Int, planCode: String) {
        self.planId = planId
        self.planCode = planCode
    }
    
    private var isDecoction: Bool {
        workflow?.isDecoction == true ||
        workflow?.processType?.name.contains("煎") == true ||
        workflow?.processType?.code == "DECOCTION"
    }
    
    private var currentStage: Int {
        workflow?.currentStage ?? 1
    }
    
    private var status: Int {
        workflow?.status ?? 0
    }
    
    private var photos: [ProcessingPhotoItem] {
        workflow?.photos ?? []
    }
    
    private var usages: [WorkflowEquipmentUsageItem] {
        workflow?.equipmentUsages ?? []
    }
    
    private var activeSoakings: [WorkflowEquipmentUsageItem] {
        usages.filter { $0.stage == 3 && $0.status == 1 }
    }
    
    private var activeDecoctions: [WorkflowEquipmentUsageItem] {
        usages.filter { $0.stage == 4 && $0.status == 1 }
    }
    
    private var activePackagings: [WorkflowEquipmentUsageItem] {
        usages.filter { $0.stage == 5 && $0.status == 1 }
    }
    
    private func formatWorkflowTime(_ raw: String?) -> String {
        return formatDateTimeToMinute(raw, defaultVal: "")
    }

    private func latestStageCompletedAt(_ stage: Int) -> String {
        let completed = usages.filter { $0.stage == stage && $0.status == 2 }.compactMap { $0.endedAt ?? $0.finishedAt }
        return formatWorkflowTime(completed.max())
    }

    private func earliestStageStartedAt(_ stage: Int) -> String {
        let started = usages.filter { $0.stage == stage && ($0.status == 1 || $0.status == 2) }.compactMap { $0.startedAt }
        return formatWorkflowTime(started.min())
    }
    
    private var dispensingStartedAt: String {
        let s1 = workflow?.startDate ?? ""
        let s2 = workflow?.plans?.first?.startDate ?? ""
        return formatWorkflowTime(s1.isEmpty ? s2 : s1)
    }

    private var dispensingCompletedAt: String {
        let c1 = workflow?.dispensingCompletedAt ?? ""
        let c2 = photos.max(by: { ($0.createdAt ?? "") < ($1.createdAt ?? "") })?.createdAt ?? ""
        return formatWorkflowTime(c1.isEmpty ? c2 : c1)
    }
    
    private var dispensingTimeLabel: String {
        let isCompleted = status == 2 || !photos.isEmpty
        if isCompleted && !dispensingCompletedAt.isEmpty { return dispensingCompletedAt }
        if status == 1 && !dispensingStartedAt.isEmpty { return dispensingStartedAt }
        return ""
    }
    
    private var soakingTimeLabel: String {
        if status == 2 { return latestStageCompletedAt(3) }
        if !activeSoakings.isEmpty { return earliestStageStartedAt(3) }
        return ""
    }
    
    private var decoctionTimeLabel: String {
        if status == 2 { return latestStageCompletedAt(4) }
        if !activeDecoctions.isEmpty { return earliestStageStartedAt(4) }
        return ""
    }
    
    private var packagingTimeLabel: String {
        if status == 2 { return latestStageCompletedAt(5) }
        if !activePackagings.isEmpty { return earliestStageStartedAt(5) }
        return ""
    }

    public var body: some View {
        ZStack {
            ScrollView {
                VStack(spacing: 14) {
                    // 1. 错误横幅
                    if let error = errorMessage {
                        AppCard(padding: 12) {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundStyle(Color.danger)
                                Text(error)
                                    .scaledFont(13)
                                    .foregroundStyle(Color.danger)
                                Spacer()
                                Button("关闭") { errorMessage = nil }
                                    .scaledFont(12, weight: .bold)
                                    .foregroundStyle(Color.appPrimary)
                            }
                        }
                    }
                    
                    // 设备占用卡片 (对齐 Android OccupyingPlanCard)
                    if let occEquip = occupiedEquipmentInfo, let usage = occEquip.currentUsage, let occPlanId = usage.processingPlanId, occPlanId > 0 {
                        OccupyingPlanCard(
                            equipmentName: occEquip.name,
                            equipmentNo: occEquip.equipmentNo ?? "",
                            planCode: usage.planCode ?? "计划 #\(occPlanId)",
                            patientName: usage.patientName ?? "患者",
                            onClick: {
                                let code = usage.planCode ?? ""
                                router.navigate(to: .workflowOperation(planId: occPlanId, planCode: code))
                            }
                        )
                    }
                    
                    // 2. 顶部计划信息卡片
                    topSummaryCard
                    
                    // 3. 待加工时顶部启动按钮
                    if status == 0 {
                        Button(action: startDispensingPlan) {
                            HStack {
                                if isBusy { ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white)) }
                                Text("开始调配")
                                    .scaledFont(15, weight: .bold)
                                    .foregroundStyle(Color.white)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 46)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 10))
                        }
                        .disabled(isBusy)
                    }
                    
                    // 4. STEP 1: 调配
                    stepDispensingCard
                    
                    // 5. 代煎专属工序步骤
                    if isDecoction {
                        // STEP 2: 浸泡
                        stepSoakingCard
                        
                        // STEP 3: 煎煮
                        stepDecoctionCard
                        
                        // STEP 4: 打包
                        stepPackagingCard
                    }
                    
                    // 6. 设备工序流转记录
                    if !usages.isEmpty {
                        equipmentHistoryCard
                    }
                    
                    // 7. 异常处理记录
                    if let exceptions = workflow?.workflowExceptions, !exceptions.isEmpty {
                        exceptionHistoryCard(exceptions: exceptions)
                    }
                    
                    // 8. 底部完成按钮
                    bottomActionButtons
                    
                    Spacer().frame(height: 24)
                }
                .padding(16)
            }
            .background(Color.pageBackground.ignoresSafeArea(.all))
            .navigationTitle("工序详情")
            .navigationBarTitleDisplayMode(.inline)
            .task {
                await loadWorkflow()
            }
            .refreshable {
                ApiClient.shared.clearResponseCache()
                await loadWorkflow()
            }
            
            if isShowingPhoto {
                ZStack {
                    Color.black.ignoresSafeArea()
                    
                    if let photoData = selectedPhotoData, let uiImage = downsampledImage(from: photoData, maxPixelSize: 5712) {
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
                                isShowingPhoto = false
                                selectedPhotoData = nil
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
        // 相机与相册 Sheet
        .sheet(isPresented: $isShowingCamera) {
            ImagePickerView(sourceType: .camera) { image in
                handlePickedImage(image)
            }
        }
        
        // 扫码/手动输入设备编号弹窗
        .alert(equipmentInputPrompt, isPresented: $isInputtingEquipment) {
            Button("确认提交") {
                submitScannedEquipment(code: equipmentInputCode)
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("请输入或确认设备编号 (如: JP01, JY02, BZ01)")
        }
        // 异常处理操作选择 Sheet
        .confirmationDialog("工序异常处理", isPresented: $showExceptionActionSheet, titleVisibility: .visible) {
            Button("撤销误扫") {
                exceptionReason = ""
                showVoidDialog = true
            }
            Button("设备故障换机", role: .destructive) {
                exceptionReason = ""
                faultNewEquipmentCode = ""
                showFaultDialog = true
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("请选择需要对当前设备进行的处理操作：")
        }
        // 撤销误扫确认弹窗
        .alert("撤销误扫记录", isPresented: $showVoidDialog) {
            Button("确认撤销", role: .destructive) {
                submitVoidEquipment()
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("确认撤销此设备的本次使用记录吗？此操作不可逆。")
        }
        // 完成加工确认弹窗
        .alert("完成加工确认", isPresented: $showFinishDialog) {
            Button("确认完成") {
                finishWorkflow(createPackage: createPackageImmediately)
            }
            Button("取消", role: .cancel) {}
        } message: {
            Text("确认加工已全部完成？将自动更新处方状态并生成待核销包裹。")
        }
        // 设备被占用警告弹窗
        .alert("设备使用中", isPresented: $showOccupiedDialog) {
            if let targetPlanId = occupiedEquipmentInfo?.currentUsage?.processingPlanId, targetPlanId > 0 {
                Button("前往该计划") {
                    let code = occupiedEquipmentInfo?.currentUsage?.planCode ?? ""
                    pendingScanAction = nil
                    router.navigate(to: .workflowOperation(planId: targetPlanId, planCode: code))
                }
            }
            Button("知道了", role: .cancel) {
                pendingScanAction = nil
            }
        } message: {
            Text("设备 \(occupiedEquipmentInfo?.name ?? "") (\(occupiedEquipmentInfo?.equipmentNo ?? "")) 当前正被占用。\n被占用计划: \(occupiedEquipmentInfo?.currentUsage?.planCode ?? "未知")")
        }
    }
    
    // MARK: - 页面模块构建
    
    // 顶部概要卡片
    private var topSummaryCard: some View {
        AppCard(padding: 16) {
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(workflow?.customerName ?? workflow?.prescription?.customerName ?? "患者")
                            .scaledFont(18, weight: .bold)
                            .foregroundStyle(Color.ink)
                        Text("\(workflow?.planCode ?? planCode) · 第 \(workflow?.batchNo ?? 1) 批")
                            .scaledFont(12)
                            .foregroundStyle(Color.muted)
                    }
                    Spacer()
                    StatusPill(text: stageText(for: currentStage))
                }
                
                Divider().foregroundStyle(Color.cardBorder)
                
                HStack(spacing: 8) {
                    summaryItem(title: "加工方式", value: workflow?.processType?.name ?? "代煎")
                    summaryItem(title: "处方剂数", value: "\(workflow?.totalDose ?? 7) 剂")
                    if isDecoction {
                        summaryItem(
                            title: "代煎规格",
                            value: "\(workflow?.bagCount ?? 14) 袋 / \(workflow?.volumeMl ?? 200)ml",
                            highlight: true
                        )
                    }
                }
            }
        }
        .onTapGesture {
            if let rxId = workflow?.prescription?.id, rxId > 0 {
                router.navigate(to: .prescriptionDetail(id: rxId))
            }
        }
    }
    
    private func summaryItem(title: String, value: String, highlight: Bool = false) -> some View {
        VStack(spacing: 3) {
            Text(title).scaledFont(11).foregroundStyle(Color.muted)
            Text(value)
                .scaledFont(13, weight: .bold)
                .foregroundStyle(highlight ? Color.appPrimary : Color.ink)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
        .background(Color.pageBackground)
        .clipShape(.rect(cornerRadius: 6))
    }
    
    // STEP 1: 调配
    private var stepDispensingCard: some View {
        AppCard(padding: 14) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    stepCircle(number: "1")
                    Text("调配").scaledFont(15, weight: .bold).foregroundStyle(Color.ink)
                    Spacer()
                    let stateText = status == 2 || photos.count > 0 ? "已完成调配" : (status == 1 ? "调配中" : "待调配")
                    Text(stateText)
                        .scaledFont(12, weight: .semibold)
                        .foregroundStyle(status == 2 || photos.count > 0 ? Color.success : Color.appPrimary)
                }
                .overlay(alignment: .center) {
                    if !dispensingTimeLabel.isEmpty {
                        Text(dispensingTimeLabel)
                            .scaledFont(11.5)
                            .foregroundStyle(Color.muted)
                    }
                }
                
                Text("称量调配完成后拍照或从相册上传留存凭证")
                    .scaledFont(12)
                    .foregroundStyle(Color.muted)
                
                if isUploadingPhoto {
                    HStack(spacing: 8) {
                        ProgressView().scaleEffect(0.8)
                        if uploadProgress > 0 {
                            Text("上传中 \(Int(uploadProgress * 100))%").scaledFont(12).foregroundStyle(Color.muted)
                        } else {
                            Text("准备上传...").scaledFont(12).foregroundStyle(Color.muted)
                        }
                        Spacer()
                        if uploadTask != nil {
                            Button("取消") {
                                uploadTask?.cancel()
                            }
                            .scaledFont(11)
                            .foregroundStyle(Color.danger)
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                // 上传的照片列表
                if !photos.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(photos) { photo in
                                HStack(spacing: 6) {
                                    Button(action: { viewPhoto(photoId: photo.id) }) {
                                        HStack(spacing: 4) {
                                            Image(systemName: "photo.fill")
                                            Text("凭证 #\(photo.id)")
                                        }
                                        .scaledFont(12, weight: .medium)
                                        .foregroundStyle(Color.appPrimary)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.appPrimarySoft)
                                        .clipShape(.rect(cornerRadius: 6))
                                    }
                                    
                                    if status == 1 && currentStage <= 2 {
                                        Button(action: { deletePhoto(photoId: photo.id) }) {
                                            Image(systemName: "trash")
                                                .scaledFont(11)
                                                .foregroundStyle(Color.danger)
                                                .padding(6)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 拍照与上传操作按钮
                if status == 1 && currentStage <= 2 && photos.count < 3 {
                    HStack(spacing: 10) {
                        Button(action: { isShowingCamera = true }) {
                            HStack(spacing: 6) {
                                Image(systemName: "camera.fill")
                                Text(photos.isEmpty ? "拍照调配" : "拍照补充")
                            }
                            .scaledFont(13, weight: .medium)
                            .foregroundStyle(Color.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                            .background(isUploadingPhoto ? Color.muted : Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 8))
                        }
                        
                        let isPhotosEmpty = photos.isEmpty
                        let isUploading = isUploadingPhoto
                        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                            HStack(spacing: 6) {
                                Image(systemName: "photo.on.rectangle")
                                Text(isPhotosEmpty ? "相册选择" : "相册补充")
                            }
                            .scaledFont(13, weight: .medium)
                            .foregroundStyle(isUploading ? Color.muted : Color.appPrimary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 38)
                            .background(Color.surface)
                            .clipShape(.rect(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(isUploading ? Color.muted : Color.appPrimary, lineWidth: 1))
                        }}
                        .onChange(of: selectedPhotoItem) { _, newItem in
                            Task { @MainActor in
                                if let data = try? await newItem?.loadTransferable(type: Data.self), let uiImage = downsampledImage(from: data, maxPixelSize: 5712) {
                                    handlePickedImage(uiImage)
                                }
                            }
                        }
                    .disabled(isUploadingPhoto)
                }
            }
        }
    }
    
    // STEP 2: 浸泡
    private var stepSoakingCard: some View {
        AppCard(padding: 14) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    stepCircle(number: "2")
                    Text("浸泡").scaledFont(15, weight: .bold).foregroundStyle(Color.ink)
                    Spacer()
                    let stateText = status == 2 ? "浸泡已完成" : (!activeSoakings.isEmpty ? "浸泡中" : "等待浸泡")
                    Text(stateText)
                        .scaledFont(12, weight: .semibold)
                        .foregroundStyle(!activeSoakings.isEmpty ? Color.appPrimary : Color.muted)
                }
                .overlay(alignment: .center) {
                    if !soakingTimeLabel.isEmpty {
                        Text(soakingTimeLabel)
                            .scaledFont(11.5)
                            .foregroundStyle(Color.muted)
                    }
                }
                
                // 进行中的浸泡设备
                if !activeSoakings.isEmpty {
                    ForEach(activeSoakings) { item in
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("第 \(item.portionNo ?? 1) 组 · \(item.equipment?.name ?? "浸泡设备")")
                                    .scaledFont(13, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("操作人：\(item.operatorUser?.displayName ?? "-") · 开始：\(formatDateTimeToMinute(item.startedAt))")
                                    .scaledFont(11)
                                    .foregroundStyle(Color.muted)
                            }
                            Spacer()
                            if status == 1 {
                                Button("异常处理") {
                                    exceptionTargetUsage = item
                                    showExceptionActionSheet = true
                                }
                                .scaledFont(11, weight: .semibold)
                                .foregroundStyle(Color.warning)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.warningSoft)
                                .clipShape(.rect(cornerRadius: 6))
                            }
                        }
                        .padding(10)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 8))
                    }
                }
                
                if status == 1 {
                    Button(action: {
                        pendingScanAction = "soaking"
                        router.presentScanner { code in
                            let equipmentCode = code.replacingOccurrences(of: "TCM:EQUIPMENT:1:", with: "")
                            submitScannedEquipment(code: equipmentCode)
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "qrcode.viewfinder")
                            Text("扫码添加浸泡桶")
                        }
                        .scaledFont(13, weight: .medium)
                        .foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 38)
                        .background(Color.appPrimary)
                        .clipShape(.rect(cornerRadius: 8))
                    }
                }
            }
        }
    }
    
    // STEP 3: 煎煮
    private var stepDecoctionCard: some View {
        AppCard(padding: 14) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    stepCircle(number: "3")
                    Text("煎煮").scaledFont(15, weight: .bold).foregroundStyle(Color.ink)
                    Spacer()
                    let stateText = status == 2 ? "煎煮已完成" : (!activeDecoctions.isEmpty ? "煎煮中" : "等待煎煮")
                    Text(stateText)
                        .scaledFont(12, weight: .semibold)
                        .foregroundStyle(!activeDecoctions.isEmpty ? Color.appPrimary : Color.muted)
                }
                .overlay(alignment: .center) {
                    if !decoctionTimeLabel.isEmpty {
                        Text(decoctionTimeLabel)
                            .scaledFont(11.5)
                            .foregroundStyle(Color.muted)
                    }
                }
                
                // 等待转煎煮的分组
                if status == 1 && !activeSoakings.isEmpty {
                    ForEach(activeSoakings) { item in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("第 \(item.portionNo ?? 1) 组 · 等待转煎煮")
                                    .scaledFont(13, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("浸泡桶：\(item.equipment?.name ?? "设备")")
                                    .scaledFont(11)
                                    .foregroundStyle(Color.muted)
                            }
                            Spacer()
                            Button("扫锅煎煮") {
                                pendingScanAction = "decoction_\(item.portionNo ?? 1)"
                                router.presentScanner { code in
                                    let equipmentCode = code.replacingOccurrences(of: "TCM:EQUIPMENT:1:", with: "")
                                    submitScannedEquipment(code: equipmentCode)
                                }
                            }
                            .scaledFont(12, weight: .bold)
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 6))
                        }
                        .padding(10)
                        .background(Color.appPrimarySoft)
                        .clipShape(.rect(cornerRadius: 8))
                    }
                }
                
                // 进行中的煎煮
                if !activeDecoctions.isEmpty {
                    ForEach(activeDecoctions) { item in
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("第 \(item.portionNo ?? 1) 组 · \(item.equipment?.name ?? "煎药机") · 煎煮中")
                                    .scaledFont(13, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("操作人：\(item.operatorUser?.displayName ?? "-") · 开始：\(formatDateTimeToMinute(item.startedAt))")
                                    .scaledFont(11)
                                    .foregroundStyle(Color.muted)
                            }
                            Spacer()
                            if status == 1 {
                                Button("异常处理") {
                                    exceptionTargetUsage = item
                                    showExceptionActionSheet = true
                                }
                                .scaledFont(11, weight: .semibold)
                                .foregroundStyle(Color.warning)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.warningSoft)
                                .clipShape(.rect(cornerRadius: 6))
                            }
                        }
                        .padding(10)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 8))
                    }
                }
                
                if activeSoakings.isEmpty && activeDecoctions.isEmpty && status != 2 {
                    Text("暂无进行中的煎煮任务").scaledFont(12).foregroundStyle(Color.muted)
                }
            }
        }
    }
    
    // STEP 4: 打包
    private var stepPackagingCard: some View {
        AppCard(padding: 14) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    stepCircle(number: "4")
                    Text("打包").scaledFont(15, weight: .bold).foregroundStyle(Color.ink)
                    Spacer()
                    let stateText = status == 2 ? "全部分组已打包" : (!activePackagings.isEmpty ? "打包中" : "等待打包")
                    Text(stateText)
                        .scaledFont(12, weight: .semibold)
                        .foregroundStyle(!activePackagings.isEmpty ? Color.appPrimary : Color.muted)
                }
                .overlay(alignment: .center) {
                    if !packagingTimeLabel.isEmpty {
                        Text(packagingTimeLabel)
                            .scaledFont(11.5)
                            .foregroundStyle(Color.muted)
                    }
                }
                
                // 等待打包的分组
                if status == 1 && !activeDecoctions.isEmpty {
                    ForEach(activeDecoctions) { item in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("第 \(item.portionNo ?? 1) 组 · 煎煮完毕等待打包")
                                    .scaledFont(13, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("煎药机：\(item.equipment?.name ?? "设备")")
                                    .scaledFont(11)
                                    .foregroundStyle(Color.muted)
                            }
                            Spacer()
                            Button("扫包装机打包") {
                                pendingScanAction = "packaging_\(item.id)"
                                router.presentScanner { code in
                                    let equipmentCode = code.replacingOccurrences(of: "TCM:EQUIPMENT:1:", with: "")
                                    submitScannedEquipment(code: equipmentCode)
                                }
                            }
                            .scaledFont(12, weight: .bold)
                            .foregroundStyle(Color.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary)
                            .clipShape(.rect(cornerRadius: 6))
                        }
                        .padding(10)
                        .background(Color.appPrimarySoft)
                        .clipShape(.rect(cornerRadius: 8))
                    }
                }
                
                // 进行中的打包
                if !activePackagings.isEmpty {
                    ForEach(activePackagings) { item in
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("第 \(item.portionNo ?? 1) 组 · \(item.equipment?.name ?? "包装机") · 打包中")
                                    .scaledFont(13, weight: .bold)
                                    .foregroundStyle(Color.ink)
                                Text("操作人：\(item.operatorUser?.displayName ?? "-") · 开始：\(formatDateTimeToMinute(item.startedAt))")
                                    .scaledFont(11)
                                    .foregroundStyle(Color.muted)
                            }
                            Spacer()
                        }
                        .padding(10)
                        .background(Color.pageBackground)
                        .clipShape(.rect(cornerRadius: 8))
                    }
                }
                
                if activeDecoctions.isEmpty && activePackagings.isEmpty && status != 2 {
                    Text("等待煎煮完成后扫描包装机").scaledFont(12).foregroundStyle(Color.muted)
                }
            }
        }
    }
    
    // 设备工序记录
    private var equipmentHistoryCard: some View {
        AppCard(padding: 14) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("设备工序记录").scaledFont(15, weight: .bold).foregroundStyle(Color.ink)
                    Spacer()
                    Text("共 \(usages.count) 条记录").scaledFont(12).foregroundStyle(Color.muted)
                }
                
                ForEach(usages) { item in
                    let stageName = stageName(for: item.stage ?? 0)
                    let isRunning = item.status == 1
                    let isSuccess = item.status == 2
                    let isVoid = item.status == 3
                    
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            HStack(spacing: 6) {
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(isRunning ? Color.appPrimary : (isSuccess ? Color.success : Color.danger))
                                    .frame(width: 3.5, height: 14)
                                
                                Text("\(stageName) · 第 \(item.portionNo ?? 1) 组 · \(item.equipment?.name ?? "设备")")
                                    .scaledFont(13, weight: .semibold)
                                    .foregroundStyle(Color.ink)
                            }
                            Spacer()
                            StatusPill(text: isRunning ? "进行中" : (isSuccess ? "已完成" : (isVoid ? "已作废" : "未知")))
                        }
                        Text("时段：\(formatDateTimeToMinute(item.startedAt)) → \(isRunning ? "进行中" : formatDateTimeToMinute(item.endedAt))")
                            .scaledFont(11.5)
                            .foregroundStyle(Color.muted)
                        
                        HStack {
                            Text("操作人：\(item.operatorUser?.displayName ?? "-")")
                                .scaledFont(11.5)
                                .foregroundStyle(Color.muted)
                            
                            Spacer()
                            
                            Text(isRunning ? "已用时 \(processingDuration(start: item.startedAt, end: nil))" : "用时 \(processingDuration(start: item.startedAt, end: item.endedAt))")
                                .scaledFont(11.5, weight: .medium)
                                .foregroundStyle(isRunning ? Color.appPrimary : Color.ink)
                        }
                        
                        if let reason = item.voidReason, !reason.isEmpty {
                            Text("作废原因：\(reason)")
                                .scaledFont(11)
                                .foregroundStyle(Color.danger)
                        }
                    }
                    .padding(8)
                    .background(Color.pageBackground)
                    .clipShape(.rect(cornerRadius: 6))
                }
            }
        }
    }
    
    // 异常记录卡片
    private func exceptionHistoryCard(exceptions: [WorkflowExceptionItem]) -> some View {
        AppCard(padding: 14) {
            VStack(alignment: .leading, spacing: 8) {
                Text("工序异常处理记录").scaledFont(15, weight: .bold).foregroundStyle(Color.ink)
                ForEach(exceptions) { ex in
                    let typeText = ex.type == 1 ? "误扫撤销" : "设备故障换机"
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(typeText) · \(ex.reason ?? "无说明")")
                            .scaledFont(12, weight: .semibold)
                            .foregroundStyle(Color.danger)
                        Text("操作人：\(ex.operatorUser?.displayName ?? "-") · \(formatDateTimeToMinute(ex.createdAt))")
                            .scaledFont(11.5)
                            .foregroundStyle(Color.muted)
                    }
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.dangerSoft.opacity(0.4))
                    .clipShape(.rect(cornerRadius: 6))
                }
            }
        }
    }
    
    // 底部操作栏
    private var bottomActionButtons: some View {
        VStack(spacing: 12) {
            let canFinish = workflow?.canCompleteWorkflow == true || workflow?.canFinalizeWorkflow == true
            if status == 1 && canFinish {
                Button(action: { showFinishDialog = true }) {
                    Text("完成加工")
                        .scaledFont(16, weight: .bold)
                        .foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.success)
                        .clipShape(.rect(cornerRadius: 10))
                }
            }
            
            let packageCreated = workflow?.packageCreated == true || workflow?.package != nil
            if status == 2 && !packageCreated {
                Button(action: { generatePackage() }) {
                    Text("生成待取包裹")
                        .scaledFont(16, weight: .bold)
                        .foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.appPrimary)
                        .clipShape(.rect(cornerRadius: 10))
                }
            }
            
            if status == 2 && packageCreated {
                Button(action: {
                    if let code = workflow?.package?.code {
                        router.navigate(to: .packageVerify(initialCode: code))
                    } else {
                        router.navigate(to: .packageVerify(initialCode: ""))
                    }
                }) {
                    Text("前往包裹核销")
                        .scaledFont(16, weight: .bold)
                        .foregroundStyle(Color.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.appPrimary)
                        .clipShape(.rect(cornerRadius: 10))
                }
            }
        }
    }
    
    // MARK: - 辅助方法与动作处理
    
    private func stepCircle(number: String) -> some View {
        Text(number)
            .scaledFont(11, weight: .bold)
            .foregroundStyle(Color.appPrimary)
            .frame(width: 22, height: 22)
            .background(Color.appPrimarySoft)
            .clipShape(Circle())
    }
    
    private func stageText(for stage: Int) -> String {
        switch stage {
        case 1: return "待调配"
        case 2: return "调配中"
        case 3: return "浸泡中"
        case 4: return "煎煮中"
        case 5: return "打包中"
        case 6: return "加工完成"
        default: return "未知阶段"
        }
    }
    
    private func stageName(for stage: Int) -> String {
        switch stage {
        case 3: return "浸泡"
        case 4: return "煎煮"
        case 5: return "打包"
        default: return "工序"
        }
    }
    
    private func loadWorkflow() async {
        let taskID = UUID()
        currentTaskID = taskID
        isLoading = true
        errorMessage = nil
        do {
            self.workflow = try await ApiClient.shared.fetchProcessingWorkflow(id: planId)
        } catch {
            errorMessage = "加载工序失败: \(error.localizedDescription)"
        }
        if currentTaskID == taskID { isLoading = false }
    }
    
    private func startDispensingPlan() {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await ApiClient.shared.transitionPlan(id: planId, status: 1)
                NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                await loadWorkflow()
            } catch {
                errorMessage = "启动调配失败: \(error.localizedDescription)"
            }
            isBusy = false
        }
    }
    
    private func handlePickedImage(_ image: UIImage) {
        isUploadingPhoto = true
        uploadProgress = 0.0
        uploadTask = Task { @MainActor in
            defer {
                isUploadingPhoto = false
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
                let fileName = "dispensing_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
                try await ApiClient.shared.completeDispensing(planId: planId, fileName: fileName, mimeType: "image/jpeg", data: data) { progress in
                    DispatchQueue.main.async {
                        self.uploadProgress = max(0, min(1, progress))
                    }
                }
                NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                await loadWorkflow()
            } catch {
                if !(error is CancellationError) {
                    errorMessage = "上传凭证照片失败: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func viewPhoto(photoId: Int) {
        self.isShowingPhoto = true
        self.selectedPhotoData = nil
        Task {
            do {
                let data = try await ApiClient.shared.fetchProcessingPhoto(planId: planId, photoId: photoId)
                await MainActor.run {
                    self.selectedPhotoData = data
                }
            } catch {
                await MainActor.run {
                    self.isShowingPhoto = false
                    self.errorMessage = "加载照片失败: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func deletePhoto(photoId: Int) {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await ApiClient.shared.deleteProcessingPhoto(planId: planId, photoId: photoId)
                NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                await loadWorkflow()
            } catch {
                errorMessage = "删除照片失败: \(error.localizedDescription)"
            }
            isBusy = false
        }
    }
    
    private func submitScannedEquipment(code: String) {
        guard let action = pendingScanAction, !code.isEmpty else { return }
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                if let equipment = try await ApiClient.shared.fetchEquipmentByCode(code: code) {
                    if equipment.status == 2 || (equipment.currentUsage?.processingPlanId != nil && equipment.currentUsage?.processingPlanId != planId) {
                        await MainActor.run {
                            self.occupiedEquipmentInfo = equipment
                            self.showOccupiedDialog = true
                            self.isBusy = false
                        }
                        return
                    }
                }
                
                if action == "soaking" {
                    let nextPortion = (activeSoakings.map { $0.portionNo ?? 1 }.max() ?? 0) + 1
                    try await ApiClient.shared.startEquipmentUsage(planId: planId, stage: 3, portionNo: nextPortion, equipmentCode: code)
                } else if action.starts(with: "decoction_") {
                    let portion = Int(action.replacingOccurrences(of: "decoction_", with: "")) ?? 1
                    try await ApiClient.shared.startEquipmentUsage(planId: planId, stage: 4, portionNo: portion, equipmentCode: code)
                } else if action.starts(with: "packaging_") {
                    let usageId = Int(action.replacingOccurrences(of: "packaging_", with: "")) ?? 0
                    try await ApiClient.shared.startPackaging(planId: planId, usageId: usageId, equipmentCode: code)
                }
                NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                await loadWorkflow()
            } catch {
                errorMessage = "设备流转失败: \(error.localizedDescription)"
            }
            isBusy = false
            pendingScanAction = nil
        }
    }
    
    private func submitVoidEquipment() {
        guard let target = exceptionTargetUsage else { return }
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await ApiClient.shared.voidEquipmentUsage(planId: planId, usageId: target.id, reason: exceptionReason.isEmpty ? "误扫撤销" : exceptionReason)
                NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                await loadWorkflow()
            } catch {
                errorMessage = "撤销失败: \(error.localizedDescription)"
            }
            isBusy = false
            exceptionTargetUsage = nil
        }
    }
    
    private func finishWorkflow(createPackage: Bool) {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await ApiClient.shared.transitionPlan(id: planId, status: 2, createPackage: createPackage)
                NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                await loadWorkflow()
            } catch {
                errorMessage = "完成加工失败: \(error.localizedDescription)"
            }
            isBusy = false
        }
    }
    
    private func generatePackage() {
        guard !isBusy else { return }
        isBusy = true
        Task {
            do {
                try await ApiClient.shared.generatePlanPackage(id: planId)
                NotificationCenter.default.post(name: NSNotification.Name("ListNeedsRefresh_Processing"), object: nil)
                await loadWorkflow()
            } catch {
                errorMessage = "生成包裹失败: \(error.localizedDescription)"
            }
            isBusy = false
        }
    }
}

// MARK: - 设备被占用提示卡片 (对标 Android OccupyingPlanCard)
public struct OccupyingPlanCard: View {
    public let equipmentName: String
    public let equipmentNo: String
    public let planCode: String
    public let patientName: String
    public let onClick: () -> Void
    
    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text("设备占用计划")
                            .scaledFont(11, weight: .bold)
                            .foregroundStyle(Color.blue)
                        let equipLabel = equipmentName.isEmpty ? equipmentNo : "\(equipmentName) (\(equipmentNo))"
                        Text(equipLabel)
                            .scaledFont(11, weight: .medium)
                            .foregroundStyle(Color.blue)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(Color.blue.opacity(0.12))
                            .clipShape(.rect(cornerRadius: 4))
                    }
                    Text("\(patientName) · \(planCode)")
                        .scaledFont(13, weight: .bold)
                        .foregroundStyle(Color.ink)
                        .lineLimit(1)
                    Text("👉 点击直达该计划工序详情")
                        .scaledFont(11, weight: .medium)
                        .foregroundStyle(Color.appPrimary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .scaledFont(12, weight: .bold)
                    .foregroundStyle(Color.blue)
            }
            .padding(12)
            .background(Color.blue.opacity(0.08))
            .clipShape(.rect(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.blue.opacity(0.3), lineWidth: 1))
        }
    }
}
