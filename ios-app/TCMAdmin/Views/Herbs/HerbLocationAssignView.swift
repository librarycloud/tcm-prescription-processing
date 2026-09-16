import SwiftUI

@MainActor
public struct HerbLocationAssignView: View {
    public let location: HerbLocationItem?
    
    @Environment(\.dismiss) private var dismiss
    
    // 货位信息
    @State private var locationType = "D" // D: 药斗, G: 药柜, F: 冰箱, C: 仓库
    @State private var unitNo = ""
    @State private var layerNo = ""
    @State private var columnNo = ""
    @State private var slotNo = "1" // 药斗专属格内序号 (1-3)
    @State private var manualLocationCode = ""
    
    // 药材信息与搜索
    @State private var allHerbs: [HerbItem] = []
    @State private var herbKeyword = ""
    @State private var selectedHerbId: Int = 0
    @State private var herbName = ""
    @State private var herbCode = ""
    @State private var specification = ""
    @State private var editingHerbId: Int? = nil
    
    @State private var isLoading = false
    @State private var isSubmitting = false
    @State private var errorMessage: String? = nil
    @State private var successAlert = false
    
    let locationTypes = [
        ("D", "药斗"),
        ("G", "药柜"),
        ("F", "冰箱"),
        ("C", "仓库")
    ]
    
    public init(location: HerbLocationItem?) {
        self.location = location
        if let loc = location {
            _locationType = State(initialValue: loc.type ?? "D")
            _unitNo = State(initialValue: loc.unitNo != nil ? "\(loc.unitNo!)" : "")
            _layerNo = State(initialValue: loc.layerNo != nil ? "\(loc.layerNo!)" : "")
            _columnNo = State(initialValue: loc.columnNo != nil ? "\(loc.columnNo!)" : "")
            _manualLocationCode = State(initialValue: loc.code ?? "")
        }
    }
    
    private var computedLocationCode: String {
        if let loc = location, let code = loc.code, !code.isEmpty {
            return code
        }
        if !manualLocationCode.isEmpty {
            return manualLocationCode
        }
        let u = unitNo.trimmingCharacters(in: .whitespacesAndNewlines)
        let l = layerNo.trimmingCharacters(in: .whitespacesAndNewlines)
        let c = columnNo.trimmingCharacters(in: .whitespacesAndNewlines)
        if u.isEmpty || l.isEmpty { return "" }
        if locationType == "D" {
            return c.isEmpty ? "" : "D-\(u)-\(l)-\(c)"
        } else {
            return "\(locationType)-\(u)-\(l)"
        }
    }
    
    private var filteredHerbs: [HerbItem] {
        let needle = herbKeyword.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if needle.isEmpty { return [] }
        return allHerbs.filter { herb in
            herb.name.lowercased().contains(needle) ||
            (herb.code?.lowercased().contains(needle) == true) ||
            (herb.pinyin?.lowercased().contains(needle) == true) ||
            (herb.specification?.lowercased().contains(needle) == true)
        }.prefix(10).map { $0 }
    }
    
    public var body: some View {
        Form {
            // 1. 已配置药材清单 (如果已有货位)
            if let loc = location, let herbs = loc.herbs, !herbs.isEmpty {
                Section(header: Text("已配置药材 (点击编辑)")) {
                    ForEach(herbs) { herb in
                        Button(action: {
                            editingHerbId = herb.id
                            selectedHerbId = 0
                            herbName = herb.name
                            herbCode = herb.code ?? ""
                            specification = herb.specification ?? ""
                            slotNo = herb.slotNo != nil ? "\(herb.slotNo!)" : "1"
                        }) {
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(herb.name)
                                        .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                                        .foregroundColor(editingHerbId == herb.id ? .appPrimary : .ink)
                                    let details = [herb.code, herb.specification].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                                    if !details.isEmpty {
                                        Text(details)
                                            .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                            .foregroundColor(.muted)
                                    }
                                }
                                Spacer()
                                if let slot = herb.slotNo {
                                    Text("第\(slot)格")
                                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                                        .foregroundColor(.muted)
                                }
                                Image(systemName: "pencil")
                                    .foregroundColor(editingHerbId == herb.id ? .appPrimary : .muted)
                            }
                        }
                    }
                }
            }
            
