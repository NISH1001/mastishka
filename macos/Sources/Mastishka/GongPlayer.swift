import AVFoundation

/// Plays the bundled gong (same additive synthesis as the Android app, rendered to AAC by
/// `scripts/generate_gong.py --m4a` because AVFoundation can't decode Ogg Vorbis).
@MainActor
final class GongPlayer {
    private var player: AVAudioPlayer?

    func play() {
        guard let url = Bundle.module.url(forResource: "gong_medium", withExtension: "m4a") else { return }
        player = try? AVAudioPlayer(contentsOf: url)
        player?.volume = 0.8
        player?.play()
    }
}
