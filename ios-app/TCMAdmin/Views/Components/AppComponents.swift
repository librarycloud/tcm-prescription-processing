import SwiftUI
import UIKit

// MARK: - 1. 通用标准卡片 (AppCard)
/// 对应 Android 中的 AppCard，带有标准圆角、阴影和浅灰色描边
struct AppCard<Content: View>: View {
    var padding: CGFloat = 12
    var onClick: (() -> Void)? = nil
    @ViewBuilder let content: () -> Content
    
    var body: some View {
        Group {
            if let action = onClick {
                Button(action: action) {
                    cardBody
                }
                .buttonStyle(PlainButtonStyle())
            } else {
                cardBody
            }
        }
    }
    
    private var cardBody: some View {
        VStack(alignment: .leading, spacing: 0) {
            content()
        }
        .padding(padding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.surface)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.cardBorder.opacity(0.5), lineWidth: 0.5)
        )
        .shadow(color: Color.black.opacity(0.05), radius: 8, x: 0, y: 3)
    }
}

// MARK: - 2. 状态标签 (StatusPill)
/// 对应 Android 中的 StatusPill，根据文字自动适配对应的颜色组合
struct StatusPill: View {
    let text: String
    
    var body: some View {
        let style = getStyle(for: text)
        
        Text(text)
            .font(.system(size: (11) * ThemeManager.shared.fontScale, weight: .medium))
            .foregroundColor(style.text)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(style.bg)
            .cornerRadius(4)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(style.text.opacity(0.35), lineWidth: 0.5)
            )
    }
    
    // 颜色匹配逻辑，完美还原 Android 的 when(text) 逻辑
    private func getStyle(for text: String) -> (text: Color, bg: Color) {
        switch text {
        case "加工完成", "已领取", "已完成", "已调平", "正常", "盘点完成", "已核销", "已付款":
            return (.success, .successSoft)
        case "自提":
            return (.appPrimary, .appPrimarySoft)
        case "跑腿", "快递":
            // 简单起见，这里借用 Primary / 也可以自定义 RunnerColor
            return (.purple, .purple.opacity(0.15))
        case "待加工", "待盘点":
            return (.blue, .blue.opacity(0.15))
        case "盘点中", "实货少", "已取消", "逾期", "已逾期", "紧急", "特急", "加急":
            return (.danger, .dangerSoft)
        case "待出库", "借出中", "未付款":
            return (.orange, .orange.opacity(0.15))
        case "全局管理员", "门店管理员", "门店员工", "管理员":
            return (.appPrimaryDark, .appPrimarySoft)
        case "加工中", "实货多", "进行中":
            return (.appPrimary, .appPrimarySoft)
        default:
            return (.warning, .warningSoft)
        }
    }
}

// MARK: - 3. 扫码与搜索框 (SearchBarField)
/// 对应 Android 中的 SearchBarField，支持左侧扫码按钮、一键清除
struct SearchBarField: View {
    @Binding var text: String
    var placeholder: String
    var onSearch: (() -> Void)?
    var onScan: (() -> Void)?
    
    init(
        text: Binding<String>,
        placeholder: String = "搜索...",
        onSearch: (() -> Void)? = nil,
        onScan: (() -> Void)? = nil
    ) {
        self._text = text
        self.placeholder = placeholder
        self.onSearch = onSearch
        self.onScan = onScan
    }
    
    var body: some View {
        HStack(spacing: 8) {
            if let onScan = onScan {
                Button(action: onScan) {
                    Image(systemName: "qrcode.viewfinder")
                        .foregroundColor(.appPrimary)
                        .font(.system(size: (20) * ThemeManager.shared.fontScale))
                }
                .frame(width: 36, height: 36)
            } else {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.muted)
                    .padding(.leading, 8)
            }
            
            TextField(placeholder, text: $text, onCommit: { onSearch?() })
                .font(.system(size: (15) * ThemeManager.shared.fontScale))
                .foregroundColor(.ink)
                .submitLabel(.search)
            
            if !text.isEmpty {
                Button(action: {
                    text = ""
                    // Haptic feedback (可选)
                    let generator = UIImpactFeedbackGenerator(style: .light)
                    generator.impactOccurred()
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.muted)
                        .font(.system(size: (16) * ThemeManager.shared.fontScale))
                }
                .frame(width: 28, height: 28)
            }
            
            Button(action: { onSearch?() }) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.appPrimary)
                    .font(.system(size: (20) * ThemeManager.shared.fontScale))
            }
            .frame(width: 36, height: 36)
        }
        .padding(.horizontal, 4)
        .frame(height: 38)
        .background(Color.surface)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.cardBorder, lineWidth: 1)
        )
    }
}

