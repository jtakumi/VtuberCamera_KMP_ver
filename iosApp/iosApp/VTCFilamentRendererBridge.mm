#import "VTCFilamentRendererBridge.h"

#import <QuartzCore/CAMetalLayer.h>

#if __has_include(<filament/Engine.h>)
#define VTC_FILAMENT_HEADERS_AVAILABLE 1
#else
#define VTC_FILAMENT_HEADERS_AVAILABLE 0
#endif

#if VTC_FILAMENT_HEADERS_AVAILABLE
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
#include <gltfio/Animator.h>
#include <gltfio/FilamentAsset.h>
#include <gltfio/FilamentInstance.h>
#include <gltfio/MaterialProvider.h>
#include <gltfio/ResourceLoader.h>
#include <gltfio/TextureProvider.h>
#include <gltfio/materials/uberarchive.h>

#include <utils/Entity.h>
#include <utils/EntityManager.h>
#include <utils/NameComponentManager.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>
#endif

typedef NS_ENUM(NSInteger, VTCFilamentRendererErrorCode) {
    VTCFilamentRendererErrorCodeInvalidInput = 0,
    VTCFilamentRendererErrorCodeUnavailable = 1,
    VTCFilamentRendererErrorCodeInvalidAsset = 2,
    VTCFilamentRendererErrorCodeResourceLoadFailed = 3,
};

static NSString *const VTCFilamentRendererErrorDomain = @"io.github.jtakumi.VtuberCamera_KMP_ver.filament";

@interface VTCMetalContainerView : UIView
@end

@implementation VTCMetalContainerView

+ (Class)layerClass {
    return [CAMetalLayer class];
}

@end

/// Neutral avatar scale used before any pinch gesture arrives.
static const float VTCDefaultAvatarScale = 1.0f;

@implementation VTCAvatarRenderState

- (instancetype)init {
    self = [super init];
    if (self != nil) {
        _avatarScale = VTCDefaultAvatarScale;
    }
    return self;
}

@end

@implementation VTCVrmHumanoidBone

- (instancetype)initWithBoneName:(NSString *)boneName nodeName:(NSString *)nodeName {
    self = [super init];
    if (self != nil) {
        _boneName = [boneName copy];
        _nodeName = [nodeName copy];
    }
    return self;
}

@end

@implementation VTCVrmMorphBind

- (instancetype)initWithEntityId:(NSInteger)entityId
                morphTargetIndex:(NSInteger)morphTargetIndex
                          weight:(float)weight {
    self = [super init];
    if (self != nil) {
        _entityId = entityId;
        _morphTargetIndex = morphTargetIndex;
        _weight = weight;
    }
    return self;
}

@end

@implementation VTCVrmExpressionBinding

- (instancetype)initWithChannel:(VTCVrmExpressionChannel)channel
                     morphBinds:(NSArray<VTCVrmMorphBind *> *)morphBinds {
    self = [super init];
    if (self != nil) {
        _channel = channel;
        _morphBinds = [morphBinds copy];
    }
    return self;
}

@end

static VTCAvatarRenderState *VTCCopyAvatarRenderState(VTCAvatarRenderState *state) {
    VTCAvatarRenderState *copy = [[VTCAvatarRenderState alloc] init];
    copy.headYawDegrees = state.headYawDegrees;
    copy.headPitchDegrees = state.headPitchDegrees;
    copy.headRollDegrees = state.headRollDegrees;
    copy.bodySwayDegrees = state.bodySwayDegrees;
    copy.bodyLeanDegrees = state.bodyLeanDegrees;
    copy.leftEyeBlink = state.leftEyeBlink;
    copy.rightEyeBlink = state.rightEyeBlink;
    copy.jawOpen = state.jawOpen;
    copy.mouthSmile = state.mouthSmile;
    copy.avatarScale = state.avatarScale;
    copy.trackingConfidence = state.trackingConfidence;
    copy.tracking = state.tracking;
    return copy;
}

#if VTC_FILAMENT_HEADERS_AVAILABLE

