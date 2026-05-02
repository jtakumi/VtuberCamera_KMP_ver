#import "VTCFilamentRendererBridge.h"

#import <QuartzCore/CAMetalLayer.h>

#if __has_include(<filament/Engine.h>) && __has_include(<filament/Renderer.h>) && __has_include(<filament/Scene.h>) && __has_include(<filament/View.h>) && __has_include(<gltfio/AssetLoader.h>) && __has_include(<gltfio/ResourceLoader.h>)
#define VTC_FILAMENT_HEADERS_AVAILABLE 1
#else
#define VTC_FILAMENT_HEADERS_AVAILABLE 0
#endif

#if VTC_FILAMENT_HEADERS_AVAILABLE
#include <cmath>
#include <limits>
#include <memory>
#include <optional>
#include <unordered_map>
#include <vector>

#include <filament/Box.h>
#include <filament/Camera.h>
#include <filament/Engine.h>
#include <filament/RenderableManager.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/SwapChain.h>
#include <filament/TransformManager.h>
#include <filament/View.h>
#include <filament/Viewport.h>
#include <gltfio/Animator.h>
#include <gltfio/AssetLoader.h>
#include <gltfio/FilamentAsset.h>
#include <gltfio/FilamentInstance.h>
#include <gltfio/ResourceLoader.h>
#include <gltfio/TextureProvider.h>
#include <gltfio/materials/uberarchive.h>
#include <math/mat4.h>
#include <math/vec3.h>
#include <utils/EntityManager.h>

#if __has_include(<gltfio/MaterialProvider.h>)
#include <gltfio/MaterialProvider.h>
#endif
#endif

typedef NS_ENUM(NSInteger, VTCFilamentRendererErrorCode) {
    VTCFilamentRendererErrorCodeInvalidInput = 0,
    VTCFilamentRendererErrorCodeUnavailable = 1,
    VTCFilamentRendererErrorCodeLoadFailed = 2,
};

static NSString *const VTCFilamentRendererErrorDomain = @"io.github.jtakumi.VtuberCamera_KMP_ver.filament";
static NSString *const VTCRuntimeSpecVersionKey = @"runtimeSpecVersion";
static NSString *const VTCHeadBoneNodeIndexKey = @"headBoneNodeIndex";
static NSString *const VTCExpressionBindingsKey = @"expressionBindings";
static NSString *const VTCExpressionChannelKey = @"channel";
static NSString *const VTCExpressionMorphBindsKey = @"morphBinds";
static NSString *const VTCMorphBindNodeIndexKey = @"nodeIndex";
static NSString *const VTCMorphBindMorphTargetIndexKey = @"morphTargetIndex";
static NSString *const VTCMorphBindWeightKey = @"weight";

static void VTCLogFilamentConfiguration(void) {
    NSLog(
        @"VTCFilamentRendererBridge Filament headers available=%@",
        VTC_FILAMENT_HEADERS_AVAILABLE ? @"YES" : @"NO"
    );
}

static NSError *VTCFilamentError(VTCFilamentRendererErrorCode code, NSString *message) {
    return [NSError errorWithDomain:VTCFilamentRendererErrorDomain
                               code:code
                           userInfo:@{NSLocalizedDescriptionKey: message}];
}

@interface VTCMetalContainerView : UIView
@end

@implementation VTCMetalContainerView

+ (Class)layerClass {
    return [CAMetalLayer class];
}

@end

@implementation VTCAvatarRenderState
@end

