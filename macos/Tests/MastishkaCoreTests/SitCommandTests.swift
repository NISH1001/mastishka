import Foundation
import Testing
@testable import MastishkaCore

// mastishka://sit?practice=<slug>&minutes=<int>&autostart=0|1&source=<app>
// (specs/ecosystem-protocol.md §1)

@Test func parsesFullCommand() {
    let c = SitCommand(url: URL(string: "mastishka://sit?practice=anapana&minutes=10&autostart=1&source=vishrama")!)
    #expect(c == SitCommand(practice: "anapana", minutes: 10, autostart: true, source: "vishrama", prompt: nil))
}

@Test func defaultsWhenParamsMissing() {
    let c = SitCommand(url: URL(string: "mastishka://sit")!)
    #expect(c == SitCommand(practice: nil, minutes: nil, autostart: false, source: nil, prompt: nil))
}

// The sender's own words shown on the sit screen (percent-encoded in the URL) —
// e.g. vishrama passes its break prompt instead of mastishka showing a hardcoded label.
@Test func parsesPromptText() {
    let c = SitCommand(url: URL(string: "mastishka://sit?minutes=10&prompt=Anapana%20%E2%80%94%20observe%20your%20breath")!)
    #expect(c?.prompt == "Anapana — observe your breath")
}

@Test func blankPromptBecomesNil() {
    #expect(SitCommand(url: URL(string: "mastishka://sit?prompt=%20%20")!)?.prompt == nil)
}

@Test func invalidMinutesIgnored() {
    #expect(SitCommand(url: URL(string: "mastishka://sit?minutes=abc")!)?.minutes == nil)
    #expect(SitCommand(url: URL(string: "mastishka://sit?minutes=-5")!)?.minutes == nil)
}

@Test func nonSitURLRejected() {
    #expect(SitCommand(url: URL(string: "mastishka://other")!) == nil)
    #expect(SitCommand(url: URL(string: "https://sit?minutes=10")!) == nil)
}
