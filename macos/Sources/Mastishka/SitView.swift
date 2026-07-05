import SwiftUI
import MastishkaCore

// Palette from the vishrama-preview artifact ("Anapana pacer"): vishrama's night gradient,
// mastishka's mint orb, shared ink.
private enum Palette {
    static let backgroundTop = Color(red: 0x17 / 255.0, green: 0x1C / 255.0, blue: 0x29 / 255.0)
    static let backgroundBottom = Color(red: 0x0A / 255.0, green: 0x0D / 255.0, blue: 0x14 / 255.0)
    static let ink = Color(red: 0xE9 / 255.0, green: 0xEE / 255.0, blue: 0xF4 / 255.0)
    // Orb "mastishka mint": core rgb(143,211,194), glow rgb(94,140,127).
    static let core = (r: 143.0 / 255.0, g: 211.0 / 255.0, b: 194.0 / 255.0)
    static let glow = (r: 94.0 / 255.0, g: 140.0 / 255.0, b: 127.0 / 255.0)
    static let mint = Color(red: 143.0 / 255.0, green: 211.0 / 255.0, blue: 194.0 / 255.0)
}

/// The mastishka breath: 10 s raised-cosine, half in, half out.
private let breathCycle: Double = 10
private func breath(_ t: Double) -> Double {
    let x = t.truncatingRemainder(dividingBy: breathCycle) / breathCycle
    return 0.5 - 0.5 * cos(2 * .pi * x)
}

