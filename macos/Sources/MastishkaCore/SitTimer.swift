import Foundation

/// The sit's rules as a pure value type, mirroring the Android app's `TimerState`:
/// count down to zero, ring the gong exactly once, and never stop counting — overtime
/// accrues until the sitter ends the sit. The caller owns real time; it feeds wall-clock
/// derived elapsed into `tick` and executes the returned effects.
public struct SitTimer: Sendable, Equatable {
    public enum Effect: Sendable, Equatable {
        case ringGong
    }

    public let startedAtEpochMs: Int64
    public let plannedMillis: Int64
    public private(set) var elapsedMillis: Int64 = 0
    public private(set) var gongRung = false

    public init(startedAtEpochMs: Int64, plannedMillis: Int64) {
        self.startedAtEpochMs = startedAtEpochMs
        self.plannedMillis = plannedMillis
    }

    public var remainingMillis: Int64 { max(0, plannedMillis - elapsedMillis) }
    public var overtimeMillis: Int64 { max(0, elapsedMillis - plannedMillis) }
    public var inOvertime: Bool { elapsedMillis >= plannedMillis }

    /// Advance to an absolute elapsed time (never backward — a clock adjustment must not
    /// un-sit the sitter). Returns `.ringGong` on the tick that reaches or jumps past zero,
    /// even if the machine slept through the moment itself.
    public mutating func tick(elapsedMillis: Int64) -> [Effect] {
        self.elapsedMillis = max(self.elapsedMillis, elapsedMillis)
        guard !gongRung, self.elapsedMillis >= plannedMillis else { return [] }
        gongRung = true
        return [.ringGong]
    }
}
