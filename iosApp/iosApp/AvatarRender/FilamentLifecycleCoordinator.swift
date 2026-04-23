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
        renderer.onRenderableContentChanged = { [weak self] in
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
        let hasContent = renderer?.hasRenderableContent ?? false
        let shouldRender = isViewVisible && isAppActive && hasContent
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

    private func stopDisplayLink() {
        displayLink?.invalidate()
        displayLink = nil
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        stopDisplayLink()
    }
}