#if VTC_FILAMENT_HEADERS_AVAILABLE
namespace {

using filament::Engine;
using filament::RenderableManager;
using filament::TransformManager;
using filament::math::mat4f;
using utils::Entity;

constexpr float kDefaultCameraDistance = 4.0f;
constexpr float kMinimumModelHalfExtent = 0.75f;
constexpr float kFallbackModelFitDistanceMultiplier = 3.25f;
constexpr float kCameraVerticalFovDegrees = 45.0f;
constexpr float kBustFramingHeadroomRatio = 0.12f;
constexpr float kBustFramingChestDropRatio = 0.36f;
constexpr float kBustFramingVerticalFill = 0.76f;
constexpr float kBustFramingDepthDistanceMultiplier = 1.8f;
constexpr float kMinimumBustFramedHeight = 1.1f;
constexpr uint8_t kSceneLayerMask = 0xff;
constexpr uint8_t kSceneLayerVisible = 0x1;

enum class VTCExpressionChannel {
    BlinkLeft,
    BlinkRight,
    JawOpen,
    Smile,
};

struct VTCMorphBind {
    NSInteger nodeIndex;
    Entity entity;
    int morphTargetIndex;
    float weight;
};

struct VTCExpressionBinding {
    VTCExpressionChannel channel;
    std::vector<VTCMorphBind> morphBinds;
};

struct VTCHeadBinding {
    TransformManager::Instance transformInstance;
    mat4f baseLocalTransform;
};

struct VTCRuntimeDescriptor {
    NSInteger headBoneNodeIndex = NSNotFound;
    std::vector<VTCExpressionBinding> expressionBindings;
};

struct VTCAssetLoaderDeleter {
    void operator()(filament::gltfio::AssetLoader *loader) const {
        if (loader != nullptr) {
            filament::gltfio::AssetLoader::destroy(&loader);
        }
    }
};

struct VTCMaterialProviderDeleter {
    void operator()(filament::gltfio::MaterialProvider *provider) const {
        if (provider != nullptr) {
            provider->destroyMaterials();
            delete provider;
        }
    }
};

struct VTCBindingDiagnostics {
    size_t morphTargetEntityCount = 0;
    size_t resolvedExpressionBindingCount = 0;
};

struct VTCPoint3 {
    float x;
    float y;
    float z;
};

float VTCDegreesToRadians(float degrees) {
    return degrees * static_cast<float>(M_PI / 180.0);
}

mat4f VTCRotationMatrix(float yawDegrees, float pitchDegrees, float rollDegrees) {
    const float yaw = VTCDegreesToRadians(yawDegrees);
    const float pitch = VTCDegreesToRadians(pitchDegrees);
    const float roll = VTCDegreesToRadians(rollDegrees);
    const float cy = std::cos(yaw);
    const float sy = std::sin(yaw);
    const float cp = std::cos(pitch);
    const float sp = std::sin(pitch);
    const float cr = std::cos(roll);
    const float sr = std::sin(roll);

    const mat4f yawMatrix = mat4f{
        cy, 0.0f, -sy, 0.0f,
        0.0f, 1.0f, 0.0f, 0.0f,
        sy, 0.0f, cy, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
    };
    const mat4f pitchMatrix = mat4f{
        1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, cp, sp, 0.0f,
        0.0f, -sp, cp, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
    };
    const mat4f rollMatrix = mat4f{
        cr, sr, 0.0f, 0.0f,
        -sr, cr, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
    };
    return yawMatrix * pitchMatrix * rollMatrix;
}

float VTCClampedWeight(float value) {
    return std::max(0.0f, std::min(value, 1.0f));
}

float VTCExpressionWeight(VTCExpressionChannel channel, VTCAvatarRenderState *state) {
    switch (channel) {
        case VTCExpressionChannel::BlinkLeft:
            return state.leftEyeBlink;
        case VTCExpressionChannel::BlinkRight:
            return state.rightEyeBlink;
        case VTCExpressionChannel::JawOpen:
            return state.jawOpen;
        case VTCExpressionChannel::Smile:
            return state.mouthSmile;
    }
}

bool VTCExpressionChannelFromString(NSString *channel, VTCExpressionChannel *output) {
    if ([channel isEqualToString:@"blinkLeft"]) {
        *output = VTCExpressionChannel::BlinkLeft;
        return true;
    }
    if ([channel isEqualToString:@"blinkRight"]) {
        *output = VTCExpressionChannel::BlinkRight;
        return true;
    }
    if ([channel isEqualToString:@"jawOpen"]) {
        *output = VTCExpressionChannel::JawOpen;
        return true;
    }
    if ([channel isEqualToString:@"smile"]) {
        *output = VTCExpressionChannel::Smile;
        return true;
    }
    return false;
}

NSInteger VTCIntegerValue(id value, NSInteger defaultValue = NSNotFound) {
    return [value respondsToSelector:@selector(integerValue)] ? [value integerValue] : defaultValue;
}

float VTCFloatValue(id value, float defaultValue = 0.0f) {
    return [value respondsToSelector:@selector(floatValue)] ? [value floatValue] : defaultValue;
}

VTCRuntimeDescriptor VTCRuntimeDescriptorFromDictionary(NSDictionary<NSString *, id> *descriptor) {
    VTCRuntimeDescriptor runtimeDescriptor;
    runtimeDescriptor.headBoneNodeIndex = VTCIntegerValue(descriptor[VTCHeadBoneNodeIndexKey]);

    NSArray *expressionPayloads = [descriptor[VTCExpressionBindingsKey] isKindOfClass:[NSArray class]]
        ? descriptor[VTCExpressionBindingsKey]
        : @[];
    for (id expressionPayload in expressionPayloads) {
        if (![expressionPayload isKindOfClass:[NSDictionary class]]) {
            continue;
        }
        NSDictionary *expression = expressionPayload;
        VTCExpressionChannel channel;
        if (!VTCExpressionChannelFromString(expression[VTCExpressionChannelKey], &channel)) {
            continue;
        }
        VTCExpressionBinding binding;
        binding.channel = channel;

        NSArray *morphPayloads = [expression[VTCExpressionMorphBindsKey] isKindOfClass:[NSArray class]]
            ? expression[VTCExpressionMorphBindsKey]
            : @[];
        for (id morphPayload in morphPayloads) {
            if (![morphPayload isKindOfClass:[NSDictionary class]]) {
                continue;
            }
            NSDictionary *morph = morphPayload;
            VTCMorphBind morphBind;
            morphBind.nodeIndex = VTCIntegerValue(morph[VTCMorphBindNodeIndexKey], -1);
            morphBind.entity = {};
            morphBind.morphTargetIndex = static_cast<int>(VTCIntegerValue(morph[VTCMorphBindMorphTargetIndexKey], -1));
            morphBind.weight = VTCFloatValue(morph[VTCMorphBindWeightKey], 0.0f);
            if (morphBind.nodeIndex < 0 || morphBind.morphTargetIndex < 0) {
                continue;
            }
            binding.morphBinds.push_back(morphBind);
        }
        if (!binding.morphBinds.empty()) {
            runtimeDescriptor.expressionBindings.push_back(binding);
        }
    }
    return runtimeDescriptor;
}

class VTCFilamentRuntime {
public:
    explicit VTCFilamentRuntime(CAMetalLayer *layer) {
        engine = Engine::create(Engine::Backend::METAL);
        if (engine == nullptr) {
            NSLog(@"VTCFilamentRuntime failed to create Metal backend engine.");
            return;
        }

        renderer = engine->createRenderer();
        scene = engine->createScene();
        view = engine->createView();
        cameraEntity = utils::EntityManager::get().create();
        camera = engine->createCamera(cameraEntity);
        swapChain = engine->createSwapChain((__bridge void *)layer);

        if (view != nullptr && scene != nullptr && camera != nullptr) {
            view->setScene(scene);
            view->setCamera(camera);
            view->setBlendMode(filament::View::BlendMode::TRANSLUCENT);
        }
        configureCamera(kDefaultCameraDistance, 0.0f, 0.0f, 0.0f);
        NSLog(
            @"VTCFilamentRuntime initialized ready=%@ renderer=%@ scene=%@ view=%@ camera=%@ swapChain=%@ metalLayer=%@",
            isReady() ? @"YES" : @"NO",
            renderer != nullptr ? @"YES" : @"NO",
            scene != nullptr ? @"YES" : @"NO",
            view != nullptr ? @"YES" : @"NO",
            camera != nullptr ? @"YES" : @"NO",
            swapChain != nullptr ? @"YES" : @"NO",
            layer != nil ? @"YES" : @"NO"
        );
    }