namespace {

using namespace filament;
using namespace filament::math;
using utils::Entity;
namespace gltfio = filament::gltfio;

// Framing, lighting and tracking-response constants mirrored from
// AndroidFilamentAvatarRenderer so both platforms frame the avatar identically.
constexpr double kDefaultCameraDistance = 4.0;
constexpr float kMinModelHalfExtent = 0.75f;
constexpr double kModelFitDistanceMultiplier = 2.8;
constexpr double kCameraYawOffsetScale = 0.8;
constexpr double kCameraPitchOffsetScale = 0.45;
constexpr double kMaxYawDegrees = 45.0;
constexpr double kMaxPitchDegrees = 30.0;
constexpr float kJawWeight = 0.5f;
constexpr float kSmileWeight = 0.35f;
constexpr float kBlinkWeight = 0.15f;
constexpr double kExpressionYOffsetScale = 0.18;
constexpr double kExpressionZOffsetScale = 0.24;
constexpr float kLightIntensity = 110000.0f;
constexpr float kIndirectLightIntensity = 35000.0f;
constexpr uint8_t kSceneLayerMask = 0xff;
constexpr uint8_t kSceneLayerVisible = 0x1;
constexpr double kFieldOfViewDegrees = 45.0;
constexpr double kNearPlane = 0.1;
constexpr double kFarPlane = 100.0;

// Matches MIN_AVATAR_SCALE / MAX_AVATAR_SCALE in the shared Compose avatar module.
constexpr float kMinAvatarScale = 0.5f;
constexpr float kMaxAvatarScale = 3.0f;

// Relaxed arm pose keeps imported VRM avatars out of their bind/T-pose.
constexpr float kRelaxedLeftArmRollDegrees = -75.0f;
constexpr float kRelaxedRightArmRollDegrees = 75.0f;

constexpr const char* kHeadBoneName = "head";
constexpr const char* kNeckBoneName = "neck";
constexpr const char* kChestBoneName = "chest";
constexpr const char* kSpineBoneName = "spine";
constexpr const char* kLeftUpperArmBoneName = "leftUpperArm";
constexpr const char* kRightUpperArmBoneName = "rightUpperArm";

// GLB container layout, used to normalize the JSON chunk before handing it to gltfio.
constexpr uint32_t kGlbMagic = 0x46546C67;
constexpr uint32_t kGlbVersion = 2;
constexpr uint32_t kGlbJsonChunkType = 0x4E4F534A;
constexpr size_t kGlbHeaderSize = 12;
constexpr size_t kGlbChunkHeaderSize = 8;
constexpr size_t kGlbMinSize = kGlbHeaderSize + kGlbChunkHeaderSize;
constexpr size_t kGlbAlignment = 4;
constexpr uint8_t kGlbJsonPaddingByte = 0x20;

double clampDouble(double value, double lowerBound, double upperBound) {
    return std::min(std::max(value, lowerBound), upperBound);
}

float clampFloat(float value, float lowerBound, float upperBound) {
    return std::min(std::max(value, lowerBound), upperBound);
}

// NaN or out-of-range scales would otherwise collapse or explode the camera distance.
float sanitizeAvatarScale(float scale) {
    if (std::isnan(scale)) {
        return VTCDefaultAvatarScale;
    }
    return clampFloat(scale, kMinAvatarScale, kMaxAvatarScale);
}

double toRadians(double degrees) {
    return degrees * M_PI / 180.0;
}

uint32_t readUint32LE(const uint8_t* bytes, size_t offset) {
    return static_cast<uint32_t>(bytes[offset]) |
           (static_cast<uint32_t>(bytes[offset + 1]) << 8) |
           (static_cast<uint32_t>(bytes[offset + 2]) << 16) |
           (static_cast<uint32_t>(bytes[offset + 3]) << 24);
}

void appendUint32LE(std::vector<uint8_t>& output, uint32_t value) {
    output.push_back(static_cast<uint8_t>(value & 0xFF));
    output.push_back(static_cast<uint8_t>((value >> 8) & 0xFF));
    output.push_back(static_cast<uint8_t>((value >> 16) & 0xFF));
    output.push_back(static_cast<uint8_t>((value >> 24) & 0xFF));
}

// Some VRM exporters escape forward slashes in the GLB JSON chunk, which gltfio's parser
// rejects when resolving buffer/image URIs. Rewrites the chunk in place, mirroring
// AndroidVrmAssetLoader.normalizeJsonEscapedSlashesForGltfio.
std::vector<uint8_t> normalizeJsonEscapedSlashes(const uint8_t* bytes, size_t size) {
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
        const size_t chunkEnd = chunkStart + chunkLength;
        std::vector<uint8_t> payload(bytes + chunkStart, bytes + chunkEnd);

        if (chunkType == kGlbJsonChunkType) {
            std::string json(payload.begin(), payload.end());
            // Drop the alignment padding gltfio would otherwise parse as trailing garbage.
            while (!json.empty()) {
                const char last = json.back();
                if (last == ' ' || last == '\0' || last == '\t' || last == '\r' || last == '\n') {
                    json.pop_back();
                } else {
                    break;
                }
            }

            std::string normalized;
            normalized.reserve(json.size());
            for (size_t index = 0; index < json.size(); ++index) {
                if (json[index] == '\\' && index + 1 < json.size() && json[index + 1] == '/') {
                    normalized.push_back('/');
                    ++index;
                } else {
                    normalized.push_back(json[index]);
                }
            }

            if (normalized != json) {
                changed = true;
                payload.assign(normalized.begin(), normalized.end());
                const size_t padding = (kGlbAlignment - (payload.size() % kGlbAlignment)) % kGlbAlignment;
                payload.insert(payload.end(), padding, kGlbJsonPaddingByte);
            }
        }

        chunks.push_back(GlbChunk{chunkType, std::move(payload)});
        offset = chunkEnd;
    }

    if (!changed) {
        return original;
    }

    size_t nextLength = kGlbHeaderSize;
    for (const GlbChunk& chunk : chunks) {
        nextLength += kGlbChunkHeaderSize + chunk.payload.size();
    }

