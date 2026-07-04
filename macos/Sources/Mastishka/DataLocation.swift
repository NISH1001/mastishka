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

    /// Stable per-device id naming this Mac's sit file (e.g. `mac-m3-max-1a2b3c`):
    /// chip name + a slice of the hardware UUID, so it's readable, unique across
    /// same-model Macs, and immune to (IT-managed) hostname changes. Generated once,
    /// persisted, never recomputed.
    static func deviceId() -> String {
        if let existing = UserDefaults.standard.string(forKey: "deviceId"), !existing.isEmpty {
            return existing
        }
        let id = "mac-\(chipSlug())-\(hardwareUUIDSlice())"
        UserDefaults.standard.set(id, forKey: "deviceId")
        return id
    }

    /// "Apple M3 Max" → "m3-max".
    private static func chipSlug() -> String {
        var size = 0
        sysctlbyname("machdep.cpu.brand_string", nil, &size, nil, 0)
        var chars = [CChar](repeating: 0, count: size)
        sysctlbyname("machdep.cpu.brand_string", &chars, &size, nil, 0)
        let brand = String(cString: chars)
            .replacingOccurrences(of: "Apple ", with: "")
            .lowercased()
            .replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
        return brand.isEmpty ? "mac" : brand
    }

    private static func hardwareUUIDSlice() -> String {
        var uuid = uuid_t(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        var timeout = timespec(tv_sec: 1, tv_nsec: 0)
        guard gethostuuid(&uuid, &timeout) == 0 else { return "0" }
        let bytes = [uuid.0, uuid.1, uuid.2]
        return bytes.map { String(format: "%02x", $0) }.joined()
    }
}