    ~VTCFilamentRuntime() {
        clearAvatar();
        if (engine == nullptr) {
            return;
        }
        if (swapChain != nullptr) {
            engine->destroy(swapChain);
        }
        if (view != nullptr) {
            engine->destroy(view);
        }
        if (scene != nullptr) {
            engine->destroy(scene);
        }
        if (cameraEntity) {
            engine->destroyCameraComponent(cameraEntity);
            utils::EntityManager::get().destroy(cameraEntity);
        }
        if (renderer != nullptr) {
            engine->destroy(renderer);
        }
        Engine::destroy(&engine);
    }

    bool isReady() const {
        return engine != nullptr && renderer != nullptr && scene != nullptr && view != nullptr &&
            camera != nullptr && swapChain != nullptr;
    }

    void resize(CGSize size, CGFloat contentScale) {
        if (view == nullptr) {
            return;
        }
        const uint32_t width = static_cast<uint32_t>(std::max<CGFloat>(1.0, size.width * contentScale));
        const uint32_t height = static_cast<uint32_t>(std::max<CGFloat>(1.0, size.height * contentScale));
        view->setViewport(filament::Viewport(0, 0, width, height));
        if (camera != nullptr && height > 0) {
            camera->setProjection(45.0, static_cast<double>(width) / static_cast<double>(height), 0.05, 100.0);
        }
    }

