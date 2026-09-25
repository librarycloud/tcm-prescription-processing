import SwiftUI
import UIKit
import ImageIO
import UniformTypeIdentifiers

func downsampledImage(from data: Data, maxPixelSize: CGFloat) -> UIImage? {
    guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
    let options: [CFString: Any] = [
        kCGImageSourceCreateThumbnailFromImageAlways: true,
        kCGImageSourceCreateThumbnailWithTransform: true,
        kCGImageSourceThumbnailMaxPixelSize: maxPixelSize
    ]
    guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
    return UIImage(cgImage: image)
}

nonisolated func jpegDataForUpload(
    from cgImage: CGImage,
    orientation: CGImagePropertyOrientation,
    maxPixelSize: CGFloat = 5712,
    quality: CGFloat = 0.85
) -> Data? {
    let sourceWidth = CGFloat(cgImage.width)
    let sourceHeight = CGFloat(cgImage.height)
    let sourceMaxDimension = max(sourceWidth, sourceHeight)
    let scale = sourceMaxDimension > maxPixelSize ? maxPixelSize / sourceMaxDimension : 1
    let outputImage: CGImage

    if scale < 1 {
        let width = max(1, Int((sourceWidth * scale).rounded()))
        let height = max(1, Int((sourceHeight * scale).rounded()))
        guard let context = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return nil
        }
        context.interpolationQuality = .high
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
        guard let resizedImage = context.makeImage() else { return nil }
        outputImage = resizedImage
    } else {
        outputImage = cgImage
    }

    let outputData = NSMutableData()
    guard let destination = CGImageDestinationCreateWithData(
        outputData,
        UTType.jpeg.identifier as CFString,
        1,
        nil
    ) else {
        return nil
    }

    let properties: [CFString: Any] = [
        kCGImagePropertyOrientation: orientation.rawValue,
        kCGImageDestinationLossyCompressionQuality: quality
    ]
    CGImageDestinationAddImage(destination, outputImage, properties as CFDictionary)
    guard CGImageDestinationFinalize(destination) else { return nil }
    return outputData as Data
}

