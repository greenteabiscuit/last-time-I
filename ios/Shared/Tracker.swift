import Foundation
import WidgetKit

struct TrackedItem: Identifiable, Codable, Equatable {
    var id = UUID().uuidString
    var title: String
    var tags: [String] = []
    var intervalDays = 0
    var history: [String] = []
    var deletedAt: Date?
    var snoozedUntil: Date?
    static func dayKey(_ date: Date) -> String {
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.string(from: date)
    }
    static func date(_ key: String) -> Date? {
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.date(from: key)
    }
    var lastDate: Date? { history.sorted().last.flatMap(Self.date) }
    func days(at now: Date = .now) -> Int? { lastDate.map { max(0, Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: $0), to: Calendar.current.startOfDay(for: now)).day ?? 0) } }
    func overdue(at now: Date = .now) -> Bool { intervalDays > 0 && (days(at: now) ?? 0) > intervalDays }
    func eligible(at now: Date = .now) -> Bool { deletedAt == nil && !(snoozedUntil.map { $0 > now } ?? false) && !tags.contains { $0.lowercased() == "health" } }
    var elapsed: String { guard let d = days() else { return "Not yet" }; return d == 0 ? "Today" : "\(d) \(d == 1 ? "day" : "days") ago" }
    mutating func record(_ date: Date = .now) { let key = Self.dayKey(date); if !history.contains(key) { history.append(key); history.sort() } }
    mutating func toggle(_ date: Date) { let key = Self.dayKey(date); if history.contains(key) { history.removeAll { $0 == key } } else { record(date) } }
    static func normalizeTags(_ raw: String) -> [String] {
        var seen = Set<String>()
        return raw.components(separatedBy: ",").compactMap { value in
            let t = value.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "^#\\s*", with: "", options: .regularExpression)
            return !t.isEmpty && seen.insert(t.lowercased()).inserted ? t : nil
        }
    }
}

enum TrackerStorage {
    static let group = "group.com.greenteabiscuit.lasttimei"
    static var directory: URL { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0] }
    static var file: URL { directory.appendingPathComponent("items.json") }
    static func load(from file: URL = file) throws -> [TrackedItem] {
        guard FileManager.default.fileExists(atPath: file.path) else {
            let initial = seeds
            try save(initial, to: file)
            return initial
        }
        return try JSONDecoder().decode([TrackedItem].self, from: Data(contentsOf: file))
    }
    static func save(_ items: [TrackedItem], to file: URL = file) throws {
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(items).write(to: file, options: .atomic)
        WidgetCenter.shared.reloadAllTimelines()
    }
    static var seeds: [TrackedItem] {
        ["Cut my nails", "Mopped the floor", "Changed my toothbrush", "Stayed at home the whole day", "Had my nose congested", "Had painful acne somewhere on my body"].map { TrackedItem(title: $0) }
    }
    static func selection(_ items: [TrackedItem], at now: Date = .now) -> (latest: [TrackedItem], oldest: [TrackedItem], extra: Int) {
        let eligible = items.filter { $0.eligible(at: now) }.sorted { ($0.lastDate ?? .distantPast, $0.id) > ($1.lastDate ?? .distantPast, $1.id) }
        let latest = Array(eligible.prefix(4)); let ids = Set(latest.map(\.id))
        let oldest = Array(eligible.reversed().filter { !ids.contains($0.id) }.prefix(4))
        return (latest, oldest, max(0, eligible.count - latest.count - oldest.count))
    }
}
