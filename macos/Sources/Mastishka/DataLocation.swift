import Foundation

/// Where sits live (specs/ecosystem-protocol.md §3–4): user override → the Google Drive
/// desktop mount's `nishparadox/mastishka` → local Application Support fallback.
enum DataLocation {
    /// `defaults write dev.nishparadox.mastishka dataRoot <path>` overrides autodetection.
    static func sitsDirectory() -> URL {
        dataRoot().appendingPathComponent("sits")
    }

    static func fallbackSitsDirectory() -> URL {
        localRoot().appendingPathComponent("sits")
    }

    static func dataRoot() -> URL {
        if let override = UserDefaults.standard.string(forKey: "dataRoot"), !override.isEmpty {
            return URL(fileURLWithPath: (override as NSString).expandingTildeInPath)
        }
        if let drive = googleDriveRoot() {
            return drive
        }
        return localRoot()
    }

    /// The Drive folder name embeds the account email, so glob `GoogleDrive-*`.
    private static func googleDriveRoot() -> URL? {
        let cloud = FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("Library/CloudStorage")
        let mounts = (try? FileManager.default.contentsOfDirectory(
            at: cloud, includingPropertiesForKeys: nil
        )) ?? []
        for mount in mounts where mount.lastPathComponent.hasPrefix("GoogleDrive-") {
            let root = mount.appendingPathComponent("My Drive/nishparadox/mastishka")
            if FileManager.default.fileExists(atPath: root.path) {
                return root
            }
        }
        return nil
    }

    private static func localRoot() -> URL {
        FileManager.default.homeDirectoryForCurrentUser
            .appendingPathComponent("Library/Application Support/Mastishka")
    }

    /// Stable per-device id naming this Mac's sit file (e.g. `mac-nishans-m3-max`).
    /// Generated once from the hostname, persisted, never recomputed.
    static func deviceId() -> String {
        if let existing = UserDefaults.standard.string(forKey: "deviceId"), !existing.isEmpty {
            return existing
        }
        let host = ProcessInfo.processInfo.hostName
            .replacingOccurrences(of: ".local", with: "")
            .lowercased()
            .replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
        let id = "mac-\(host.isEmpty ? "unknown" : host)"
        UserDefaults.standard.set(id, forKey: "deviceId")
        return id
    }
}
