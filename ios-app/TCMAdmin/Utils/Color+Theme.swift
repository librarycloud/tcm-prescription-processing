import SwiftUI

extension Color {
    // 主题色 (Primary)
    static var appPrimary: Color { ThemeManager.shared.primaryColor }
    static var appPrimarySoft: Color { ThemeManager.shared.primaryColorSoft }
    static var appPrimaryDark: Color { ThemeManager.shared.primaryColorDark }
    
    // 背景色 (Background)
    static let pageBackground = Color(UIColor.systemGroupedBackground)
    static let surface = Color(UIColor.secondarySystemGroupedBackground)
    static let surfaceVariant = Color(UIColor.tertiarySystemGroupedBackground)
    
    // 文本色 (Text)
    static let ink = Color.primary
    static let muted = Color.secondary
    
    // 边框色 (Border)
    static let cardBorder = Color.gray.opacity(0.2)
    
    // 状态色 (Status)
    static let success = Color.green
    static let successSoft = Color.green.opacity(0.15)
    static let warning = Color.orange
    static let warningSoft = Color.orange.opacity(0.15)
    static let danger = Color.red
    static let dangerSoft = Color.red.opacity(0.15)
}

// 十六进制颜色转换辅助
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (1, 1, 1, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
