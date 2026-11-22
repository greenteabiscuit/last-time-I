import SwiftUI
import WidgetKit
import AppIntents

struct RecordTodayIntent: AppIntent {
    static var title: LocalizedStringResource = "Record today"
    @Parameter(title: "Item") var itemID: String
    init() {}
    init(_ id: String) { itemID = id }
    func perform() async throws -> some IntentResult {
        var items = try TrackerStorage.load()
        if let index = items.firstIndex(where: { $0.id == itemID && $0.deletedAt == nil }) { items[index].record(); try TrackerStorage.save(items) }
        return .result()
    }
}
struct SnoozeIntent: AppIntent {
    static var title: LocalizedStringResource = "Snooze widget for 7 days"
    @Parameter(title: "Item") var itemID: String
    init() {}
    init(_ id: String) { itemID = id }
    func perform() async throws -> some IntentResult {
        var items = try TrackerStorage.load()
        if let index = items.firstIndex(where: { $0.id == itemID && $0.deletedAt == nil }) { items[index].snoozedUntil = Date.now.addingTimeInterval(604800); try TrackerStorage.save(items) }
        return .result()
    }
}
struct TrackerEntry: TimelineEntry { let date: Date; let items: [TrackedItem]; var failed = false }
struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> TrackerEntry { TrackerEntry(date: .now, items: TrackerStorage.seeds) }
    func getSnapshot(in context: Context, completion: @escaping (TrackerEntry) -> Void) { completion(entry()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<TrackerEntry>) -> Void) {
        let value = entry()
        let nextMidnight = Calendar.current.startOfDay(for: .now).addingTimeInterval(86400)
        let expiry = value.items.compactMap(\.snoozedUntil).filter { $0 > .now }.min() ?? .distantFuture
        completion(Timeline(entries: [value], policy: .after(min(Date.now.addingTimeInterval(1800), nextMidnight, expiry))))
    }
    func entry() -> TrackerEntry { do { return TrackerEntry(date: .now, items: try TrackerStorage.load()) } catch { return TrackerEntry(date: .now, items: [], failed: true) } }
}
struct WidgetView: View {
    let entry: TrackerEntry
    @Environment(\.widgetFamily) var family
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack { Label("Last time I…", systemImage: "clock.arrow.circlepath").font(.system(.headline, design: .serif)); Spacer(); Link(destination: URL(string: "lasttimei://add")!) { Image(systemName: "plus.circle.fill") }.accessibilityLabel("Open Last Time I") }
            let selection = TrackerStorage.selection(entry.items, at: entry.date)
            if selection.latest.isEmpty {
                Text(entry.failed ? "Open the app to check your saved items." : "A little memory for everyday life.").font(.subheadline)
                Text("Open the app to add or unsnooze items.").font(.caption).foregroundStyle(.secondary)
            } else if family == .systemSmall {
                ForEach(Array(selection.latest.prefix(1))) { item in
                    Text(item.title).font(.subheadline.weight(.medium)).lineLimit(2)
                    Text(item.elapsed).font(.title3.bold())
                    Button(intent: RecordTodayIntent(item.id)) { Label("Today", systemImage: "checkmark") }.buttonStyle(.bordered)
                }
            } else if family == .systemLarge {
                section("LATEST", items: selection.latest)
                if !selection.oldest.isEmpty { section("OLDEST", items: selection.oldest) }
                if selection.extra > 0 { Text("+\(selection.extra) more in app").font(.caption2).foregroundStyle(.secondary) }
            } else {
                HStack(alignment: .top, spacing: 16) {
                    section("LATEST", items: Array(selection.latest.prefix(2)))
                    section("OLDEST", items: Array(entry.items.filter { $0.eligible(at: entry.date) }.sorted { ($0.lastDate ?? .distantPast) < ($1.lastDate ?? .distantPast) }.prefix(2)))
                }
            }
            Spacer(minLength: 0)
        }.foregroundStyle(Color(red: 0.12, green: 0.34, blue: 0.28)).containerBackground(Color(red: 0.96, green: 0.95, blue: 0.91), for: .widget)
    }
    func section(_ title: String, items: [TrackedItem]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 9, weight: .bold)).tracking(1.5).foregroundStyle(.secondary)
            ForEach(items) { item in
                HStack(spacing: 6) {
                    VStack(alignment: .leading, spacing: 1) { Text(item.title).font(.system(size: 11, weight: .medium)).lineLimit(1); Text(item.elapsed + (item.overdue(at: entry.date) ? " · Overdue" : "")).font(.system(size: 10)).foregroundStyle(item.overdue(at: entry.date) ? .orange : .secondary) }.frame(maxWidth: .infinity, alignment: .leading)
                    Button(intent: RecordTodayIntent(item.id)) { Image(systemName: "checkmark.circle.fill").font(.system(size: 18)) }.buttonStyle(.plain).accessibilityLabel("Record \(item.title) today")
                    Button(intent: SnoozeIntent(item.id)) { Image(systemName: "moon").font(.system(size: 13)) }.buttonStyle(.plain).accessibilityLabel("Snooze \(item.title) for 7 days")
                }.frame(minHeight: 25)
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
}
@main struct LastTimeWidget: Widget {
    var body: some WidgetConfiguration { StaticConfiguration(kind: "LastTimeWidget", provider: Provider()) { WidgetView(entry: $0) }.configurationDisplayName("Last time I…").description("Remember, record today, or snooze for seven days.").supportedFamilies([.systemSmall, .systemMedium, .systemLarge]) }
}