func cgImagePropertyOrientation(from orientation: UIImage.Orientation) -> CGImagePropertyOrientation {
    switch orientation {
    case .up: return .up
    case .upMirrored: return .upMirrored
    case .down: return .down
    case .downMirrored: return .downMirrored
    case .leftMirrored: return .leftMirrored
    case .right: return .right
    case .rightMirrored: return .rightMirrored
    case .left: return .left
    @unknown default: return .up
    }
}

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
        .clipShape(.rect(cornerRadius: 16))
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
            .scaledFont(11, weight: .medium)
            .foregroundStyle(style.text)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(style.bg)
            .clipShape(.rect(cornerRadius: 4))
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
        case "部分归还":
            return (.warning, .warningSoft)
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
                        .foregroundStyle(Color.appPrimary)
                        .scaledFont(20)
                }
                .frame(width: 36, height: 36)
            } else {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(Color.muted)
                    .padding(.leading, 8)
            }
            
            TextField(placeholder, text: $text, onCommit: { onSearch?() })
                .scaledFont(15)
                .foregroundStyle(Color.ink)
                .submitLabel(.search)
                .autocorrectionDisabled(true)
                #if compiler(>=5.5)
                .textInputAutocapitalization(.never)
                #else
                .autocapitalization(.none)
                #endif
            
            if !text.isEmpty {
                Button(action: {
                    text = ""
                    HapticManager.shared.impact(style: .light)
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(Color.muted)
                        .scaledFont(16)
                }
                .frame(width: 28, height: 28)
            }
            
            Button(action: { onSearch?() }) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(Color.appPrimary)
                    .scaledFont(20)
            }
            .frame(width: 36, height: 36)
        }
        .padding(.horizontal, 4)
        .frame(height: 38)
        .background(Color.surface)
        .clipShape(.rect(cornerRadius: 10))
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
                .scaledFont(13, weight: isSelected ? .semibold : .regular)
                .foregroundStyle(isSelected ? Color.white : Color.ink)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(isSelected ? Color.appPrimary : Color.surface)
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(isSelected ? Color.clear : Color.cardBorder, lineWidth: 1)
                )
                .shadow(color: isSelected ? Color.black.opacity(0.15) : Color.black.opacity(0.02), radius: 1, x: 0, y: 1)
                .padding(.vertical, 2)
                .padding(.horizontal, 1)
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
                .scaledFont(14)
                .foregroundStyle(Color.muted)
            Spacer(minLength: 12)
            Text(value)
                .scaledFont(14, weight: isBold ? .semibold : .regular)
                .foregroundStyle(valueColor)
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
            .scaledFont(11, weight: .bold)
            .foregroundStyle(Color.danger)
            .padding(.horizontal, 6)
            .padding(.vertical, 2.5)
            .background(Color.dangerSoft)
            .clipShape(.rect(cornerRadius: 4))
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
                            
                            Text("库存总量: 105.5 g").font(.subheadline).foregroundStyle(Color.muted)
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
    var showArrow: Bool
    var action: () -> Void
    
    public init(icon: String, title: String, value: String? = nil, showArrow: Bool = true, action: @escaping () -> Void) {
        self.icon = icon
        self.title = title
        self.value = value
        self.showArrow = showArrow
        self.action = action
    }
    
    public var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .scaledFont(16)
                    .foregroundStyle(Color.appPrimary)
                    .frame(width: 24, height: 24)
                
                Text(title)
                    .scaledFont(15, weight: .medium)
                    .foregroundStyle(Color.ink)
                
                Spacer()
                
                if let value = value {
                    Text(value)
                        .scaledFont(14)
                        .foregroundStyle(Color.muted)
                }
                
                if showArrow {
                    Image(systemName: "chevron.right")
                        .scaledFont(13, weight: .semibold)
                        .foregroundStyle(Color.muted)
                }
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
                    .scaledFont(16, weight: .bold)
                    .foregroundStyle(Color.ink)
                
                if let sub = subtitle, !sub.isEmpty {
                    Text(sub)
                        .scaledFont(12)
                        .foregroundStyle(Color.muted)
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
        if trimmed.isEmpty {
            Text(text)
                .font(font)
                .fontWeight(weight)
                .foregroundStyle(regularColor)
        } else {
            Text(buildAttributedString(trimmed: trimmed))
                .font(font)
                .fontWeight(weight)
                .foregroundStyle(regularColor)
        }
    }
    
    private func buildAttributedString(trimmed: String) -> AttributedString {
        var attributed = AttributedString(text)
        var searchRange = attributed.startIndex..<attributed.endIndex
        while let range = attributed[searchRange].range(of: trimmed, options: .caseInsensitive) {
            attributed[range].foregroundColor = highlightColor
            attributed[range].inlinePresentationIntent = .stronglyEmphasized
            searchRange = range.upperBound..<attributed.endIndex
        }
        return attributed
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




// MARK: - Font Scaling System
public enum AppFont {
    case custom(CGFloat, Font.Weight = .regular)
}

public struct ScaledFontModifier: ViewModifier {
    let style: AppFont
    
    public func body(content: Content) -> some View {
        switch style {
        case .custom(let size, let weight):
            content.font(.system(size: size * ThemeManager.shared.fontScale, weight: weight))
        }
    }
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
    
    func scaledFont(_ size: CGFloat, weight: Font.Weight = .regular) -> some View {
        self.modifier(ScaledFontModifier(style: .custom(size, weight)))
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
    func makeUIView(context: Context) -> KeyboardDismissalHostView {
        let view = KeyboardDismissalHostView()
        view.coordinator = context.coordinator
        return view
    }

    func updateUIView(_ uiView: KeyboardDismissalHostView, context: Context) {}

    static func dismantleUIView(_ uiView: KeyboardDismissalHostView, coordinator: Coordinator) {
        coordinator.removeGesture()
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class KeyboardDismissalHostView: UIView {
        weak var coordinator: Coordinator?

        override func didMoveToWindow() {
            super.didMoveToWindow()
            if let window {
                coordinator?.installGesture(on: window)
            } else {
                coordinator?.removeGesture()
            }
        }
    }
    
    class Coordinator: NSObject, UIGestureRecognizerDelegate {
        weak var window: UIWindow?
        weak var gesture: UITapGestureRecognizer?

        func installGesture(on window: UIWindow) {
            guard self.window !== window else { return }
            removeGesture()

            let tapGesture = UITapGestureRecognizer(target: window, action: #selector(UIView.endEditing(_:)))
            tapGesture.cancelsTouchesInView = false
            tapGesture.requiresExclusiveTouchType = false
            tapGesture.delegate = self
            window.addGestureRecognizer(tapGesture)
            self.window = window
            self.gesture = tapGesture
        }

        func removeGesture() {
            if let gesture, let window {
                window.removeGestureRecognizer(gesture)
            }
            gesture = nil
            window = nil
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            // 键盘收起点击不与导航侧滑、滚动和系统手势并行识别。
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
                if c is UIScrollView {
                    return false
                }
                current = c.superview
            }
            return true
        }
        
        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRequireFailureOf otherGestureRecognizer: UIGestureRecognizer) -> Bool {
            otherGestureRecognizer is UIPanGestureRecognizer
        }
    }
}

import WebKit

struct InlineWebView: UIViewRepresentable {
    let type: String

    func makeUIView(context: Context) -> WKWebView {
        return WKWebView()
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        let baseUrl = ApiClient.shared.baseURL
        let htmlData = """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
            <style>
                body { font-family: -apple-system, sans-serif; line-height: 1.6; padding: 16px; color: #333; }
                .loading { text-align: center; color: #666; margin-top: 50px; }
                img { max-width: 100%; height: auto; }
                pre { background: #f6f8fa; padding: 16px; overflow: auto; border-radius: 6px; }
                blockquote { border-left: 4px solid #dfe2e5; padding: 0 15px; color: #6a737d; margin: 0 0 16px 0; }
            </style>
        </head>
        <body>
            <div id="content"><div class="loading">加载中...</div></div>
            <script>
                fetch('\(baseUrl)/app/legal-docs')
                    .then(res => res.json())
                    .then(json => {
                        const data = json.code === 0 ? json.data : (json || {});
                        const md = data.\(type) || '暂无内容';
                        document.getElementById('content').innerHTML = marked.parse(md);
                    })
                    .catch(e => {
                        document.getElementById('content').innerHTML = '<div class="loading">加载失败，请检查网络并重试</div>';
                    });
            </script>
        </body>
        </html>
        """
        uiView.loadHTMLString(htmlData, baseURL: nil)
    }
}

// MARK: - 隐私政策提示弹窗
public struct PrivacyPolicyView: View {
    public var onAgree: () -> Void
    public var onDisagree: (() -> Void)? = nil
    @State private var webUrlToShow: String?
    @State private var showDisagreeAlert = false
    @State private var isRejected = false

    public init(onAgree: @escaping () -> Void, onDisagree: (() -> Void)? = nil) {
        self.onAgree = onAgree
        self.onDisagree = onDisagree
    }

    public var body: some View {
        ZStack {
            Color.black.opacity(0.4).ignoresSafeArea()

            if isRejected {
                // 拒绝后友好禁用态，杜绝 exit(0) 导致苹果拒审
                VStack(spacing: 20) {
                    Image(systemName: "lock.shield.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(Color.appPrimary)

                    Text("服务暂未开启")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundStyle(Color.ink)

                    Text("由于您暂未同意《隐私政策》与《用户协议》，药房助手暂无法向您提供处方管理及加工流转服务。\n\n如需继续使用，请点击下方按钮重新阅读并同意协议。若暂不使用，可直接上滑或按 Home 键退出。")
                        .font(.subheadline)
                        .foregroundStyle(Color.muted)
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)

                    Button(action: {
                        isRejected = false
                    }) {
                        Text("重新阅读协议并同意")
                            .bold()
                            .padding(.horizontal, 24)
                            .padding(.vertical, 12)
                            .background(Color.appPrimary)
                            .foregroundStyle(Color.white)
                            .clipShape(.rect(cornerRadius: 10))
                    }
                    .padding(.top, 8)
                }
                .padding(28)
                .background(Color(UIColor.systemBackground))
                .clipShape(.rect(cornerRadius: 16))
                .padding(32)
                .shadow(radius: 12)
            } else {
                VStack(spacing: 20) {
                    Text("隐私政策与用户协议")
                        .font(.headline)
                        .fontWeight(.bold)

                    Text("感谢您使用本应用！我们非常重视您的个人信息和隐私保护。在您使用本应用前，请仔细阅读[《隐私政策》](privacy_policy)和[《用户协议》](user_agreement)。\n\n我们将在获得您的明确同意后，收集必要的设备信息、网络信息等，并初始化相关第三方 SDK 以提供服务。")
                        .font(.body)
                        .tint(.blue)
                        .environment(\.openURL, OpenURLAction { url in
                            self.webUrlToShow = url.absoluteString
                            return .handled
                        })

                    HStack(spacing: 40) {
                        Button(action: {
                            showDisagreeAlert = true
                        }) {
                            Text("暂不同意")
                                .foregroundStyle(Color.gray)
                        }

                        Button(action: onAgree) {
                            Text("同 意")
                                .bold()
                                .padding(.horizontal, 20)
                                .padding(.vertical, 8)
                                .background(Color.blue)
                                .foregroundStyle(Color.white)
                                .clipShape(.rect(cornerRadius: 8))
                        }
                    }
                }
                .padding(24)
                .background(Color(UIColor.systemBackground))
                .clipShape(.rect(cornerRadius: 16))
                .padding(32)
            }
        }
        .alert("温馨提示", isPresented: $showDisagreeAlert) {
            Button("重新阅读", role: .cancel) {}
            Button("暂不使用", role: .destructive) {
                isRejected = true
                onDisagree?()
            }
        } message: {
            Text("若不同意《隐私政策》和《用户协议》，应用将无法提供相关核心服务。您可以按手机 Home 键退出应用或重新阅读协议。")
        }
        .sheet(item: Binding<String?>(
            get: { webUrlToShow },
            set: { webUrlToShow = $0 }
        )) { type in
            NavigationStack {
                InlineWebView(type: type)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("关闭") {
                                webUrlToShow = nil
                            }
                        }
                    }
            }
        }
    }
}