// MARK: - 4. 胶囊筛选按钮 (SegmentedButton)
/// 对应 Android 中的 SegmentedButton，常用于门店或状态的切换
struct SegmentedButton: View {
    var label: String
    var isSelected: Bool
    var action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: isSelected ? .semibold : .regular))
                .foregroundColor(isSelected ? .white : .ink)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isSelected ? Color.appPrimary : Color.surface)
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(isSelected ? Color.clear : Color.cardBorder, lineWidth: 1)
                )
                .shadow(color: isSelected ? Color.black.opacity(0.15) : Color.black.opacity(0.02), radius: 1, x: 0, y: 1)
        }
    }
}

// MARK: - 5. 信息展示行 (InfoRowItem)
/// 对应 Android 中的 InfoRowItem
struct InfoRowItem: View {
    var label: String
    var value: String
    var valueColor: Color = .ink
    var isBold: Bool = false
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(label)
                .font(.system(size: (14) * ThemeManager.shared.fontScale))
                .foregroundColor(.muted)
            Spacer()
            Text(value)
                .font(.system(size: (14) * ThemeManager.shared.fontScale, weight: isBold ? .semibold : .regular))
                .foregroundColor(valueColor)
                .multilineTextAlignment(.trailing)
        }
    }
}

// MARK: - 6. 加急标签 (UrgentBadge)
/// 对应 Android 中的 UrgentBadge
struct UrgentBadge: View {
    var text: String = "加急"
    
    var body: some View {
        Text(text)
            .font(.system(size: (11) * ThemeManager.shared.fontScale, weight: .bold))
            .foregroundColor(.danger)
            .padding(.horizontal, 6)
            .padding(.vertical, 2.5)
            .background(Color.dangerSoft)
            .cornerRadius(4)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(Color.danger.opacity(0.4), lineWidth: 0.5)
            )
    }
}

// MARK: - Preview 展示面板
struct AppComponents_Previews: PreviewProvider {
    static var previews: some View {
        ZStack {
            Color.pageBackground.ignoresSafeArea()
            
            ScrollView {
                VStack(spacing: 20) {
                    // 1. SearchBar
                    SearchBarField(text: .constant(""), placeholder: "输入拼音/名称或扫码", onSearch: {}, onScan: {})
                    
                    // 2. SegmentedButtons
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            SegmentedButton(label: "全部门店", isSelected: true, action: {})
                            SegmentedButton(label: "高新店", isSelected: false, action: {})
                            SegmentedButton(label: "曲江店", isSelected: false, action: {})
                        }
                    }
                    
                    // 3. Card & Pills
                    AppCard {
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text("陈皮 (特级)").font(.headline)
                                Spacer()
                                StatusPill(text: "正常")
                            }
                            
                            HStack {
                                StatusPill(text: "待加工")
                                StatusPill(text: "加急")
                                StatusPill(text: "自提")
                            }
                            
                            Text("库存总量: 105.5 g").font(.subheadline).foregroundColor(.muted)
                        }
                    }
                }
                .padding()
            }
        }
    }
}

// MARK: - 9. 设置/个人信息行 (ProfileRow)
public struct ProfileRow: View {
    var icon: String
    var title: String
    var value: String? = nil
    var action: () -> Void
    
    public init(icon: String, title: String, value: String? = nil, action: @escaping () -> Void) {
        self.icon = icon
        self.title = title
        self.value = value
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: (16) * ThemeManager.shared.fontScale))
                    .foregroundColor(.appPrimary)
                    .frame(width: 24, height: 24)
                
                Text(title)
                    .font(.system(size: (15) * ThemeManager.shared.fontScale, weight: .medium))
                    .foregroundColor(.ink)
                
                Spacer()
                
                if let value = value {
                    Text(value)
                        .font(.system(size: (14) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                }
                
                Image(systemName: "chevron.right")
                    .font(.system(size: (13) * ThemeManager.shared.fontScale, weight: .semibold))
                    .foregroundColor(.muted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 10. 图片选择与相机调用 (ImagePickerView)
public struct ImagePickerView: UIViewControllerRepresentable {
    public var sourceType: UIImagePickerController.SourceType = .camera
    public var onImagePicked: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss
    
    public init(sourceType: UIImagePickerController.SourceType = .camera, onImagePicked: @escaping (UIImage) -> Void) {
        self.sourceType = sourceType
        self.onImagePicked = onImagePicked
    }
    
    public func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        if UIImagePickerController.isSourceTypeAvailable(sourceType) {
            picker.sourceType = sourceType
        } else {
            picker.sourceType = .photoLibrary
        }
        picker.delegate = context.coordinator
        return picker
    }
    
    public func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    public class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: ImagePickerView
        
        init(_ parent: ImagePickerView) {
            self.parent = parent
        }
        
        public func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.onImagePicked(image)
            }
            parent.dismiss()
        }
        
        public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

// MARK: - SectionHeader
struct SectionHeader<ActionView: View>: View {
    let title: String
    let subtitle: String?
    let action: ActionView?

    init(title: String, subtitle: String? = nil, @ViewBuilder action: () -> ActionView) {
        self.title = title
        self.subtitle = subtitle
        self.action = action()
    }
    
    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            RoundedRectangle(cornerRadius: 2)
                .fill(Color.appPrimary)
                .frame(width: 3.5, height: 15)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: (16) * ThemeManager.shared.fontScale, weight: .bold))
                    .foregroundColor(.ink)
                
                if let sub = subtitle, !sub.isEmpty {
                    Text(sub)
                        .font(.system(size: (12) * ThemeManager.shared.fontScale))
                        .foregroundColor(.muted)
                }
            }
            
            Spacer()
            
            if let action = action {
                action
            }
        }
    }
}