    std::vector<uint8_t> output;
    output.reserve(nextLength);
    appendUint32LE(output, kGlbMagic);
    appendUint32LE(output, kGlbVersion);
    appendUint32LE(output, static_cast<uint32_t>(nextLength));
    for (const GlbChunk& chunk : chunks) {
        appendUint32LE(output, static_cast<uint32_t>(chunk.payload.size()));
        appendUint32LE(output, chunk.type);
        output.insert(output.end(), chunk.payload.begin(), chunk.payload.end());
    }
    return output;
}

// Column-major yaw/pitch/roll composition matching AndroidAvatarRuntimeController.rotationMatrix.
mat4f rotationMatrix(float yawDegrees, float pitchDegrees, float rollDegrees) {
    const float yaw = static_cast<float>(toRadians(yawDegrees));
    const float pitch = static_cast<float>(toRadians(pitchDegrees));
    const float roll = static_cast<float>(toRadians(rollDegrees));

    const float cy = std::cos(yaw);
    const float sy = std::sin(yaw);
    const float cp = std::cos(pitch);
    const float sp = std::sin(pitch);
    const float cr = std::cos(roll);
    const float sr = std::sin(roll);

    const mat4f yawMatrix{
        float4{cy, 0.0f, -sy, 0.0f},
        float4{0.0f, 1.0f, 0.0f, 0.0f},
        float4{sy, 0.0f, cy, 0.0f},
        float4{0.0f, 0.0f, 0.0f, 1.0f},
    };
    const mat4f pitchMatrix{
        float4{1.0f, 0.0f, 0.0f, 0.0f},
        float4{0.0f, cp, sp, 0.0f},
        float4{0.0f, -sp, cp, 0.0f},
        float4{0.0f, 0.0f, 0.0f, 1.0f},
    };
    const mat4f rollMatrix{
        float4{cr, sr, 0.0f, 0.0f},
        float4{-sr, cr, 0.0f, 0.0f},
        float4{0.0f, 0.0f, 1.0f, 0.0f},
        float4{0.0f, 0.0f, 0.0f, 1.0f},
    };

    return yawMatrix * (pitchMatrix * rollMatrix);
}

/// Camera target and distance derived from the loaded model's bounding box.
struct SceneFraming {
    double targetX = 0.0;
    double targetY = 0.0;
    double targetZ = 0.0;
    double cameraDistance = kDefaultCameraDistance;
};

struct PoseBinding {
    Entity entity;
    mat4f baseLocalTransform;
    float rotationWeight;
    float swayWeight;
};

struct ArmPoseBinding {
    Entity entity;
    mat4f baseLocalTransform;
    float rollDegrees;
};

struct MorphBind {
    Entity entity;
    size_t morphTargetIndex;
    float weight;
};

struct ExpressionBinding {
    VTCVrmExpressionChannel channel;
    std::vector<MorphBind> morphBinds;
};

/// Owns the Filament engine and everything drawn into the avatar overlay.
class AvatarScene {
public:
    /// Returns nullptr when the Metal backend or engine cannot be created, letting the
    /// caller fall back to the static avatar preview.
    static AvatarScene* create(CAMetalLayer* layer) {
        Engine* engine = Engine::Builder().backend(backend::Backend::METAL).build();
        if (engine == nullptr) {
            return nullptr;
        }
        return new AvatarScene(engine, layer);
    }

    ~AvatarScene() {
        clearAvatar();

        // Let the backend finish with the asset's buffers before the loaders that own the
        // matching CPU-side blobs go away.
        mEngine->flushAndWait();

        mResourceLoader->evictResourceData();
        delete mResourceLoader;
        delete mStbTextureProvider;
        delete mKtxTextureProvider;
        gltfio::AssetLoader::destroy(&mAssetLoader);
        delete mMaterialProvider;
        delete mNameComponentManager;

        mScene->remove(mLightEntity);
        mEngine->destroy(mLightEntity);
        utils::EntityManager::get().destroy(mLightEntity);

        mScene->setIndirectLight(nullptr);
        mEngine->destroy(mIndirectLight);

        destroySwapChain();
        mEngine->destroy(mRenderer);
        mEngine->destroy(mView);
        mEngine->destroy(mScene);
        mEngine->destroyCameraComponent(mCameraEntity);
        utils::EntityManager::get().destroy(mCameraEntity);
        Engine::destroy(&mEngine);
    }

    bool loadAvatar(const uint8_t* bytes,
                    size_t size,
                    const std::vector<std::pair<std::string, std::string>>& humanoidBones,
                    VTCFilamentRendererErrorCode& errorCode) {
        clearAvatar();

        const std::vector<uint8_t> renderBytes = normalizeJsonEscapedSlashes(bytes, size);
        gltfio::FilamentAsset* asset = mAssetLoader->createAsset(renderBytes.data(),
                                                                static_cast<uint32_t>(renderBytes.size()));
        if (asset == nullptr) {
            errorCode = VTCFilamentRendererErrorCodeInvalidAsset;
            return false;
        }

        if (!mResourceLoader->loadResources(asset)) {
            mAssetLoader->destroyAsset(asset);
            errorCode = VTCFilamentRendererErrorCodeResourceLoadFailed;
            return false;
        }
        mResourceLoader->evictResourceData();
        asset->releaseSourceData();

        mAsset = asset;
        configureRenderables();
        createPoseBindings(humanoidBones);
        createMorphTargetBuffers();
        mFraming = framingForAsset(asset);
        mScene->addEntities(asset->getEntities(), asset->getEntityCount());
        applyAvatarState();
        return true;
    }