extension String: @retroactive Identifiable {
    public var id: String { self }
}

// MARK: - AppScrollView (带双击 Tab 栏及悬浮按钮回到顶部)
public struct AppScrollView<Content: View>: View {
    private let content: () -> Content
    @State private var showScrollToTop = false

    public init(@ViewBuilder content: @escaping () -> Content) {
        self.content = content
    }

    public var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                // 顶部锚点
                Color.clear
                    .frame(height: 0)
                    .id("SCROLL_TOP_ANCHOR")

                content()
            }
            .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("ScrollToTop"))) { _ in
                withAnimation(.easeOut(duration: 0.3)) {
                    proxy.scrollTo("SCROLL_TOP_ANCHOR", anchor: .top)
                }
            }
            // iOS 17+ 原生滚动距离监听，最可靠
            .onScrollGeometryChange(for: CGFloat.self) { geo in
                geo.contentOffset.y
            } action: { _, newY in
                let isPastThreshold = newY > 800.0
                
                if isPastThreshold && !showScrollToTop {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        showScrollToTop = true
                    }
                } else if !isPastThreshold && showScrollToTop {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        showScrollToTop = false
                    }
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if showScrollToTop {
                    Button {
                        withAnimation(.easeOut(duration: 0.3)) {
                            proxy.scrollTo("SCROLL_TOP_ANCHOR", anchor: .top)
                        }
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 48, height: 48)
                            .background(Color.appPrimary)
                            .clipShape(Circle())
                            .shadow(color: Color.black.opacity(0.15), radius: 6, x: 0, y: 3)
                    }
                    .padding(.trailing, 20)
                    .padding(.bottom, 20)
                    .transition(.scale.combined(with: .opacity))
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("TabDoubleTapped"))) { _ in
                withAnimation(.easeOut(duration: 0.3)) {
                    proxy.scrollTo("SCROLL_TOP_ANCHOR", anchor: .top)
                }
            }
        }
    }
}

