import SwiftUI
import Observation

@Observable
@MainActor
public class ThemeManager {
    public static let shared = ThemeManager()
    
    public var themeColorHex: String {
        didSet { UserDefaults.standard.set(themeColorHex, forKey: "themeColorHex") }
    }
    public var colorSchemeMode: Int {
        didSet { UserDefaults.standard.set(colorSchemeMode, forKey: "colorSchemeMode") }
    }
    public var fontSizeGear: Int {
        didSet { UserDefaults.standard.set(fontSizeGear, forKey: "fontSizeGear") }
    }
    
    public var themeId: UUID = UUID()
    
    private init() {
        self.themeColorHex = UserDefaults.standard.string(forKey: "themeColorHex") ?? "#2563EB"
        self.colorSchemeMode = UserDefaults.standard.integer(forKey: "colorSchemeMode")
        self.fontSizeGear = UserDefaults.standard.integer(forKey: "fontSizeGear")
    }
    
    public var currentSizeCategory: ContentSizeCategory {
        switch fontSizeGear {
        case 1: return .extraLarge
        case 2: return .accessibilityMedium
        case 3: return .accessibilityLarge
        default: return .large // default standard
        }
    }
    
    public var primaryColor: Color { Color(hex: themeColorHex) }
    public var primaryColorSoft: Color {
        return Color(UIColor { traitCollection in
            let uiColor = UIColor(Color(hex: self.themeColorHex))
            var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            if uiColor.getHue(&h, saturation: &s, brightness: &b, alpha: &a) {
                if traitCollection.userInterfaceStyle == .dark {
                    // 暗色模式下，背景色调亮并稍微增加透明度，才能从深灰背景中凸显出来
                    return UIColor(hue: h, saturation: max(s - 0.2, 0.0), brightness: min(b + 0.3, 1.0), alpha: 0.25)
                } else {
                    return uiColor.withAlphaComponent(0.15)
                }
            }
            return uiColor.withAlphaComponent(0.15)
        })
    }
    public var primaryColorDark: Color {
        return Color(UIColor { traitCollection in
            let uiColor = UIColor(Color(hex: self.themeColorHex))
            var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
            if uiColor.getHue(&h, saturation: &s, brightness: &b, alpha: &a) {
                if traitCollection.userInterfaceStyle == .dark {
                    // 暗黑模式下，让文本颜色变浅、加白，保证可读性
                    return UIColor(hue: h, saturation: max(s - 0.35, 0.0), brightness: min(b + 0.4, 1.0), alpha: a)
                } else {
                    // 浅色模式下，加深颜色
                    return UIColor(hue: h, saturation: s, brightness: max(b - 0.2, 0.0), alpha: a)
                }
            }
            return uiColor
        })
    }
    
    
    public var fontScale: CGFloat {
        switch fontSizeGear {
        case 1: return 1.15
        case 2: return 1.3
        case 3: return 1.45
        default: return 1.0
        }
    }
    public var currentColorScheme: ColorScheme? {
        switch colorSchemeMode {
        case 1: return .light
        case 2: return .dark
        default: return nil
        }
    }
    
    public func updateTheme() {
        themeId = UUID()
    }
}
