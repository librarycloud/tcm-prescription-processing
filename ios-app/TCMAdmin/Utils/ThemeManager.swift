import SwiftUI
import Combine

@MainActor
public class ThemeManager: ObservableObject {
    public static let shared = ThemeManager()
    
    @AppStorage("themeColorHex") public var themeColorHex: String = "#2563EB"
    @AppStorage("colorSchemeMode") public var colorSchemeMode: Int = 0 // 0: Auto, 1: Light, 2: Dark
    @AppStorage("fontSizeGear") public var fontSizeGear: Int = 0 // 0: 标准, 1: 中大号, 2: 大号, 3: 特大号
    
    @Published public var themeId: UUID = UUID()
    
    public var currentSizeCategory: ContentSizeCategory {
        switch fontSizeGear {
        case 1: return .extraLarge
        case 2: return .accessibilityMedium
        case 3: return .accessibilityLarge
        default: return .large // default standard
        }
    }
    
    public var primaryColor: Color { Color(hex: themeColorHex) }
    public var primaryColorSoft: Color { primaryColor.opacity(0.15) }
    public var primaryColorDark: Color {
        let uiColor = UIColor(Color(hex: themeColorHex))
        var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        if uiColor.getHue(&h, saturation: &s, brightness: &b, alpha: &a) {
            return Color(hue: Double(h), saturation: Double(s), brightness: Double(max(b - 0.2, 0.0)), opacity: Double(a))
        }
        return primaryColor
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