public func parseDateSafely(_ dateString: String?) -> Date? {
    guard let raw = dateString?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty, raw != "null", raw != "-" else {
        return nil
    }
    
    if let d = _isoFractionalFormatter.date(from: raw) { return d }
    if let d = _isoStandardFormatter.date(from: raw) { return d }
    for df in _fallbackUtcFormatters {
        if let d = df.date(from: raw) { return d }
    }
    return nil
}

public func processingDuration(start: String?, end: String?) -> String {
    guard let startDate = parseDateSafely(start) else { return "-" }
    
    let endDate: Date
    if let ed = parseDateSafely(end) {
        endDate = ed
    } else {
        endDate = Date()
    }
    
    let timeInterval = endDate.timeIntervalSince(startDate)
    let minutes = max(0, Int(timeInterval / 60))
    
    if minutes < 60 {
        return "\(minutes)分钟"
    } else {
        let hours = minutes / 60
        let mins = minutes % 60
        return "\(hours)小时\(mins)分钟"
    }
}
import SwiftUI

struct FlowLayout: Layout {
    var spacing: CGFloat
    var lineSpacing: CGFloat
    var alignment: HorizontalAlignment

    init(spacing: CGFloat = 8, lineSpacing: CGFloat = 8, alignment: HorizontalAlignment = .leading) {
        self.spacing = spacing
        self.lineSpacing = lineSpacing
        self.alignment = alignment
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = FlowResult(in: proposal.width ?? 0, subviews: subviews, spacing: spacing, lineSpacing: lineSpacing, alignment: alignment)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = FlowResult(in: bounds.width, subviews: subviews, spacing: spacing, lineSpacing: lineSpacing, alignment: alignment)
        for (index, subview) in subviews.enumerated() {
            let point = result.positions[index]
            subview.place(at: CGPoint(x: point.x + bounds.minX, y: point.y + bounds.minY), proposal: .unspecified)
        }
    }

