import Foundation

// MARK: - 基础用户与会话模型
public struct UserItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let username: String?
    public let name: String?
    public let nickname: String?
    public let phone: String?
    public let role: Int? // 0: 全局管理员, 1: 门店管理员, 2: 门店员工
    public let storeId: Int?
    public let store: StoreItem?
    
    public var displayName: String {
        nickname?.isEmpty == false ? nickname! : (name?.isEmpty == false ? name! : (username?.isEmpty == false ? username! : (phone ?? "员工")))
    }
    
    public var roleName: String {
        switch role {
        case 0: return "全局管理员"
        case 1: return "门店管理员"
        case 2: return "门店员工"
        default: return "员工"
        }
    }
}

public struct StoreItem: Codable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let code: String?
    public let address: String?
    public let phone: String?
    public let status: Int?
}

// MARK: - 处方模型
public struct PrescriptionItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let prescriptionNo: String?
    public let customerName: String?
    public let gender: Int?
    public let age: Int?
    public let phone: String?
    public let storeId: Int?
    public let status: Int? // 0: 进行中, 1: 已完成, 2: 已取消
    public let diagnosis: String?
    public let remark: String?
    public let dose: Int?
    public let totalDose: Int?
    public let plans: [ProcessingPlanItem]?
    public let createdAt: String?
    public let herbs: [PrescriptionHerbItem]?
    public let isExternal: Bool?
    public let source: SourceNested?
    public let creator: CreatorNested?
    public let attachment: PrescriptionAttachmentNested?
    public let e6Imports: [E6ImportItem]?
    public let totalPrice: Double?
    public let sourceId: Int?
    public let doctorId: Int?
    public let externalHospital: String?
    public let externalDoctor: String?
    public let externalRemark: String?
    
    // 嵌套关系
    public let doctor: DoctorNested?
    public let store: StoreNested?
    
    enum CodingKeys: String, CodingKey {
        case id, prescriptionNo, customerName, gender, age, phone, storeId, status
        case diagnosis, remark, dose, totalDose, plans, createdAt, herbs, isExternal
        case source, creator, attachment, e6Imports, doctor, store
        case totalPrice, sourceId, doctorId, externalHospital, externalDoctor, externalRemark
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int.self, forKey: .id) ?? 0
        self.prescriptionNo = try? c.decodeIfPresent(String.self, forKey: .prescriptionNo)
        self.customerName = try? c.decodeIfPresent(String.self, forKey: .customerName)
        self.gender = try? c.decodeIfPresent(Int.self, forKey: .gender)
        self.age = try? c.decodeIfPresent(Int.self, forKey: .age)
        self.phone = try? c.decodeIfPresent(String.self, forKey: .phone)
        self.storeId = try? c.decodeIfPresent(Int.self, forKey: .storeId)
        self.status = try? c.decodeIfPresent(Int.self, forKey: .status)
        self.diagnosis = try? c.decodeIfPresent(String.self, forKey: .diagnosis)
        self.remark = try? c.decodeIfPresent(String.self, forKey: .remark)
        self.dose = try? c.decodeIfPresent(Int.self, forKey: .dose)
        self.totalDose = try? c.decodeIfPresent(Int.self, forKey: .totalDose)
        self.plans = try? c.decodeIfPresent([ProcessingPlanItem].self, forKey: .plans)
        self.createdAt = try? c.decodeIfPresent(String.self, forKey: .createdAt)
        self.herbs = try? c.decodeIfPresent([PrescriptionHerbItem].self, forKey: .herbs)
        
        // 兼容后端数据库 TinyInt (0 或 1) 与标准 Bool
        if let b = try? c.decodeIfPresent(Bool.self, forKey: .isExternal) {
            self.isExternal = b
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .isExternal) {
            self.isExternal = (i != 0)
        } else {
            self.isExternal = nil
        }
        
        self.source = try? c.decodeIfPresent(SourceNested.self, forKey: .source)
        self.creator = try? c.decodeIfPresent(CreatorNested.self, forKey: .creator)
        self.attachment = try? c.decodeIfPresent(PrescriptionAttachmentNested.self, forKey: .attachment)
        self.e6Imports = try? c.decodeIfPresent([E6ImportItem].self, forKey: .e6Imports)
        self.doctor = try? c.decodeIfPresent(DoctorNested.self, forKey: .doctor)
        self.store = try? c.decodeIfPresent(StoreNested.self, forKey: .store)

        if let d = try? c.decodeIfPresent(Double.self, forKey: .totalPrice) {
            self.totalPrice = d
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .totalPrice), let d = Double(s) {
            self.totalPrice = d
        } else {
            self.totalPrice = nil
        }
        self.sourceId = try? c.decodeIfPresent(Int.self, forKey: .sourceId)
        self.doctorId = try? c.decodeIfPresent(Int.self, forKey: .doctorId)
        self.externalHospital = try? c.decodeIfPresent(String.self, forKey: .externalHospital)
        self.externalDoctor = try? c.decodeIfPresent(String.self, forKey: .externalDoctor)
        self.externalRemark = try? c.decodeIfPresent(String.self, forKey: .externalRemark)
    }
    
    public struct DoctorNested: Codable , Equatable {
        public let id: Int?
        public let name: String?
    }
    
    public struct DoctorItem: Codable, Identifiable , Equatable {
        public let id: Int
        public let name: String
        public let phone: String?
        public let code: String?
    }
    
    public struct StoreNested: Codable , Equatable {
        public let id: Int?
        public let name: String?
    }
    
    public struct SourceNested: Codable , Equatable {
        public let id: Int?
        public let name: String?
    }
    
    public struct CreatorNested: Codable , Equatable {
        public let id: Int?
        public let username: String?
        public let nickname: String?
    }
    
    public struct PrescriptionAttachmentNested: Codable , Equatable {
        public let id: Int?
        public let originalName: String?
        public let mimeType: String?
        public let fileSize: Int64?
        public let filePath: String?
    }
    
    // 兼容原有的计算属性，供UI层调用
    public var patientName: String? { customerName }
    public var patientGender: String? { 
        if let g = gender { return g == 1 ? "男" : (g == 2 ? "女" : "未知") }
        return nil
    }
    public var patientAge: Int? { age }
    public var patientPhone: String? { phone }
    public var doctorName: String? { doctor?.name }
    public var storeName: String? { store?.name }
    public var totalAmount: Double? { nil } // 后端不直接返 totalAmount
    
    public var statusText: String {
        switch status {
        case 0: return "进行中"
        case 1: return "已完成"
        case 2: return "已取消"
        default: return "未知状态"
        }
    }
}

public struct PrescriptionHerbItem: Codable, Identifiable , Equatable {
    public var id: String { "\(name)_\(unit ?? "")_\(dosage ?? 0)" }
    public let name: String
    public let dosage: Double?
    public let unit: String?
    public let unitPrice: Double?
    public let amount: Double?
    public let usageMethod: String?

    enum CodingKeys: String, CodingKey {
        case name, dosage, unit, unitPrice, amount, usageMethod
    }

    public init(name: String, dosage: Double? = nil, unit: String? = nil, unitPrice: Double? = nil, amount: Double? = nil, usageMethod: String? = nil) {
        self.name = name
        self.dosage = dosage
        self.unit = unit
        self.unitPrice = unitPrice
        self.amount = amount
        self.usageMethod = usageMethod
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.name = (try? c.decodeIfPresent(String.self, forKey: .name)) ?? "未知药材"
        self.unit = try? c.decodeIfPresent(String.self, forKey: .unit)
        self.usageMethod = try? c.decodeIfPresent(String.self, forKey: .usageMethod)

        if let d = try? c.decodeIfPresent(Double.self, forKey: .dosage) {
            self.dosage = d
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .dosage), let d = Double(s) {
            self.dosage = d
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .dosage) {
            self.dosage = Double(i)
        } else {
            self.dosage = nil
        }

        if let p = try? c.decodeIfPresent(Double.self, forKey: .unitPrice) {
            self.unitPrice = p
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .unitPrice), let p = Double(s) {
            self.unitPrice = p
        } else {
            self.unitPrice = nil
        }

        if let a = try? c.decodeIfPresent(Double.self, forKey: .amount) {
            self.amount = a
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .amount), let a = Double(s) {
            self.amount = a
        } else {
            self.amount = nil
        }
    }
}