            // 2. 货位位置结构 (如果新建货位则展示分类与行列输入)
            if location == nil {
                Section(header: Text("货位位置与分类")) {
                    Picker("货位类型", selection: $locationType) {
                        ForEach(locationTypes, id: \.0) { t in
                            Text(t.1).tag(t.0)
                        }
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    
                    HStack {
                        Text(locationType == "D" ? "斗号" : "编号")
                        Spacer()
                        TextField("如 1", text: $unitNo)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                    
                    HStack {
                        Text("层")
                        Spacer()
                        TextField("如 1", text: $layerNo)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                    
                    if locationType == "D" {
                        HStack {
                            Text("列")
                            Spacer()
                            TextField("如 1", text: $columnNo)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                    
                    HStack {
                        Text("自动生成编号")
                        Spacer()
                        Text(computedLocationCode.isEmpty ? "等待输入..." : computedLocationCode)
                            .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .bold))
                            .foregroundColor(computedLocationCode.isEmpty ? .muted : .appPrimary)
                    }
                }
            } else {
                Section(header: Text("货位编号")) {
                    Text(location?.code ?? "-")
                        .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .bold))
                        .foregroundColor(.ink)
                }
            }
            
            // 3. 药材匹配与搜索
            if editingHerbId == nil {
                Section(header: Text("搜索已有药材 (可选)"), footer: Text("输入名称或拼音从现有药材库选择，或直接在下方输入新药材")) {
                    TextField("输入药材名称、编码或拼音首字母", text: $herbKeyword)
                    
                    if !filteredHerbs.isEmpty {
                        ForEach(filteredHerbs) { herb in
                            Button(action: {
                                selectedHerbId = herb.id
                                herbName = herb.name
                                herbCode = herb.code ?? ""
                                specification = herb.specification ?? ""
                                herbKeyword = ""
                            }) {
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(herb.name)
                                            .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: .semibold))
                                            .foregroundColor(.ink)
                                        let details = [herb.code, herb.specification].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                                        if !details.isEmpty {
                                            Text(details)
                                                .font(.system(size: (11) * ThemeManager.shared.fontScale))
                                                .foregroundColor(.muted)
                                        }
                                    }
                                    Spacer()
                                    if selectedHerbId == herb.id {
                                        Image(systemName: "checkmark")
                                            .foregroundColor(.appPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 4. 药材详细表单
            Section(
                header: Text(editingHerbId != nil ? "修改药材信息" : (selectedHerbId > 0 ? "已选择药材" : "新增药材信息")),
                footer: Text(editingHerbId != nil ? "保存将修改该药材的基础信息" : "确认后将把药材保存并绑定到该货位")
            ) {
                if editingHerbId != nil {
                    Button(action: {
                        editingHerbId = nil
                        selectedHerbId = 0
                        herbName = ""
                        herbCode = ""
                        specification = ""
                    }) {
                        Text("取消修改，切换为新增")
                            .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .medium))
                            .foregroundColor(.appPrimary)
                    }
                }
                
                TextField("药材名称 *", text: $herbName)
                    .disabled(selectedHerbId > 0)
                TextField("药材编码 (可选)", text: $herbCode)
                    .disabled(selectedHerbId > 0)
                TextField("规格说明 (可选)", text: $specification)
                    .disabled(selectedHerbId > 0)
                
                if (location?.type == "D" || locationType == "D") && editingHerbId == nil {
                    HStack {
                        Text("格内序号 (1-3)")
                        Spacer()
                        TextField("格号 (1-3)", text: $slotNo)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }
            
            if let error = errorMessage {
                Section {
                    Text(error).foregroundColor(.danger).font(.system(size: (13) * ThemeManager.shared.fontScale))
                }
            }
            
            // 提交按钮
            Button(action: submitForm) {
                HStack {
                    Spacer()
                    if isSubmitting {
                        ProgressView().padding(.trailing, 8)
                    }
                    Text(buttonTitle).bold()
                    Spacer()
                }
                .foregroundColor(isSaveEnabled ? .appPrimary : .muted)
            }
            .disabled(!isSaveEnabled || isSubmitting)
        }
        .navigationTitle(location == nil ? "新增货位配置" : "配置货位 \(location?.code ?? "")")
        .navigationBarTitleDisplayMode(.inline)
        .alert(isPresented: $successAlert) {
            Alert(
                title: Text("保存成功"),
                message: Text("货位药材配置已成功更新"),
                dismissButton: .default(Text("确定")) {
                    dismiss()
                }
            )
        }
        .task {
            if let matrix = try? await ApiClient.shared.fetchHerbLocationMatrix() {
                self.allHerbs = matrix.herbs ?? []
            }
        }
    }
    
    private var buttonTitle: String {
        if isSubmitting { return "正在保存..." }
        if editingHerbId != nil { return "保存药材修改" }
        return "确认保存货位配置"
    }
    
    private var isSaveEnabled: Bool {
        if editingHerbId != nil {
            return !herbName.isEmpty
        }
        let code = computedLocationCode
        return !code.isEmpty && (!herbName.isEmpty || selectedHerbId > 0)
    }
    
    private func submitForm() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isSubmitting = true
        errorMessage = nil
        
        let targetCode = computedLocationCode
        
        Task {
            do {
                if let editId = editingHerbId {
                    let payload: [String: Any] = [
                        "name": herbName.trimmingCharacters(in: .whitespacesAndNewlines),
                        "code": herbCode.trimmingCharacters(in: .whitespacesAndNewlines),
                        "specification": specification.trimmingCharacters(in: .whitespacesAndNewlines)
                    ]
                    try await ApiClient.shared.updateHerb(id: editId, payload: payload)
                } else {
                    var payload: [String: Any] = [
                        "locationCode": targetCode
                    ]
                    if selectedHerbId > 0 {
                        payload["herbId"] = selectedHerbId
                    } else {
                        payload["name"] = herbName.trimmingCharacters(in: .whitespacesAndNewlines)
                        payload["code"] = herbCode.trimmingCharacters(in: .whitespacesAndNewlines)
                        payload["specification"] = specification.trimmingCharacters(in: .whitespacesAndNewlines)
                    }
                    if location?.type == "D" || locationType == "D" {
                        if let s = Int(slotNo) {
                            payload["slotNo"] = s
                        }
                    }
                    try await ApiClient.shared.assignHerbLocation(payload: payload)
                }
                
                await MainActor.run {
                    isSubmitting = false
                    successAlert = true
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isSubmitting = false
                }
            }
        }
    }
}
