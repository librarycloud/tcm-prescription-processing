import Foundation

let _isoStandardFormatter: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime]
    return f
}()
let _isoFractionalFormatter: ISO8601DateFormatter = {
    let f = ISO8601DateFormatter()
    f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return f
}()
let _fallbackUtcFormatters: [DateFormatter] = {
    let formats = [
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm:ss"
    ]
    return formats.map { fmt in
        let f = DateFormatter()
        f.dateFormat = fmt
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(secondsFromGMT: 0)
        return f
    }
}()

func parseDateSafely(_ dateString: String?) -> Date? {
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

func processingDuration(start: String?, end: String?) -> String {
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

print(processingDuration(start: "2023-10-10 10:00:00", end: "2023-10-10 11:30:00"))
