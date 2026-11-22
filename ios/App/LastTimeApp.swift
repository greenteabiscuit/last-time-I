import SwiftUI
import WidgetKit

@MainActor final class TrackerStore: ObservableObject {
    @Published var items: [TrackedItem] = []
    @Published var error: String?
    init() {
        do { try TransferSupport.runIfRequested() } catch { self.error = "Transfer failed: \(error.localizedDescription)" }
        reload()
    }
    func reload() { do { items = try TrackerStorage.load() } catch { self.error = "Could not read your saved items: \(error.localizedDescription)" } }
    func commit(_ next: [TrackedItem]) { do { try TrackerStorage.save(next); items = next } catch { self.error = "Could not save: \(error.localizedDescription)" } }
    func update(_ item: TrackedItem) { var next = items; if let i = next.firstIndex(where: { $0.id == item.id }) { next[i] = item } else { next.append(item) }; commit(next) }
    func change(_ id: String, _ action: (inout TrackedItem) -> Void) { guard var item = items.first(where: { $0.id == id }) else { return }; action(&item); update(item) }
}

@main struct LastTimeApp: App {
    @StateObject private var store = TrackerStore()
    @Environment(\.scenePhase) private var phase
    var body: some Scene { WindowGroup { ContentView().environmentObject(store).tint(Color.accent).onChange(of: phase) { _, value in if value == .active { store.reload() } } } }
}
extension Color {
    static let accent = Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 0.55, green: 0.79, blue: 0.67, alpha: 1) : UIColor(red: 0.12, green: 0.34, blue: 0.28, alpha: 1) })
    static let paper = Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(red: 0.08, green: 0.12, blue: 0.10, alpha: 1) : UIColor(red: 0.96, green: 0.95, blue: 0.91, alpha: 1) })
}

