import AVFoundation

/// Plays the bundled gong (same additive synthesis as the Android app, rendered to AAC by
/// `scripts/generate_gong.py --m4a` because AVFoundation can't decode Ogg Vorbis).
/// Size and volume come from UserDefaults, set via the menu bar's Gong submenu.
@MainActor
final class GongPlayer {
    static let sizes = ["small", "medium", "large"]
    static let volumes: [Float] = [0.25, 0.5, 0.8, 1.0]

    static var size: String {
        get {
            let stored = UserDefaults.standard.string(forKey: "gongSize") ?? "medium"
            return sizes.contains(stored) ? stored : "medium"
        }
        set { UserDefaults.standard.set(newValue, forKey: "gongSize") }
    }

    static var volume: Float {
        get {
            let stored = UserDefaults.standard.object(forKey: "gongVolume") as? Float
            return stored ?? 0.8
        }
        set { UserDefaults.standard.set(newValue, forKey: "gongVolume") }
    }

    private var player: AVAudioPlayer?

    func play() {
        guard let url = Bundle.module.url(forResource: "gong_\(Self.size)", withExtension: "m4a") else { return }
        player = try? AVAudioPlayer(contentsOf: url)
        player?.volume = Self.volume
        player?.play()
    }
}
