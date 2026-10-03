import UIKit
import ComposeApp

/// Supplies the UIKit glass surface that the shared Compose overlay embeds behind its camera
/// controls.
///
/// The shared layer can only approximate Liquid Glass, because the camera image and the avatar are
/// drawn by platform views outside Compose and a Compose fill cannot blur them. Handing a
/// `UIVisualEffectView` back lets the system blur and refract whatever is behind the controls on
/// iOS 26 and later, while older systems keep the shared Compose glass.
final class GlassEffectBackdropViewProvider: NSObject, LiquidGlassBackdropViewProvider {
    /// Whether the running system can draw `UIGlassEffect`. The app deploys to iOS 18.5, so this
    /// stays false on the systems that predate Liquid Glass and the shared side keeps its own glass.
    var isGlassEffectSupported: Bool {
        if #available(iOS 26.0, *) {
            return true
        } else {
            return false
        }
    }

    /// Creates the glass surface for one control.
    ///
    /// - Parameters:
    ///   - tintArgb: The shared theme's tint, packed as ARGB with alpha in the high byte.
    ///   - cornerRadiusPoints: The corner radius in points, ignored when `isCapsule` is true.
    ///   - isCapsule: Whether the corners follow half of the measured height instead of a radius.
    /// - Returns: A view that renders the system glass, or a transparent view of the same shape on
    ///   systems without `UIGlassEffect`, so the shared layer stack keeps its order either way.
    func makeBackdropView(tintArgb: Int64, cornerRadiusPoints: Double, isCapsule: Bool) -> UIView {
        MainActor.assumeIsolated {
            let backdropView = GlassEffectBackdropView(
                cornerRadius: CGFloat(cornerRadiusPoints),
                isCapsule: isCapsule
            )
            backdropView.applyGlass(tintArgb: tintArgb)
            return backdropView
        }
    }

    /// Moves `backdropView` to the tint of the newly selected background preset over
    /// `durationSeconds`, matching the shared side's tone interpolation so the glass and the
    /// foreground colors change together.
    ///
    /// A view this provider did not create cannot carry a glass effect, so it is logged instead of
    /// being updated silently.
    func updateBackdropView(backdropView: UIView, tintArgb: Int64, durationSeconds: Double) {
        MainActor.assumeIsolated {
            guard let glassBackdropView = backdropView as? GlassEffectBackdropView else {
                NSLog("Liquid glass backdrop update skipped: unexpected view %@", String(describing: type(of: backdropView)))
                return
            }
            glassBackdropView.animateGlass(tintArgb: tintArgb, duration: durationSeconds)
        }
    }
}

/// A system glass surface whose corners the shared Compose layer describes.
///
/// `UIGlassEffect` draws its own shape and ignores `layer.cornerRadius`, so the radius is applied
/// through `cornerConfiguration` on every layout pass. A capsule resolves its radius from the
/// measured height, which the shared side cannot know because the control is sized by its content.
final class GlassEffectBackdropView: UIVisualEffectView {
    private let requestedCornerRadius: CGFloat
    private let isCapsule: Bool

    init(cornerRadius: CGFloat, isCapsule: Bool) {
        self.requestedCornerRadius = cornerRadius
        self.isCapsule = isCapsule
        super.init(effect: nil)
        // Compose owns every gesture on the camera screen; the glass is decoration behind it.
        isUserInteractionEnabled = false
    }

    /// This view is only created in code; UIKit still requires the nib initializer to exist.
    required init?(coder: NSCoder) {
        fatalError("GlassEffectBackdropView is created in code, never from a nib")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        applyCornerConfiguration()
    }

    /// The corner radius for `height`: a capsule follows half the height, and a fixed radius is
    /// capped at the same half so a short control cannot draw corners that overlap.
    func resolvedCornerRadius(forHeight height: CGFloat) -> CGFloat {
        let capsuleRadius = max(height / 2, 0)

        return isCapsule ? capsuleRadius : min(requestedCornerRadius, capsuleRadius)
    }

    /// Installs the system glass with `tintArgb`. Does nothing before iOS 26, where the shared
    /// Compose glass is used instead.
    func applyGlass(tintArgb: Int64) {
        guard #available(iOS 26.0, *) else {
            return
        }
        effect = makeGlassEffect(tintArgb: tintArgb)
    }

    /// Animates the glass to `tintArgb`. Apple's glass changes tint by swapping in a new effect,
    /// so the whole effect is replaced inside the animation.
    func animateGlass(tintArgb: Int64, duration: Double) {
        guard #available(iOS 26.0, *) else {
            return
        }
        let glassEffect = makeGlassEffect(tintArgb: tintArgb)
        UIView.animate(withDuration: duration) { [weak self] in
            self?.effect = glassEffect
        }
    }

    @available(iOS 26.0, *)
    private func makeGlassEffect(tintArgb: Int64) -> UIGlassEffect {
        let glassEffect = UIGlassEffect()
        glassEffect.tintColor = UIColor(liquidGlassArgb: tintArgb)
        // Interactive glass reacts to touches it receives, and this view takes none: the controls
        // above it own every gesture.
        glassEffect.isInteractive = false

        return glassEffect
    }

    private func applyCornerConfiguration() {
        guard #available(iOS 26.0, *) else {
            return
        }
        cornerConfiguration = .corners(radius: .fixed(resolvedCornerRadius(forHeight: bounds.height)))
    }
}

private extension UIColor {
    /// Creates a color from the shared theme's tint token, which packs ARGB into one integer with
    /// alpha in the high byte.
    convenience init(liquidGlassArgb argb: Int64) {
        let channelMax = 255.0
        let alpha = CGFloat(Double((argb >> 24) & 0xFF) / channelMax)
        let red = CGFloat(Double((argb >> 16) & 0xFF) / channelMax)
        let green = CGFloat(Double((argb >> 8) & 0xFF) / channelMax)
        let blue = CGFloat(Double(argb & 0xFF) / channelMax)

        self.init(red: red, green: green, blue: blue, alpha: alpha)
    }
}