// MARK: - 加工计划模型
public struct ProcessingPlanItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let planCode: String
    public let status: Int? // 0:待加工, 1:加工中, 2:加工完成, 3:待领取, 4:已领取, 5:已取消
    public let createdAt: String?
    public let delayDays: Int?
    public let priority: Int?
    private let rawIsUrgent: Bool?
    public let batchNo: Int?
    public let totalDose: Int?
    public let prescriptionId: Int?
    public let plans: [ProcessingPlanItem]?
    public let bagCount: Int?
    public let volumeMl: Int?
    public let pickupMethod: Int?
    public let processDate: String?
    public let startDate: String?
    public let finishDate: String?
    public let updatedAt: String?
    public let remark: String?
    public let processRemark: String?
    
    public var isUrgent: Bool {
        if let u = rawIsUrgent { return u }
        return (priority ?? 0) > 0
    }
    
    // 嵌套结构
    public let prescription: PrescriptionNested?
    public let processType: ProcessTypeNested?
    public let package: PackageNested?
    public let store: StoreNested?
    
    enum CodingKeys: String, CodingKey {
        case id, planCode, status, createdAt, updatedAt, delayDays, priority, isUrgent, batchNo, totalDose
        case prescriptionId, plans, bagCount, volumeMl, pickupMethod, processDate, startDate, finishDate, remark, processRemark
        case prescription, processType, package, store
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int.self, forKey: .id) ?? 0
        self.planCode = (try? c.decodeIfPresent(String.self, forKey: .planCode)) ?? ""
        self.status = try? c.decodeIfPresent(Int.self, forKey: .status)
        self.createdAt = try? c.decodeIfPresent(String.self, forKey: .createdAt)
        self.updatedAt = try? c.decodeIfPresent(String.self, forKey: .updatedAt)
        self.delayDays = try? c.decodeIfPresent(Int.self, forKey: .delayDays)
        self.priority = try? c.decodeIfPresent(Int.self, forKey: .priority)
        if let u = try? c.decodeIfPresent(Bool.self, forKey: .isUrgent) {
            self.rawIsUrgent = u
        } else if let p = try? c.decodeIfPresent(Int.self, forKey: .priority) {
            self.rawIsUrgent = (p > 0)
        } else {
            self.rawIsUrgent = nil
        }
        self.batchNo = try? c.decodeIfPresent(Int.self, forKey: .batchNo)
        self.totalDose = try? c.decodeIfPresent(Int.self, forKey: .totalDose)
        self.prescriptionId = try? c.decodeIfPresent(Int.self, forKey: .prescriptionId)
        self.plans = try? c.decodeIfPresent([ProcessingPlanItem].self, forKey: .plans)
        self.bagCount = try? c.decodeIfPresent(Int.self, forKey: .bagCount)
        self.volumeMl = try? c.decodeIfPresent(Int.self, forKey: .volumeMl)
        self.pickupMethod = try? c.decodeIfPresent(Int.self, forKey: .pickupMethod)
        self.processDate = try? c.decodeIfPresent(String.self, forKey: .processDate)
        self.startDate = try? c.decodeIfPresent(String.self, forKey: .startDate)
        self.finishDate = try? c.decodeIfPresent(String.self, forKey: .finishDate)
        self.remark = try? c.decodeIfPresent(String.self, forKey: .remark)
        self.processRemark = try? c.decodeIfPresent(String.self, forKey: .processRemark)
        self.prescription = try? c.decodeIfPresent(PrescriptionNested.self, forKey: .prescription)
        self.processType = try? c.decodeIfPresent(ProcessTypeNested.self, forKey: .processType)
        self.package = try? c.decodeIfPresent(PackageNested.self, forKey: .package)
        self.store = try? c.decodeIfPresent(StoreNested.self, forKey: .store)
    }
    
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(planCode, forKey: .planCode)
        try c.encodeIfPresent(status, forKey: .status)
        try c.encodeIfPresent(createdAt, forKey: .createdAt)
        try c.encodeIfPresent(updatedAt, forKey: .updatedAt)
        try c.encodeIfPresent(delayDays, forKey: .delayDays)
        try c.encodeIfPresent(priority, forKey: .priority)
        try c.encodeIfPresent(rawIsUrgent, forKey: .isUrgent)
        try c.encodeIfPresent(batchNo, forKey: .batchNo)
        try c.encodeIfPresent(totalDose, forKey: .totalDose)
        try c.encodeIfPresent(prescriptionId, forKey: .prescriptionId)
        try c.encodeIfPresent(plans, forKey: .plans)
        try c.encodeIfPresent(bagCount, forKey: .bagCount)
        try c.encodeIfPresent(volumeMl, forKey: .volumeMl)
        try c.encodeIfPresent(pickupMethod, forKey: .pickupMethod)
        try c.encodeIfPresent(processDate, forKey: .processDate)
        try c.encodeIfPresent(startDate, forKey: .startDate)
        try c.encodeIfPresent(finishDate, forKey: .finishDate)
        try c.encodeIfPresent(remark, forKey: .remark)
        try c.encodeIfPresent(processRemark, forKey: .processRemark)
        try c.encodeIfPresent(prescription, forKey: .prescription)
        try c.encodeIfPresent(processType, forKey: .processType)
        try c.encodeIfPresent(package, forKey: .package)
        try c.encodeIfPresent(store, forKey: .store)
    }
    
    public struct PrescriptionNested: Codable , Equatable {
        public let id: Int?
        public let prescriptionNo: String?
        public let customerName: String?
        public let phone: String?
        public let doctor: DoctorNested?
    }
    
    public struct DoctorNested: Codable , Equatable {
        public let name: String?
    }
    
    public struct ProcessTypeNested: Codable , Equatable {
        public let name: String?
    }
    
    public struct PackageNested: Codable , Equatable {
        public let id: Int?
        public let code: String?
        public let status: Int?
    }
    
    public struct StoreNested: Codable , Equatable {
        public let name: String?
    }
    
    public var patientName: String? { prescription?.customerName }
    public var prescriptionNo: String? { prescription?.prescriptionNo }
    public var method: String? { processType?.name }
    public var equipmentName: String? { nil } // 设备通常挂在具体工序上
    public var steps: [ProcessingStepItem]? { nil }
    public var doctorName: String? { prescription?.doctor?.name }
    
    public var statusText: String {
        switch status {
        case 0: return "待加工"
        case 1: return "加工中"
        case 2: return "加工完成"
        case 3: return "待领取"
        case 4: return "已领取"
        case 5: return "已取消"
        default: return "未知"
        }
    }
}

public struct ProcessingStepItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let stepName: String
    public let status: Int? // 0:未开始, 1:进行中, 2:已完成
    public let userName: String?
    public let startTime: String?
    public let finishTime: String?
    public let equipmentName: String?
}

// MARK: - 加工调度统计模型
public struct ProcessingStatsModel: Codable , Equatable {
    public let waitingCount: Int?
    public let processingCount: Int?
    public let overdueCount: Int?
    public let todayFinished: Int?
    public let urgentCount: Int?
    public let waitingNoticeCount: Int?
    public let tomorrowWaitingCount: Int?
    public let processingPlanTotalCount: Int?
    public let pickupWaitingCount: Int?
    public let pickupReceivedCount: Int?
}

// MARK: - 包裹模型 (1:1 映射 Android AppModels.kt)
public struct PackageModel: Codable, Identifiable , Equatable {
    public let id: Int
    public let name: String
    public let customer: String
    public let code: String
    public let status: String
    public let time: String
    public let phone: String
    public let store: String
    public let method: String
    public let info: String
    public let statusCode: Int
    public let methodCode: Int
    public let expressTrackingNo: String
    public let pickupQrContent: String
    public let createdAt: String
    public let pickedAt: String
    public let creatorName: String
    public let verifierName: String
    
    enum CodingKeys: String, CodingKey {
        case id, name, customer, code, status, time, phone, store, method, info
        case statusCode, methodCode, expressTrackingNo, pickupQrContent, createdAt, pickedAt, creatorName, verifierName
        case itemName, receiverName, pickupCode, receiverPhone, itemInfo, pickupMethod
        case creator, verifier
    }
    
    public init(
        id: Int,
        name: String = "包裹",
        customer: String = "客户",
        code: String = "",
        status: String = "待领取",
        time: String = "",
        phone: String = "",
        store: String = "",
        method: String = "自提",
        info: String = "",
        statusCode: Int = 0,
        methodCode: Int = 0,
        expressTrackingNo: String = "",
        pickupQrContent: String = "",
        createdAt: String = "",
        pickedAt: String = "",
        creatorName: String = "",
        verifierName: String = ""
    ) {
        self.id = id
        self.name = name
        self.customer = customer
        self.code = code
        self.status = status
        self.time = time
        self.phone = phone
        self.store = store
        self.method = method
        self.info = info
        self.statusCode = statusCode
        self.methodCode = methodCode
        self.expressTrackingNo = expressTrackingNo
        self.pickupQrContent = pickupQrContent
        self.createdAt = createdAt
        self.pickedAt = pickedAt
        self.creatorName = creatorName
        self.verifierName = verifierName
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int.self, forKey: .id) ?? 0
        
        let rawItemName = try? c.decodeIfPresent(String.self, forKey: .itemName)
        let rawName = try? c.decodeIfPresent(String.self, forKey: .name)
        self.name = rawItemName ?? rawName ?? "中药代煎液"
        
        let rawReceiver = try? c.decodeIfPresent(String.self, forKey: .receiverName)
        let rawCustomer = try? c.decodeIfPresent(String.self, forKey: .customer)
        self.customer = rawReceiver ?? rawCustomer ?? "客户"
        
        let rawCode = try? c.decodeIfPresent(String.self, forKey: .pickupCode)
        let directCode = try? c.decodeIfPresent(String.self, forKey: .code)
        self.code = rawCode ?? directCode ?? ""
        
        let rawPhone = try? c.decodeIfPresent(String.self, forKey: .receiverPhone)
        let directPhone = try? c.decodeIfPresent(String.self, forKey: .phone)
        self.phone = rawPhone ?? directPhone ?? ""
        
        let rawInfo = try? c.decodeIfPresent(String.self, forKey: .itemInfo)
        let directInfo = try? c.decodeIfPresent(String.self, forKey: .info)
        self.info = rawInfo ?? directInfo ?? ""
        
        var stCode = 0
        if let s = try? c.decodeIfPresent(Int.self, forKey: .statusCode) {
            stCode = s
        } else if let s = try? c.decodeIfPresent(Int.self, forKey: .status) {
            stCode = s
        }
        self.statusCode = stCode
        
        if let directStatus = try? c.decodeIfPresent(String.self, forKey: .status) {
            self.status = directStatus
        } else {
            self.status = stCode == 1 ? "已领取" : "待领取"
        }
        
        var mCode = 0
        if let m = try? c.decodeIfPresent(Int.self, forKey: .methodCode) {
            mCode = m
        } else if let m = try? c.decodeIfPresent(Int.self, forKey: .pickupMethod) {
            mCode = m
        }
        self.methodCode = mCode
        
