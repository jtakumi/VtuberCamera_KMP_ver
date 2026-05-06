import Foundation
import Testing
@testable import VtuberCamera_KMP_ver

struct VrmMorphBindingResolverTests {
    @Test
    func resolvePrefersVrm0AliasesAndMapsNodesToEntities() {
        let resolved = VrmMorphBindingResolver.resolve(
            specVersion: .vrm0,
            expressions: [
                expression("happy", nodeIndex: 0, morphTargetIndex: 1, weight: 0.5),
                expression("joy", nodeIndex: 1, morphTargetIndex: 2, weight: 0.75),
                expression("aa", nodeIndex: 2, morphTargetIndex: 3, weight: 1)
            ],
            entityIndices: [10, 20, 30]
        )

        let smile = resolved.first { $0.expressionId == .smile }
        let jawOpen = resolved.first { $0.expressionId == .jawOpen }

        #expect(smile?.morphBinds == [
            ResolvedVrmRendererMorphBind(entityIndex: 20, morphTargetIndex: 2, weight: 0.75)
        ])
        #expect(jawOpen?.morphBinds == [
            ResolvedVrmRendererMorphBind(entityIndex: 30, morphTargetIndex: 3, weight: 1)
        ])
    }

    @Test
    func resolvePrefersVrm1Aliases() {
        let resolved = VrmMorphBindingResolver.resolve(
            specVersion: .vrm1,
            expressions: [
                expression("joy", nodeIndex: 0, morphTargetIndex: 0, weight: 1),
                expression("happy", nodeIndex: 1, morphTargetIndex: 1, weight: 1),
                expression("a", nodeIndex: 2, morphTargetIndex: 2, weight: 1),
                expression("aa", nodeIndex: 3, morphTargetIndex: 3, weight: 1)
            ],
            entityIndices: [10, 20, 30, 40]
        )

        let smile = resolved.first { $0.expressionId == .smile }
        let jawOpen = resolved.first { $0.expressionId == .jawOpen }

        #expect(smile?.morphBinds.first?.entityIndex == 20)
        #expect(jawOpen?.morphBinds.first?.entityIndex == 40)
    }

    @Test
    func resolveDropsInvalidNodesAndMissingAliases() {
        let resolved = VrmMorphBindingResolver.resolve(
            specVersion: .vrm1,
            expressions: [
                expression("neutral", nodeIndex: 0, morphTargetIndex: 0, weight: 1),
                expression("happy", nodeIndex: 99, morphTargetIndex: 1, weight: 1)
            ],
            entityIndices: [10]
        )

        #expect(resolved.isEmpty)
    }

    @Test
    func resolvePreservesMultipleMorphBinds() {
        let resolved = VrmMorphBindingResolver.resolve(
            specVersion: .vrm1,
            expressions: [
                VrmRendererExpressionDescriptor(
                    runtimeName: "blinkLeft",
                    morphTargetBinds: [
                        VrmRendererMorphTargetBind(nodeIndex: 0, morphTargetIndex: 1, weight: 0.25),
                        VrmRendererMorphTargetBind(nodeIndex: 1, morphTargetIndex: 2, weight: 0.5)
                    ]
                )
            ],
            entityIndices: [10, 20]
        )

        #expect(resolved == [
            ResolvedVrmRendererExpressionMorphBinding(
                expressionId: .blinkLeft,
                morphBinds: [
                    ResolvedVrmRendererMorphBind(entityIndex: 10, morphTargetIndex: 1, weight: 0.25),
                    ResolvedVrmRendererMorphBind(entityIndex: 20, morphTargetIndex: 2, weight: 0.5)
                ]
            )
        ])
    }

    private func expression(
        _ runtimeName: String,
        nodeIndex: Int,
        morphTargetIndex: Int,
        weight: Float
    ) -> VrmRendererExpressionDescriptor {
        VrmRendererExpressionDescriptor(
            runtimeName: runtimeName,
            morphTargetBinds: [
                VrmRendererMorphTargetBind(
                    nodeIndex: nodeIndex,
                    morphTargetIndex: morphTargetIndex,
                    weight: weight
                )
            ]
        )
    }
}