struct ContentView: View {
    @EnvironmentObject var store: TrackerStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var heldOrder: [String]?
    @State private var reorderTask: Task<Void, Never>?
    @State private var search = ""
    @State private var sort = 0
    @State private var trash = false
    @State private var editor: TrackedItem?
    @State private var adding = false
    var visible: [TrackedItem] {
        let sorted = store.items.filter { ($0.deletedAt != nil) == trash && (search.isEmpty || ($0.title + " " + $0.tags.joined(separator: " ")).localizedCaseInsensitiveContains(search)) }.sorted {
            if sort == 2 { return $0.title.localizedStandardCompare($1.title) == .orderedAscending }
            let a = $0.lastDate ?? .distantPast, b = $1.lastDate ?? .distantPast
            return a == b ? $0.title < $1.title : (sort == 0 ? a > b : a < b)
        }
        guard let heldOrder else { return sorted }
        let positions = Dictionary(uniqueKeysWithValues: heldOrder.enumerated().map { ($1, $0) })
        return sorted.sorted { (positions[$0.id] ?? Int.max) < (positions[$1.id] ?? Int.max) }
    }
    // Persist and show the new date immediately; only defer the row's movement.
    private func recordWithMotion(_ id: String, _ action: (inout TrackedItem) -> Void) {
        reorderTask?.cancel()
        heldOrder = visible.map(\.id)
        store.change(id, action)
        reorderTask = Task { @MainActor in
            do { try await Task.sleep(for: .milliseconds(500)) } catch { return }
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.45)) {
                heldOrder = nil
            }
            reorderTask = nil
        }
    }
    private func cancelReorder() {
        reorderTask?.cancel()
        reorderTask = nil
        heldOrder = nil
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("\(visible.count) items").font(.caption).foregroundStyle(.secondary)
                        let overdue = visible.filter { $0.overdue() }.count
                        if overdue > 0 { Text("· \(overdue) overdue").font(.caption).foregroundStyle(.orange) }
                        Spacer()
                        Menu { Picker("Sort", selection: $sort) { Text("Latest updated").tag(0); Text("Oldest updated").tag(1); Text("A to Z").tag(2) } } label: { Image(systemName: "arrow.up.arrow.down").font(.subheadline).frame(width: 44, height: 30) }.accessibilityLabel("Sort")
                    }
                    if visible.isEmpty {
                        ContentUnavailableView(search.isEmpty ? "Nothing here yet" : "No matches", systemImage: "clock.badge.questionmark", description: Text(search.isEmpty ? "Add something you’d like to remember." : "Try another name or tag."))
                    }
                    LazyVStack(spacing: 6) { ForEach(visible) { item in card(item) } }
                }.padding(.horizontal, 12).padding(.bottom, 8)
            }.background(Color.paper)
            .navigationTitle(trash ? "Deleted" : "Last time I…")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $search, prompt: "Search items or tags")
            .onOpenURL { url in if url.host == "add" { adding = true } }
            .onChange(of: sort) { _, _ in cancelReorder() }
            .onChange(of: search) { _, _ in cancelReorder() }
            .onChange(of: trash) { _, _ in cancelReorder() }
            .onDisappear { cancelReorder() }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button { trash.toggle() } label: { Image(systemName: trash ? "clock" : "trash") }.accessibilityLabel(trash ? "Tracked items" : "Recently deleted") }
                ToolbarItem(placement: .topBarTrailing) { Button { adding = true } label: { Label("Add item", systemImage: "plus") } }
            }
            .sheet(isPresented: $adding) { ItemEditor(item: TrackedItem(title: ""), isNew: true).environmentObject(store) }
            .sheet(item: $editor) { ItemEditor(item: $0, isNew: false).environmentObject(store) }
            .alert("Storage error", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) { Button("OK") { store.error = nil } } message: { Text(store.error ?? "") }
        }
    }
    func card(_ item: TrackedItem) -> some View {
        VStack(alignment: .leading, spacing: 4) {
        HStack(spacing: 4) {
            NavigationLink { HistoryView(id: item.id) } label: {
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title).font(.system(size: 16, weight: .medium)).foregroundStyle(.primary).lineLimit(2)
                    if item.lastDate != nil || item.intervalDays > 0 || item.snoozedUntil.map({ $0 > .now }) == true {
                    HStack(spacing: 5) {
                        if item.lastDate != nil { Text(item.elapsed).foregroundStyle(item.overdue() ? .orange : Color.accent) }
                        if item.intervalDays > 0 { Text("· \(item.intervalDays)d interval").foregroundStyle(.secondary) }
                        if item.overdue() { Text("· Overdue").foregroundStyle(.orange) }
                        if item.snoozedUntil.map({ $0 > .now }) == true { Image(systemName: "moon.fill").foregroundStyle(.secondary).accessibilityLabel("Widget snoozed") }
                    }.font(.caption)
                    }
                    if !item.tags.isEmpty { Text(item.tags.map { "#" + $0 }.joined(separator: " ")).font(.caption2).foregroundStyle(.secondary).lineLimit(1) }
                }.frame(maxWidth: .infinity, alignment: .leading).frame(minHeight: 44, alignment: .leading).contentShape(Rectangle())
            }.buttonStyle(.plain)
            if item.deletedAt == nil {
                Button { recordWithMotion(item.id) { $0.record() } } label: {
                    Text("Today").font(.system(size: 13, weight: .semibold)).frame(minWidth: 48, minHeight: 44).foregroundStyle(Color.accent)
                }.buttonStyle(.plain).accessibilityLabel("Record \(item.title) today")
            }
                Menu {
                    Button("Edit", systemImage: "pencil") { editor = item }
                    if item.deletedAt == nil {
                        Button(item.snoozedUntil.map { $0 > .now } == true ? "Unsnooze now" : "Snooze widget for 7 days", systemImage: "moon") { store.change(item.id) { $0.snoozedUntil = ($0.snoozedUntil.map { $0 > .now } == true) ? nil : Date.now.addingTimeInterval(604800) } }
                        Button("Delete", systemImage: "trash", role: .destructive) { store.change(item.id) { $0.deletedAt = .now } }
                    } else { Button("Restore", systemImage: "arrow.uturn.backward") { store.change(item.id) { $0.deletedAt = nil } } }
                } label: { Image(systemName: "ellipsis").font(.system(size: 16, weight: .semibold)).frame(width: 36, height: 44).contentShape(Rectangle()) }.accessibilityLabel("Options for \(item.title)")
        }
            ItemCalendarStrip(item: item) { date in recordWithMotion(item.id) { $0.toggle(date) } }
        }.padding(.leading, 12).padding(.trailing, 2).padding(.vertical, 6)
            .background(.background, in: RoundedRectangle(cornerRadius: 12))
            .overlay { RoundedRectangle(cornerRadius: 12).strokeBorder(item.overdue() ? Color.orange.opacity(0.4) : Color.accent.opacity(0.08)) }

    }
}