        if let directMethod = try? c.decodeIfPresent(String.self, forKey: .method) {
            self.method = directMethod
        } else {
            switch mCode {
            case 1: self.method = "跑腿"
            case 2: self.method = "快递"
            default: self.method = "自提"
            }
        }
        
        if let sObj = try? c.decodeIfPresent(StoreItem.self, forKey: .store) {
            self.store = sObj.name
        } else if let sStr = try? c.decodeIfPresent(String.self, forKey: .store) {
            self.store = sStr
        } else {
            self.store = ""
        }
        
        self.expressTrackingNo = (try? c.decodeIfPresent(String.self, forKey: .expressTrackingNo)) ?? ""
        self.pickupQrContent = (try? c.decodeIfPresent(String.self, forKey: .pickupQrContent)) ?? ""
        self.createdAt = (try? c.decodeIfPresent(String.self, forKey: .createdAt)) ?? ""
        let pAt = (try? c.decodeIfPresent(String.self, forKey: .pickedAt)) ?? ""
        self.pickedAt = pAt
        self.time = (try? c.decodeIfPresent(String.self, forKey: .time)) ?? (pAt.isEmpty ? "未领取" : pAt)
        
        if let creatorObj = try? c.decodeIfPresent(UserItem.self, forKey: .creator) {
            self.creatorName = creatorObj.displayName
        } else {
            self.creatorName = (try? c.decodeIfPresent(String.self, forKey: .creatorName)) ?? "-"
        }
        
        if let verifierObj = try? c.decodeIfPresent(UserItem.self, forKey: .verifier) {
            self.verifierName = verifierObj.displayName
        } else {
            self.verifierName = (try? c.decodeIfPresent(String.self, forKey: .verifierName)) ?? ""
        }
    }
    
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(customer, forKey: .customer)
        try c.encode(code, forKey: .code)
        try c.encode(status, forKey: .status)
        try c.encode(time, forKey: .time)
        try c.encode(phone, forKey: .phone)
        try c.encode(store, forKey: .store)
        try c.encode(method, forKey: .method)
        try c.encode(info, forKey: .info)
        try c.encode(statusCode, forKey: .statusCode)
        try c.encode(methodCode, forKey: .methodCode)
        try c.encode(expressTrackingNo, forKey: .expressTrackingNo)
        try c.encode(pickupQrContent, forKey: .pickupQrContent)
        try c.encode(createdAt, forKey: .createdAt)
        try c.encode(pickedAt, forKey: .pickedAt)
        try c.encode(creatorName, forKey: .creatorName)
        try c.encode(verifierName, forKey: .verifierName)
    }
    
    public var statusText: String { status }
}

// MARK: - 斗谱与货位模型
public struct HerbLocationData: Codable , Equatable {
    public let locations: [HerbLocationItem]?
    public let herbs: [HerbItem]?
}

public struct HerbLocationItem: Codable, Identifiable, Hashable {
    public let id: Int
    public let code: String?
    public let type: String?
    public let unitNo: Int?
    public let layerNo: Int?
    public let columnNo: Int?
    public let herbs: [HerbItem]?
    
    public var typeLabel: String {
        switch type {
        case "D": return "药斗"
        case "G": return "药柜"
        case "F": return "冰箱"
        case "C": return "仓库"
        default: return type ?? ""
        }
    }
}

public struct HerbItem: Codable, Identifiable, Hashable {
    public let id: Int
    public let code: String?
    public let name: String
    public let specification: String?
    public let pinyin: String?
    public let slotNo: Int?
}

// MARK: - 库存商品模型 (E6 Pharmacy Product)
public struct InventoryItem: Codable, Identifiable, Hashable {
    public let id: Int
    public let productCode: String?
    public let name: String
    public let barcode: String?
    public let specification: String?
    public let retailPrice: Double?
    public let totalQuantity: Double?
    public let unit: String?
    public let manufacturer: String?
    public let inventories: [InventoryBatch]?
    
    public var displayStock: Double { totalQuantity ?? 0.0 }
    public var displayUnit: String { unit ?? "g" }
    public var displayLocation: String {
        guard let batches = inventories, !batches.isEmpty else { return "未分配" }
        let locations = batches.compactMap { $0.locationName }.filter { !$0.isEmpty && $0 != "-" }
        if locations.isEmpty { return "未分配" }
        return Array(Set(locations)).joined(separator: ", ")
    }
    public var displayStatus: String {
        return displayStock > 100 ? "正常" : "实货少"
    }

    enum CodingKeys: String, CodingKey {
        case id, productCode, name, barcode, specification, retailPrice, totalQuantity, unit, manufacturer, inventories
    }

    public init(id: Int, productCode: String? = nil, name: String, barcode: String? = nil, specification: String? = nil, retailPrice: Double? = nil, totalQuantity: Double? = nil, unit: String? = nil, manufacturer: String? = nil, inventories: [InventoryBatch]? = nil) {
        self.id = id
        self.productCode = productCode
        self.name = name
        self.barcode = barcode
        self.specification = specification
        self.retailPrice = retailPrice
        self.totalQuantity = totalQuantity
        self.unit = unit
        self.manufacturer = manufacturer
        self.inventories = inventories
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let i = try? c.decodeIfPresent(Int.self, forKey: .id) {
            self.id = i
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let i = Int(s) {
            self.id = i
        } else {
            self.id = 0
        }
        self.productCode = try? c.decodeIfPresent(String.self, forKey: .productCode)
        self.name = (try? c.decodeIfPresent(String.self, forKey: .name)) ?? "未命名商品"
        self.barcode = try? c.decodeIfPresent(String.self, forKey: .barcode)
        self.specification = try? c.decodeIfPresent(String.self, forKey: .specification)
        self.unit = try? c.decodeIfPresent(String.self, forKey: .unit)
        self.manufacturer = try? c.decodeIfPresent(String.self, forKey: .manufacturer)
        self.inventories = try? c.decodeIfPresent([InventoryBatch].self, forKey: .inventories)

        if let p = try? c.decodeIfPresent(Double.self, forKey: .retailPrice) {
            self.retailPrice = p
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .retailPrice), let p = Double(s) {
            self.retailPrice = p
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .retailPrice) {
            self.retailPrice = Double(i)
        } else {
            self.retailPrice = nil
        }

        if let q = try? c.decodeIfPresent(Double.self, forKey: .totalQuantity) {
            self.totalQuantity = q
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .totalQuantity), let q = Double(s) {
            self.totalQuantity = q
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .totalQuantity) {
            self.totalQuantity = Double(i)
        } else {
            self.totalQuantity = nil
        }
    }
}

public struct InventoryBatch: Codable, Identifiable, Hashable {
    public let id: Int
    public let batchNo: String?
    public let locationCode: String?
    public let locationName: String?
    public let quantity: Double?
    public let productionDate: String?
    public let expiryDate: String?
    public let inboundDate: String?

    enum CodingKeys: String, CodingKey {
        case id, batchNo, locationCode, locationName, quantity, productionDate, expiryDate, inboundDate
    }

    public init(id: Int, batchNo: String? = nil, locationCode: String? = nil, locationName: String? = nil, quantity: Double? = nil, productionDate: String? = nil, expiryDate: String? = nil, inboundDate: String? = nil) {
        self.id = id
        self.batchNo = batchNo
        self.locationCode = locationCode
        self.locationName = locationName
        self.quantity = quantity
        self.productionDate = productionDate
        self.expiryDate = expiryDate
        self.inboundDate = inboundDate
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let i = try? c.decodeIfPresent(Int.self, forKey: .id) {
            self.id = i
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let i = Int(s) {
            self.id = i
        } else {
            self.id = 0
        }
        self.batchNo = try? c.decodeIfPresent(String.self, forKey: .batchNo)
        self.locationCode = try? c.decodeIfPresent(String.self, forKey: .locationCode)
        self.locationName = try? c.decodeIfPresent(String.self, forKey: .locationName)
        self.productionDate = try? c.decodeIfPresent(String.self, forKey: .productionDate)
        self.expiryDate = try? c.decodeIfPresent(String.self, forKey: .expiryDate)
        self.inboundDate = try? c.decodeIfPresent(String.self, forKey: .inboundDate)

        if let q = try? c.decodeIfPresent(Double.self, forKey: .quantity) {
            self.quantity = q
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .quantity), let q = Double(s) {
            self.quantity = q
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .quantity) {
            self.quantity = Double(i)
        } else {
            self.quantity = nil
        }
    }
}

public struct E6ImportItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let orderNo: String?
    public let externalOrderNo: String?
    public let patientName: String?
    public let customerName: String?
    public let patientGender: String?
    public let patientAge: Int?
    public let phone: String?
    public let clinicName: String?
    public let doseCount: Int?
    public let isPaid: Int?
    public let totalAmount: Double?
    public let totalPrice: Double?
    public let status: Int?
    public let createdAt: String?
    public let sourceCreatedAt: String?
    public let cashierName: String?
    public let userName: String?
    public let salespersonCode: String?
    public let remark: String?
    public let errorMessage: String?
    public let prescriptionId: Int?
    public let processingPlanId: Int?
    public let prescription: PrescriptionItem?
    public let processingPlan: ProcessingPlanItem?
    public let operatorUserMapping: E6UserMapping?
    public let salespersonUserMapping: E6UserMapping?
    public let doctorMapping: E6DoctorMapping?
    public let rawPayload: E6RawPayload?
    
    public struct E6UserMapping: Codable , Equatable {
        public let userName: String?
    }
    
    public struct E6DoctorMapping: Codable , Equatable {
        public let id: Int?
        public let doctorId: Int?
        public let doctor: DoctorNested?
        
        public struct DoctorNested: Codable, Equatable {
            public let id: Int?
            public let name: String?
        }
    }
    