    void clearAvatar() {
        if (mAsset == nullptr) {
            return;
        }
        mScene->removeEntities(mAsset->getEntities(), mAsset->getEntityCount());
        mAssetLoader->destroyAsset(mAsset);
        mAsset = nullptr;
        mPoseBindings.clear();
        mArmPoseBindings.clear();
        mExpressionBindings.clear();
        mMorphWeights.clear();
        mFraming = SceneFraming{};
    }

    bool hasAvatar() const { return mAsset != nullptr; }

    Entity entityForNodeName(const std::string& nodeName) const {
        if (mAsset == nullptr || nodeName.empty()) {
            return Entity{};
        }
        return mAsset->getFirstEntityByName(nodeName.c_str());
    }

    void setExpressionBindings(std::vector<ExpressionBinding> bindings) {
        mExpressionBindings = std::move(bindings);
        applyExpressions();
    }

    void setAvatarState(VTCAvatarRenderState* state) {
        mHeadYawDegrees = state.headYawDegrees;
        mHeadPitchDegrees = state.headPitchDegrees;
        mHeadRollDegrees = state.headRollDegrees;
        mBodySwayDegrees = state.bodySwayDegrees;
        mBodyLeanDegrees = state.bodyLeanDegrees;
        mLeftEyeBlink = state.leftEyeBlink;
        mRightEyeBlink = state.rightEyeBlink;
        mJawOpen = state.jawOpen;
        mMouthSmile = state.mouthSmile;
        mAvatarScale = sanitizeAvatarScale(state.avatarScale);
        mTrackingConfidence = state.trackingConfidence;
        mIsTracking = state.tracking;
        applyAvatarState();
    }

    void resize(uint32_t widthPixels, uint32_t heightPixels) {
        const uint32_t width = std::max<uint32_t>(widthPixels, 1);
        const uint32_t height = std::max<uint32_t>(heightPixels, 1);
        if (width == mWidth && height == mHeight) {
            return;
        }
        mWidth = width;
        mHeight = height;

        mView->setViewport({0, 0, width, height});
        mCamera->setProjection(kFieldOfViewDegrees,
                               static_cast<double>(width) / static_cast<double>(height),
                               kNearPlane,
                               kFarPlane,
                               Camera::Fov::VERTICAL);
        updateCamera();
    }

    void draw() {
        if (mWidth == 0 || mHeight == 0) {
            return;
        }
        if (!ensureSwapChain()) {
            return;
        }
        if (mRenderer->beginFrame(mSwapChain)) {
            mRenderer->render(mView);
            mRenderer->endFrame();
        }
    }

private:
    AvatarScene(Engine* engine, CAMetalLayer* layer) : mEngine(engine), mLayer(layer) {
        mRenderer = mEngine->createRenderer();
        mScene = mEngine->createScene();
        mView = mEngine->createView();
        mCameraEntity = utils::EntityManager::get().create();
        mCamera = mEngine->createCamera(mCameraEntity);
        mLightEntity = utils::EntityManager::get().create();

        Renderer::ClearOptions clearOptions = mRenderer->getClearOptions();
        clearOptions.clear = true;
        clearOptions.clearColor = {0.0f, 0.0f, 0.0f, 0.0f};
        mRenderer->setClearOptions(clearOptions);

        mView->setScene(mScene);
        mView->setCamera(mCamera);
        mView->setBlendMode(View::BlendMode::TRANSLUCENT);

        const float3 irradiance[1] = {float3{1.0f, 1.0f, 1.0f}};
        mIndirectLight = IndirectLight::Builder()
                             .irradiance(1, irradiance)
                             .intensity(kIndirectLightIntensity)
                             .build(*mEngine);
        mScene->setIndirectLight(mIndirectLight);

        LightManager::Builder(LightManager::Type::DIRECTIONAL)
            .direction({0.35f, -1.0f, -0.45f})
            .color({1.0f, 0.98f, 0.95f})
            .intensity(kLightIntensity)
            .build(*mEngine, mLightEntity);
        mScene->addEntity(mLightEntity);

        // gltfio only retains glTF node names when it is given a NameComponentManager, and
        // resolving VRM humanoid bones depends on looking those names up after loading.
        mNameComponentManager = new utils::NameComponentManager(utils::EntityManager::get());
        mMaterialProvider = gltfio::createUbershaderProvider(mEngine,
                                                            UBERARCHIVE_DEFAULT_DATA,
                                                            UBERARCHIVE_DEFAULT_SIZE);

        gltfio::AssetConfiguration assetConfiguration{};
        assetConfiguration.engine = mEngine;
        assetConfiguration.materials = mMaterialProvider;
        assetConfiguration.names = mNameComponentManager;
        assetConfiguration.entities = &utils::EntityManager::get();
        mAssetLoader = gltfio::AssetLoader::create(assetConfiguration);

        gltfio::ResourceConfiguration resourceConfiguration{};
        resourceConfiguration.engine = mEngine;
        resourceConfiguration.gltfPath = nullptr;
        resourceConfiguration.normalizeSkinningWeights = true;
        mResourceLoader = new gltfio::ResourceLoader(resourceConfiguration);
        mStbTextureProvider = gltfio::createStbProvider(mEngine);
        mKtxTextureProvider = gltfio::createKtx2Provider(mEngine);
        mResourceLoader->addTextureProvider("image/png", mStbTextureProvider);
        mResourceLoader->addTextureProvider("image/jpeg", mStbTextureProvider);
        mResourceLoader->addTextureProvider("image/ktx2", mKtxTextureProvider);

        updateCamera();
    }