struct ItemCalendarStrip: View {
    let item: TrackedItem
    let toggle: (Date) -> Void
    private var start: Date { Calendar.current.date(from: DateComponents(year: 2010, month: 1, day: 1))! }
    private var dayCount: Int { (Calendar.current.dateComponents([.day], from: start, to: Calendar.current.startOfDay(for: .now)).day ?? 0) + 1 }
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            LazyHStack(spacing: 5) {
                ForEach(0..<dayCount, id: \.self) { offset in
                    let date = Calendar.current.date(byAdding: .day, value: offset, to: start)!
                    let recorded = item.history.contains(TrackedItem.dayKey(date))
                    let today = Calendar.current.isDateInToday(date)
                    Button { toggle(date) } label: {
                        VStack(spacing: 1) {
                            Text(date.formatted(.dateTime.month(.abbreviated))).font(.system(size: 8)).foregroundStyle(.secondary)
                            Text("\(Calendar.current.component(.day, from: date))")
                                .font(.system(size: 11, weight: today ? .bold : .medium)).monospacedDigit()
                                .frame(width: 27, height: 25)
                                .background(recorded ? Color.accent : Color.accent.opacity(0.05), in: Circle())
                                .foregroundStyle(recorded ? Color.paper : .primary)
                                .overlay { Circle().strokeBorder(today ? Color.accent : .clear, lineWidth: 1) }
                        }.frame(width: 29, height: 38)
                    }.buttonStyle(.plain).disabled(item.deletedAt != nil)
                        .accessibilityLabel("\(item.title), \(date.formatted(date: .complete, time: .omitted)), \(recorded ? "recorded" : "not recorded")")
                }
            }.padding(.trailing, 10)
        }.defaultScrollAnchor(.trailing).frame(height: 38)
            .accessibilityLabel("Swipe dates for \(item.title)")
    }
}

struct ItemEditor: View {
    @EnvironmentObject var store: TrackerStore
    @Environment(\.dismiss) var dismiss
    @State var item: TrackedItem
    var isNew: Bool
    @State private var rawTags = ""
    @State private var interval = ""
    @State private var recordToday = false
    var valid: Bool { !item.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && (interval.isEmpty || (Int(interval).map { $0 >= 0 && $0 <= 365000 } ?? false)) }
    var body: some View {
        NavigationStack {
            Form {
                Section("Last time I…") { TextField("e.g. Changed my toothbrush", text: $item.title) }
                Section { TextField("Days (optional)", text: $interval).keyboardType(.numberPad) } header: { Text("Expected interval") } footer: { Text("An item becomes overdue after this many days. Leave empty for no interval.") }
                Section { TextField("home, personal", text: $rawTags).textInputAutocapitalization(.never) } header: { Text("Tags, separated by commas") } footer: { Text("Items tagged health stay in the app and are hidden from the widget.") }
                if isNew { Toggle("Record today", isOn: $recordToday) }
            }.navigationTitle(isNew ? "New moment" : "Edit moment").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Save") { item.title = item.title.trimmingCharacters(in: .whitespacesAndNewlines); item.tags = TrackedItem.normalizeTags(rawTags); item.intervalDays = Int(interval) ?? 0; if isNew && recordToday { item.record() }; store.update(item); if store.error == nil { dismiss() } }.disabled(!valid) } }
                .onAppear { rawTags = item.tags.joined(separator: ", "); interval = item.intervalDays == 0 ? "" : String(item.intervalDays) }
        }
    }
}