    enum CodingKeys: String, CodingKey {
        case id, orderNo, externalOrderNo, patientName, customerName, patientGender, patientAge, phone, clinicName
        case doseCount, isPaid, totalAmount, totalPrice, status, createdAt, sourceCreatedAt
        case cashierName, userName, salespersonCode, remark, errorMessage, prescriptionId, processingPlanId
        case prescription, processingPlan, operatorUserMapping, doctorMapping, rawPayload
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int.self, forKey: .id) ?? 0
        self.orderNo = try? c.decodeIfPresent(String.self, forKey: .orderNo)
        self.externalOrderNo = try? c.decodeIfPresent(String.self, forKey: .externalOrderNo)
        self.patientName = try? c.decodeIfPresent(String.self, forKey: .patientName)
        self.customerName = try? c.decodeIfPresent(String.self, forKey: .customerName)
        self.patientGender = try? c.decodeIfPresent(String.self, forKey: .patientGender)
        
        if let age = try? c.decodeIfPresent(Int.self, forKey: .patientAge) {
            self.patientAge = age
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .patientAge), let age = Int(s) {
            self.patientAge = age
        } else {
            self.patientAge = nil
        }
        
        self.phone = try? c.decodeIfPresent(String.self, forKey: .phone)
        self.clinicName = try? c.decodeIfPresent(String.self, forKey: .clinicName)
        
        if let dc = try? c.decodeIfPresent(Int.self, forKey: .doseCount) {
            self.doseCount = dc
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .doseCount), let dc = Int(s) {
            self.doseCount = dc
        } else if let d = try? c.decodeIfPresent(Double.self, forKey: .doseCount) {
            self.doseCount = Int(d)
        } else {
            self.doseCount = nil
        }
        
        if let p = try? c.decodeIfPresent(Int.self, forKey: .isPaid) {
            self.isPaid = p
        } else if let b = try? c.decodeIfPresent(Bool.self, forKey: .isPaid) {
            self.isPaid = b ? 1 : 0
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .isPaid) {
            self.isPaid = (s == "1" || s.lowercased() == "true" || s.lowercased() == "paid") ? 1 : 0
        } else {
            self.isPaid = nil
        }
        
        if let d = try? c.decodeIfPresent(Double.self, forKey: .totalAmount) {
            self.totalAmount = d
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .totalAmount), let d = Double(s) {
            self.totalAmount = d
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .totalAmount) {
            self.totalAmount = Double(i)
        } else {
            self.totalAmount = nil
        }
        
        if let d = try? c.decodeIfPresent(Double.self, forKey: .totalPrice) {
            self.totalPrice = d
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .totalPrice), let d = Double(s) {
            self.totalPrice = d
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .totalPrice) {
            self.totalPrice = Double(i)
        } else {
            self.totalPrice = nil
        }
        
        if let st = try? c.decodeIfPresent(Int.self, forKey: .status) {
            self.status = st
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .status), let st = Int(s) {
            self.status = st
        } else {
            self.status = nil
        }
        
        self.createdAt = try? c.decodeIfPresent(String.self, forKey: .createdAt)
        self.sourceCreatedAt = try? c.decodeIfPresent(String.self, forKey: .sourceCreatedAt)
        self.cashierName = try? c.decodeIfPresent(String.self, forKey: .cashierName)
        self.userName = try? c.decodeIfPresent(String.self, forKey: .userName)
        self.salespersonCode = try? c.decodeIfPresent(String.self, forKey: .salespersonCode)
        self.remark = try? c.decodeIfPresent(String.self, forKey: .remark)
        self.errorMessage = try? c.decodeIfPresent(String.self, forKey: .errorMessage)
        
        if let pid = try? c.decodeIfPresent(Int.self, forKey: .prescriptionId) {
            self.prescriptionId = pid
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .prescriptionId), let pid = Int(s) {
            self.prescriptionId = pid
        } else {
            self.prescriptionId = nil
        }
        
        if let plid = try? c.decodeIfPresent(Int.self, forKey: .processingPlanId) {
            self.processingPlanId = plid
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .processingPlanId), let plid = Int(s) {
            self.processingPlanId = plid
        } else {
            self.processingPlanId = nil
        }
        
        self.prescription = try? c.decodeIfPresent(PrescriptionItem.self, forKey: .prescription)
        self.processingPlan = try? c.decodeIfPresent(ProcessingPlanItem.self, forKey: .processingPlan)
        self.operatorUserMapping = try? c.decodeIfPresent(E6UserMapping.self, forKey: .operatorUserMapping)
        self.doctorMapping = try? c.decodeIfPresent(E6DoctorMapping.self, forKey: .doctorMapping)
        
        if let direct = try? c.decodeIfPresent(E6RawPayload.self, forKey: .rawPayload) {
            self.rawPayload = direct
        } else if let jsonString = try? c.decodeIfPresent(String.self, forKey: .rawPayload),
                  let data = jsonString.data(using: .utf8),
                  let parsed = try? JSONDecoder().decode(E6RawPayload.self, from: data) {
            self.rawPayload = parsed
        } else {
            self.rawPayload = nil
        }
    }
    
    // 快捷计算属性
    public var displayOrderNo: String { externalOrderNo ?? orderNo ?? "E6-\(id)" }
    public var displayCustomer: String { customerName ?? patientName ?? "-" }
    public var displayPhone: String { phone ?? "-" }
    public var displayDate: String { sourceCreatedAt ?? createdAt ?? "-" }
    public var displayDose: Int { doseCount ?? 0 }
    public var displayPrice: Double { totalPrice ?? totalAmount ?? 0.0 }
    public var isPaidBool: Bool { isPaid == 1 }
    
    public var displayOperator: String {
        let mapped = operatorUserMapping?.userName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !mapped.isEmpty { return mapped }
        let op = userName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !op.isEmpty { return op }
        return cashierName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "-"
    }
    
    public var displaySalesperson: String {
        salespersonUserMapping?.userName ?? salespersonCode ?? "-"
    }
    
    public var statusText: String {
        switch status {
        case 0: return "待确认"
        case 1: return "待映射"
        case 2: return "导入异常"
        case 3: return "已生成处方"
        case 4: return "已驳回"
        case 5: return "已取消"
        case 6: return "数据冲突"
        case 7: return "处理中"
        default: return "待处理"
        }
    }
    
    public var canConfirm: Bool {
        let st = status ?? -1
        let noPrescription = (prescriptionId == nil || prescriptionId == 0)
        let noActivePlan = (processingPlanId == nil || processingPlanId == 0)
        return (([0, 1, 2].contains(st) && noPrescription) || ([3, 6].contains(st) && noActivePlan))
    }
    
    public var canReview: Bool {
        let st = status ?? -1
        let noPrescription = (prescriptionId == nil || prescriptionId == 0)
        return [0, 1, 2].contains(st) && noPrescription
    }
}

public struct E6RawPayload: Codable , Equatable {
    public let items: [E6RawItem]?
}

public struct E6RawItem: Codable, Identifiable , Equatable {
    public var id: String { "\(name ?? "")_\(sequence ?? 0)" }
    public let sequence: Int?
    public let name: String?
    public let doseCount: Double?
    public let quantity: Double?
    public let totalQuantity: Double?
    public let unit: String?
    
    enum CodingKeys: String, CodingKey {
        case sequence, name, doseCount, quantity, totalQuantity, unit
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.name = try? c.decodeIfPresent(String.self, forKey: .name)
        self.unit = try? c.decodeIfPresent(String.self, forKey: .unit)
        
        if let s = try? c.decodeIfPresent(Int.self, forKey: .sequence) {
            self.sequence = s
        } else if let str = try? c.decodeIfPresent(String.self, forKey: .sequence), let s = Int(str) {
            self.sequence = s
        } else {
            self.sequence = nil
        }
        
        if let d = try? c.decodeIfPresent(Double.self, forKey: .doseCount) {
            self.doseCount = d
        } else if let str = try? c.decodeIfPresent(String.self, forKey: .doseCount), let d = Double(str) {
            self.doseCount = d
        } else {
            self.doseCount = nil
        }
        
        if let d = try? c.decodeIfPresent(Double.self, forKey: .quantity) {
            self.quantity = d
        } else if let str = try? c.decodeIfPresent(String.self, forKey: .quantity), let d = Double(str) {
            self.quantity = d
        } else {
            self.quantity = nil
        }
        
        if let d = try? c.decodeIfPresent(Double.self, forKey: .totalQuantity) {
            self.totalQuantity = d
        } else if let str = try? c.decodeIfPresent(String.self, forKey: .totalQuantity), let d = Double(str) {
            self.totalQuantity = d
        } else {
            self.totalQuantity = nil
        }
    }
}

// MARK: - 盘点模型
public struct StocktakingModel: Codable, Identifiable, Equatable {
    public let id: Int
    public let checkName: String
    public let checkNo: String?
    public let checkType: Int? // 1: 日盘, 2: 月盘, 3: 抽盘
    public let status: Int? // 0: 待盘点, 1: 盘点中, 2: 盘点完成
    public let storeId: Int?
    public let store: StoreNested?
    public let totalCount: Int?
    public let diffCount: Int?
    public let checkedCount: Int?
    public let createdAt: String?
    public let createdBy: Int?
    public let items: [StocktakingItemModel]?
    public let summary: StocktakingSummaryModel?
    
    public struct StoreNested: Codable, Equatable {
        public let name: String?
    }
    
    public var storeName: String? { store?.name }
    public var creatorName: String? { nil } // 后端仅返回了 createdBy
    public var displayCheckNo: String { checkNo ?? "PD-\(id)" }
    
    public var statusText: String {
        switch status {
        case 0: return "待盘点"
        case 1: return "盘点中"
        case 2: return "盘点完成"
        default: return "未知"
        }
    }

