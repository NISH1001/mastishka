import Foundation

/// One sit in the ecosystem's canonical schema 1 (specs/ecosystem-protocol.md §2) — the exact
/// JSON shape the Android app writes, so files are interchangeable across devices. Writers emit
/// every field; readers tolerate missing fields (defaults below) and ignore unknown ones.
public struct SitRecord: Codable, Sendable, Equatable {
    /// Epoch milliseconds — the record's identity: readers dedupe by this.
    public var startedAt: Int64
    public var plannedMillis: Int64
    public var totalMillis: Int64
    /// 1–5; 0 = unset.
    public var calmness: Int
    public var people: [String]
    public var notes: String
    public var type: String
    public var hrAvg: Int
    public var hrMin: Int
    public var hrMax: Int
    /// Comma-separated bpm samples; "" = absent.
    public var hrSeries: String

    public init(
        startedAt: Int64,
        plannedMillis: Int64,
        totalMillis: Int64,
        calmness: Int = 0,
        people: [String] = [],
        notes: String = "",
        type: String = "Meditation",
        hrAvg: Int = 0,
        hrMin: Int = 0,
        hrMax: Int = 0,
        hrSeries: String = ""
    ) {
        self.startedAt = startedAt
        self.plannedMillis = plannedMillis
        self.totalMillis = totalMillis
        self.calmness = calmness
        self.people = people
        self.notes = notes
        self.type = type
        self.hrAvg = hrAvg
        self.hrMin = hrMin
        self.hrMax = hrMax
        self.hrSeries = hrSeries
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        startedAt = try c.decode(Int64.self, forKey: .startedAt)
        plannedMillis = try c.decodeIfPresent(Int64.self, forKey: .plannedMillis) ?? 0
        totalMillis = try c.decodeIfPresent(Int64.self, forKey: .totalMillis) ?? 0
        calmness = try c.decodeIfPresent(Int.self, forKey: .calmness) ?? 0
        people = try c.decodeIfPresent([String].self, forKey: .people) ?? []
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        type = try c.decodeIfPresent(String.self, forKey: .type) ?? "Meditation"
        hrAvg = try c.decodeIfPresent(Int.self, forKey: .hrAvg) ?? 0
        hrMin = try c.decodeIfPresent(Int.self, forKey: .hrMin) ?? 0
        hrMax = try c.decodeIfPresent(Int.self, forKey: .hrMax) ?? 0
        hrSeries = try c.decodeIfPresent(String.self, forKey: .hrSeries) ?? ""
    }

    /// Parse one jsonl line.
    public init(jsonLine: String) throws {
        self = try JSONDecoder().decode(SitRecord.self, from: Data(jsonLine.utf8))
    }

    /// Serialize as one single-line JSON object (a jsonl line, no trailing newline).
    public func jsonLine() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        // Encoding a Codable struct of primitives cannot fail.
        let data = (try? encoder.encode(self)) ?? Data("{}".utf8)
        return String(decoding: data, as: UTF8.self)
    }
}