    struct FlowResult {
        var size: CGSize = .zero
        var positions: [CGPoint] = []

        init(in maxWidth: CGFloat, subviews: Subviews, spacing: CGFloat, lineSpacing: CGFloat, alignment: HorizontalAlignment) {
            var currentX: CGFloat = 0
            var currentY: CGFloat = 0
            var lineHeight: CGFloat = 0
            
            var lineItemIndices: [Int] = []
            
            func flushLine() {
                if lineItemIndices.isEmpty { return }
                let lineTotalWidth = currentX - spacing
                let offsetX: CGFloat
                switch alignment {
                case .trailing: offsetX = max(0, maxWidth - lineTotalWidth)
                case .center: offsetX = max(0, (maxWidth - lineTotalWidth) / 2)
                default: offsetX = 0
                }
                for i in lineItemIndices {
                    positions[i].x += offsetX
                }
                lineItemIndices.removeAll()
            }

            for (index, subview) in subviews.enumerated() {
                let size = subview.sizeThatFits(.unspecified)
                if currentX + size.width > maxWidth && currentX > 0 {
                    flushLine()
                    currentX = 0
                    currentY += lineHeight + lineSpacing
                    lineHeight = 0
                }

                positions.append(CGPoint(x: currentX, y: currentY))
                lineItemIndices.append(index)
                lineHeight = max(lineHeight, size.height)
                currentX += size.width + spacing
            }
            flushLine()

            size = CGSize(width: maxWidth, height: currentY + lineHeight)
        }
    }
}

