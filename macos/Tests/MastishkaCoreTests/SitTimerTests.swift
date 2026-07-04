import Foundation
import Testing
@testable import MastishkaCore

// The signature semantics, ported from the Android app: the gong rings exactly once at zero
// without interrupting anything, and counting never stops — overtime accrues until end.

@Test func countsDownBeforeZero() {
    var t = SitTimer(startedAtEpochMs: 1000, plannedMillis: 60_000)
    let effects = t.tick(elapsedMillis: 10_000)
    #expect(effects.isEmpty)
    #expect(t.remainingMillis == 50_000)
    #expect(!t.inOvertime)
    #expect(t.overtimeMillis == 0)
}

@Test func gongRingsExactlyOnceAtZero() {
    var t = SitTimer(startedAtEpochMs: 0, plannedMillis: 60_000)
    #expect(t.tick(elapsedMillis: 59_900).isEmpty)
    #expect(t.tick(elapsedMillis: 60_000) == [.ringGong])
    #expect(t.tick(elapsedMillis: 60_200).isEmpty)
    #expect(t.tick(elapsedMillis: 120_000).isEmpty)
}

@Test func countingContinuesIntoOvertime() {
    var t = SitTimer(startedAtEpochMs: 0, plannedMillis: 60_000)
    _ = t.tick(elapsedMillis: 90_000)
    #expect(t.inOvertime)
    #expect(t.overtimeMillis == 30_000)
    #expect(t.remainingMillis == 0)
    #expect(t.elapsedMillis == 90_000)
}

// Mac slept through zero: the first tick after wake jumps far past planned —
// the gong must still ring (once) on that tick.
@Test func gongRingsOnTickThatJumpsPastZero() {
    var t = SitTimer(startedAtEpochMs: 0, plannedMillis: 60_000)
    #expect(t.tick(elapsedMillis: 30_000).isEmpty)
    #expect(t.tick(elapsedMillis: 300_000) == [.ringGong])
}

// Wall-clock elapsed can regress if the system clock adjusts; the sit must never run backward.
@Test func elapsedNeverDecreases() {
    var t = SitTimer(startedAtEpochMs: 0, plannedMillis: 60_000)
    _ = t.tick(elapsedMillis: 30_000)
    _ = t.tick(elapsedMillis: 20_000)
    #expect(t.elapsedMillis == 30_000)
}