    bool loadAvatar(NSData *data, NSDictionary<NSString *, id> *descriptor, NSError **error) {
        if (!isReady()) {
            if (error != nullptr) {
                *error = VTCFilamentError(VTCFilamentRendererErrorCodeUnavailable, @"Filament renderer is unavailable.");
            }
            return false;
        }

        clearAvatar();
        runtimeDescriptor = VTCRuntimeDescriptorFromDictionary(descriptor);
        didLogFirstStateUpdate = false;
        didLogFirstRender = false;
        NSLog(
            @"VTCFilamentRuntime loadAvatar started bytes=%lu headBoneNodeIndex=%ld expressionBindings=%zu",
            static_cast<unsigned long>(data.length),
            static_cast<long>(runtimeDescriptor.headBoneNodeIndex),
            runtimeDescriptor.expressionBindings.size()
        );

        materialProvider.reset(filament::gltfio::createUbershaderProvider(
            engine,
            UBERARCHIVE_DEFAULT_DATA,
            UBERARCHIVE_DEFAULT_SIZE
        ));
        if (materialProvider == nullptr) {
            if (error != nullptr) {
                *error = VTCFilamentError(VTCFilamentRendererErrorCodeUnavailable, @"Failed to create glTF material provider.");
            }
            return false;
        }

        assetLoader.reset(filament::gltfio::AssetLoader::create({engine, materialProvider.get()}));
        if (assetLoader == nullptr) {
            if (error != nullptr) {
                *error = VTCFilamentError(VTCFilamentRendererErrorCodeUnavailable, @"Failed to create glTF asset loader.");
            }
            return false;
        }

        if (data.length > std::numeric_limits<uint32_t>::max()) {
            if (error != nullptr) {
                *error = VTCFilamentError(VTCFilamentRendererErrorCodeInvalidInput, @"Avatar GLB/VRM bytes are too large.");
            }
            return false;
        }

        asset = assetLoader->createAsset(
            static_cast<const uint8_t *>(data.bytes),
            static_cast<uint32_t>(data.length)
        );
        if (asset == nullptr) {
            if (error != nullptr) {
                *error = VTCFilamentError(VTCFilamentRendererErrorCodeLoadFailed, @"Failed to parse avatar GLB/VRM bytes.");
            }
            return false;
        }

        filament::gltfio::ResourceLoader resourceLoader({engine});
        if (!resourceLoader.loadResources(asset)) {
            if (error != nullptr) {
                *error = VTCFilamentError(VTCFilamentRendererErrorCodeLoadFailed, @"Failed to load avatar resources.");
            }
            clearAvatar();
            return false;
        }

        configureRenderableEntities();
        const VTCBindingDiagnostics diagnostics = createRuntimeBindings();
        asset->getInstance()->getAnimator()->updateBoneMatrices();
        scene->addEntities(asset->getEntities(), asset->getEntityCount());
        configureCameraForAsset();
        NSLog(
            @"VTCFilamentRuntime loadAvatar succeeded entityCount=%zu renderableEntityCount=%zu headBoneNodeIndex=%ld expressionBindings=%zu morphTargets=%zu resolvedExpressionBindings=%zu headBinding=%@",
            asset->getEntityCount(),
            asset->getRenderableEntityCount(),
            static_cast<long>(runtimeDescriptor.headBoneNodeIndex),
            runtimeDescriptor.expressionBindings.size(),
            diagnostics.morphTargetEntityCount,
            diagnostics.resolvedExpressionBindingCount,
            headBinding.has_value() ? @"YES" : @"NO"
        );
        return true;
    }