extension SectionHeader where ActionView == EmptyView {
    init(title: String, subtitle: String? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.action = nil
    }
}

// MARK: - HighlightedText
struct HighlightedText: View, Equatable {
    static func == (lhs: HighlightedText, rhs: HighlightedText) -> Bool {
        return lhs.text == rhs.text && lhs.keyword == rhs.keyword && lhs.regularColor == rhs.regularColor && lhs.highlightColor == rhs.highlightColor
    }
    let text: String
    let keyword: String
    let font: Font
    let regularColor: Color
    let highlightColor: Color
    let weight: Font.Weight
    
    init(
        text: String,
        keyword: String,
        font: Font = .system(size: 14),
        regularColor: Color = .ink,
        highlightColor: Color = .appPrimary,
        weight: Font.Weight = .regular
    ) {
        self.text = text
        self.keyword = keyword
        self.font = font
        self.regularColor = regularColor
        self.highlightColor = highlightColor
        self.weight = weight
    }
    
    var body: some View {
        let trimmed = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || !text.localizedCaseInsensitiveContains(trimmed) {
            Text(text).font(font).fontWeight(weight).foregroundColor(regularColor)
        } else {
            buildHighlightedText(for: trimmed)
        }
    }
    
    private func buildHighlightedText(for trimmedKey: String) -> Text {
        var result = Text("")
        let lowerText = text.lowercased()
        let lowerKey = trimmedKey.lowercased()
        
        var currentIndex = text.startIndex
        var searchStartIndex = lowerText.startIndex
        
        while let range = lowerText.range(of: lowerKey, range: searchStartIndex..<lowerText.endIndex) {
            let beforeText = String(text[currentIndex..<range.lowerBound])
            if !beforeText.isEmpty {
                result = Text("\(result)\(Text(beforeText).font(font).fontWeight(weight).foregroundColor(regularColor))")
            }
            
            let matchedText = String(text[range])
            result = Text("\(result)\(Text(matchedText).font(font).fontWeight(.bold).foregroundColor(highlightColor))")
            
            currentIndex = range.upperBound
            searchStartIndex = range.upperBound
        }
        
        let remainingText = String(text[currentIndex..<text.endIndex])
        if !remainingText.isEmpty {
            result = Text("\(result)\(Text(remainingText).font(font).fontWeight(weight).foregroundColor(regularColor))")
        }
        
        return result
    }
}

// MARK: - 日期时间格式化缓存器
private let _isoFractionalFormatter: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f
}()

private let _isoStandardFormatter: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime]
    return f
}()

private let _fallbackUtcFormatters: [DateFormatter] = [
    "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
    "yyyy-MM-dd'T'HH:mm:ssZ",
    "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yyyy-MM-dd'T'HH:mm:ss",
    "yyyy-MM-dd HH:mm:ss"
].map { fmt in
    let df = DateFormatter()
    df.locale = Locale(identifier: "en_US_POSIX")
    df.timeZone = TimeZone(secondsFromGMT: 0)
    df.dateFormat = fmt
    return df
}

private let _minuteOutputFormatter: DateFormatter = {
    let f = DateFormatter()
    f.dateFormat = "yyyy-MM-dd HH:mm"
    f.locale = Locale(identifier: "zh_CN")
    f.timeZone = TimeZone.autoupdatingCurrent
    return f
}()