    bool ensureSwapChain() {
        if (mSwapChain != nullptr) {
            return true;
        }
        mSwapChain = mEngine->createSwapChain((__bridge void*)mLayer, SwapChain::CONFIG_TRANSPARENT);
        return mSwapChain != nullptr;
    }

    void destroySwapChain() {
        if (mSwapChain != nullptr) {
            mEngine->destroy(mSwapChain);
            mSwapChain = nullptr;
        }
    }

    // Mirrors AndroidAvatarRenderBridge.configureRenderables so VRM meshes stay visible from
    // both sides and are never culled out of the overlay.
    void configureRenderables() {
        RenderableManager& renderableManager = mEngine->getRenderableManager();
        const Entity* entities = mAsset->getRenderableEntities();
        const size_t count = mAsset->getRenderableEntityCount();
        for (size_t index = 0; index < count; ++index) {
            auto instance = renderableManager.getInstance(entities[index]);
            if (!instance) {
                continue;
            }
            renderableManager.setLayerMask(instance, kSceneLayerMask, kSceneLayerVisible);
            renderableManager.setCulling(instance, false);
            const size_t primitiveCount = renderableManager.getPrimitiveCount(instance);
            for (size_t primitive = 0; primitive < primitiveCount; ++primitive) {
                MaterialInstance* material = renderableManager.getMaterialInstanceAt(instance, primitive);
                if (material != nullptr) {
                    material->setDoubleSided(true);
                }
            }
        }
    }

    static SceneFraming framingForAsset(gltfio::FilamentAsset* asset) {
        const Aabb bounds = asset->getBoundingBox();
        const float3 center = bounds.center();
        const float3 halfExtent = bounds.extent();
        const float maxHalfExtent = std::max(std::max(halfExtent.x, halfExtent.y),
                                             std::max(halfExtent.z, kMinModelHalfExtent));
        SceneFraming framing;
        framing.targetX = static_cast<double>(center.x);
        framing.targetY = static_cast<double>(center.y);
        framing.targetZ = static_cast<double>(center.z);
        framing.cameraDistance = std::max(kDefaultCameraDistance,
                                          static_cast<double>(maxHalfExtent) * kModelFitDistanceMultiplier);
        return framing;
    }

    // Mirrors AndroidAvatarRuntimeController.createPoseBindings: head carries the rotation weight
    // of any joint the model is missing, so tracking still moves an incompletely rigged avatar.
    void createPoseBindings(const std::vector<std::pair<std::string, std::string>>& humanoidBones) {
        struct BoneWeight {
            const char* name;
            float rotationWeight;
            float swayWeight;
        };
        const BoneWeight specs[] = {
            {kHeadBoneName, 0.82f, 0.0f},
            {kNeckBoneName, 0.18f, 0.32f},
            {kChestBoneName, 0.0f, 0.72f},
            {kSpineBoneName, 0.0f, 0.55f},
        };

        TransformManager& transformManager = mEngine->getTransformManager();
        std::unordered_map<std::string, Entity> boneEntities;
        for (const auto& bone : humanoidBones) {
            const Entity entity = entityForNodeName(bone.second);
            if (!entity.isNull()) {
                boneEntities.emplace(bone.first, entity);
            }
        }

        if (boneEntities.find(kHeadBoneName) == boneEntities.end()) {
            return;
        }

        float missingRotationWeight = 0.0f;
        for (const BoneWeight& spec : specs) {
            if (boneEntities.find(spec.name) == boneEntities.end()) {
                missingRotationWeight += spec.rotationWeight;
            }
        }

        for (const BoneWeight& spec : specs) {
            const auto found = boneEntities.find(spec.name);
            if (found == boneEntities.end()) {
                continue;
            }
            const auto transformInstance = transformManager.getInstance(found->second);
            if (!transformInstance) {
                continue;
            }
            const float extraWeight = std::strcmp(spec.name, kHeadBoneName) == 0 ? missingRotationWeight : 0.0f;
            mPoseBindings.push_back(PoseBinding{
                found->second,
                transformManager.getTransform(transformInstance),
                spec.rotationWeight + extraWeight,
                spec.swayWeight,
            });
        }

        const std::pair<const char*, float> armSpecs[] = {
            {kLeftUpperArmBoneName, kRelaxedLeftArmRollDegrees},
            {kRightUpperArmBoneName, kRelaxedRightArmRollDegrees},
        };
        for (const auto& spec : armSpecs) {
            const auto found = boneEntities.find(spec.first);
            if (found == boneEntities.end()) {
                continue;
            }
            const auto transformInstance = transformManager.getInstance(found->second);
            if (!transformInstance) {
                continue;
            }
            mArmPoseBindings.push_back(ArmPoseBinding{
                found->second,
                transformManager.getTransform(transformInstance),
                spec.second,
            });
        }
    }

