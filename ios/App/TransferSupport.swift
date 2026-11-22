import Foundation

/// Development-device transfer bridge. All transfers run inside the app sandbox.
enum TransferSupport {
    static func runIfRequested() throws {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        guard arguments.contains("--export-transfer") || arguments.contains("--import-transfer") else { return }
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("Transfer", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let current = try TrackerStorage.load()
        if arguments.contains("--import-transfer") {
            let incoming = try JSONDecoder().decode([TrackedItem].self, from: Data(contentsOf: directory.appendingPathComponent("incoming.json")))
            guard Set(incoming.map(\.id)).count == incoming.count,
                  incoming.allSatisfy({ !$0.id.isEmpty && !$0.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.intervalDays >= 0 && $0.history.allSatisfy { TrackedItem.date($0) != nil } }) else {
                throw NSError(domain: "LastTimeTransfer", code: 1, userInfo: [NSLocalizedDescriptionKey: "The transfer file contains invalid records."])
            }
            // A fresh backup precedes every import, including repeated imports.
            try encoder.encode(current).write(to: directory.appendingPathComponent("before-\(UUID().uuidString).json"), options: .atomic)
            try TrackerStorage.save(incoming)
        }
        let verified = try TrackerStorage.load()
        try encoder.encode(verified).write(to: directory.appendingPathComponent("current.json"), options: .atomic)
        #endif
    }
}
