import Foundation
import Testing
@testable import MastishkaCore

// SitRecord must speak schema 1 exactly (specs/ecosystem-protocol.md §2) — the same JSON the
// Android app writes to its sit file and reads back on import.

// Shaped exactly like a line from the Android app's sits/<device>.jsonl (synthetic values).
private let androidLine = """
    {"startedAt":1751500000000,"plannedMillis":1800000,"totalMillis":2046573,"calmness":3,\
    "people":["ma","buwa"],"notes":"calm evening","type":"Vipassana",\
    "hrAvg":62,"hrMin":55,"hrMax":78,"hrSeries":"60,62,64"}
    """

@Test func decodesAnAndroidLine() throws {
    let r = try SitRecord(jsonLine: androidLine)
    #expect(r.startedAt == 1751500000000)
    #expect(r.plannedMillis == 1_800_000)
    #expect(r.totalMillis == 2_046_573)
    #expect(r.calmness == 3)
    #expect(r.people == ["ma", "buwa"])
    #expect(r.notes == "calm evening")
    #expect(r.type == "Vipassana")
    #expect(r.hrAvg == 62)
    #expect(r.hrSeries == "60,62,64")
}

@Test func roundTripsThroughJsonLine() throws {
    let original = try SitRecord(jsonLine: androidLine)
    let reparsed = try SitRecord(jsonLine: original.jsonLine())
    #expect(original == reparsed)
}

@Test func jsonLineIsSingleLineWithAllSchemaFields() {
    let line = SitRecord(startedAt: 123, plannedMillis: 60_000, totalMillis: 61_000).jsonLine()
    #expect(!line.contains("\n"))
    for field in ["startedAt", "plannedMillis", "totalMillis", "calmness", "people",
                  "notes", "type", "hrAvg", "hrMin", "hrMax", "hrSeries"] {
        #expect(line.contains("\"\(field)\""), "missing \(field)")
    }
}

@Test func missingFieldsFallBackToDefaults() throws {
    let r = try SitRecord(jsonLine: #"{"startedAt":9,"plannedMillis":1,"totalMillis":2}"#)
    #expect(r.calmness == 0)
    #expect(r.people.isEmpty)
    #expect(r.notes.isEmpty)
    #expect(r.type == "Meditation")
    #expect(r.hrAvg == 0)
    #expect(r.hrSeries.isEmpty)
}
