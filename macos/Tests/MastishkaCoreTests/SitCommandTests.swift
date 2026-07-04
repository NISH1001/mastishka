import Foundation
import Testing
@testable import MastishkaCore

// mastishka://sit?practice=<slug>&minutes=<int>&autostart=0|1&source=<app>
// (specs/ecosystem-protocol.md §1)

@Test func parsesFullCommand() {
    let c = SitCommand(url: URL(string: "mastishka://sit?practice=anapana&minutes=10&autostart=1&source=vishrama")!)
    #expect(c == SitCommand(practice: "anapana", minutes: 10, autostart: true, source: "vishrama"))
}

@Test func defaultsWhenParamsMissing() {
    let c = SitCommand(url: URL(string: "mastishka://sit")!)
    #expect(c == SitCommand(practice: nil, minutes: nil, autostart: false, source: nil))
}

@Test func invalidMinutesIgnored() {
    #expect(SitCommand(url: URL(string: "mastishka://sit?minutes=abc")!)?.minutes == nil)
    #expect(SitCommand(url: URL(string: "mastishka://sit?minutes=-5")!)?.minutes == nil)
}

@Test func nonSitURLRejected() {
    #expect(SitCommand(url: URL(string: "mastishka://other")!) == nil)
    #expect(SitCommand(url: URL(string: "https://sit?minutes=10")!) == nil)
}