    void updateState(VTCAvatarRenderState *state) {
        if (asset == nullptr) {
            return;
        }
        if (!didLogFirstStateUpdate) {
            didLogFirstStateUpdate = true;
            NSLog(
                @"VTCFilamentRuntime updateAvatarState called headYaw=%.2f headPitch=%.2f headRoll=%.2f blinkL=%.2f blinkR=%.2f jaw=%.2f smile=%.2f headBinding=%@ expressionBindings=%zu",
                state.headYawDegrees,
                state.headPitchDegrees,
                state.headRollDegrees,
                state.leftEyeBlink,
                state.rightEyeBlink,
                state.jawOpen,
                state.mouthSmile,
                headBinding.has_value() ? @"YES" : @"NO",
                expressionBindings.size()
            );
        }
        applyHeadPose(state);
        applyExpressions(state);
        asset->getInstance()->getAnimator()->updateBoneMatrices();
    }

    void render() {
        if (!isReady() || asset == nullptr) {
            return;
        }
        if (!didLogFirstRender) {
            didLogFirstRender = true;
            NSLog(@"VTCFilamentRuntime drawIfNeeded called; rendering first dynamic avatar frame.");
        }
        if (renderer->beginFrame(swapChain)) {
            renderer->render(view);
            renderer->endFrame();
        }
    }

    void clearAvatar() {
        morphTargets.clear();
        expressionBindings.clear();
        headBinding.reset();
        if (asset != nullptr) {
            if (scene != nullptr) {
                scene->removeEntities(asset->getEntities(), asset->getEntityCount());
            }
            if (assetLoader != nullptr) {
                assetLoader->destroyAsset(asset);
            }
            asset = nullptr;
        }
        assetLoader.reset();
        materialProvider.reset();
        didLogFirstStateUpdate = false;
        didLogFirstRender = false;
    }

private:
    struct EntityHash {
        std::size_t operator()(const Entity &entity) const {
            return std::hash<uint32_t>{}(entity.getId());
        }
    };

    void configureCamera(float distance, float targetX, float targetY, float targetZ) {
        if (camera == nullptr) {
            return;
        }
        camera->lookAt(
            filament::math::double3(targetX, targetY, targetZ + distance),
            filament::math::double3(targetX, targetY, targetZ),
            filament::math::double3(0.0, 1.0, 0.0)
        );
    }

    void configureCameraForAsset() {
        const filament::Aabb bounds = asset->getBoundingBox();
        const auto center = bounds.center();
        const auto halfExtent = bounds.extent();
        const float maxHalfExtent = std::max(
            std::max(halfExtent[0], halfExtent[1]),
            std::max(halfExtent[2], kMinimumModelHalfExtent)
        );

        const std::optional<VTCPoint3> headPosition = headWorldPosition();
        if (headPosition.has_value()) {
            const float modelHeight = std::max(halfExtent[1] * 2.0f, kMinimumBustFramedHeight);
            const float minY = center[1] - halfExtent[1];
            const float maxY = center[1] + halfExtent[1];
            const float topY = std::min(
                maxY,
                headPosition->y + modelHeight * kBustFramingHeadroomRatio
            );
            const float bottomY = std::max(
                minY,
                headPosition->y - modelHeight * kBustFramingChestDropRatio
            );
            const float framedHeight = std::max(topY - bottomY, kMinimumBustFramedHeight);
            const float visibleHeight = framedHeight / kBustFramingVerticalFill;
            const float fovRadians = VTCDegreesToRadians(kCameraVerticalFovDegrees);
            const float distanceForHeight = visibleHeight / (2.0f * std::tan(fovRadians * 0.5f));
            const float distanceForDepth = std::max(halfExtent[2], kMinimumModelHalfExtent) *
                kBustFramingDepthDistanceMultiplier;

            configureCamera(
                std::max(kDefaultCameraDistance, std::max(distanceForHeight, distanceForDepth)),
                center[0],
                (topY + bottomY) * 0.5f,
                center[2]
            );
            return;
        }

        configureCamera(
            std::max(kDefaultCameraDistance, maxHalfExtent * kFallbackModelFitDistanceMultiplier),
            center[0],
            center[1],
            center[2]
        );
    }