    void createMorphTargetBuffers() {
        RenderableManager& renderableManager = mEngine->getRenderableManager();
        const Entity* entities = mAsset->getRenderableEntities();
        const size_t count = mAsset->getRenderableEntityCount();
        for (size_t index = 0; index < count; ++index) {
            const auto instance = renderableManager.getInstance(entities[index]);
            if (!instance) {
                continue;
            }
            const size_t morphTargetCount = renderableManager.getMorphTargetCount(instance);
            if (morphTargetCount > 0) {
                mMorphWeights.emplace(entities[index], std::vector<float>(morphTargetCount, 0.0f));
            }
        }
    }

    void applyAvatarState() {
        applyRelaxedArmPose();
        applyHeadPose();
        applyExpressions();
        updateBoneMatrices();
        updateCamera();
    }

    // Changing a joint's TransformManager matrix does not automatically update the matrices
    // consumed by skinned renderables. Keep this in the same state-application path as Android's
    // Animator.updateBoneMatrices call so the relaxed arm pose is visible from the first frame.
    void updateBoneMatrices() {
        if (mAsset == nullptr) {
            return;
        }
        gltfio::FilamentInstance* instance = mAsset->getInstance();
        if (instance == nullptr) {
            return;
        }
        gltfio::Animator* animator = instance->getAnimator();
        if (animator != nullptr) {
            animator->updateBoneMatrices();
        }
    }

    void applyHeadPose() {
        if (mPoseBindings.empty()) {
            return;
        }
        TransformManager& transformManager = mEngine->getTransformManager();
        for (const PoseBinding& binding : mPoseBindings) {
            const auto instance = transformManager.getInstance(binding.entity);
            if (!instance) {
                continue;
            }
            const mat4f rotation = rotationMatrix(
                mHeadYawDegrees * binding.rotationWeight + mBodySwayDegrees * binding.swayWeight,
                mHeadPitchDegrees * binding.rotationWeight + mBodyLeanDegrees * binding.swayWeight,
                mHeadRollDegrees * binding.rotationWeight - mBodySwayDegrees * binding.swayWeight * 0.35f);
            transformManager.setTransform(instance, binding.baseLocalTransform * rotation);
        }
    }

    void applyRelaxedArmPose() {
        if (mArmPoseBindings.empty()) {
            return;
        }
        TransformManager& transformManager = mEngine->getTransformManager();
        for (const ArmPoseBinding& binding : mArmPoseBindings) {
            const auto instance = transformManager.getInstance(binding.entity);
            if (!instance) {
                continue;
            }
            const mat4f rotation = rotationMatrix(0.0f, 0.0f, binding.rollDegrees);
            transformManager.setTransform(instance, binding.baseLocalTransform * rotation);
        }
    }

    void applyExpressions() {
        if (mMorphWeights.empty()) {
            return;
        }

        for (auto& entry : mMorphWeights) {
            std::fill(entry.second.begin(), entry.second.end(), 0.0f);
        }

        for (const ExpressionBinding& binding : mExpressionBindings) {
            const float expressionWeight = clampFloat(weightForChannel(binding.channel), 0.0f, 1.0f);
            if (expressionWeight <= 0.0f) {
                continue;
            }
            for (const MorphBind& morphBind : binding.morphBinds) {
                const auto found = mMorphWeights.find(morphBind.entity);
                if (found == mMorphWeights.end()) {
                    continue;
                }
                std::vector<float>& weights = found->second;
                if (morphBind.morphTargetIndex >= weights.size()) {
                    continue;
                }
                weights[morphBind.morphTargetIndex] = clampFloat(
                    weights[morphBind.morphTargetIndex] + expressionWeight * morphBind.weight, 0.0f, 1.0f);
            }
        }

        RenderableManager& renderableManager = mEngine->getRenderableManager();
        for (const auto& entry : mMorphWeights) {
            const auto instance = renderableManager.getInstance(entry.first);
            if (!instance) {
                continue;
            }
            renderableManager.setMorphWeights(instance, entry.second.data(), entry.second.size(), 0);
        }
    }

    float weightForChannel(VTCVrmExpressionChannel channel) const {
        switch (channel) {
            case VTCVrmExpressionChannelBlinkLeft:
                return mLeftEyeBlink;
            case VTCVrmExpressionChannelBlinkRight:
                return mRightEyeBlink;
            case VTCVrmExpressionChannelJawOpen:
                return mJawOpen;
            case VTCVrmExpressionChannelSmile:
                return mMouthSmile;
        }
        return 0.0f;
    }

