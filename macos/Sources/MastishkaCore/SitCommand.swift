import Foundation

/// A parsed `mastishka://sit` command (specs/ecosystem-protocol.md §1) — how vishrama (or
/// anything else) asks for a sit. All parameters optional; bad values degrade to nil, never fail.
public struct SitCommand: Sendable, Equatable {
    public let practice: String?
    public let minutes: Int?
    public let autostart: Bool
    public let source: String?

    public init(practice: String?, minutes: Int?, autostart: Bool, source: String?) {
        self.practice = practice
        self.minutes = minutes
        self.autostart = autostart
        self.source = source
    }

    public init?(url: URL) {
        guard url.scheme == "mastishka", url.host == "sit" else { return nil }
        let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
        func value(_ name: String) -> String? {
            items.first(where: { $0.name == name })?.value
        }
        let parsedMinutes = value("minutes").flatMap(Int.init)
        self.init(
            practice: value("practice")?.lowercased(),
            minutes: (parsedMinutes ?? 0) > 0 ? parsedMinutes : nil,
            autostart: value("autostart") == "1",
            source: value("source")
        )
    }
}