// MARK: - 日期时间格式化（精确到分，自动适配设备当前时区）
public func formatDateTimeToMinute(_ dateString: String?, defaultVal: String = "-") -> String {
    guard let raw = dateString?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty, raw != "null", raw != "-" else {
        return defaultVal
    }
    
    if raw == "未领取" || raw == "刚刚" {
        return raw
    }
    
    // 如果只有日期 (如 2026-09-16)
    if raw.count == 10 && raw.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil {
        return raw
    }
    
    // 如果已经是 yyyy-MM-dd HH:mm 格式，避免重复格式化
    if raw.count == 16 && raw.range(of: #"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$"#, options: .regularExpression) != nil {
        return raw
    }
    
    var parsedDate = _isoFractionalFormatter.date(from: raw) ?? _isoStandardFormatter.date(from: raw)
    
    if parsedDate == nil {
        for df in _fallbackUtcFormatters {
            if let d = df.date(from: raw) {
                parsedDate = d
                break
            }
        }
    }
    
    guard let date = parsedDate else {
        let cleaned = raw.replacingOccurrences(of: "T", with: " ").replacingOccurrences(of: "Z", with: "")
        if cleaned.count >= 16 {
            return String(cleaned.prefix(16))
        }
        return raw
    }
    
    return _minuteOutputFormatter.string(from: date)
}

// MARK: - 日期格式化（不带时间，自动适配设备时区）
public func formatDateOnly(_ dateString: String?, defaultVal: String = "-") -> String {
    guard let raw = dateString?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty, raw != "null", raw != "-" else {
        return defaultVal
    }
    if raw.count == 10 && raw.range(of: #"^\d{4}-\d{2}-\d{2}$"#, options: .regularExpression) != nil {
        return raw
    }
    let formatted = formatDateTimeToMinute(raw, defaultVal: defaultVal)
    if formatted.count >= 10 && formatted.range(of: #"^\d{4}-\d{2}-\d{2}"#, options: .regularExpression) != nil {
        return String(formatted.prefix(10))
    }
    return formatted
}

// MARK: - 手机号脱敏
public func maskPhone(_ phone: String?) -> String {
    guard let raw = phone?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty, raw != "null" else {
        return "-"
    }
    if raw.count == 11 {
        return "\(raw.prefix(3))****\(raw.suffix(4))"
    }
    return raw
}




// MARK: - View Extensions
public extension View {
    func endTextEditing() -> some View {
        self.onTapGesture {
            UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        }
    }
    
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
    
    func enableGlobalKeyboardDismiss() -> some View {
        self.modifier(KeyboardDismissalModifier())
    }
}

// MARK: - 全局点击空白收起键盘 (Global Keyboard Dismissal)
struct KeyboardDismissalModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .background(KeyboardDismissalView())
    }
}

struct KeyboardDismissalView: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        DispatchQueue.main.async {
            if let window = view.window {
                let tapGesture = UITapGestureRecognizer(target: window, action: #selector(UIView.endEditing(_:)))
                tapGesture.cancelsTouchesInView = false
                tapGesture.delegate = context.coordinator
                window.addGestureRecognizer(tapGesture)
            }
        }
        return view
    }
    
    func updateUIView(_ uiView: UIView, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    class Coordinator: NSObject, UIGestureRecognizerDelegate {
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            return false
        }
        
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            let view = touch.view
            if view is UIControl {
                return false
            }
            var current = view
            while let c = current {
                if c is UITextField || c is UITextView {
                    return false
                }
                current = c.superview
            }
            return true
        }
    }
}

// MARK: - 隐私政策提示弹窗
public struct PrivacyPolicyView: View {
    public var onAgree: () -> Void
    public var onDisagree: () -> Void

    public init(onAgree: @escaping () -> Void, onDisagree: @escaping () -> Void) {
        self.onAgree = onAgree
        self.onDisagree = onDisagree
    }

    public var body: some View {
        ZStack {
            Color.black.opacity(0.4).ignoresSafeArea()

            VStack(spacing: 20) {
                Text("隐私政策与用户协议")
                    .font(.headline)
                    .fontWeight(.bold)

                Text("感谢您使用本应用！我们非常重视您的个人信息和隐私保护。在您使用本应用前，请仔细阅读[《隐私政策》](https://yourdomain.com/privacy.html)和[《用户协议》](https://yourdomain.com/agreement.html)。\n\n我们将在获得您的明确同意后，收集必要的设备信息、网络信息等，并初始化相关第三方 SDK 以提供服务。")
                    .font(.body)
                    .tint(.blue)

                HStack(spacing: 40) {
                    Button(action: onDisagree) {
                        Text("暂不同意/退出")
                            .foregroundColor(.gray)
                    }

                    Button(action: onAgree) {
                        Text("同 意")
                            .bold()
                            .padding(.horizontal, 20)
                            .padding(.vertical, 8)
                            .background(Color.blue)
                            .foregroundColor(.white)
                            .cornerRadius(8)
                    }
                }
            }
            .padding(24)
            .background(Color(UIColor.systemBackground))
            .cornerRadius(16)
            .padding(32)
        }
    }
}