    // Mirrors AndroidFilamentAvatarRenderer.updateCameraLookAt: a larger avatar scale pulls the
    // camera closer so the model appears to grow inside the 3D scene.
    void updateCamera() {
        const double scaledCameraDistance = mFraming.cameraDistance / static_cast<double>(mAvatarScale);
        const double trackingInfluence =
            mIsTracking ? clampDouble(static_cast<double>(mTrackingConfidence), 0.0, 1.0) : 0.0;
        const double expressionInfluence = clampDouble(
            static_cast<double>(mJawOpen * kJawWeight + mMouthSmile * kSmileWeight +
                                ((mLeftEyeBlink + mRightEyeBlink) * 0.5f) * kBlinkWeight),
            0.0, 1.0);
        const double yawRadians =
            toRadians(clampDouble(static_cast<double>(mHeadYawDegrees), -kMaxYawDegrees, kMaxYawDegrees));
        const double pitchRadians =
            toRadians(clampDouble(static_cast<double>(mHeadPitchDegrees), -kMaxPitchDegrees, kMaxPitchDegrees));

        mCamera->lookAt(
            double3{
                mFraming.targetX + std::sin(yawRadians) * kCameraYawOffsetScale * trackingInfluence,
                mFraming.targetY + std::sin(pitchRadians) * kCameraPitchOffsetScale * trackingInfluence +
                    expressionInfluence * kExpressionYOffsetScale,
                mFraming.targetZ + scaledCameraDistance - expressionInfluence * kExpressionZOffsetScale,
            },
            double3{mFraming.targetX, mFraming.targetY, mFraming.targetZ},
            double3{0.0, 1.0, 0.0});
    }

    Engine* mEngine = nullptr;
    CAMetalLayer* mLayer = nil;
    Renderer* mRenderer = nullptr;
    Scene* mScene = nullptr;
    View* mView = nullptr;
    Camera* mCamera = nullptr;
    SwapChain* mSwapChain = nullptr;
    IndirectLight* mIndirectLight = nullptr;
    Entity mCameraEntity;
    Entity mLightEntity;

    utils::NameComponentManager* mNameComponentManager = nullptr;
    gltfio::MaterialProvider* mMaterialProvider = nullptr;
    gltfio::AssetLoader* mAssetLoader = nullptr;
    gltfio::ResourceLoader* mResourceLoader = nullptr;
    gltfio::TextureProvider* mStbTextureProvider = nullptr;
    gltfio::TextureProvider* mKtxTextureProvider = nullptr;

    gltfio::FilamentAsset* mAsset = nullptr;
    std::vector<PoseBinding> mPoseBindings;
    std::vector<ArmPoseBinding> mArmPoseBindings;
    std::vector<ExpressionBinding> mExpressionBindings;
    std::unordered_map<Entity, std::vector<float>, Entity::Hasher> mMorphWeights;
    SceneFraming mFraming;

    uint32_t mWidth = 0;
    uint32_t mHeight = 0;

    float mHeadYawDegrees = 0.0f;
    float mHeadPitchDegrees = 0.0f;
    float mHeadRollDegrees = 0.0f;
    float mBodySwayDegrees = 0.0f;
    float mBodyLeanDegrees = 0.0f;
    float mLeftEyeBlink = 0.0f;
    float mRightEyeBlink = 0.0f;
    float mJawOpen = 0.0f;
    float mMouthSmile = 0.0f;
    float mAvatarScale = VTCDefaultAvatarScale;
    float mTrackingConfidence = 0.0f;
    bool mIsTracking = false;
};

}  // namespace

#endif  // VTC_FILAMENT_HEADERS_AVAILABLE

@interface VTCFilamentRendererBridge ()

@property (nonatomic, strong) UIView *renderView;
@property (nonatomic, strong) VTCAvatarRenderState *latestAvatarState;

@end

@implementation VTCFilamentRendererBridge {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    AvatarScene *_scene;
#endif
}

- (instancetype)init {
    self = [super init];
    if (self != nil) {
        _renderView = [[VTCMetalContainerView alloc] initWithFrame:CGRectZero];
        _renderView.backgroundColor = [UIColor clearColor];
        _renderView.opaque = NO;
        _latestAvatarState = [[VTCAvatarRenderState alloc] init];
#if VTC_FILAMENT_HEADERS_AVAILABLE
        CAMetalLayer *layer = (CAMetalLayer *)_renderView.layer;
        layer.opaque = NO;
        _scene = AvatarScene::create(layer);
#endif
    }
    return self;
}

- (void)dealloc {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    delete _scene;
    _scene = nullptr;
#endif
}

- (BOOL)isRenderingAvailable {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    return _scene != nullptr;
#else
    return NO;
#endif
}

- (BOOL)isAvatarLoaded {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    return _scene != nullptr && _scene->hasAvatar();
#else
    return NO;
#endif
}

