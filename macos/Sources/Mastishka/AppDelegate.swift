import AppKit
import MastishkaCore

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let session = SitSessionController()

    func applicationDidFinishLaunching(_ notification: Notification) {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.squareLength)
        statusItem.button?.image = NSImage(
            systemSymbolName: "circle.circle", accessibilityDescription: "Mastishka"
        )
        statusItem.menu = buildMenu()
    }

    /// mastishka://sit?practice=…&minutes=…&autostart=…&source=… — the ecosystem doorbell
    /// (vishrama's break overlay opens this). A live sit is never interrupted.
    func application(_ application: NSApplication, open urls: [URL]) {
        for url in urls {
            guard let command = SitCommand(url: url) else { continue }
            session.start(
                minutes: command.minutes ?? 10,
                practice: displayName(forSlug: command.practice),
                prompt: command.prompt
            )
            return
        }
    }

    private func buildMenu() -> NSMenu {
        let menu = NSMenu()
        // Disabled header (nil action): which build is sitting in the menu bar.
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "dev"
        menu.addItem(NSMenuItem(title: "Mastishka v\(version)", action: nil, keyEquivalent: ""))
        menu.addItem(.separator())
        for minutes in [1, 2, 5, 10, 15, 20, 30, 45, 60] {
            let item = NSMenuItem(title: "Sit \(minutes) min", action: #selector(beginSit(_:)), keyEquivalent: "")
            item.target = self
            item.tag = minutes
            menu.addItem(item)
        }
        menu.addItem(.separator())
        let folder = NSMenuItem(title: "Open sits folder", action: #selector(openSitsFolder), keyEquivalent: "")
        folder.target = self
        menu.addItem(folder)
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "Quit Mastishka", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        return menu
    }

    @objc private func beginSit(_ sender: NSMenuItem) {
        session.start(minutes: sender.tag, practice: "Meditation")
    }

    @objc private func openSitsFolder() {
        let dir = DataLocation.sitsDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        NSWorkspace.shared.open(dir)
    }

    private func displayName(forSlug slug: String?) -> String {
        switch slug {
        case "vipassana": return "Vipassana"
        case "anapana": return "Anapana"
        case "metta": return "Metta"
        default: return "Meditation"
        }
    }
}
