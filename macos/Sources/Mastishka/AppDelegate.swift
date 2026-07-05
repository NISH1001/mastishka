import AppKit
import MastishkaCore
import ServiceManagement

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let session = SitSessionController()
    private let testGong = GongPlayer()

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

        // Gong: size, volume, test — persisted in UserDefaults, read by GongPlayer.
        let gongItem = NSMenuItem(title: "Gong", action: nil, keyEquivalent: "")
        let gongMenu = NSMenu()
        for size in GongPlayer.sizes {
            let item = NSMenuItem(title: size.capitalized, action: #selector(setGongSize(_:)), keyEquivalent: "")
            item.target = self
            item.representedObject = size
            item.state = GongPlayer.size == size ? .on : .off
            gongMenu.addItem(item)
        }
        gongMenu.addItem(.separator())
        for volume in GongPlayer.volumes {
            let item = NSMenuItem(title: "\(Int(volume * 100))%", action: #selector(setGongVolume(_:)), keyEquivalent: "")
            item.target = self
            item.representedObject = volume
            item.state = abs(GongPlayer.volume - volume) < 0.01 ? .on : .off
            gongMenu.addItem(item)
        }
        gongMenu.addItem(.separator())
        let test = NSMenuItem(title: "Test gong", action: #selector(playTestGong), keyEquivalent: "")
        test.target = self
        gongMenu.addItem(test)
        gongItem.submenu = gongMenu
        menu.addItem(gongItem)

        let folder = NSMenuItem(title: "Open sits folder", action: #selector(openSitsFolder), keyEquivalent: "")
        folder.target = self
        menu.addItem(folder)

        let login = NSMenuItem(title: "Launch at login", action: #selector(toggleLaunchAtLogin), keyEquivalent: "")
        login.target = self
        login.state = SMAppService.mainApp.status == .enabled ? .on : .off
        menu.addItem(login)

        menu.addItem(.separator())
        menu.addItem(NSMenuItem(title: "Quit Mastishka", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"))
        return menu
    }

    @objc private func setGongSize(_ sender: NSMenuItem) {
        GongPlayer.size = sender.representedObject as? String ?? "medium"
        statusItem.menu = buildMenu()
    }

    @objc private func setGongVolume(_ sender: NSMenuItem) {
        GongPlayer.volume = sender.representedObject as? Float ?? 0.8
        statusItem.menu = buildMenu()
    }

    @objc private func playTestGong() {
        testGong.play()
    }

    @objc private func toggleLaunchAtLogin() {
        let service = SMAppService.mainApp
        if service.status == .enabled {
            try? service.unregister()
        } else {
            try? service.register()
        }
        statusItem.menu = buildMenu()
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
