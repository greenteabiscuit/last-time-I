import XCTest
final class FlowTests: XCTestCase {
    func testCreateRecordHistoryAndRestore() {
        let app = XCUIApplication()
        app.launch()
        app.buttons["Add item"].tap()
        let name = "UI verification " + String(UUID().uuidString.prefix(4))
        app.textFields["e.g. Changed my toothbrush"].tap()
        app.textFields["e.g. Changed my toothbrush"].typeText(name)
        app.buttons["Save"].tap()
        let today = app.buttons["Record \(name) today"]
        app.swipeUp()
        XCTAssertTrue(today.waitForExistence(timeout: 5))
        today.tap()
        app.swipeDown()
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: .now)!
        let dateLabel = name + ", " + yesterday.formatted(date: .complete, time: .omitted)
        let emptyDay = app.buttons[dateLabel + ", not recorded"]
        XCTAssertTrue(emptyDay.waitForExistence(timeout: 5))
        emptyDay.tap()
        let recordedDay = app.buttons[dateLabel + ", recorded"]
        XCTAssertTrue(recordedDay.waitForExistence(timeout: 5))
        recordedDay.tap()
        app.buttons["Options for \(name)"].tap()
        app.buttons["Snooze widget for 7 days"].tap()
        app.buttons["Options for \(name)"].tap()
        XCTAssertTrue(app.buttons["Unsnooze now"].waitForExistence(timeout: 3))
        app.buttons["Unsnooze now"].tap()
        app.buttons["Options for \(name)"].tap()
        app.buttons["Delete"].tap()
        app.buttons["Recently deleted"].tap()
        XCTAssertTrue(app.staticTexts[name].waitForExistence(timeout: 3))
        app.buttons["Options for \(name)"].tap()
        app.buttons["Restore"].tap()
        app.buttons["Tracked items"].tap()
        app.terminate(); app.launch()
        XCTAssertTrue(app.staticTexts[name].waitForExistence(timeout: 3))
        app.staticTexts[name].tap()
        XCTAssertTrue(app.staticTexts["1 recorded days"].waitForExistence(timeout: 3))
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.lifetime = .keepAlways; add(attachment)
    }
}
