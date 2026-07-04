import Foundation

/// Clock formatting, matching the Android app: `M:SS`, or `H:MM:SS` past an hour.
public func formatClock(_ millis: Int64) -> String {
    let totalSeconds = max(0, millis) / 1000
    let hours = totalSeconds / 3600
    let minutes = (totalSeconds % 3600) / 60
    let seconds = totalSeconds % 60
    if hours > 0 {
        return String(format: "%d:%02d:%02d", hours, minutes, seconds)
    }
    return String(format: "%d:%02d", minutes, seconds)
}
