import AppKit

// Menu-bar-only app (LSUIElement in Info.plist): no Dock icon, no main menu — just the
// status item, the sit window when sitting, and the mastishka:// URL listener.
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.accessory)
app.run()