    enum CodingKeys: String, CodingKey {
        case id, checkName, checkNo, checkType, status, storeId, store
        case totalCount, diffCount, checkedCount, createdAt, createdBy
        case items, summary
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let idVal = try? c.decodeIfPresent(Int.self, forKey: .id) {
            self.id = idVal
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let idVal = Int(s) {
            self.id = idVal
        } else {
            self.id = 0
        }
        self.checkName = (try? c.decodeIfPresent(String.self, forKey: .checkName)) ?? "盘点单"
        self.checkNo = try? c.decodeIfPresent(String.self, forKey: .checkNo)
        self.checkType = try? c.decodeIfPresent(Int.self, forKey: .checkType)
        self.status = try? c.decodeIfPresent(Int.self, forKey: .status)
        self.storeId = try? c.decodeIfPresent(Int.self, forKey: .storeId)
        self.store = try? c.decodeIfPresent(StoreNested.self, forKey: .store)
        self.totalCount = try? c.decodeIfPresent(Int.self, forKey: .totalCount)
        self.diffCount = try? c.decodeIfPresent(Int.self, forKey: .diffCount)
        self.checkedCount = try? c.decodeIfPresent(Int.self, forKey: .checkedCount)
        self.createdAt = try? c.decodeIfPresent(String.self, forKey: .createdAt)
        self.createdBy = try? c.decodeIfPresent(Int.self, forKey: .createdBy)
        self.items = try? c.decodeIfPresent([StocktakingItemModel].self, forKey: .items)
        self.summary = try? c.decodeIfPresent(StocktakingSummaryModel.self, forKey: .summary)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(checkName, forKey: .checkName)
        try c.encodeIfPresent(checkNo, forKey: .checkNo)
        try c.encodeIfPresent(checkType, forKey: .checkType)
        try c.encodeIfPresent(status, forKey: .status)
        try c.encodeIfPresent(storeId, forKey: .storeId)
        try c.encodeIfPresent(store, forKey: .store)
        try c.encodeIfPresent(totalCount, forKey: .totalCount)
        try c.encodeIfPresent(diffCount, forKey: .diffCount)
        try c.encodeIfPresent(checkedCount, forKey: .checkedCount)
        try c.encodeIfPresent(createdAt, forKey: .createdAt)
        try c.encodeIfPresent(createdBy, forKey: .createdBy)
        try c.encodeIfPresent(items, forKey: .items)
        try c.encodeIfPresent(summary, forKey: .summary)
    }
}

public struct StocktakingSummaryModel: Codable, Equatable {
    public let total: Int?
    public let counted: Int?
    public let missing: Int?
    public let pendingRecount: Int?
    public let adjustment: Int?
    public let mine: Int?
}

public struct StocktakingItemModel: Codable, Identifiable, Equatable {
    public let rawId: Int?
    public let checkId: Int?
    public let productId: Int?
    public let batchNo: String?
    public let locationCode: String?
    public let systemLocationCode: String?
    public let systemLocationName: String?
    public let countLocationCode: String?
    public let countLocationName: String?
    public let systemQty: Double?
    public let firstCountQty: Double?
    public let recountQty: Double?
    public let diffQty: Double?
    public let difference: Double?
    public let checkStatus: Int?
    public let reviewStatus: Int?
    public let product: StocktakingProductNested?
    
    public struct StocktakingProductNested: Codable, Equatable {
        public let id: Int?
        public let name: String?
        public let productCode: String?
        public let specification: String?
        public let unit: String?
        public let barcode: String?
    }
    
    public var id: Int {
        if let rawId = rawId, rawId > 0 {
            return rawId
        }
        var hasher = Hasher()
        hasher.combine(productId ?? 0)
        hasher.combine(batchNo ?? "")
        hasher.combine(displayLocation)
        let hash = abs(hasher.finalize())
        return -(hash == 0 ? 1 : hash)
    }
    
    public var itemId: Int? {
        if let r = rawId, r > 0 { return r }
        return nil
    }
    
    public var displayLocation: String {
        if let loc = countLocationName, !loc.isEmpty {
            return (countLocationCode?.isEmpty == false ? "\(countLocationCode!)-" : "") + loc
        }
        if let loc = systemLocationName, !loc.isEmpty {
            return (systemLocationCode?.isEmpty == false ? "\(systemLocationCode!)-" : "") + loc
        }
        if let loc = locationCode, !loc.isEmpty { return loc }
        return ""
    }
    
    public var productName: String { product?.name ?? "商品 #\(productId ?? 0)" }
    public var productSpec: String { product?.specification ?? "-" }
    public var productUnit: String { product?.unit ?? "" }
    
    public var displayDiff: Double {
        if let d = diffQty { return d }
        if let d = difference { return d }
        if let r = recountQty { return r - (systemQty ?? 0) }
        if let f = firstCountQty { return f - (systemQty ?? 0) }
        return 0
    }
    
    enum CodingKeys: String, CodingKey {
        case rawId = "id"
        case itemId, checkItemId
        case checkId, productId, batchNo, locationCode
        case systemLocationCode, systemLocationName
        case countLocationCode, countLocationName
        case systemQty, firstCountQty, recountQty, diffQty, difference
        case checkStatus, reviewStatus, product
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let val = try? c.decodeIfPresent(Int.self, forKey: .rawId) {
            self.rawId = val
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .rawId), let val = Int(s) {
            self.rawId = val
        } else if let val = try? c.decodeIfPresent(Int.self, forKey: .itemId) {
            self.rawId = val
        } else if let val = try? c.decodeIfPresent(Int.self, forKey: .checkItemId) {
            self.rawId = val
        } else {
            self.rawId = nil
        }
        
        self.checkId = try? c.decodeIfPresent(Int.self, forKey: .checkId)
        self.productId = try? c.decodeIfPresent(Int.self, forKey: .productId)
        self.batchNo = try? c.decodeIfPresent(String.self, forKey: .batchNo)
        self.locationCode = try? c.decodeIfPresent(String.self, forKey: .locationCode)
        self.systemLocationCode = try? c.decodeIfPresent(String.self, forKey: .systemLocationCode)
        self.systemLocationName = try? c.decodeIfPresent(String.self, forKey: .systemLocationName)
        self.countLocationCode = try? c.decodeIfPresent(String.self, forKey: .countLocationCode)
        self.countLocationName = try? c.decodeIfPresent(String.self, forKey: .countLocationName)
        
        func decodeDouble(_ key: CodingKeys) -> Double? {
            if let val = try? c.decodeIfPresent(Double.self, forKey: key) { return val }
            if let val = try? c.decodeIfPresent(Int.self, forKey: key) { return Double(val) }
            if let valStr = try? c.decodeIfPresent(String.self, forKey: key), let val = Double(valStr) { return val }
            return nil
        }
        
        self.systemQty = decodeDouble(.systemQty)
        self.firstCountQty = decodeDouble(.firstCountQty)
        self.recountQty = decodeDouble(.recountQty)
        self.diffQty = decodeDouble(.diffQty) ?? decodeDouble(.difference)
        self.difference = decodeDouble(.difference)
        self.checkStatus = try? c.decodeIfPresent(Int.self, forKey: .checkStatus)
        self.reviewStatus = try? c.decodeIfPresent(Int.self, forKey: .reviewStatus)
        self.product = try? c.decodeIfPresent(StocktakingProductNested.self, forKey: .product)
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(rawId, forKey: .rawId)
        try c.encodeIfPresent(checkId, forKey: .checkId)
        try c.encodeIfPresent(productId, forKey: .productId)
        try c.encodeIfPresent(batchNo, forKey: .batchNo)
        try c.encodeIfPresent(locationCode, forKey: .locationCode)
        try c.encodeIfPresent(systemLocationCode, forKey: .systemLocationCode)
        try c.encodeIfPresent(systemLocationName, forKey: .systemLocationName)
        try c.encodeIfPresent(countLocationCode, forKey: .countLocationCode)
        try c.encodeIfPresent(countLocationName, forKey: .countLocationName)
        try c.encodeIfPresent(systemQty, forKey: .systemQty)
        try c.encodeIfPresent(firstCountQty, forKey: .firstCountQty)
        try c.encodeIfPresent(recountQty, forKey: .recountQty)
        try c.encodeIfPresent(diffQty, forKey: .diffQty)
        try c.encodeIfPresent(difference, forKey: .difference)
        try c.encodeIfPresent(checkStatus, forKey: .checkStatus)
        try c.encodeIfPresent(reviewStatus, forKey: .reviewStatus)
        try c.encodeIfPresent(product, forKey: .product)
    }
}

public struct StocktakingCandidateModel: Codable, Identifiable, Equatable {
    public let id: Int
    public let productId: Int
    public let name: String
    public let productCode: String?
    public let specification: String?
    public let unit: String?
    public let barcode: String?
    public let batchNo: String?
    public let locationCode: String?
    public let currentStock: Double?

    enum CodingKeys: String, CodingKey {
        case id, productId, name, productCode, specification, unit, barcode, batchNo
        case locationCode, locationName, countLocationName
        case currentStock, quantity, systemQty
        case product
    }

    private struct NestedProduct: Codable {
        let id: Int?
        let name: String?
        let productCode: String?
        let specification: String?
        let unit: String?
        let barcode: String?
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let nested = try? c.decodeIfPresent(NestedProduct.self, forKey: .product)

        let pid = (try? c.decodeIfPresent(Int.self, forKey: .productId))
            ?? nested?.id
            ?? (try? c.decodeIfPresent(Int.self, forKey: .id))
            ?? 0
        self.productId = pid

        self.id = (try? c.decodeIfPresent(Int.self, forKey: .id)) ?? pid

