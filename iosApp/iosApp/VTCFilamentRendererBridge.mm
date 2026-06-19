#import "VTCFilamentRendererBridge.h"

#import <QuartzCore/CAMetalLayer.h>

#if __has_include(<filament/Engine.h>)
#define VTC_FILAMENT_HEADERS_AVAILABLE 1
#else
#define VTC_FILAMENT_HEADERS_AVAILABLE 0
#endif

#if VTC_FILAMENT_HEADERS_AVAILABLE
#include <filament/Box.h>
#include <filament/Camera.h>
#include <filament/Engine.h>
#include <filament/IndirectLight.h>
#include <filament/LightManager.h>
#include <filament/MaterialInstance.h>
#include <filament/RenderableManager.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/SwapChain.h>
#include <filament/TransformManager.h>
#include <filament/View.h>
#include <filament/Viewport.h>
#include <gltfio/AssetLoader.h>
#include <gltfio/FilamentAsset.h>
#include <gltfio/FilamentInstance.h>
#include <gltfio/MaterialProvider.h>
#include <gltfio/ResourceLoader.h>
#include <gltfio/TextureProvider.h>
#include <gltfio/materials/uberarchive.h>
#include <math/mat4.h>
#include <math/vec3.h>
#include <utils/Entity.h>
#include <utils/EntityManager.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>
#endif

typedef NS_ENUM(NSInteger, VTCFilamentRendererErrorCode) {
    VTCFilamentRendererErrorCodeInvalidInput = 0,
    VTCFilamentRendererErrorCodeUnavailable = 1,
    VTCFilamentRendererErrorCodeInvalidAsset = 2,
    VTCFilamentRendererErrorCodeResourceLoadFailed = 3,
    VTCFilamentRendererErrorCodeSceneSetupFailed = 4,
};

static NSString *const VTCFilamentRendererErrorDomain = @"io.github.jtakumi.VtuberCamera_KMP_ver.filament";

