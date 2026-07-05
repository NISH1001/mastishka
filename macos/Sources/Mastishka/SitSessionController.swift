import AppKit
import SwiftUI
import MastishkaCore

/// Drives one sit: owns the window, the clock, the gong, and the save. Elapsed time is always
/// recomputed from the wall-clock start (never accumulated ticks), so timer coalescing or a
/// sleep/wake gap can't lose time — and `SitTimer` rings the gong on the first tick past zero.
@MainActor
final class SitSessionController: NSObject, ObservableObject, NSWindowDelegate {
    enum Phase {
        case idle
        case sitting
        case review
    }

    @Published private(set) var phase: Phase = .idle
    @Published private(set) var timer = SitTimer(startedAtEpochMs: 0, plannedMillis: 0)
    @Published private(set) var practice = "Meditation"
    /// The sender's words (URL `prompt` param), shown under the practice label; nil for menu sits.
    @Published private(set) var prompt: String?
    /// Who rang the doorbell (URL `source` param). Arms the completion callback — a sit
    /// started from the menu has no source and never phones anyone (protocol §1).
    private var source: String?

    private var window: NSWindow?
    private var tick: Timer?
    private var startedAt = Date()
    private let gong = GongPlayer()
    private let sleepless = PowerAssertion()

    /// Begin a sit now. Ignored if one is already running — nothing interrupts a live sit.
    func start(minutes: Int, practice: String, prompt: String? = nil, source: String? = nil) {
        guard phase == .idle else {
            window?.makeKeyAndOrderFront(nil)
            return
        }
        self.practice = practice
        self.prompt = prompt
        self.source = source
        startedAt = Date()
        timer = SitTimer(
            startedAtEpochMs: Int64(startedAt.timeIntervalSince1970 * 1000),
            plannedMillis: Int64(minutes) * 60_000
        )
        phase = .sitting
        sleepless.begin(reason: "Mastishka sit in progress")
        showWindow()

        tick = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.onTick() }
        }
    }

    private func onTick() {
        guard phase == .sitting else { return }
        let elapsed = Int64(Date().timeIntervalSince(startedAt) * 1000)
        for effect in timer.tick(elapsedMillis: elapsed) {
            switch effect {
            case .ringGong: gong.play()
            }
        }
    }

    /// End the sit: freeze the clock and move to the save/discard review.
    func end() {
        guard phase == .sitting else { return }
        onTick()
        tick?.invalidate()
        tick = nil
        sleepless.release()
        phase = .review
    }

    func save(calmness: Int, notes: String) {
        let record = SitRecord(
            startedAt: timer.startedAtEpochMs,
            plannedMillis: timer.plannedMillis,
            totalMillis: timer.elapsedMillis,
            calmness: calmness,
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
            type: practice
        )
        // Fail-soft: a Drive hiccup must not eat the moment — fall back to Application Support.
        let store = SitLogStore(directory: DataLocation.sitsDirectory(), deviceId: DataLocation.deviceId())
        do {
            try store.append(record)
        } catch {
            let fallback = SitLogStore(directory: DataLocation.fallbackSitsDirectory(), deviceId: DataLocation.deviceId())
            try? fallback.append(record)
        }
        // Close the round trip: a vishrama-triggered sit reports back on save, so the break
        // is credited the moment the sit actually ends (protocol §1; failure silently ignored).
        if source == "vishrama",
           let url = URL(string: "vishrama://sitCompleted?startedAt=\(record.startedAt)&totalMillis=\(record.totalMillis)") {
            NSWorkspace.shared.open(url)
        }
        finish()
    }

    func discard() {
        finish()
    }

    private func finish() {
        tick?.invalidate()
        tick = nil
        sleepless.release()
        phase = .idle
        source = nil
        window?.delegate = nil
        window?.close()
        window = nil
    }

    private func showWindow() {
        if window == nil {
            let w = NSWindow(
                contentRect: NSRect(x: 0, y: 0, width: 560, height: 640),
                styleMask: [.titled, .closable, .fullSizeContentView],
                backing: .buffered,
                defer: false
            )
            w.titlebarAppearsTransparent = true
            w.titleVisibility = .hidden
            w.appearance = NSAppearance(named: .darkAqua)
            w.isReleasedWhenClosed = false
            w.center()
            w.delegate = self
            w.contentView = NSHostingView(rootView: SitView(session: self))
            window = w
        }
        window?.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }

    /// Closing the window mid-sit ends the sit into review instead of losing it.
    func windowShouldClose(_ sender: NSWindow) -> Bool {
        if phase == .sitting {
            end()
            return false
        }
        if phase == .review {
            discard()
            return false
        }
        return true
    }
}
