import Foundation

enum VrmRendererExpressionId: CaseIterable, Equatable {
    case blinkLeft
    case blinkRight
    case jawOpen
    case smile
}

enum VrmRendererSpecVersion {
    case vrm0
    case vrm1
}

struct VrmRendererExpressionDescriptor {
    let runtimeName: String
    let morphTargetBinds: [VrmRendererMorphTargetBind]
}

struct VrmRendererMorphTargetBind: Equatable {
    let nodeIndex: Int
    let morphTargetIndex: Int
    let weight: Float
}

struct ResolvedVrmRendererExpressionMorphBinding: Equatable {
    let expressionId: VrmRendererExpressionId
    let morphBinds: [ResolvedVrmRendererMorphBind]
}

struct ResolvedVrmRendererMorphBind: Equatable {
    let entityIndex: Int
    let morphTargetIndex: Int
    let weight: Float
}

enum VrmMorphBindingResolver {
    static func resolve(
        specVersion: VrmRendererSpecVersion,
        expressions: [VrmRendererExpressionDescriptor],
        entityIndices: [Int]
    ) -> [ResolvedVrmRendererExpressionMorphBinding] {
        let availableNames = Set(expressions.map(\.runtimeName))
        return VrmRendererExpressionId.allCases.compactMap { expressionId in
            guard
                let runtimeName = resolveRuntimeName(
                    expressionId: expressionId,
                    specVersion: specVersion,
                    availableNames: availableNames
                ),
                let expression = expressions.first(where: { $0.runtimeName == runtimeName })
            else {
                return nil
            }

            let morphBinds = expression.morphTargetBinds.compactMap { bind -> ResolvedVrmRendererMorphBind? in
                guard entityIndices.indices.contains(bind.nodeIndex) else {
                    return nil
                }
                return ResolvedVrmRendererMorphBind(
                    entityIndex: entityIndices[bind.nodeIndex],
                    morphTargetIndex: bind.morphTargetIndex,
                    weight: bind.weight
                )
            }

            guard !morphBinds.isEmpty else {
                return nil
            }
            return ResolvedVrmRendererExpressionMorphBinding(
                expressionId: expressionId,
                morphBinds: morphBinds
            )
        }
    }

    private static func resolveRuntimeName(
        expressionId: VrmRendererExpressionId,
        specVersion: VrmRendererSpecVersion,
        availableNames: Set<String>
    ) -> String? {
        aliases(for: expressionId, specVersion: specVersion).first { alias in
            availableNames.contains(alias)
        }
    }

    private static func aliases(
        for expressionId: VrmRendererExpressionId,
        specVersion: VrmRendererSpecVersion
    ) -> [String] {
        switch (expressionId, specVersion) {
        case (.blinkLeft, .vrm0):
            return ["blink_l", "blinkLeft", "Blink_L"]
        case (.blinkLeft, .vrm1):
            return ["blinkLeft", "blink_l", "BlinkLeft"]
        case (.blinkRight, .vrm0):
            return ["blink_r", "blinkRight", "Blink_R"]
        case (.blinkRight, .vrm1):
            return ["blinkRight", "blink_r", "BlinkRight"]
        case (.jawOpen, .vrm0):
            return ["a", "aa", "jawOpen"]
        case (.jawOpen, .vrm1):
            return ["aa", "a", "jawOpen"]
        case (.smile, .vrm0):
            return ["joy", "smile", "happy"]
        case (.smile, .vrm1):
            return ["happy", "smile", "joy"]
        }
    }
}