static NSError *VTCMakeRendererError(VTCFilamentRendererErrorCode code, NSString *message) {
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

@implementation VTCAvatarMorphBind
@end

static VTCAvatarRenderState *VTCCopyAvatarRenderState(VTCAvatarRenderState *state) {
    VTCAvatarRenderState *copy = [[VTCAvatarRenderState alloc] init];
    copy.headYawDegrees = state.headYawDegrees;
    copy.headPitchDegrees = state.headPitchDegrees;
    copy.headRollDegrees = state.headRollDegrees;
    copy.leftEyeBlink = state.leftEyeBlink;
    copy.rightEyeBlink = state.rightEyeBlink;
    copy.jawOpen = state.jawOpen;
    copy.mouthSmile = state.mouthSmile;
    copy.isTracking = state.isTracking;
    copy.trackingConfidence = state.trackingConfidence;
    return copy;
}

#if VTC_FILAMENT_HEADERS_AVAILABLE

namespace {

// Scene constants mirrored from AndroidFilamentAvatarRenderer / AndroidAvatarRenderBridge so both
// platforms frame and light the same avatar identically.
constexpr float kLightDirection[3] = {0.35f, -1.0f, -0.45f};
constexpr float kLightColor[3] = {1.0f, 0.98f, 0.95f};
constexpr float kLightIntensity = 110000.0f;
constexpr float kIndirectLightIntensity = 35000.0f;
constexpr double kCameraFovDegrees = 45.0;
constexpr double kCameraNear = 0.1;
constexpr double kCameraFar = 100.0;
constexpr double kDefaultCameraDistance = 4.0;
constexpr float kMinModelHalfExtent = 0.75f;
constexpr double kModelFitDistanceMultiplier = 2.8;
constexpr double kCameraYawOffsetScale = 0.8;
constexpr double kCameraPitchOffsetScale = 0.45;
constexpr double kMaxCameraYawDegrees = 45.0;
constexpr double kMaxCameraPitchDegrees = 30.0;
constexpr float kJawWeight = 0.5f;
constexpr float kSmileWeight = 0.35f;
constexpr float kBlinkWeight = 0.15f;
constexpr double kExpressionYOffsetScale = 0.18;
constexpr double kExpressionZOffsetScale = 0.24;
constexpr uint8_t kSceneLayerMask = 0xff;
constexpr uint8_t kSceneLayerVisible = 0x1;

// GLB layout constants used to rewrite escaped slashes inside the JSON chunk before gltfio/cgltf
// parses URIs (port of AndroidVrmAssetLoader.normalizeJsonEscapedSlashesForGltfio).
constexpr uint32_t kGlbMagic = 0x46546C67;
constexpr uint32_t kGlbVersion = 2;
constexpr uint32_t kGlbJsonChunkType = 0x4E4F534A;
constexpr size_t kGlbHeaderSize = 12;
constexpr size_t kGlbChunkHeaderSize = 8;
constexpr size_t kGlbMinSize = kGlbHeaderSize + kGlbChunkHeaderSize;
constexpr size_t kGlbAlignment = 4;
constexpr uint8_t kGlbJsonPaddingByte = 0x20;

uint32_t readUint32LE(const uint8_t *bytes, size_t offset) {
    uint32_t value = 0;
    std::memcpy(&value, bytes + offset, sizeof(value));
    return value;
}

void appendUint32LE(std::vector<uint8_t> &output, uint32_t value) {
    output.push_back(static_cast<uint8_t>(value & 0xFF));
    output.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
    output.push_back(static_cast<uint8_t>((value >> 16) & 0xFF));
    output.push_back(static_cast<uint8_t>((value >> 24) & 0xFF));
}

// Rewrites "\/" into "/" inside the GLB JSON chunk; returns the input unchanged when the bytes are
// not a well-formed GLB container or no escaped slashes are present.
std::vector<uint8_t> normalizeJsonEscapedSlashesForGltfio(const uint8_t *bytes, size_t size) {
    std::vector<uint8_t> original(bytes, bytes + size);
    if (size < kGlbMinSize || readUint32LE(bytes, 0) != kGlbMagic) {
        return original;
    }

    const uint32_t declaredLength = readUint32LE(bytes, 8);
    if (declaredLength > size || declaredLength < kGlbMinSize) {
        return original;
    }

    struct GlbChunk {
        uint32_t type;
        std::vector<uint8_t> payload;
    };
    std::vector<GlbChunk> chunks;
    size_t offset = kGlbHeaderSize;
    bool changed = false;
    while (offset + kGlbChunkHeaderSize <= declaredLength) {
        const uint32_t chunkLength = readUint32LE(bytes, offset);
        const uint32_t chunkType = readUint32LE(bytes, offset + 4);
        if (chunkLength > declaredLength - offset - kGlbChunkHeaderSize) {
            return original;
        }

        const size_t chunkStart = offset + kGlbChunkHeaderSize;
        std::vector<uint8_t> payload(bytes + chunkStart, bytes + chunkStart + chunkLength);
        if (chunkType == kGlbJsonChunkType) {
            std::string json(payload.begin(), payload.end());
            while (!json.empty() && (json.back() == ' ' || json.back() == '\0' || json.back() == '\t' ||
                                     json.back() == '\r' || json.back() == '\n')) {
                json.pop_back();
            }
            std::string normalized;
            normalized.reserve(json.size());
            for (size_t index = 0; index < json.size(); ++index) {
                if (json[index] == '\\' && index + 1 < json.size() && json[index + 1] == '/') {
                    normalized.push_back('/');
                    ++index;
                    changed = true;
                } else {
                    normalized.push_back(json[index]);
                }
            }
            payload.assign(normalized.begin(), normalized.end());
            while (payload.size() % kGlbAlignment != 0) {
                payload.push_back(kGlbJsonPaddingByte);
            }
        }
        chunks.push_back({chunkType, std::move(payload)});
        offset = chunkStart + chunkLength;
    }

    if (!changed) {
        return original;
    }

    size_t nextLength = kGlbHeaderSize;
    for (const auto &chunk : chunks) {
        nextLength += kGlbChunkHeaderSize + chunk.payload.size();
    }
    std::vector<uint8_t> output;
    output.reserve(nextLength);
    appendUint32LE(output, kGlbMagic);
    appendUint32LE(output, kGlbVersion);
    appendUint32LE(output, static_cast<uint32_t>(nextLength));
    for (const auto &chunk : chunks) {
        appendUint32LE(output, static_cast<uint32_t>(chunk.payload.size()));
        appendUint32LE(output, chunk.type);
        output.insert(output.end(), chunk.payload.begin(), chunk.payload.end());
    }
    return output;
}

float clamp01(float value) {
    return std::min(std::max(value, 0.0f), 1.0f);
}

// Owns every Filament object created by the bridge and applies render-state updates to the loaded
// avatar. All methods must run on the main thread (the bridge's callers are @MainActor-bound).
class VTCFilamentCore {
public:
    ~VTCFilamentCore() {
        destroyAll();
    }

    // Lazily creates the engine, scene, lights, and gltfio loaders. Returns false when Metal-backed
    // engine creation fails (for example in environments without a Metal device).
    bool ensureEngine() {
        if (mEngine != nullptr) {
            return true;
        }
        mEngine = filament::Engine::create(filament::Engine::Backend::METAL);
        if (mEngine == nullptr) {
            return false;
        }

        mRenderer = mEngine->createRenderer();
        filament::Renderer::ClearOptions clearOptions = mRenderer->getClearOptions();
        clearOptions.clear = true;
        clearOptions.clearColor = {0.0f, 0.0f, 0.0f, 0.0f};
        mRenderer->setClearOptions(clearOptions);

        mScene = mEngine->createScene();
        mView = mEngine->createView();
        mCameraEntity = utils::EntityManager::get().create();
        mCamera = mEngine->createCamera(mCameraEntity);
        mView->setScene(mScene);
        mView->setCamera(mCamera);
        mView->setBlendMode(filament::View::BlendMode::TRANSLUCENT);

        mLightEntity = utils::EntityManager::get().create();
        filament::LightManager::Builder(filament::LightManager::Type::DIRECTIONAL)
            .direction({kLightDirection[0], kLightDirection[1], kLightDirection[2]})
            .color({kLightColor[0], kLightColor[1], kLightColor[2]})
            .intensity(kLightIntensity)
            .build(*mEngine, mLightEntity);
        mScene->addEntity(mLightEntity);

        const filament::math::float3 irradiance[1] = {{1.0f, 1.0f, 1.0f}};
        mIndirectLight = filament::IndirectLight::Builder()
            .irradiance(1, irradiance)
            .intensity(kIndirectLightIntensity)
            .build(*mEngine);
        mScene->setIndirectLight(mIndirectLight);

        mMaterialProvider = filament::gltfio::createUbershaderProvider(
            mEngine, UBERARCHIVE_DEFAULT_DATA, UBERARCHIVE_DEFAULT_SIZE);
        filament::gltfio::AssetConfiguration assetConfiguration{};
        assetConfiguration.engine = mEngine;
        assetConfiguration.materials = mMaterialProvider;
        mAssetLoader = filament::gltfio::AssetLoader::create(assetConfiguration);

        filament::gltfio::ResourceConfiguration resourceConfiguration{};
        resourceConfiguration.engine = mEngine;
        resourceConfiguration.normalizeSkinningWeights = true;
        mResourceLoader = new filament::gltfio::ResourceLoader(resourceConfiguration);
        mStbDecoder = filament::gltfio::createStbProvider(mEngine);
        mKtx2Decoder = filament::gltfio::createKtx2Provider(mEngine);
        mResourceLoader->addTextureProvider("image/png", mStbDecoder);
        mResourceLoader->addTextureProvider("image/jpeg", mStbDecoder);
        mResourceLoader->addTextureProvider("image/ktx2", mKtx2Decoder);

        applyViewportIfReady();
        updateCameraLookAt();
        return true;
    }

    // Creates the swap chain against the Metal layer once it has a non-zero drawable size.
    void ensureSwapChain(CAMetalLayer *layer) {
        if (mEngine == nullptr || mSwapChain != nullptr || layer == nil) {
            return;
        }
        if (layer.drawableSize.width < 1 || layer.drawableSize.height < 1) {
            return;
        }
        mSwapChain = mEngine->createSwapChain(
            (__bridge void *)layer, filament::SwapChain::CONFIG_TRANSPARENT);
    }

    // Loads the avatar bytes and rebinds the head bone and morph targets. On failure the partially
    // created asset is destroyed and the previous avatar (if any) stays untouched.
    bool loadAvatar(NSData *data,
                    NSInteger headNodeIndex,
                    NSArray<VTCAvatarMorphBind *> *morphBinds,
                    NSError *__autoreleasing *error) {
        std::vector<uint8_t> renderBytes = normalizeJsonEscapedSlashesForGltfio(
            static_cast<const uint8_t *>(data.bytes), static_cast<size_t>(data.length));
        filament::gltfio::FilamentAsset *nextAsset = mAssetLoader->createAsset(
            renderBytes.data(), static_cast<uint32_t>(renderBytes.size()));
        if (nextAsset == nullptr) {
            if (error != nullptr) {
                *error = VTCMakeRendererError(
                    VTCFilamentRendererErrorCodeInvalidAsset,
                    @"The avatar bytes could not be parsed as a GLB/VRM asset.");
            }
            return false;
        }

        if (!mResourceLoader->loadResources(nextAsset)) {
            mAssetLoader->destroyAsset(nextAsset);
            if (error != nullptr) {
                *error = VTCMakeRendererError(
                    VTCFilamentRendererErrorCodeResourceLoadFailed,
                    @"The avatar resources (buffers/textures) could not be loaded.");
            }
            return false;
        }
        mResourceLoader->evictResourceData();
        nextAsset->releaseSourceData();

        clearAvatar();
        mAsset = nextAsset;
        configureRenderables();
        mScene->addEntities(mAsset->getEntities(), mAsset->getEntityCount());
        updateSceneFraming();
        bindHeadNode(headNodeIndex);
        bindMorphTargets(morphBinds);
        updateCameraLookAt();
        return true;
    }

    // Removes the current avatar from the scene and resets framing back to the defaults.
    void clearAvatar() {
        if (mAsset != nullptr) {
            mScene->removeEntities(mAsset->getEntities(), mAsset->getEntityCount());
            mAssetLoader->destroyAsset(mAsset);
            mAsset = nullptr;
        }
        mHeadTransformInstanceValid = false;
        mMorphWeights.clear();
        mResolvedMorphBinds.clear();
        mFramingTarget = {0.0, 0.0, 0.0};
        mFramingDistance = kDefaultCameraDistance;
    }

    bool hasAsset() const {
        return mAsset != nullptr;
    }

    // Stores the surface size in pixels and refreshes the viewport and projection when ready.
    void resize(double widthPixels, double heightPixels) {
        mSurfaceWidth = std::max(widthPixels, 1.0);
        mSurfaceHeight = std::max(heightPixels, 1.0);
        applyViewportIfReady();
    }

    // Applies the latest render state and draws a frame when the swap chain and asset are ready.
    void draw(VTCAvatarRenderState *state) {
        if (mEngine == nullptr || mSwapChain == nullptr || mAsset == nullptr) {
            return;
        }
        applyRenderState(state);

        filament::gltfio::FilamentInstance *instance = mAsset->getInstance();
        if (instance != nullptr && instance->getAnimator() != nullptr) {
            instance->getAnimator()->updateBoneMatrices();
        }
        if (mRenderer->beginFrame(mSwapChain)) {
            mRenderer->render(mView);
            mRenderer->endFrame();
        }
    }

    // Destroys every Filament resource in the same order as the Android renderer teardown.
    void destroyAll() {
        if (mEngine == nullptr) {
            return;
        }
        clearAvatar();
        if (mSwapChain != nullptr) {
            mEngine->destroy(mSwapChain);
            mSwapChain = nullptr;
        }
        mEngine->flushAndWait();
        mScene->remove(mLightEntity);
        mEngine->destroy(mLightEntity);
        utils::EntityManager::get().destroy(mLightEntity);
        mScene->setIndirectLight(nullptr);
        mEngine->destroy(mIndirectLight);
        mIndirectLight = nullptr;
        delete mResourceLoader;
        mResourceLoader = nullptr;
        delete mStbDecoder;
        mStbDecoder = nullptr;
        delete mKtx2Decoder;
        mKtx2Decoder = nullptr;
        filament::gltfio::AssetLoader::destroy(&mAssetLoader);
        mMaterialProvider->destroyMaterials();
        delete mMaterialProvider;
        mMaterialProvider = nullptr;
        mEngine->destroy(mRenderer);
        mRenderer = nullptr;
        mEngine->destroy(mView);
        mView = nullptr;
        mEngine->destroy(mScene);
        mScene = nullptr;
        mEngine->destroyCameraComponent(mCameraEntity);
        utils::EntityManager::get().destroy(mCameraEntity);
        filament::Engine::destroy(&mEngine);
        mEngine = nullptr;
    }

private:
    struct RenderSnapshot {
        float yaw = 0;
        float pitch = 0;
        float roll = 0;
        float leftEyeBlink = 0;
        float rightEyeBlink = 0;
        float jawOpen = 0;
        float mouthSmile = 0;
        bool isTracking = false;
        float trackingConfidence = 0;

        bool operator==(const RenderSnapshot &other) const {
            return yaw == other.yaw && pitch == other.pitch && roll == other.roll &&
                leftEyeBlink == other.leftEyeBlink && rightEyeBlink == other.rightEyeBlink &&
                jawOpen == other.jawOpen && mouthSmile == other.mouthSmile &&
                isTracking == other.isTracking && trackingConfidence == other.trackingConfidence;
        }
    };

    struct ResolvedMorphBind {
        VTCAvatarMorphChannel channel;
        utils::Entity entity;
        NSInteger morphTargetIndex;
        float weight;
    };

    // Mirrors FilamentAsset.configureRenderables on Android: always-visible layers, no culling,
    // and double-sided materials so thin VRM meshes do not disappear.
    void configureRenderables() {
        filament::RenderableManager &renderableManager = mEngine->getRenderableManager();
        const utils::Entity *renderables = mAsset->getRenderableEntities();
        const size_t renderableCount = mAsset->getRenderableEntityCount();
        for (size_t index = 0; index < renderableCount; ++index) {
            auto renderable = renderableManager.getInstance(renderables[index]);
            if (!renderable) {
                continue;
            }
            renderableManager.setLayerMask(renderable, kSceneLayerMask, kSceneLayerVisible);
            renderableManager.setCulling(renderable, false);
            const size_t primitiveCount = renderableManager.getPrimitiveCount(renderable);
            for (size_t primitiveIndex = 0; primitiveIndex < primitiveCount; ++primitiveIndex) {
                filament::MaterialInstance *material =
                    renderableManager.getMaterialInstanceAt(renderable, primitiveIndex);
                if (material != nullptr) {
                    material->setDoubleSided(true);
                }
            }
        }
    }

    void updateSceneFraming() {
        const filament::Aabb bounds = mAsset->getBoundingBox();
        const filament::math::float3 center = bounds.center();
        const filament::math::float3 halfExtent = bounds.extent();
        const float maxHalfExtent = std::max(
            std::max(halfExtent.x, halfExtent.y), std::max(halfExtent.z, kMinModelHalfExtent));
        mFramingTarget = {center.x, center.y, center.z};
        mFramingDistance = std::max(
            kDefaultCameraDistance, static_cast<double>(maxHalfExtent) * kModelFitDistanceMultiplier);
    }

    // Caches the head bone transform instance and its rest-pose local transform. A missing or
    // out-of-range head node leaves the avatar driven by morphs only (same as Android).
    void bindHeadNode(NSInteger headNodeIndex) {
        mHeadTransformInstanceValid = false;
        if (headNodeIndex < 0 ||
            static_cast<size_t>(headNodeIndex) >= mAsset->getEntityCount()) {
            return;
        }
        const utils::Entity headEntity = mAsset->getEntities()[headNodeIndex];
        filament::TransformManager &transformManager = mEngine->getTransformManager();
        auto transformInstance = transformManager.getInstance(headEntity);
        if (!transformInstance) {
            return;
        }
        mHeadEntity = headEntity;
        mHeadBaseLocalTransform = transformManager.getTransform(transformInstance);
        mHeadTransformInstanceValid = true;
    }

    // Resolves notification-provided node indices into entities and pre-allocates one morph weight
    // buffer per entity that exposes morph targets.
    void bindMorphTargets(NSArray<VTCAvatarMorphBind *> *morphBinds) {
        mMorphWeights.clear();
        mResolvedMorphBinds.clear();

        filament::RenderableManager &renderableManager = mEngine->getRenderableManager();
        const utils::Entity *renderables = mAsset->getRenderableEntities();
        const size_t renderableCount = mAsset->getRenderableEntityCount();
        for (size_t index = 0; index < renderableCount; ++index) {
            auto renderable = renderableManager.getInstance(renderables[index]);
            if (!renderable) {
                continue;
            }
            const size_t morphTargetCount = renderableManager.getMorphTargetCount(renderable);
            if (morphTargetCount > 0) {
                mMorphWeights[renderables[index].getId()] =
                    std::vector<float>(morphTargetCount, 0.0f);
            }
        }

        const utils::Entity *entities = mAsset->getEntities();
        const size_t entityCount = mAsset->getEntityCount();
        for (VTCAvatarMorphBind *bind in morphBinds) {
            if (bind.nodeIndex < 0 || static_cast<size_t>(bind.nodeIndex) >= entityCount) {
                continue;
            }
            const utils::Entity entity = entities[bind.nodeIndex];
            if (mMorphWeights.find(entity.getId()) == mMorphWeights.end()) {
                continue;
            }
            mResolvedMorphBinds.push_back(
                {bind.channel, entity, bind.morphTargetIndex, bind.weight});
        }
    }

    void applyRenderState(VTCAvatarRenderState *state) {
        RenderSnapshot snapshot;
        snapshot.yaw = state.headYawDegrees;
        snapshot.pitch = state.headPitchDegrees;
        snapshot.roll = state.headRollDegrees;
        snapshot.leftEyeBlink = state.leftEyeBlink;
        snapshot.rightEyeBlink = state.rightEyeBlink;
        snapshot.jawOpen = state.jawOpen;
        snapshot.mouthSmile = state.mouthSmile;
        snapshot.isTracking = state.isTracking;
        snapshot.trackingConfidence = state.trackingConfidence;
        if (snapshot == mAppliedSnapshot) {
            return;
        }
        mAppliedSnapshot = snapshot;
        applyHeadPose(snapshot);
        applyExpressions(snapshot);
        updateCameraLookAt();
    }

    // Rotates the head bone around its rest pose: base * R_yaw(Y) * R_pitch(X) * R_roll(Z), the
    // same axis order as AndroidAvatarRuntimeController.rotationMatrix.
    void applyHeadPose(const RenderSnapshot &snapshot) {
        if (!mHeadTransformInstanceValid) {
            return;
        }
        filament::TransformManager &transformManager = mEngine->getTransformManager();
        auto transformInstance = transformManager.getInstance(mHeadEntity);
        if (!transformInstance) {
            return;
        }
        const float yawRadians = snapshot.yaw * static_cast<float>(M_PI) / 180.0f;
        const float pitchRadians = snapshot.pitch * static_cast<float>(M_PI) / 180.0f;
        const float rollRadians = snapshot.roll * static_cast<float>(M_PI) / 180.0f;
        const filament::math::mat4f rotation =
            filament::math::mat4f::rotation(yawRadians, filament::math::float3{0, 1, 0}) *
            filament::math::mat4f::rotation(pitchRadians, filament::math::float3{1, 0, 0}) *
            filament::math::mat4f::rotation(rollRadians, filament::math::float3{0, 0, 1});
        transformManager.setTransform(transformInstance, mHeadBaseLocalTransform * rotation);
    }

    // Accumulates expression weights into per-entity morph buffers and pushes them to Filament,
    // mirroring AndroidAvatarRuntimeController.applyExpressions.
    void applyExpressions(const RenderSnapshot &snapshot) {
        if (mMorphWeights.empty()) {
            return;
        }
        for (auto &entry : mMorphWeights) {
            std::fill(entry.second.begin(), entry.second.end(), 0.0f);
        }
        for (const auto &bind : mResolvedMorphBinds) {
            const float expressionWeight = clamp01(channelWeight(snapshot, bind.channel));
            if (expressionWeight <= 0.0f) {
                continue;
            }
            auto weightsEntry = mMorphWeights.find(bind.entity.getId());
            if (weightsEntry == mMorphWeights.end()) {
                continue;
            }
            std::vector<float> &weights = weightsEntry->second;
            if (bind.morphTargetIndex < 0 ||
                static_cast<size_t>(bind.morphTargetIndex) >= weights.size()) {
                continue;
            }
            weights[bind.morphTargetIndex] =
                clamp01(weights[bind.morphTargetIndex] + expressionWeight * bind.weight);
        }

        filament::RenderableManager &renderableManager = mEngine->getRenderableManager();
        const utils::Entity *renderables = mAsset->getRenderableEntities();
        const size_t renderableCount = mAsset->getRenderableEntityCount();
        for (size_t index = 0; index < renderableCount; ++index) {
            auto weightsEntry = mMorphWeights.find(renderables[index].getId());
            if (weightsEntry == mMorphWeights.end()) {
                continue;
            }
            auto renderable = renderableManager.getInstance(renderables[index]);
            if (!renderable) {
                continue;
            }
            renderableManager.setMorphWeights(
                renderable, weightsEntry->second.data(), weightsEntry->second.size(), 0);
        }
    }

    static float channelWeight(const RenderSnapshot &snapshot, VTCAvatarMorphChannel channel) {
        switch (channel) {
            case VTCAvatarMorphChannelBlinkLeft:
                return snapshot.leftEyeBlink;
            case VTCAvatarMorphChannelBlinkRight:
                return snapshot.rightEyeBlink;
            case VTCAvatarMorphChannelJawOpen:
                return snapshot.jawOpen;
            case VTCAvatarMorphChannelSmile:
                return snapshot.mouthSmile;
        }
        return 0.0f;
    }

    void applyViewportIfReady() {
        if (mView == nullptr || mCamera == nullptr) {
            return;
        }
        const uint32_t width = static_cast<uint32_t>(mSurfaceWidth);
        const uint32_t height = static_cast<uint32_t>(mSurfaceHeight);
        mView->setViewport({0, 0, width, height});
        mCamera->setProjection(
            kCameraFovDegrees, mSurfaceWidth / mSurfaceHeight, kCameraNear, kCameraFar,
            filament::Camera::Fov::VERTICAL);
    }

    // Mirrors AndroidFilamentAvatarRenderer.updateCameraLookAt: tracked head pose adds a small
    // camera parallax, and strong expressions pull the camera slightly up and closer.
    void updateCameraLookAt() {
        if (mCamera == nullptr) {
            return;
        }
        const RenderSnapshot &snapshot = mAppliedSnapshot;
        const double trackingInfluence =
            snapshot.isTracking ? clamp01(snapshot.trackingConfidence) : 0.0;
        const double expressionInfluence = clamp01(
            snapshot.jawOpen * kJawWeight + snapshot.mouthSmile * kSmileWeight +
            ((snapshot.leftEyeBlink + snapshot.rightEyeBlink) * 0.5f) * kBlinkWeight);
        const double yawRadians =
            std::clamp(static_cast<double>(snapshot.yaw), -kMaxCameraYawDegrees, kMaxCameraYawDegrees) *
            M_PI / 180.0;
        const double pitchRadians =
            std::clamp(static_cast<double>(snapshot.pitch), -kMaxCameraPitchDegrees, kMaxCameraPitchDegrees) *
            M_PI / 180.0;

        mCamera->lookAt(
            {mFramingTarget.x + std::sin(yawRadians) * kCameraYawOffsetScale * trackingInfluence,
             mFramingTarget.y + std::sin(pitchRadians) * kCameraPitchOffsetScale * trackingInfluence +
                 expressionInfluence * kExpressionYOffsetScale,
             mFramingTarget.z + mFramingDistance - expressionInfluence * kExpressionZOffsetScale},
            {mFramingTarget.x, mFramingTarget.y, mFramingTarget.z},
            {0.0, 1.0, 0.0});
    }

    filament::Engine *mEngine = nullptr;
    filament::Renderer *mRenderer = nullptr;
    filament::Scene *mScene = nullptr;
    filament::View *mView = nullptr;
    filament::Camera *mCamera = nullptr;
    filament::SwapChain *mSwapChain = nullptr;
    filament::IndirectLight *mIndirectLight = nullptr;
    utils::Entity mCameraEntity;
    utils::Entity mLightEntity;
    filament::gltfio::MaterialProvider *mMaterialProvider = nullptr;
    filament::gltfio::AssetLoader *mAssetLoader = nullptr;
    filament::gltfio::ResourceLoader *mResourceLoader = nullptr;
    filament::gltfio::TextureProvider *mStbDecoder = nullptr;
    filament::gltfio::TextureProvider *mKtx2Decoder = nullptr;
    filament::gltfio::FilamentAsset *mAsset = nullptr;

    utils::Entity mHeadEntity;
    filament::math::mat4f mHeadBaseLocalTransform;
    bool mHeadTransformInstanceValid = false;
    std::unordered_map<uint32_t, std::vector<float>> mMorphWeights;
    std::vector<ResolvedMorphBind> mResolvedMorphBinds;

    filament::math::double3 mFramingTarget = {0.0, 0.0, 0.0};
    double mFramingDistance = kDefaultCameraDistance;
    double mSurfaceWidth = 1.0;
    double mSurfaceHeight = 1.0;
    RenderSnapshot mAppliedSnapshot;
};

}  // namespace

#endif  // VTC_FILAMENT_HEADERS_AVAILABLE

@interface VTCFilamentRendererBridge ()

@property (nonatomic, strong) UIView *renderView;
@property (nonatomic, strong) VTCAvatarRenderState *latestAvatarState;

@end

@implementation VTCFilamentRendererBridge {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    std::unique_ptr<VTCFilamentCore> _core;
#endif
}

+ (BOOL)isFilamentRuntimeAvailable {
    return VTC_FILAMENT_HEADERS_AVAILABLE;
}

- (instancetype)init {
    self = [super init];
    if (self != nil) {
        _renderView = [[VTCMetalContainerView alloc] initWithFrame:CGRectZero];
        _latestAvatarState = [[VTCAvatarRenderState alloc] init];
    }
    return self;
}

- (BOOL)loadAvatarData:(NSData *)data
         headNodeIndex:(NSInteger)headNodeIndex
            morphBinds:(NSArray<VTCAvatarMorphBind *> *)morphBinds
                 error:(NSError * _Nullable __autoreleasing *)error {
    if (data.length == 0) {
        if (error != nil) {
            *error = VTCMakeRendererError(
                VTCFilamentRendererErrorCodeInvalidInput, @"Avatar data must not be empty.");
        }
        return NO;
    }

#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_core == nullptr) {
        _core = std::make_unique<VTCFilamentCore>();
    }
    if (!_core->ensureEngine()) {
        if (error != nil) {
            *error = VTCMakeRendererError(
                VTCFilamentRendererErrorCodeUnavailable,
                @"The Filament Metal engine could not be created on this device.");
        }
        return NO;
    }
    return _core->loadAvatar(data, headNodeIndex, morphBinds, error);
#else
    if (error != nil) {
        *error = VTCMakeRendererError(
            VTCFilamentRendererErrorCodeUnavailable,
            @"Filament SDK headers are not configured for iosApp yet. Run scripts/setup_filament_ios.sh.");
    }
    return NO;
#endif
}

