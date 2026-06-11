import Foundation
import Testing
@testable import VtuberCamera_KMP_ver

struct IOSVrmAssetLoaderTests {
    @Test
    func decodeRuntimeDescriptorReadsAllFields() {
        let descriptor = IOSVrmAssetLoader.decodeRuntimeDescriptor(from: [
            IOSAvatarRenderBridge.specVersionKey: IOSAvatarRenderBridge.specVersionVrm1Value,
            IOSAvatarRenderBridge.headNodeIndexKey: NSNumber(value: 4),
            IOSAvatarRenderBridge.expressionsKey: [
                [
                    IOSAvatarRenderBridge.expressionRuntimeNameKey: "blinkLeft",
                    IOSAvatarRenderBridge.expressionMorphTargetBindsKey: [
                        [
                            IOSAvatarRenderBridge.morphBindNodeIndexKey: NSNumber(value: 2),
                            IOSAvatarRenderBridge.morphBindMorphTargetIndexKey: NSNumber(value: 3),
                            IOSAvatarRenderBridge.morphBindWeightKey: NSNumber(value: 0.75),
                        ],
                    ],
                ],
            ],
        ])

        #expect(descriptor != nil)
        #expect(descriptor?.specVersion == .vrm1)
        #expect(descriptor?.headNodeIndex == 4)
        #expect(descriptor?.expressions.count == 1)
        #expect(descriptor?.expressions.first?.runtimeName == "blinkLeft")
        #expect(descriptor?.expressions.first?.morphTargetBinds == [
            VrmRendererMorphTargetBind(nodeIndex: 2, morphTargetIndex: 3, weight: 0.75),
        ])
    }

    @Test
    func decodeRuntimeDescriptorAllowsMissingHeadNodeIndex() {
        let descriptor = IOSVrmAssetLoader.decodeRuntimeDescriptor(from: [
            IOSAvatarRenderBridge.specVersionKey: IOSAvatarRenderBridge.specVersionVrm0Value,
            IOSAvatarRenderBridge.expressionsKey: [[AnyHashable: Any]](),
        ])

        #expect(descriptor != nil)
        #expect(descriptor?.specVersion == .vrm0)
        #expect(descriptor?.headNodeIndex == nil)
        #expect(descriptor?.expressions.isEmpty == true)
    }

    @Test
    func decodeRuntimeDescriptorReturnsNilWhenSpecVersionIsMissing() {
        let descriptor = IOSVrmAssetLoader.decodeRuntimeDescriptor(from: [
            IOSAvatarRenderBridge.expressionsKey: [[AnyHashable: Any]](),
        ])

        #expect(descriptor == nil)
    }

    @Test
    func decodeRuntimeDescriptorReturnsNilForUnknownSpecVersion() {
        let descriptor = IOSVrmAssetLoader.decodeRuntimeDescriptor(from: [
            IOSAvatarRenderBridge.specVersionKey: "vrm9",
            IOSAvatarRenderBridge.expressionsKey: [[AnyHashable: Any]](),
        ])

        #expect(descriptor == nil)
    }

    @Test
    func decodeRuntimeDescriptorReturnsNilWhenExpressionsAreMissing() {
        let descriptor = IOSVrmAssetLoader.decodeRuntimeDescriptor(from: [
            IOSAvatarRenderBridge.specVersionKey: IOSAvatarRenderBridge.specVersionVrm1Value,
        ])

        #expect(descriptor == nil)
    }

    @Test
    func decodeRuntimeDescriptorSkipsMalformedExpressionAndBindEntries() {
        let descriptor = IOSVrmAssetLoader.decodeRuntimeDescriptor(from: [
            IOSAvatarRenderBridge.specVersionKey: IOSAvatarRenderBridge.specVersionVrm1Value,
            IOSAvatarRenderBridge.expressionsKey: [
                // Missing runtime name: skipped entirely.
                [
                    IOSAvatarRenderBridge.expressionMorphTargetBindsKey: [[AnyHashable: Any]](),
                ],
                [
                    IOSAvatarRenderBridge.expressionRuntimeNameKey: "happy",
                    IOSAvatarRenderBridge.expressionMorphTargetBindsKey: [
                        // Missing morph target index: skipped.
                        [
                            IOSAvatarRenderBridge.morphBindNodeIndexKey: NSNumber(value: 1),
                            IOSAvatarRenderBridge.morphBindWeightKey: NSNumber(value: 1.0),
                        ],
                        [
                            IOSAvatarRenderBridge.morphBindNodeIndexKey: NSNumber(value: 5),
                            IOSAvatarRenderBridge.morphBindMorphTargetIndexKey: NSNumber(value: 6),
                            IOSAvatarRenderBridge.morphBindWeightKey: NSNumber(value: 1.0),
                        ],
                    ],
                ],
            ],
        ])

        #expect(descriptor?.expressions.count == 1)
        #expect(descriptor?.expressions.first?.runtimeName == "happy")
        #expect(descriptor?.expressions.first?.morphTargetBinds == [
            VrmRendererMorphTargetBind(nodeIndex: 5, morphTargetIndex: 6, weight: 1.0),
        ])
    }
}