        self.name = (try? c.decodeIfPresent(String.self, forKey: .name))
            ?? nested?.name
            ?? "未知商品"

        self.productCode = (try? c.decodeIfPresent(String.self, forKey: .productCode)) ?? nested?.productCode
        self.specification = (try? c.decodeIfPresent(String.self, forKey: .specification)) ?? nested?.specification
        self.unit = (try? c.decodeIfPresent(String.self, forKey: .unit)) ?? nested?.unit
        self.barcode = (try? c.decodeIfPresent(String.self, forKey: .barcode)) ?? nested?.barcode
        self.batchNo = try? c.decodeIfPresent(String.self, forKey: .batchNo)

        self.locationCode = (try? c.decodeIfPresent(String.self, forKey: .locationCode))
            ?? (try? c.decodeIfPresent(String.self, forKey: .locationName))
            ?? (try? c.decodeIfPresent(String.self, forKey: .countLocationName))

        if let val = try? c.decodeIfPresent(Double.self, forKey: .currentStock) {
            self.currentStock = val
        } else if let val = try? c.decodeIfPresent(Double.self, forKey: .quantity) {
            self.currentStock = val
        } else if let val = try? c.decodeIfPresent(Double.self, forKey: .systemQty) {
            self.currentStock = val
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .quantity), let val = Double(s) {
            self.currentStock = val
        } else {
            self.currentStock = nil
        }
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(productId, forKey: .productId)
        try c.encode(name, forKey: .name)
        try c.encodeIfPresent(productCode, forKey: .productCode)
        try c.encodeIfPresent(specification, forKey: .specification)
        try c.encodeIfPresent(unit, forKey: .unit)
        try c.encodeIfPresent(barcode, forKey: .barcode)
        try c.encodeIfPresent(batchNo, forKey: .batchNo)
        try c.encodeIfPresent(locationCode, forKey: .locationCode)
        try c.encodeIfPresent(currentStock, forKey: .currentStock)
    }
}

// MARK: - 差异统计与商品模型
public struct DifferenceStatsModel: Codable , Equatable {
    public let more: Int?
    public let less: Int?
    public let total: Int?
    
    public init(more: Int? = 0, less: Int? = 0, total: Int? = 0) {
        self.more = more
        self.less = less
        self.total = total
    }
}

public struct DifferenceProductModel: Codable, Identifiable , Equatable {
    public let id: Int
    public let name: String?
    public let barcode: String?
    public let productCode: String?
    public let specification: String?
    public let unit: String?
    public let preReceiptQuantity: Double?
    public let preShipmentQuantity: Double?
    public let diffQuantity: Double?
    public let remark: String?
    
    public var displayName: String { name ?? "未知商品" }
    public var displayDiff: Double { diffQuantity ?? 0.0 }

    enum CodingKeys: String, CodingKey {
        case id, name, barcode, productCode, specification, unit, preReceiptQuantity, preShipmentQuantity, diffQuantity, remark
    }

    public init(id: Int, name: String? = nil, barcode: String? = nil, productCode: String? = nil, specification: String? = nil, unit: String? = nil, preReceiptQuantity: Double? = nil, preShipmentQuantity: Double? = nil, diffQuantity: Double? = nil, remark: String? = nil) {
        self.id = id
        self.name = name
        self.barcode = barcode
        self.productCode = productCode
        self.specification = specification
        self.unit = unit
        self.preReceiptQuantity = preReceiptQuantity
        self.preShipmentQuantity = preShipmentQuantity
        self.diffQuantity = diffQuantity
        self.remark = remark
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let i = try? c.decodeIfPresent(Int.self, forKey: .id) {
            self.id = i
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let i = Int(s) {
            self.id = i
        } else {
            self.id = 0
        }
        self.name = try? c.decodeIfPresent(String.self, forKey: .name)
        self.barcode = try? c.decodeIfPresent(String.self, forKey: .barcode)
        self.productCode = try? c.decodeIfPresent(String.self, forKey: .productCode)
        self.specification = try? c.decodeIfPresent(String.self, forKey: .specification)
        self.unit = try? c.decodeIfPresent(String.self, forKey: .unit)
        self.remark = try? c.decodeIfPresent(String.self, forKey: .remark)

        func decodeDouble(_ key: CodingKeys) -> Double? {
            if let d = try? c.decodeIfPresent(Double.self, forKey: key) { return d }
            if let s = try? c.decodeIfPresent(String.self, forKey: key), let d = Double(s) { return d }
            if let i = try? c.decodeIfPresent(Int.self, forKey: key) { return Double(i) }
            return nil
        }
        self.preReceiptQuantity = decodeDouble(.preReceiptQuantity)
        self.preShipmentQuantity = decodeDouble(.preShipmentQuantity)
        self.diffQuantity = decodeDouble(.diffQuantity)
    }
}

public struct DifferenceLogModel: Codable, Identifiable , Equatable {
    public let id: Int
    public let operationNo: String?
    public let operationType: String?
    public let changeQuantity: Double?
    public let balanceAfter: Double?
    public let reason: String?
    public let businessDate: String?
    public let createdAt: String?
    public let product: DifferenceProductModel?
    public let store: StoreItem?
    public let creator: UserItem?
    
    public var operationTypeLabel: String {
        switch operationType {
        case "WRITE_OFF_RECEIPT": return "入库销账"
        case "WRITE_OFF_SHIPMENT": return "销库销账"
        case "MANUAL_ADJUST": return "手工调整"
        case "GOODS_CHECK": return "盘点调整"
        case "REGISTER_DIFFERENCE": return "差异登记"
        default: return operationType ?? "变动"
        }
    }

    enum CodingKeys: String, CodingKey {
        case id, operationNo, operationType, changeQuantity, balanceAfter, reason, businessDate, createdAt, product, store, creator
    }

    public init(id: Int, operationNo: String? = nil, operationType: String? = nil, changeQuantity: Double? = nil, balanceAfter: Double? = nil, reason: String? = nil, businessDate: String? = nil, createdAt: String? = nil, product: DifferenceProductModel? = nil, store: StoreItem? = nil, creator: UserItem? = nil) {
        self.id = id
        self.operationNo = operationNo
        self.operationType = operationType
        self.changeQuantity = changeQuantity
        self.balanceAfter = balanceAfter
        self.reason = reason
        self.businessDate = businessDate
        self.createdAt = createdAt
        self.product = product
        self.store = store
        self.creator = creator
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let i = try? c.decodeIfPresent(Int.self, forKey: .id) {
            self.id = i
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let i = Int(s) {
            self.id = i
        } else {
            self.id = 0
        }
        self.operationNo = try? c.decodeIfPresent(String.self, forKey: .operationNo)
        self.operationType = try? c.decodeIfPresent(String.self, forKey: .operationType)
        self.reason = try? c.decodeIfPresent(String.self, forKey: .reason)
        self.businessDate = try? c.decodeIfPresent(String.self, forKey: .businessDate)
        self.createdAt = try? c.decodeIfPresent(String.self, forKey: .createdAt)
        self.product = try? c.decodeIfPresent(DifferenceProductModel.self, forKey: .product)
        self.store = try? c.decodeIfPresent(StoreItem.self, forKey: .store)
        self.creator = try? c.decodeIfPresent(UserItem.self, forKey: .creator)

        if let d = try? c.decodeIfPresent(Double.self, forKey: .changeQuantity) {
            self.changeQuantity = d
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .changeQuantity), let d = Double(s) {
            self.changeQuantity = d
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .changeQuantity) {
            self.changeQuantity = Double(i)
        } else {
            self.changeQuantity = nil
        }

        if let d = try? c.decodeIfPresent(Double.self, forKey: .balanceAfter) {
            self.balanceAfter = d
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .balanceAfter), let d = Double(s) {
            self.balanceAfter = d
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .balanceAfter) {
            self.balanceAfter = Double(i)
        } else {
            self.balanceAfter = nil
        }
    }
}

// MARK: - 门店调拨模型
public struct TransferItemModel: Codable, Identifiable , Equatable {
    public let id: Int
    public let itemName: String?
    public let productName: String?
    public let specification: String?
    public let batchNo: String?
    public let quantity: Double?
    public let returnedQuantity: Double?
    public let pendingReturnQuantity: Double?
    public let remainingQuantity: Double?
    public let availableReturnQuantity: Double?
    public let unit: String?
    
    public var displayName: String {
        itemName ?? productName ?? "物资"
    }

    enum CodingKeys: String, CodingKey {
        case id, itemName, productName, specification, batchNo, quantity, returnedQuantity, pendingReturnQuantity, remainingQuantity, availableReturnQuantity, unit
    }