    std::optional<VTCPoint3> headWorldPosition() {
        if (asset == nullptr ||
            runtimeDescriptor.headBoneNodeIndex < 0 ||
            static_cast<size_t>(runtimeDescriptor.headBoneNodeIndex) >= asset->getEntityCount()) {
            return std::nullopt;
        }

        TransformManager &transformManager = engine->getTransformManager();
        Entity const *entities = asset->getEntities();
        const Entity headEntity = entities[runtimeDescriptor.headBoneNodeIndex];
        const TransformManager::Instance transformInstance = transformManager.getInstance(headEntity);
        if (!transformInstance) {
            return std::nullopt;
        }

        const mat4f worldTransform = transformManager.getWorldTransform(transformInstance);
        return VTCPoint3{
            worldTransform[3][0],
            worldTransform[3][1],
            worldTransform[3][2],
        };
    }

    void configureRenderableEntities() {
        RenderableManager &renderableManager = engine->getRenderableManager();
        Entity const *renderableEntities = asset->getRenderableEntities();
        const size_t count = asset->getRenderableEntityCount();
        for (size_t index = 0; index < count; index++) {
            const Entity entity = renderableEntities[index];
            const RenderableManager::Instance instance = renderableManager.getInstance(entity);
            if (!instance) {
                continue;
            }
            renderableManager.setLayerMask(instance, kSceneLayerMask, kSceneLayerVisible);
            renderableManager.setCulling(instance, false);
            const size_t primitiveCount = renderableManager.getPrimitiveCount(instance);
            for (size_t primitiveIndex = 0; primitiveIndex < primitiveCount; primitiveIndex++) {
                renderableManager.getMaterialInstanceAt(instance, primitiveIndex)->setDoubleSided(true);
            }
        }
    }

    VTCBindingDiagnostics createRuntimeBindings() {
        VTCBindingDiagnostics diagnostics;
        TransformManager &transformManager = engine->getTransformManager();
        Entity const *entities = asset->getEntities();
        const size_t entityCount = asset->getEntityCount();
        if (runtimeDescriptor.headBoneNodeIndex < 0) {
            NSLog(
                @"VTCFilamentRuntime headBinding unresolved: headBoneNodeIndex is missing (%ld).",
                static_cast<long>(runtimeDescriptor.headBoneNodeIndex)
            );
        } else if (static_cast<size_t>(runtimeDescriptor.headBoneNodeIndex) >= entityCount) {
            NSLog(
                @"VTCFilamentRuntime headBinding unresolved: headBoneNodeIndex=%ld is outside entityCount=%zu.",
                static_cast<long>(runtimeDescriptor.headBoneNodeIndex),
                entityCount
            );
        } else {
            const Entity headEntity = entities[runtimeDescriptor.headBoneNodeIndex];
            const TransformManager::Instance transformInstance = transformManager.getInstance(headEntity);
            if (transformInstance) {
                VTCHeadBinding binding;
                binding.transformInstance = transformInstance;
                binding.baseLocalTransform = transformManager.getTransform(transformInstance);
                headBinding = binding;
            } else {
                NSLog(
                    @"VTCFilamentRuntime headBinding unresolved: entity index %ld has no TransformManager instance.",
                    static_cast<long>(runtimeDescriptor.headBoneNodeIndex)
                );
            }
        }

        RenderableManager &renderableManager = engine->getRenderableManager();
        Entity const *renderableEntities = asset->getRenderableEntities();
        const size_t renderableCount = asset->getRenderableEntityCount();
        for (size_t index = 0; index < renderableCount; index++) {
            const Entity entity = renderableEntities[index];
            const RenderableManager::Instance instance = renderableManager.getInstance(entity);
            if (!instance) {
                continue;
            }
            const size_t morphTargetCount = renderableManager.getMorphTargetCount(instance);
            if (morphTargetCount > 0) {
                morphTargets[entity] = std::vector<float>(morphTargetCount, 0.0f);
                diagnostics.morphTargetEntityCount++;
            }
        }
        if (diagnostics.morphTargetEntityCount == 0) {
            NSLog(@"VTCFilamentRuntime found no renderable entities with morph targets.");
        }

        for (const VTCExpressionBinding &payloadBinding : runtimeDescriptor.expressionBindings) {
            VTCExpressionBinding binding;
            binding.channel = payloadBinding.channel;
            for (VTCMorphBind morphBind : payloadBinding.morphBinds) {
                if (morphBind.nodeIndex < 0 || static_cast<size_t>(morphBind.nodeIndex) >= entityCount) {
                    NSLog(
                        @"VTCFilamentRuntime expression binding skipped: nodeIndex=%ld is outside entityCount=%zu.",
                        static_cast<long>(morphBind.nodeIndex),
                        entityCount
                    );
                    continue;
                }
                morphBind.entity = entities[morphBind.nodeIndex];
                auto weights = morphTargets.find(morphBind.entity);
                if (weights == morphTargets.end()) {
                    NSLog(
                        @"VTCFilamentRuntime expression binding skipped: nodeIndex=%ld has no morph targets.",
                        static_cast<long>(morphBind.nodeIndex)
                    );
                    continue;
                }
                if (morphBind.morphTargetIndex < 0 ||
                    static_cast<size_t>(morphBind.morphTargetIndex) >= weights->second.size()) {
                    NSLog(
                        @"VTCFilamentRuntime expression binding skipped: morphTargetIndex=%d is outside morphTargetCount=%zu for nodeIndex=%ld.",
                        morphBind.morphTargetIndex,
                        weights->second.size(),
                        static_cast<long>(morphBind.nodeIndex)
                    );
                    continue;
                }
                binding.morphBinds.push_back(morphBind);
            }
            if (!binding.morphBinds.empty()) {
                expressionBindings.push_back(binding);
                diagnostics.resolvedExpressionBindingCount++;
            }
        }
        if (runtimeDescriptor.expressionBindings.empty()) {
            NSLog(@"VTCFilamentRuntime has no expression binding payloads from KMP.");
        } else if (diagnostics.resolvedExpressionBindingCount == 0) {
            NSLog(
                @"VTCFilamentRuntime resolved no expression bindings from %zu payload bindings.",
                runtimeDescriptor.expressionBindings.size()
            );
        }
        return diagnostics;
    }