- (BOOL)loadAvatarWithData:(NSData *)data
             humanoidBones:(NSArray<VTCVrmHumanoidBone *> *)humanoidBones
                     error:(NSError * _Nullable __autoreleasing *)error {
    if (data.length == 0) {
        [self populateError:error
                       code:VTCFilamentRendererErrorCodeInvalidInput
                    message:@"Avatar data must not be empty."];
        return NO;
    }

#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_scene == nullptr) {
        [self populateError:error
                       code:VTCFilamentRendererErrorCodeUnavailable
                    message:@"The Filament engine could not be created on this device."];
        return NO;
    }

    std::vector<std::pair<std::string, std::string>> bones;
    bones.reserve(humanoidBones.count);
    for (VTCVrmHumanoidBone *bone in humanoidBones) {
        if (bone.boneName.length == 0 || bone.nodeName.length == 0) {
            continue;
        }
        bones.emplace_back(bone.boneName.UTF8String, bone.nodeName.UTF8String);
    }

    VTCFilamentRendererErrorCode errorCode = VTCFilamentRendererErrorCodeInvalidAsset;
    const BOOL didLoad = _scene->loadAvatar(static_cast<const uint8_t *>(data.bytes),
                                            data.length,
                                            bones,
                                            errorCode);
    if (!didLoad) {
        NSString *message = errorCode == VTCFilamentRendererErrorCodeResourceLoadFailed
            ? @"The avatar's buffers or textures could not be uploaded to the GPU."
            : @"The selected file is not a readable glTF/VRM asset.";
        [self populateError:error code:errorCode message:message];
        return NO;
    }

    _scene->setAvatarState(self.latestAvatarState);
    return YES;
#else
    [self populateError:error
                   code:VTCFilamentRendererErrorCodeUnavailable
                message:@"The Filament SDK is not vendored into iosApp; run scripts/setup_filament_ios.sh."];
    return NO;
#endif
}

- (NSArray<NSNumber *> *)entityIdsForNodeNames:(NSArray<NSString *> *)nodeNames {
    NSMutableArray<NSNumber *> *entityIds = [NSMutableArray arrayWithCapacity:nodeNames.count];
#if VTC_FILAMENT_HEADERS_AVAILABLE
    for (NSString *nodeName in nodeNames) {
        utils::Entity entity;
        if (_scene != nullptr && nodeName.length > 0) {
            entity = _scene->entityForNodeName(nodeName.UTF8String);
        }
        [entityIds addObject:@(entity.isNull() ? NSNotFound : (NSInteger)entity.getId())];
    }
#else
    for (NSString *nodeName in nodeNames) {
        (void)nodeName;
        [entityIds addObject:@(NSNotFound)];
    }
#endif
    return entityIds;
}

- (void)setExpressionBindings:(NSArray<VTCVrmExpressionBinding *> *)expressionBindings {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_scene == nullptr) {
        return;
    }
    std::vector<ExpressionBinding> bindings;
    bindings.reserve(expressionBindings.count);
    for (VTCVrmExpressionBinding *binding in expressionBindings) {
        std::vector<MorphBind> morphBinds;
        morphBinds.reserve(binding.morphBinds.count);
        for (VTCVrmMorphBind *morphBind in binding.morphBinds) {
            if (morphBind.entityId == NSNotFound || morphBind.morphTargetIndex < 0) {
                continue;
            }
            morphBinds.push_back(MorphBind{
                utils::Entity::import(static_cast<int32_t>(morphBind.entityId)),
                static_cast<size_t>(morphBind.morphTargetIndex),
                morphBind.weight,
            });
        }
        if (!morphBinds.empty()) {
            bindings.push_back(ExpressionBinding{binding.channel, std::move(morphBinds)});
        }
    }
    _scene->setExpressionBindings(std::move(bindings));
#else
    (void)expressionBindings;
#endif
}

- (void)clearAvatar {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_scene != nullptr) {
        _scene->clearAvatar();
    }
#endif
}

- (void)updateAvatarState:(VTCAvatarRenderState *)state {
    self.latestAvatarState = VTCCopyAvatarRenderState(state);
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_scene != nullptr) {
        _scene->setAvatarState(self.latestAvatarState);
    }
#endif
}

- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale {
    self.renderView.frame = bounds;

    CGFloat fallbackScale = self.renderView.window.screen.scale;
    if (fallbackScale <= 0) {
        fallbackScale = 1.0;
    }
    const CGFloat scale = contentScale > 0 ? contentScale : fallbackScale;
    self.renderView.contentScaleFactor = scale;

    const CGFloat widthPixels = bounds.size.width * scale;
    const CGFloat heightPixels = bounds.size.height * scale;
    if (widthPixels <= 0 || heightPixels <= 0) {
        return;
    }

#if VTC_FILAMENT_HEADERS_AVAILABLE
    CAMetalLayer *layer = (CAMetalLayer *)self.renderView.layer;
    layer.drawableSize = CGSizeMake(widthPixels, heightPixels);
    if (_scene != nullptr) {
        _scene->resize(static_cast<uint32_t>(widthPixels), static_cast<uint32_t>(heightPixels));
    }
#endif
}

- (void)drawIfNeeded {
#if VTC_FILAMENT_HEADERS_AVAILABLE
    if (_scene != nullptr) {
        _scene->draw();
    }
#endif
}

- (void)populateError:(NSError * _Nullable __autoreleasing *)error
                 code:(VTCFilamentRendererErrorCode)code
              message:(NSString *)message {
    if (error == nil) {
        return;
    }
    *error = [NSError errorWithDomain:VTCFilamentRendererErrorDomain
                                 code:code
                             userInfo:@{NSLocalizedDescriptionKey: message}];
}

@end
