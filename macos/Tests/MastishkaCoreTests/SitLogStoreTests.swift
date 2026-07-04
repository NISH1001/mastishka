import Foundation
import Testing
@testable import MastishkaCore

private func makeTempDir() -> URL {
    FileManager.default.temporaryDirectory
        .appendingPathComponent("sits-test-\(UUID().uuidString)")
}

@Test func appendThenReadBack() throws {
    let dir = makeTempDir()
    defer { try? FileManager.default.removeItem(at: dir) }
    let store = SitLogStore(directory: dir, deviceId: "mac-test")
    try store.append(SitRecord(startedAt: 100, plannedMillis: 60_000, totalMillis: 61_000))
    try store.append(SitRecord(startedAt: 200, plannedMillis: 60_000, totalMillis: 60_000))
    #expect(store.readAll().map(\.startedAt) == [100, 200])
}

// The ecosystem payoff: another device's file in the same folder is read into the union.
@Test func unionsAllDeviceFilesSortedAndDeduped() throws {
    let dir = makeTempDir()
    defer { try? FileManager.default.removeItem(at: dir) }
    let store = SitLogStore(directory: dir, deviceId: "mac-test")
    try store.append(SitRecord(startedAt: 300, plannedMillis: 1, totalMillis: 1))
    let other = SitRecord(startedAt: 100, plannedMillis: 1, totalMillis: 1)
    let duplicate = SitRecord(startedAt: 300, plannedMillis: 1, totalMillis: 1)
    try (other.jsonLine() + "\n" + duplicate.jsonLine() + "\n")
        .write(to: dir.appendingPathComponent("pixel-9-abc.jsonl"), atomically: true, encoding: .utf8)
    #expect(store.readAll().map(\.startedAt) == [100, 300])
}

@Test func corruptLinesAreSkippedNeverFatal() throws {
    let dir = makeTempDir()
    defer { try? FileManager.default.removeItem(at: dir) }
    let store = SitLogStore(directory: dir, deviceId: "mac-test")
    try store.append(SitRecord(startedAt: 100, plannedMillis: 1, totalMillis: 1))
    let file = dir.appendingPathComponent("mac-test.jsonl")
    let good = try String(contentsOf: file, encoding: .utf8)
    try (good + "not json\n{\"torn\":\n").write(to: file, atomically: true, encoding: .utf8)
    try store.append(SitRecord(startedAt: 200, plannedMillis: 1, totalMillis: 1))
    #expect(store.readAll().map(\.startedAt) == [100, 200])
}

@Test func readAllOnMissingDirectoryIsEmpty() {
    let store = SitLogStore(directory: makeTempDir(), deviceId: "mac-test")
    #expect(store.readAll().isEmpty)
}
