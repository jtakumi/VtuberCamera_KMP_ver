import UIKit
import Testing
@testable import VtuberCamera_KMP_ver

struct GlassEffectBackdropViewTests {
    @MainActor
    @Test
    func capsuleCornersFollowHalfOfTheMeasuredHeight() {
        let backdropView = GlassEffectBackdropView(cornerRadius: 0, isCapsule: true)

        #expect(backdropView.resolvedCornerRadius(forHeight: 48) == 24)
        #expect(backdropView.resolvedCornerRadius(forHeight: 96) == 48)
    }

    @MainActor
    @Test
    func requestedCornersKeepTheirRadiusWhileItFits() {
        let backdropView = GlassEffectBackdropView(cornerRadius: 28, isCapsule: false)

        #expect(backdropView.resolvedCornerRadius(forHeight: 100) == 28)
    }

    /// A radius larger than half of the height would draw corners that overlap in the middle of the
    /// edge, so it is capped instead of being passed through.
    @MainActor
    @Test
    func requestedCornersAreCappedAtHalfOfTheMeasuredHeight() {
        let backdropView = GlassEffectBackdropView(cornerRadius: 28, isCapsule: false)

        #expect(backdropView.resolvedCornerRadius(forHeight: 40) == 20)
    }

    /// A view that has not been laid out yet has no height, and a negative radius would be rejected
    /// by UIKit.
    @MainActor
    @Test
    func cornersStayAtZeroBeforeTheFirstLayout() {
        let capsuleView = GlassEffectBackdropView(cornerRadius: 0, isCapsule: true)
        let roundedView = GlassEffectBackdropView(cornerRadius: 28, isCapsule: false)

        #expect(capsuleView.resolvedCornerRadius(forHeight: 0) == 0)
        #expect(roundedView.resolvedCornerRadius(forHeight: 0) == 0)
    }
}