struct SitView: View {
    @ObservedObject var session: SitSessionController
    // Neutral pre-selected, matching the Android post-sit check (1/2/3 = lower/neutral/higher).
    @State private var calmness = 2
    @State private var notes = ""
    private let appeared = Date()

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Palette.backgroundTop, Palette.backgroundBottom],
                startPoint: .top, endPoint: .bottom
            )
            .ignoresSafeArea()

            OrbCanvas(appeared: appeared)

            VStack {
                Text("मस्तिष्क")
                    .font(.system(size: 21, weight: .light))
                    .foregroundStyle(Palette.ink.opacity(0.35))
                    .padding(.top, 44)
                Text(session.practice.uppercased())
                    .font(.system(size: 13, weight: .medium))
                    .tracking(3.5)
                    .foregroundStyle(Palette.ink.opacity(0.5))
                    .padding(.top, 22)
                // The sender's own words (mastishka://sit&prompt=…), serif like the artifact.
                if let prompt = session.prompt {
                    Text(prompt)
                        .font(.system(size: 24, design: .serif))
                        .foregroundStyle(Palette.ink.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 460)
                        .padding(.top, 22)
                }
                Spacer()
                if session.phase == .review {
                    reviewControls
                } else {
                    Button("End sit") { session.end() }
                        .buttonStyle(SoftButtonStyle())
                }
            }
            .padding(.bottom, 40)

            clock

            if session.phase == .sitting {
                BreathHint(appeared: appeared)
            }
        }
        .frame(minWidth: 560, minHeight: 640)
        .onChange(of: session.phase) {
            if session.phase == .sitting {
                calmness = 2
                notes = ""
            }
        }
    }

    private var clock: some View {
        // Countdown to the gong; overtime counts up with a "+", mirroring the Android sit.
        let t = session.timer
        let text = t.inOvertime ? "+\(formatClock(t.overtimeMillis))" : formatClock(t.remainingMillis)
        return Text(text)
            .font(.system(size: 46, weight: .ultraLight))
            .tracking(1)
            .monospacedDigit()
            .foregroundStyle(Palette.ink.opacity(0.88))
            .shadow(color: .black.opacity(0.45), radius: 12, y: 2)
    }

    private var reviewControls: some View {
        VStack(spacing: 18) {
            Text("Sat \(formatClock(session.timer.elapsedMillis))")
                .font(.system(size: 14))
                .foregroundStyle(Palette.ink.opacity(0.5))
            // Three tap-categories, matching the Android post-sit check (1/2/3 = lower/neutral/
            // higher). Leaving it unset (0) means "unrated" — excluded from the calmness charts.
            HStack(spacing: 10) {
                ForEach([(1, "Lower"), (2, "Neutral"), (3, "Higher")], id: \.0) { value, label in
                    let selected = calmness == value
                    Button {
                        calmness = value
                    } label: {
                        Text(label)
                            .font(.system(size: 14))
                            .foregroundStyle(selected ? Palette.backgroundBottom : Palette.ink.opacity(0.85))
                            .padding(.vertical, 8)
                            .padding(.horizontal, 18)
                            .background(
                                selected ? Palette.mint : Palette.ink.opacity(0.12),
                                in: RoundedRectangle(cornerRadius: 8)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            TextField("notes (optional)", text: $notes)
                .textFieldStyle(.plain)
                .multilineTextAlignment(.center)
                .font(.system(size: 14))
                .foregroundStyle(Palette.ink.opacity(0.8))
                .padding(8)
                .background(Palette.ink.opacity(0.08), in: RoundedRectangle(cornerRadius: 7))
                .frame(maxWidth: 320)
            HStack(spacing: 18) {
                Button("Save sit") { session.save(calmness: calmness, notes: notes) }
                    .buttonStyle(SoftButtonStyle())
                Button("Discard") { session.discard() }
                    .buttonStyle(GhostButtonStyle())
            }
        }
    }
}

/// The breathing orb, ported 1:1 from the artifact's Canvas code: swelling aura, gel-gradient
/// body with a wobbling rim (two slow sine harmonics), all phased by the raised-cosine breath.
private struct OrbCanvas: View {
    let appeared: Date

    var body: some View {
        TimelineView(.animation) { timeline in
            Canvas { ctx, size in
                let t: Double = timeline.date.timeIntervalSince(appeared)
                let b: Double = breath(t)
                let cx: Double = size.width / 2
                let cy: Double = size.height * 0.5
                let radius: Double = min(size.width, size.height) * 0.19 * (0.80 + 0.35 * b)
                drawAura(ctx: ctx, size: size, cx: cx, cy: cy, radius: radius, b: b)
                let path = orbPath(cx: cx, cy: cy, radius: radius, t: t)
                fillOrb(ctx: ctx, path: path, cx: cx, cy: cy, radius: radius, b: b)
            }
        }
        .ignoresSafeArea()
    }

    // Outer aura, swelling with the breath.
    private func drawAura(ctx: GraphicsContext, size: CGSize, cx: Double, cy: Double, radius: Double, b: Double) {
        let aura = Gradient(stops: [
            .init(color: color(Palette.glow, alpha: 0.16 + 0.10 * b), location: 0),
            .init(color: color(Palette.glow, alpha: 0), location: 1),
        ])
        ctx.fill(
            Path(CGRect(origin: .zero, size: size)),
            with: .radialGradient(
                aura,
                center: CGPoint(x: cx, y: cy),
                startRadius: radius * 0.4,
                endRadius: radius * 2.6
            )
        )
    }

    // The orb rim: a 72-point polygon wobbled by two slow sine harmonics.
    private func orbPath(cx: Double, cy: Double, radius: Double, t: Double) -> Path {
        var path = Path()
        let n = 72
        for i in 0...n {
            let ang: Double = Double(i) / Double(n) * 2 * .pi
            let wobble: Double = 1 + 0.05 * sin(ang * 3 + t * 1.1) + 0.035 * sin(ang * 5 - t * 0.8)
            let r: Double = radius * wobble
            let point = CGPoint(x: cx + cos(ang) * r, y: cy + sin(ang) * r)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }

    private func fillOrb(ctx: GraphicsContext, path: Path, cx: Double, cy: Double, radius: Double, b: Double) {
        let gel = Gradient(stops: [
            .init(color: color(Palette.core, alpha: 0.34 + 0.10 * b), location: 0),
            .init(color: color(Palette.core, alpha: 0.16 + 0.08 * b), location: 0.65),
            .init(color: color(Palette.core, alpha: 0.04), location: 1),
        ])
        ctx.fill(
            path,
            with: .radialGradient(
                gel,
                center: CGPoint(x: cx - radius * 0.25, y: cy - radius * 0.3),
                startRadius: radius * 0.1,
                endRadius: radius * 1.05
            )
        )
        ctx.stroke(path, with: .color(color(Palette.core, alpha: 0.35 + 0.2 * b)), lineWidth: 1)
    }

    private func color(_ rgb: (r: Double, g: Double, b: Double), alpha: Double) -> Color {
        Color(red: rgb.r, green: rgb.g, blue: rgb.b, opacity: alpha)
    }
}

/// "breathe in / breathe out", following the breath phase (updated at 2 Hz, not render rate).
private struct BreathHint: View {
    let appeared: Date

    var body: some View {
        TimelineView(.periodic(from: .now, by: 0.5)) { timeline in
            let t = timeline.date.timeIntervalSince(appeared)
            let phase = t.truncatingRemainder(dividingBy: breathCycle) / breathCycle
            Text(phase < 0.5 ? "BREATHE IN" : "BREATHE OUT")
                .font(.system(size: 14))
                .tracking(2.5)
                .foregroundStyle(Palette.ink.opacity(0.5))
                .offset(y: 130)
        }
    }
}

private struct SoftButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14))
            .foregroundStyle(Palette.ink)
            .padding(.vertical, 9)
            .padding(.horizontal, 20)
            .background(Palette.ink.opacity(configuration.isPressed ? 0.22 : 0.14), in: RoundedRectangle(cornerRadius: 7))
    }
}

private struct GhostButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14))
            .foregroundStyle(Palette.ink.opacity(configuration.isPressed ? 0.6 : 0.35))
            .padding(.vertical, 9)
            .padding(.horizontal, 20)
    }
}