- (void)clearAvatar {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_core != nullptr) {
        _core->clearAvatar();
    }
#endif
}

- (void)updateAvatarState:(VTCAvatarRenderState *)state {
    self.latestAvatarState = VTCCopyAvatarRenderState(state);
}

- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale {
    self.renderView.frame = bounds;
    CGFloat fallbackScale = self.renderView.window.screen.scale;
    if (fallbackScale <= 0) {
        fallbackScale = 1.0;
    }
    const CGFloat scale = contentScale > 0 ? contentScale : fallbackScale;
    self.renderView.contentScaleFactor = scale;

#if VTC_FILAMENT_HEADERS_AVAILABLE
    CAMetalLayer *metalLayer = (CAMetalLayer *)self.renderView.layer;
    const CGSize drawableSize = CGSizeMake(bounds.size.width * scale, bounds.size.height * scale);
    if (drawableSize.width >= 1 && drawableSize.height >= 1) {
        metalLayer.drawableSize = drawableSize;
        if (_core != nullptr) {
            _core->resize(drawableSize.width, drawableSize.height);
        }
    }
#endif
}

- (void)drawIfNeeded {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_core == nullptr || !_core->hasAsset()) {
        return;
    }
    _core->ensureSwapChain((CAMetalLayer *)self.renderView.layer);
    _core->draw(self.latestAvatarState);
#endif
}

@end