public class HapticManager {
    public static let shared = HapticManager()
    
    private init() {}
    
    public func impact(style: UIImpactFeedbackGenerator.FeedbackStyle = .medium) {
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.prepare()
        generator.impactOccurred()
    }
    
    public func notify(type: UINotificationFeedbackGenerator.FeedbackType) {
        let generator = UINotificationFeedbackGenerator()
        generator.prepare()
        generator.notificationOccurred(type)
    }
}
import SwiftUI
import SwiftUI
import UIKit

public struct ZoomableImageView: UIViewRepresentable {
    public let image: UIImage
    
    public init(image: UIImage) {
        self.image = image
    }
    
    public func makeUIView(context: Context) -> ZoomingScrollView {
        let scrollView = ZoomingScrollView()
        scrollView.maximumZoomScale = 5.0
        scrollView.minimumZoomScale = 1.0
        scrollView.bouncesZoom = true
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.showsVerticalScrollIndicator = false
        scrollView.backgroundColor = .clear
        
        let doubleTap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.handleDoubleTap(_:)))
        doubleTap.numberOfTapsRequired = 2
        scrollView.addGestureRecognizer(doubleTap)
        
        scrollView.imageView.image = image
        context.coordinator.scrollView = scrollView
        
        return scrollView
    }
    
    public func updateUIView(_ uiView: ZoomingScrollView, context: Context) {
        uiView.imageView.image = image
    }
    
    public func makeCoordinator() -> Coordinator {
        Coordinator()
    }
    
    public class Coordinator: NSObject {
        weak var scrollView: ZoomingScrollView?
        
        @objc func handleDoubleTap(_ recognizer: UITapGestureRecognizer) {
            guard let scrollView = scrollView else { return }
            if scrollView.zoomScale > scrollView.minimumZoomScale {
                scrollView.setZoomScale(scrollView.minimumZoomScale, animated: true)
            } else {
                let pointInView = recognizer.location(in: scrollView.imageView)
                let zoomScale = min(scrollView.maximumZoomScale, 3.0)
                let scrollViewSize = scrollView.bounds.size
                let w = scrollViewSize.width / zoomScale
                let h = scrollViewSize.height / zoomScale
                let x = pointInView.x - (w / 2.0)
                let y = pointInView.y - (h / 2.0)
                scrollView.zoom(to: CGRect(x: x, y: y, width: w, height: h), animated: true)
            }
        }
    }
}

public class ZoomingScrollView: UIScrollView, UIScrollViewDelegate {
    let imageView = UIImageView()
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        self.delegate = self
        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        addSubview(imageView)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    public override func layoutSubviews() {
        super.layoutSubviews()
        // If zoom scale is 1, keep frame matching bounds to allow aspect fit to work
        if zoomScale == minimumZoomScale {
            imageView.frame = bounds
        }
    }
    
    public func viewForZooming(in scrollView: UIScrollView) -> UIView? {
        return imageView
    }
    
    public func scrollViewDidZoom(_ scrollView: UIScrollView) {
        let offsetX = max((bounds.width - contentSize.width) * 0.5, 0)
        let offsetY = max((bounds.height - contentSize.height) * 0.5, 0)
        imageView.center = CGPoint(x: contentSize.width * 0.5 + offsetX,
                                 y: contentSize.height * 0.5 + offsetY)
    }
}