    void applyHeadPose(VTCAvatarRenderState *state) {
        if (!headBinding.has_value()) {
            return;
        }
        TransformManager &transformManager = engine->getTransformManager();
        const mat4f rotation = VTCRotationMatrix(
            state.headYawDegrees,
            state.headPitchDegrees,
            state.headRollDegrees
        );
        transformManager.setTransform(
            headBinding->transformInstance,
            headBinding->baseLocalTransform * rotation
        );
    }

    void applyExpressions(VTCAvatarRenderState *state) {
        if (morphTargets.empty()) {
            return;
        }
        for (auto &entry : morphTargets) {
            std::fill(entry.second.begin(), entry.second.end(), 0.0f);
        }
        for (const VTCExpressionBinding &binding : expressionBindings) {
            const float expressionWeight = VTCClampedWeight(VTCExpressionWeight(binding.channel, state));
            if (expressionWeight <= 0.0f) {
                continue;
            }
            for (const VTCMorphBind &morphBind : binding.morphBinds) {
                auto weights = morphTargets.find(morphBind.entity);
                if (weights == morphTargets.end() ||
                    morphBind.morphTargetIndex < 0 ||
                    static_cast<size_t>(morphBind.morphTargetIndex) >= weights->second.size()) {
                    continue;
                }
                float &weight = weights->second[morphBind.morphTargetIndex];
                weight = VTCClampedWeight(weight + expressionWeight * morphBind.weight);
            }
        }

        RenderableManager &renderableManager = engine->getRenderableManager();
        for (auto &entry : morphTargets) {
            const RenderableManager::Instance instance = renderableManager.getInstance(entry.first);
            if (instance) {
                renderableManager.setMorphWeights(instance, entry.second.data(), entry.second.size(), 0);
            }
        }
    }

    Engine *engine = nullptr;
    filament::Renderer *renderer = nullptr;
    filament::Scene *scene = nullptr;
    filament::View *view = nullptr;
    filament::Camera *camera = nullptr;
    filament::SwapChain *swapChain = nullptr;
    Entity cameraEntity;
    filament::gltfio::FilamentAsset *asset = nullptr;
    std::unique_ptr<filament::gltfio::AssetLoader, VTCAssetLoaderDeleter> assetLoader;
    std::unique_ptr<filament::gltfio::MaterialProvider, VTCMaterialProviderDeleter> materialProvider;
    VTCRuntimeDescriptor runtimeDescriptor;
    std::optional<VTCHeadBinding> headBinding;
    std::vector<VTCExpressionBinding> expressionBindings;
    std::unordered_map<Entity, std::vector<float>, EntityHash> morphTargets;
    bool didLogFirstStateUpdate = false;
    bool didLogFirstRender = false;
};

} // namespace
#endif