    public init(id: Int, itemName: String? = nil, productName: String? = nil, specification: String? = nil, batchNo: String? = nil, quantity: Double? = nil, returnedQuantity: Double? = nil, pendingReturnQuantity: Double? = nil, remainingQuantity: Double? = nil, availableReturnQuantity: Double? = nil, unit: String? = nil) {
        self.id = id
        self.itemName = itemName
        self.productName = productName
        self.specification = specification
        self.batchNo = batchNo
        self.quantity = quantity
        self.returnedQuantity = returnedQuantity
        self.pendingReturnQuantity = pendingReturnQuantity
        self.remainingQuantity = remainingQuantity
        self.availableReturnQuantity = availableReturnQuantity
        self.unit = unit
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let i = try? c.decodeIfPresent(Int.self, forKey: .id) {
            self.id = i
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let i = Int(s) {
            self.id = i
        } else {
            self.id = 0
        }
        self.itemName = try? c.decodeIfPresent(String.self, forKey: .itemName)
        self.productName = try? c.decodeIfPresent(String.self, forKey: .productName)
        self.specification = try? c.decodeIfPresent(String.self, forKey: .specification)
        self.batchNo = try? c.decodeIfPresent(String.self, forKey: .batchNo)
        self.unit = try? c.decodeIfPresent(String.self, forKey: .unit)

        func decodeDouble(_ key: CodingKeys) -> Double? {
            if let d = try? c.decodeIfPresent(Double.self, forKey: key) { return d }
            if let s = try? c.decodeIfPresent(String.self, forKey: key), let d = Double(s) { return d }
            if let i = try? c.decodeIfPresent(Int.self, forKey: key) { return Double(i) }
            return nil
        }
        self.quantity = decodeDouble(.quantity)
        self.returnedQuantity = decodeDouble(.returnedQuantity)
        self.pendingReturnQuantity = decodeDouble(.pendingReturnQuantity)
        self.remainingQuantity = decodeDouble(.remainingQuantity)
        self.availableReturnQuantity = decodeDouble(.availableReturnQuantity)
    }
}

public struct TransferReturnRecord: Codable, Identifiable , Equatable {
    public let id: Int
    public let transferItemId: Int?
    public let itemName: String?
    public let quantity: Double?
    public let returnDate: String?
    public let status: Int? // 0: 待确认, 1: 已确认
    public let `operator`: UserItem?
    public let confirmer: UserItem?
    public let createdAt: String?
    public let confirmedAt: String?
    public let remark: String?

    enum CodingKeys: String, CodingKey {
        case id, transferItemId, itemName, quantity, returnDate, status, `operator`, confirmer, createdAt, confirmedAt, remark
    }

    public init(id: Int, transferItemId: Int? = nil, itemName: String? = nil, quantity: Double? = nil, returnDate: String? = nil, status: Int? = nil, `operator`: UserItem? = nil, confirmer: UserItem? = nil, createdAt: String? = nil, confirmedAt: String? = nil, remark: String? = nil) {
        self.id = id
        self.transferItemId = transferItemId
        self.itemName = itemName
        self.quantity = quantity
        self.returnDate = returnDate
        self.status = status
        self.`operator` = `operator`
        self.confirmer = confirmer
        self.createdAt = createdAt
        self.confirmedAt = confirmedAt
        self.remark = remark
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let i = try? c.decodeIfPresent(Int.self, forKey: .id) {
            self.id = i
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let i = Int(s) {
            self.id = i
        } else {
            self.id = 0
        }
        self.transferItemId = try? c.decodeIfPresent(Int.self, forKey: .transferItemId)
        self.itemName = try? c.decodeIfPresent(String.self, forKey: .itemName)
        self.returnDate = try? c.decodeIfPresent(String.self, forKey: .returnDate)
        self.status = try? c.decodeIfPresent(Int.self, forKey: .status)
        self.`operator` = try? c.decodeIfPresent(UserItem.self, forKey: .operator)
        self.confirmer = try? c.decodeIfPresent(UserItem.self, forKey: .confirmer)
        self.createdAt = try? c.decodeIfPresent(String.self, forKey: .createdAt)
        self.confirmedAt = try? c.decodeIfPresent(String.self, forKey: .confirmedAt)
        self.remark = try? c.decodeIfPresent(String.self, forKey: .remark)

        if let d = try? c.decodeIfPresent(Double.self, forKey: .quantity) {
            self.quantity = d
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .quantity), let d = Double(s) {
            self.quantity = d
        } else if let i = try? c.decodeIfPresent(Int.self, forKey: .quantity) {
            self.quantity = Double(i)
        } else {
            self.quantity = nil
        }
    }
}

public struct TransferPermissions: Codable , Equatable {
    public let canUpdate: Bool?
    public let canConfirmOutbound: Bool?
    public let canSubmitReturn: Bool?
    public let canConfirmReturn: Bool?
    public let canCancel: Bool?
}

public struct TransferModel: Codable, Identifiable , Equatable {
    public let id: Int
    public let transferNo: String
    public let sourceStore: StoreItem?
    public let targetStore: StoreItem?
    public let fromStore: StoreItem?
    public let toStore: StoreItem?
    public let status: Int? // 0: 借出中, 1: 部分归还, 2: 已调平, 3: 已取消
    public let outboundStatus: Int? // 0: 待出库, 1: 已确认出库
    public let itemsCount: Int?
    public let transferDate: String?
    public let expectedReturnDate: String?
    public let overdue: Bool?
    public let remark: String?
    public let createdAt: String?
    public let creator: UserItem?
    public let outboundConfirmer: UserItem?
    public let outboundConfirmedAt: String?
    public let items: [TransferItemModel]?
    public let returnRecords: [TransferReturnRecord]?
    public let permissions: TransferPermissions?
    
    // UI用的兼容字段
    public var sourceStoreName: String? { fromStore?.name ?? sourceStore?.name }
    public var targetStoreName: String? { toStore?.name ?? targetStore?.name }
    
    public var itemsDisplay: String {
        guard let validItems = items, !validItems.isEmpty else { return "-" }
        let names = validItems.map { $0.displayName }.filter { !$0.isEmpty }
        if names.isEmpty { return "-" }
        if names.count > 2 {
            return "\(names.prefix(2).joined(separator: "、")) 等\(names.count)项"
        }
        return names.joined(separator: "、")
    }
    
    public var displaySourceStore: String {
        fromStore?.name ?? sourceStore?.name ?? "调出门店"
    }
    
    public var displayTargetStore: String {
        toStore?.name ?? targetStore?.name ?? "调入门店"
    }
    
    public var statusText: String {
        if overdue == true { return "已逾期" }
        if status == 3 { return "已取消" }
        if status == 2 { return "已调平" }
        if status == 1 { return "部分归还" }
        if outboundStatus == 0 { return "待出库" }
        return "借出中"
    }

    enum CodingKeys: String, CodingKey {
        case id, transferNo, sourceStore, targetStore, fromStore, toStore, status, outboundStatus, itemsCount
        case transferDate, expectedReturnDate, overdue, remark, createdAt, creator, outboundConfirmer, outboundConfirmedAt, items, returnRecords, permissions
    }

    public init(id: Int, transferNo: String, sourceStore: StoreItem? = nil, targetStore: StoreItem? = nil, fromStore: StoreItem? = nil, toStore: StoreItem? = nil, status: Int? = nil, outboundStatus: Int? = nil, itemsCount: Int? = nil, transferDate: String? = nil, expectedReturnDate: String? = nil, overdue: Bool? = nil, remark: String? = nil, createdAt: String? = nil, creator: UserItem? = nil, outboundConfirmer: UserItem? = nil, outboundConfirmedAt: String? = nil, items: [TransferItemModel]? = nil, returnRecords: [TransferReturnRecord]? = nil, permissions: TransferPermissions? = nil) {
        self.id = id
        self.transferNo = transferNo
        self.sourceStore = sourceStore
        self.targetStore = targetStore
        self.fromStore = fromStore
        self.toStore = toStore
        self.status = status
        self.outboundStatus = outboundStatus
        self.itemsCount = itemsCount
        self.transferDate = transferDate
        self.expectedReturnDate = expectedReturnDate
        self.overdue = overdue
        self.remark = remark
        self.createdAt = createdAt
        self.creator = creator
        self.outboundConfirmer = outboundConfirmer
        self.outboundConfirmedAt = outboundConfirmedAt
        self.items = items
        self.returnRecords = returnRecords
        self.permissions = permissions
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let resolvedId: Int
        if let i = try? c.decodeIfPresent(Int.self, forKey: .id) {
            resolvedId = i
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let i = Int(s) {
            resolvedId = i
        } else {
            resolvedId = 0
        }
        self.id = resolvedId
        self.transferNo = (try? c.decodeIfPresent(String.self, forKey: .transferNo)) ?? "TR-\(resolvedId)"
        self.sourceStore = try? c.decodeIfPresent(StoreItem.self, forKey: .sourceStore)
        self.targetStore = try? c.decodeIfPresent(StoreItem.self, forKey: .targetStore)
        self.fromStore = try? c.decodeIfPresent(StoreItem.self, forKey: .fromStore)
        self.toStore = try? c.decodeIfPresent(StoreItem.self, forKey: .toStore)
        self.status = try? c.decodeIfPresent(Int.self, forKey: .status)
        self.outboundStatus = try? c.decodeIfPresent(Int.self, forKey: .outboundStatus)
        self.itemsCount = try? c.decodeIfPresent(Int.self, forKey: .itemsCount)
        self.transferDate = try? c.decodeIfPresent(String.self, forKey: .transferDate)
        self.expectedReturnDate = try? c.decodeIfPresent(String.self, forKey: .expectedReturnDate)
        self.overdue = try? c.decodeIfPresent(Bool.self, forKey: .overdue)
        self.remark = try? c.decodeIfPresent(String.self, forKey: .remark)
        self.createdAt = try? c.decodeIfPresent(String.self, forKey: .createdAt)
        self.creator = try? c.decodeIfPresent(UserItem.self, forKey: .creator)
        self.outboundConfirmer = try? c.decodeIfPresent(UserItem.self, forKey: .outboundConfirmer)
        self.outboundConfirmedAt = try? c.decodeIfPresent(String.self, forKey: .outboundConfirmedAt)
        self.items = try? c.decodeIfPresent([TransferItemModel].self, forKey: .items)
        self.returnRecords = try? c.decodeIfPresent([TransferReturnRecord].self, forKey: .returnRecords)
        self.permissions = try? c.decodeIfPresent(TransferPermissions.self, forKey: .permissions)
    }
}

// MARK: - 设备模型
public struct EquipmentModel: Codable, Identifiable , Equatable {
    public let id: Int
    public let name: String
    public let typeName: String?
    public let equipmentNo: String?
    public let status: Int? // 1: 正常空闲, 2: 维护中, 0: 已停用
    public let currentUsage: EquipmentUsageModel?
}

public struct EquipmentUsageModel: Codable , Equatable {
    public let id: Int?
    public let processingPlanId: Int?
    public let planCode: String?
    public let patientName: String?
}

// MARK: - 基础字典模型
public struct DoctorItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let name: String
    public let phone: String?
    public let title: String?
    public let status: Int?
    
