import Foundation

extension String {
    var pinyinInitials: String {
        let mutableString = NSMutableString(string: self)
        CFStringTransform(mutableString, nil, kCFStringTransformToLatin, false)
        CFStringTransform(mutableString, nil, kCFStringTransformStripDiacritics, false)
        let pinyin = mutableString as String
        return pinyin.components(separatedBy: CharacterSet.whitespacesAndNewlines)
            .compactMap { $0.first }
            .map { String($0) }
            .joined()
    }
}
