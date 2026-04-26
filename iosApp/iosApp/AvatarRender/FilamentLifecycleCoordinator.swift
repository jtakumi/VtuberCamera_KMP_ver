import Foundation
import QuartzCore
import UIKit

@MainActor
final class FilamentLifecycleCoordinator {
    private weak var renderer: FilamentAvatarRenderer?
    private var displayLink: CADisplayLink?
    private var isViewVisible = false
    private var isAppActive = true

    func attach(renderer: FilamentAvatarRenderer) {
        self.renderer = renderer
        renderer.onRenderingRequirementsChanged = { [weak self] in
            self?.syncRenderingState()
        }
        addObserversIfNeeded()
    }

    func viewDidAppear() {
        isViewVisible = true
        syncRenderingState()
    }

    func viewDidDisappear() {
        isViewVisible = false
        syncRenderingState()
    }

    func teardown() {
        NotificationCenter.default.removeObserver(self)
        stopDisplayLink()
        renderer?.setPaused(true)
    }

    @objc
    private func onDisplayLinkTick() {
        renderer?.drawFrameIfNeeded()
    }

    @objc
    private func onDidBecomeActive() {
        isAppActive = true
        syncRenderingState()
    }

    @objc
    private func onWillResignActive() {
        isAppActive = false
        syncRenderingState()
    }

    private func syncRenderingState() {
        let needsDisplayLink = renderer?.needsDisplayLink ?? false
        let shouldRender = isViewVisible && isAppActive && needsDisplayLink
        renderer?.setPaused(!shouldRender)
        shouldRender ? startDisplayLinkIfNeeded() : stopDisplayLink()
    }

    private func addObserversIfNeeded() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(onDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification,
            object: nil
        )
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(onWillResignActive),
            name: UIApplication.willResignActiveNotification,
            object: nil
        )
    }

    private func startDisplayLinkIfNeeded() {
        guard displayLink == nil else { return }
        let link = CADisplayLink(target: self, selector: #selector(onDisplayLinkTick))
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    nonisolated private func stopDisplayLink() {
        Task{ @MainActor [weak self] in
            self?.displayLink?.invalidate()
            self?.displayLink = nil
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        stopDisplayLink()
    }
}
