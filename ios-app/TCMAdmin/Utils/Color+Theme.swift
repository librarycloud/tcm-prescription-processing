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
    static var successSoft: Color {
        Color(UIColor { trait in trait.userInterfaceStyle == .dark ? UIColor.systemGreen.withAlphaComponent(0.25) : UIColor.systemGreen.withAlphaComponent(0.15) })
    }
    static let warning = Color.orange
    static var warningSoft: Color {
        Color(UIColor { trait in trait.userInterfaceStyle == .dark ? UIColor.systemOrange.withAlphaComponent(0.25) : UIColor.systemOrange.withAlphaComponent(0.15) })
    }
    static let danger = Color.red
    static var dangerSoft: Color {
        Color(UIColor { trait in trait.userInterfaceStyle == .dark ? UIColor.systemRed.withAlphaComponent(0.25) : UIColor.systemRed.withAlphaComponent(0.15) })
    }
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

extension ShapeStyle where Self == Color {
    static var appPrimary: Color { ThemeManager.shared.primaryColor }
    static var appPrimarySoft: Color { ThemeManager.shared.primaryColorSoft }
    static var appPrimaryDark: Color { ThemeManager.shared.primaryColorDark }
    
    static var pageBackground: Color { Color(UIColor.systemGroupedBackground) }
    static var surface: Color { Color(UIColor.secondarySystemGroupedBackground) }
    static var surfaceVariant: Color { Color(UIColor.tertiarySystemGroupedBackground) }
    
    static var ink: Color { Color.primary }
    static var muted: Color { Color.secondary }
    
    static var cardBorder: Color { Color.gray.opacity(0.2) }
    
    static var success: Color { Color.green }
    static var successSoft: Color {
        Color(UIColor { trait in trait.userInterfaceStyle == .dark ? UIColor.systemGreen.withAlphaComponent(0.25) : UIColor.systemGreen.withAlphaComponent(0.15) })
    }
    static var warning: Color { Color.orange }
    static var warningSoft: Color {
        Color(UIColor { trait in trait.userInterfaceStyle == .dark ? UIColor.systemOrange.withAlphaComponent(0.25) : UIColor.systemOrange.withAlphaComponent(0.15) })
    }
    static var danger: Color { Color.red }
    static var dangerSoft: Color {
        Color(UIColor { trait in trait.userInterfaceStyle == .dark ? UIColor.systemRed.withAlphaComponent(0.25) : UIColor.systemRed.withAlphaComponent(0.15) })
    }
}
import Foundation

extension String {
    nonisolated private static let pinyinCacheQueue = DispatchQueue(label: "com.tcm.pinyincache", attributes: .concurrent)
    nonisolated(unsafe) private static var _pinyinCache: [String: String] = [:]

    nonisolated private static let tcmPolyphonicMap: [Character: Character] = [
        "参": "身", // shen -> s
        "术": "竹", // zhu -> z
        "重": "虫", // chong -> c
        "阿": "婀", // e -> e
        "壳": "桥", // qiao -> q
        "查": "扎", // zha -> z
        "蛤": "哥", // ge -> g
        "长": "常", // chang -> c
        "曾": "增", // zeng -> z
    ]

    nonisolated var pinyinInitials: String {
        // Fast path: check cache
        var cached: String?
        String.pinyinCacheQueue.sync {
            cached = String._pinyinCache[self]
        }
        if let cached = cached { return cached }
        
        // Handle TCM polyphonic characters to preserve 1:1 length while getting correct initials
        let replacedStr = String(self.map { String.tcmPolyphonicMap[$0] ?? $0 })
        
        // Compute
        let mutableString = NSMutableString(string: replacedStr)
        CFStringTransform(mutableString, nil, kCFStringTransformToLatin, false)
        CFStringTransform(mutableString, nil, kCFStringTransformStripDiacritics, false)
        let pinyin = mutableString as String
        let result = pinyin.components(separatedBy: CharacterSet.whitespacesAndNewlines)
            .compactMap { $0.first }
            .map { String($0) }
            .joined()
            
        // Save to cache
        String.pinyinCacheQueue.async(flags: .barrier) {
            String._pinyinCache[self] = result
        }
        
        return result
    }
}
