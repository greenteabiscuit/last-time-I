import XCTest
@testable import LastTimeI
final class TrackerTests: XCTestCase {
    func testFirstLaunchPersistsStableIDsForWidgetActions() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let file = directory.appendingPathComponent("items.json")
        let first = try TrackerStorage.load(from: file)
        XCTAssertEqual(first.count, 6)
        XCTAssertEqual(try TrackerStorage.load(from: file), first)
        XCTAssertTrue(first.allSatisfy { $0.history.isEmpty })
    }
    func testRecordDeduplicatesAndToggleRecomputesLatest() {
        var item = TrackedItem(title: "Test")
        let a = TrackedItem.date("2026-11-01")!, b = TrackedItem.date("2026-11-02")!
        item.record(a); item.record(a); item.record(b)
        XCTAssertEqual(item.history.count, 2)
        item.toggle(b); XCTAssertEqual(item.lastDate, a)
        item.toggle(a); XCTAssertNil(item.lastDate)
    }
    func testOverdueBoundaryAndCalendarDays() {
        var item = TrackedItem(title: "Test", intervalDays: 7)
        item.record(TrackedItem.date("2026-11-01")!)
        XCTAssertFalse(item.overdue(at: TrackedItem.date("2026-11-08")!))
        XCTAssertTrue(item.overdue(at: TrackedItem.date("2026-11-09")!))
    }
    func testSnoozeBoundaryAndWidgetExclusions() {
        let now = Date.now
        var item = TrackedItem(title: "Test", snoozedUntil: now.addingTimeInterval(604800))
        XCTAssertFalse(item.eligible(at: now))
        XCTAssertTrue(item.eligible(at: now.addingTimeInterval(604800)))
        item.tags = ["HEALTH"]; XCTAssertFalse(item.eligible(at: now.addingTimeInterval(604800)))
        item.tags = []; item.deletedAt = now; XCTAssertFalse(item.eligible(at: now.addingTimeInterval(604800)))
    }
    func testSelectionAndPersistenceRoundtrip() throws {
        let items = (1...12).map { n in var item = TrackedItem(title: "\(n)"); item.record(TrackedItem.date(String(format: "2026-11-%02d", n))!); return item }
        let selected = TrackerStorage.selection(items)
        XCTAssertEqual(selected.latest.map(\.title), ["12", "11", "10", "9"])
        XCTAssertEqual(selected.oldest.map(\.title), ["1", "2", "3", "4"])
        XCTAssertEqual(selected.extra, 4)
        XCTAssertEqual(try JSONDecoder().decode([TrackedItem].self, from: JSONEncoder().encode(items)), items)
        XCTAssertEqual(TrackedItem.normalizeTags("#Home, home, Health,  "), ["Home", "Health"])
    }
}