@interface VTCFilamentRendererBridge ()

@property (nonatomic, strong) UIView *renderView;

@end

@implementation VTCFilamentRendererBridge {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    std::unique_ptr<VTCFilamentRuntime> _runtime;
#endif
}

+ (BOOL)isFilamentSdkConfigured {
    return VTC_FILAMENT_HEADERS_AVAILABLE;
}

- (instancetype)init {
    self = [super init];
    if (self != nil) {
        _renderView = [[VTCMetalContainerView alloc] initWithFrame:CGRectZero];
        VTCLogFilamentConfiguration();
        NSLog(
            @"VTCFilamentRendererBridge initialized metalLayer=%@",
            [_renderView.layer isKindOfClass:[CAMetalLayer class]] ? @"YES" : @"NO"
        );
#if VTC_FILAMENT_HEADERS_AVAILABLE
        _runtime = std::make_unique<VTCFilamentRuntime>((CAMetalLayer *)_renderView.layer);
#endif
    }
    return self;
}

- (BOOL)loadAvatarAtURL:(NSURL *)url error:(NSError * _Nullable __autoreleasing *)error {
    if (!url.isFileURL) {
        if (error != nil) {
            *error = VTCFilamentError(
                VTCFilamentRendererErrorCodeInvalidInput,
                @"Avatar URLs must point to a local file."
            );
        }
        return NO;
    }
    NSData *data = [NSData dataWithContentsOfURL:url options:0 error:error];
    if (data == nil) {
        return NO;
    }
    return [self loadAvatarData:data runtimeDescriptor:@{} error:error];
}

- (BOOL)loadAvatarData:(NSData *)data
     runtimeDescriptor:(NSDictionary<NSString *, id> *)runtimeDescriptor
                 error:(NSError * _Nullable __autoreleasing *)error {
    if (data.length == 0) {
        if (error != nil) {
            *error = VTCFilamentError(
                VTCFilamentRendererErrorCodeInvalidInput,
                @"Avatar data must not be empty."
            );
        }
        return NO;
    }

#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_runtime == nullptr || !_runtime->isReady()) {
        NSLog(@"VTCFilamentRendererBridge loadAvatarData failed: Filament runtime unavailable.");
        if (error != nil) {
            *error = VTCFilamentError(
                VTCFilamentRendererErrorCodeUnavailable,
                @"Filament renderer is unavailable."
            );
        }
        return NO;
    }
    return _runtime->loadAvatar(data, runtimeDescriptor ?: @{}, error);
#else
    NSLog(
        @"VTCFilamentRendererBridge loadAvatarData failed: Filament SDK headers are not configured. Check iosApp/Configuration/Filament.xcconfig."
    );
    if (error != nil) {
        *error = VTCFilamentError(
            VTCFilamentRendererErrorCodeUnavailable,
            @"Filament SDK headers are not configured for iosApp yet."
        );
    }
    return NO;
#endif
}

- (void)updateAvatarState:(VTCAvatarRenderState *)state {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_runtime != nullptr) {
        _runtime->updateState(state);
    }
#else
    (void)state;
#endif
}

- (void)clearAvatar {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_runtime != nullptr) {
        _runtime->clearAvatar();
    }
#endif
}

- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale {
    self.renderView.frame = bounds;
    CGFloat fallbackScale = self.renderView.window.screen.scale;
    if (fallbackScale <= 0) {
        fallbackScale = 1.0;
    }
    CGFloat resolvedScale = contentScale > 0 ? contentScale : fallbackScale;
    self.renderView.contentScaleFactor = resolvedScale;
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_runtime != nullptr) {
        _runtime->resize(bounds.size, resolvedScale);
    }
#endif
}

- (void)drawIfNeeded {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_runtime != nullptr) {
        _runtime->render();
    }
#endif
}

@end