struct HistoryView: View {
    @EnvironmentObject var store: TrackerStore
    let id: String
    @State private var month = Calendar.current.dateInterval(of: .month, for: .now)!.start
    var item: TrackedItem? { store.items.first { $0.id == id } }
    let columns = Array(repeating: GridItem(.flexible()), count: 7)
    var body: some View {
        if let item {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text(item.elapsed).font(.system(size: 32, weight: .semibold, design: .serif)).foregroundStyle(Color.accent)
                    Text("\(item.history.count) recorded days").foregroundStyle(.secondary)
                    HStack {
                        Button { move(-1) } label: { Image(systemName: "chevron.left").padding(10) }.disabled(month <= earliestMonth)
                        Spacer(); Text(month.formatted(.dateTime.month(.wide).year())).font(.headline); Spacer()
                        Button { move(1) } label: { Image(systemName: "chevron.right").padding(10) }.disabled(Calendar.current.isDate(month, equalTo: .now, toGranularity: .month))
                    }
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(Array(Calendar.current.veryShortStandaloneWeekdaySymbols.enumerated()), id: \.offset) { _, symbol in Text(symbol).font(.caption).foregroundStyle(.secondary) }
                        let offset = (Calendar.current.component(.weekday, from: month) - 1)
                        ForEach(0..<offset, id: \.self) { _ in Color.clear.frame(height: 36) }
                        ForEach(1..<(Calendar.current.range(of: .day, in: .month, for: month)!.count + 1), id: \.self) { day in
                            let date = Calendar.current.date(byAdding: .day, value: day - 1, to: month)!
                            let recorded = item.history.contains(TrackedItem.dayKey(date))
                            Button { store.change(id) { $0.toggle(date) } } label: { Text("\(day)").font(.subheadline.weight(.medium)).frame(maxWidth: .infinity).frame(height: 36).background(recorded ? Color.accent : Color.accent.opacity(0.06), in: Circle()).foregroundStyle(recorded ? .white : .primary) }.disabled(date > .now || item.deletedAt != nil).accessibilityLabel("\(date.formatted(date: .complete, time: .omitted)), \(recorded ? "recorded" : "not recorded")")
                        }
                    }
                    Text("Tap a day to add or remove a record.").font(.footnote).foregroundStyle(.secondary)
                    DatePicker("Jump to month", selection: $month, in: earliestMonth...Date.now, displayedComponents: .date).onChange(of: month) { _, date in month = Calendar.current.dateInterval(of: .month, for: date)!.start }
                    Text("RECENT HISTORY").font(.caption.weight(.bold)).tracking(1.5).foregroundStyle(.secondary)
                    ForEach(item.history.sorted().reversed(), id: \.self) { key in if let date = TrackedItem.date(key) { Label(date.formatted(date: .long, time: .omitted), systemImage: "checkmark.circle.fill").foregroundStyle(Color.accent) } }
                }.padding(24)
            }.background(Color.paper).navigationTitle(item.title).navigationBarTitleDisplayMode(.inline)
        }
    }
    var earliestMonth: Date { Calendar.current.date(from: DateComponents(year: 2010, month: 1, day: 1))! }
    func move(_ n: Int) { month = Calendar.current.date(byAdding: .month, value: n, to: month)! }
}