    public init(id: Int, name: String, phone: String? = nil, title: String? = nil, status: Int? = nil) {
        self.id = id
        self.name = name
        self.phone = phone
        self.title = title
        self.status = status
    }
}

// MARK: - 工序工作流模型
public struct ProcessTypeItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let name: String
    public let code: String?
}

public struct ProcessingPhotoItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let createdAt: String?
}

public struct WorkflowExceptionItem: Codable, Identifiable , Equatable {
    public let id: Int?
    public let type: Int?
    public let reason: String?
    public let operatorUser: UserItem?
    public let createdAt: String?
    
    enum CodingKeys: String, CodingKey {
        case id, type, reason, createdAt
        case operatorUser = "operator"
        case creator
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try? c.decodeIfPresent(Int.self, forKey: .id)
        self.type = try? c.decodeIfPresent(Int.self, forKey: .type)
        self.reason = try? c.decodeIfPresent(String.self, forKey: .reason)
        self.createdAt = try? c.decodeIfPresent(String.self, forKey: .createdAt)
        let op = try? c.decodeIfPresent(UserItem.self, forKey: .operatorUser)
        let cr = try? c.decodeIfPresent(UserItem.self, forKey: .creator)
        self.operatorUser = op ?? cr
    }
    
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(id, forKey: .id)
        try c.encodeIfPresent(type, forKey: .type)
        try c.encodeIfPresent(reason, forKey: .reason)
        try c.encodeIfPresent(createdAt, forKey: .createdAt)
        try c.encodeIfPresent(operatorUser, forKey: .operatorUser)
    }
}

public struct WorkflowEquipmentUsageItem: Codable, Identifiable , Equatable {
    public let id: Int
    public let stage: Int? // 3: 浸泡, 4: 煎煮, 5: 打包
    public let status: Int? // 1: 进行中, 2: 已完成, 3: 已作废
    public let portionNo: Int?
    public let equipment: EquipmentModel?
    public let operatorUser: UserItem?
    public let startedAt: String?
    public let endedAt: String?
    public let finishedAt: String?
    public let voidReason: String?
    
    enum CodingKeys: String, CodingKey {
        case id, stage, status, portionNo, equipment, startedAt, endedAt, finishedAt, voidReason
        case operatorUser = "operator"
        case creator
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decodeIfPresent(Int.self, forKey: .id) ?? 0
        self.stage = try? c.decodeIfPresent(Int.self, forKey: .stage)
        self.status = try? c.decodeIfPresent(Int.self, forKey: .status)
        self.portionNo = try? c.decodeIfPresent(Int.self, forKey: .portionNo)
        self.equipment = try? c.decodeIfPresent(EquipmentModel.self, forKey: .equipment)
        let op = try? c.decodeIfPresent(UserItem.self, forKey: .operatorUser)
        let cr = try? c.decodeIfPresent(UserItem.self, forKey: .creator)
        self.operatorUser = op ?? cr
        self.startedAt = try? c.decodeIfPresent(String.self, forKey: .startedAt)
        self.endedAt = try? c.decodeIfPresent(String.self, forKey: .endedAt)
        self.finishedAt = try? c.decodeIfPresent(String.self, forKey: .finishedAt)
        self.voidReason = try? c.decodeIfPresent(String.self, forKey: .voidReason)
    }
    
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encodeIfPresent(stage, forKey: .stage)
        try c.encodeIfPresent(status, forKey: .status)
        try c.encodeIfPresent(portionNo, forKey: .portionNo)
        try c.encodeIfPresent(equipment, forKey: .equipment)
        try c.encodeIfPresent(operatorUser, forKey: .operatorUser)
        try c.encodeIfPresent(startedAt, forKey: .startedAt)
        try c.encodeIfPresent(endedAt, forKey: .endedAt)
        try c.encodeIfPresent(finishedAt, forKey: .finishedAt)
        try c.encodeIfPresent(voidReason, forKey: .voidReason)
    }
}

public struct WorkflowDetailModel: Codable , Equatable {
    public let id: Int?
    public let planCode: String?
    public let customerName: String?
    public let customerPhone: String?
    public let batchNo: Int?
    public let totalDose: Int?
    public let plans: [ProcessingPlanItem]?
    public let bagCount: Int?
    public let volumeMl: Int?
    public let status: Int?
    public let currentStage: Int?
    public let isDecoction: Bool?
    public let canCompleteWorkflow: Bool?
    public let canFinalizeWorkflow: Bool?
    public let packageCreated: Bool?
    public let pickupCode: String?
    public let pickupMethod: Int?
    public let startDate: String?
    public let processDate: String?
    public let dispensingCompletedAt: String?
    public let prescription: PrescriptionItem?
    public let processType: ProcessTypeItem?
    public let package: PackageModel?
    public let equipmentUsages: [WorkflowEquipmentUsageItem]?
    public let photos: [ProcessingPhotoItem]?
    public let workflowExceptions: [WorkflowExceptionItem]?
    
    enum CodingKeys: String, CodingKey {
        case id, planCode, customerName, customerPhone, batchNo, totalDose, plans
        case bagCount, volumeMl, status, currentStage, isDecoction
        case canCompleteWorkflow, canFinalizeWorkflow, packageCreated, pickupCode, pickupMethod
        case startDate, processDate, dispensingCompletedAt, prescription, processType, package
        case equipmentUsages, photos, workflowExceptions
    }
    
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try? c.decodeIfPresent(Int.self, forKey: .id)
        self.planCode = try? c.decodeIfPresent(String.self, forKey: .planCode)
        self.customerName = try? c.decodeIfPresent(String.self, forKey: .customerName)
        self.customerPhone = try? c.decodeIfPresent(String.self, forKey: .customerPhone)
        self.batchNo = try? c.decodeIfPresent(Int.self, forKey: .batchNo)
        self.totalDose = try? c.decodeIfPresent(Int.self, forKey: .totalDose)
        self.plans = try? c.decodeIfPresent([ProcessingPlanItem].self, forKey: .plans)
        self.bagCount = try? c.decodeIfPresent(Int.self, forKey: .bagCount)
        self.volumeMl = try? c.decodeIfPresent(Int.self, forKey: .volumeMl)
        self.status = try? c.decodeIfPresent(Int.self, forKey: .status)
        self.currentStage = try? c.decodeIfPresent(Int.self, forKey: .currentStage)
        self.isDecoction = try? c.decodeIfPresent(Bool.self, forKey: .isDecoction)
        self.canCompleteWorkflow = try? c.decodeIfPresent(Bool.self, forKey: .canCompleteWorkflow)
        self.canFinalizeWorkflow = try? c.decodeIfPresent(Bool.self, forKey: .canFinalizeWorkflow)
        self.packageCreated = try? c.decodeIfPresent(Bool.self, forKey: .packageCreated)
        self.pickupCode = try? c.decodeIfPresent(String.self, forKey: .pickupCode)
        self.pickupMethod = try? c.decodeIfPresent(Int.self, forKey: .pickupMethod)
        self.startDate = try? c.decodeIfPresent(String.self, forKey: .startDate)
        self.processDate = try? c.decodeIfPresent(String.self, forKey: .processDate)
        self.dispensingCompletedAt = try? c.decodeIfPresent(String.self, forKey: .dispensingCompletedAt)
        self.prescription = try? c.decodeIfPresent(PrescriptionItem.self, forKey: .prescription)
        self.processType = try? c.decodeIfPresent(ProcessTypeItem.self, forKey: .processType)
        self.package = try? c.decodeIfPresent(PackageModel.self, forKey: .package)
        self.equipmentUsages = try? c.decodeIfPresent([WorkflowEquipmentUsageItem].self, forKey: .equipmentUsages)
        self.photos = try? c.decodeIfPresent([ProcessingPhotoItem].self, forKey: .photos)
        self.workflowExceptions = try? c.decodeIfPresent([WorkflowExceptionItem].self, forKey: .workflowExceptions)
    }
}

// MARK: - 基础字典项模型
public struct DictionaryItem: Codable, Identifiable, Equatable {
    public let id: Int
    public let name: String
    public let code: String?
    public let type: String?

    enum CodingKeys: String, CodingKey {
        case id, name, label, code, value, type
    }

    public init(id: Int, name: String, code: String? = nil, type: String? = nil) {
        self.id = id
        self.name = name
        self.code = code
        self.type = type
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        if let i = try? c.decodeIfPresent(Int.self, forKey: .id) {
            self.id = i
        } else if let s = try? c.decodeIfPresent(String.self, forKey: .id), let i = Int(s) {
            self.id = i
        } else {
            self.id = 0
        }
        self.name = (try? c.decodeIfPresent(String.self, forKey: .name))
            ?? (try? c.decodeIfPresent(String.self, forKey: .label))
            ?? ""
        self.code = (try? c.decodeIfPresent(String.self, forKey: .code))
            ?? (try? c.decodeIfPresent(String.self, forKey: .value))
        self.type = try? c.decodeIfPresent(String.self, forKey: .type)
    }
    
    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(name, forKey: .name)
        try container.encodeIfPresent(code, forKey: .code)
        try container.encodeIfPresent(type, forKey: .type)
    }
}

extension String {
    public var formattedPickupCode: String {
        guard self.count == 6 else { return self }
        let idx = self.index(self.startIndex, offsetBy: 3)
        return "\(self[..<idx])-\(self[idx...])"
    }
}
