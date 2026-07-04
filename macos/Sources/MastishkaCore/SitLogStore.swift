import Foundation

/// Append-only jsonl store over the shared sits folder (specs/ecosystem-protocol.md §3).
/// This device appends ONLY to its own `<deviceId>.jsonl`; `readAll` unions every device's
/// file, skips corrupt lines (a torn mid-sync line must never poison a file), dedupes by
/// `startedAt`, and sorts. There is no merge step — consolidation is read-time only.
public struct SitLogStore: Sendable {
    public let directory: URL
    public let deviceId: String

    public var deviceFile: URL { directory.appendingPathComponent("\(deviceId).jsonl") }

    public init(directory: URL, deviceId: String) {
        self.directory = directory
        self.deviceId = deviceId
    }

    /// Append one sit to this device's file, creating the folder/file on first use.
    public func append(_ record: SitRecord) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let line = Data((record.jsonLine() + "\n").utf8)
        if let handle = try? FileHandle(forWritingTo: deviceFile) {
            defer { try? handle.close() }
            try handle.seekToEnd()
            try handle.write(contentsOf: line)
        } else {
            try line.write(to: deviceFile)
        }
    }

    /// The union of all `*.jsonl` in the folder, deduped by `startedAt`, sorted ascending.
    public func readAll() -> [SitRecord] {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: nil
        )) ?? []
        var seen = Set<Int64>()
        var records: [SitRecord] = []
        for file in files where file.pathExtension == "jsonl" {
            // Reads fail soft (Drive placeholder files may be temporarily unreadable).
            guard let text = try? String(contentsOf: file, encoding: .utf8) else { continue }
            for line in text.split(separator: "\n") {
                guard let record = try? SitRecord(jsonLine: String(line)) else { continue }
                if seen.insert(record.startedAt).inserted {
                    records.append(record)
                }
            }
        }
        return records.sorted { $0.startedAt < $1.startedAt }
    }
}
