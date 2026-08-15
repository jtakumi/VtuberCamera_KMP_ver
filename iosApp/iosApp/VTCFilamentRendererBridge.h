#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface VTCAvatarRenderState : NSObject

/// Head yaw in degrees.
@property (nonatomic) float headYawDegrees;
/// Head pitch in degrees.
@property (nonatomic) float headPitchDegrees;
/// Head roll in degrees.
@property (nonatomic) float headRollDegrees;
/// Horizontal body sway in degrees.
@property (nonatomic) float bodySwayDegrees;
/// Forward/backward body lean in degrees.
@property (nonatomic) float bodyLeanDegrees;
/// Left-eye blink amount normalized to 0.0...1.0.
@property (nonatomic) float leftEyeBlink;
/// Right-eye blink amount normalized to 0.0...1.0.
@property (nonatomic) float rightEyeBlink;
/// Jaw-open amount normalized to 0.0...1.0.
@property (nonatomic) float jawOpen;
/// Smile amount normalized to 0.0...1.0.
@property (nonatomic) float mouthSmile;
/// Pinch-driven avatar display scale where `1.0` keeps the default size.
@property (nonatomic) float avatarScale;
/// Face-tracking confidence normalized to 0.0...1.0.
@property (nonatomic) float trackingConfidence;
/// `YES` while face tracking actively drives the avatar.
@property (nonatomic, getter=isTracking) BOOL tracking;

@end

/// One VRM humanoid bone, mapping the spec bone name to the glTF node that carries it.
/// The node is identified by name because that is how gltfio exposes the imported hierarchy.
@interface VTCVrmHumanoidBone : NSObject

@property (nonatomic, copy, readonly) NSString *boneName;
@property (nonatomic, copy, readonly) NSString *nodeName;

- (instancetype)initWithBoneName:(NSString *)boneName nodeName:(NSString *)nodeName;

@end

/// One resolved morph-target weight applied to a renderable entity.
@interface VTCVrmMorphBind : NSObject

/// Filament entity ID, as handed out by `-entityIdsForNodeCount:nodeNames:`.
@property (nonatomic, readonly) NSInteger entityId;
@property (nonatomic, readonly) NSInteger morphTargetIndex;
@property (nonatomic, readonly) float weight;

- (instancetype)initWithEntityId:(NSInteger)entityId
                morphTargetIndex:(NSInteger)morphTargetIndex
                          weight:(float)weight;

@end

/// Expression channels the renderer can drive, matching the shared avatar render state.
typedef NS_ENUM(NSInteger, VTCVrmExpressionChannel) {
    VTCVrmExpressionChannelBlinkLeft = 0,
    VTCVrmExpressionChannelBlinkRight = 1,
    VTCVrmExpressionChannelJawOpen = 2,
    VTCVrmExpressionChannelSmile = 3,
};

/// Morph binds grouped by the expression channel that drives their weight.
@interface VTCVrmExpressionBinding : NSObject

@property (nonatomic, readonly) VTCVrmExpressionChannel channel;
@property (nonatomic, copy, readonly) NSArray<VTCVrmMorphBind *> *morphBinds;

- (instancetype)initWithChannel:(VTCVrmExpressionChannel)channel
                     morphBinds:(NSArray<VTCVrmMorphBind *> *)morphBinds;

@end

@interface VTCFilamentRendererBridge : NSObject

@property (nonatomic, readonly) UIView *renderView;
@property (nonatomic, readonly) VTCAvatarRenderState *latestAvatarState;

/// `YES` once a Filament engine is live and able to draw frames. This is `NO` when the
/// Filament SDK is not vendored into the build (see scripts/setup_filament_ios.sh) or
/// when engine creation failed, in which case callers should fall back to a static preview.
@property (nonatomic, readonly, getter=isRenderingAvailable) BOOL renderingAvailable;

/// `YES` while a VRM asset is loaded into the scene, so rendered frames show the avatar.
@property (nonatomic, readonly, getter=isAvatarLoaded) BOOL avatarLoaded;

- (instancetype)init;

/// Loads VRM/GLB bytes into the Filament scene, replacing any previously loaded avatar.
/// `humanoidBones` drives head and body pose; pass an empty array to render without rigging.
- (BOOL)loadAvatarWithData:(NSData *)data
             humanoidBones:(NSArray<VTCVrmHumanoidBone *> *)humanoidBones
                     error:(NSError * _Nullable __autoreleasing * _Nullable)error;

/// Loads a VRM/GLB asset with its parsed VRM specification version. VRM 0.x assets are rotated
/// into the +Z-forward renderer coordinate system while VRM 1.0 assets retain their native basis.
- (BOOL)loadAvatarWithData:(NSData *)data
             humanoidBones:(NSArray<VTCVrmHumanoidBone *> *)humanoidBones
                    isVrm0:(BOOL)isVrm0
                      error:(NSError * _Nullable __autoreleasing * _Nullable)error;

/// Returns the Filament entity ID for each glTF node name, or `NSNotFound` where the loaded
/// asset has no entity for that name. Call after a successful load to resolve morph binds.
- (NSArray<NSNumber *> *)entityIdsForNodeNames:(NSArray<NSString *> *)nodeNames;

/// Applies the expression-to-morph-target bindings resolved from the VRM descriptor.
- (void)setExpressionBindings:(NSArray<VTCVrmExpressionBinding *> *)expressionBindings;

/// Removes the current avatar from the scene and releases its GPU resources.
- (void)clearAvatar;

/// Applies the latest normalized avatar pose and expression state to the renderer.
- (void)updateAvatarState:(VTCAvatarRenderState *)state;

/// Keeps the native render surface in sync with the host view bounds and scale.
- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale;

/// Draws one frame when the renderer has a live surface and a loaded avatar.
- (void)drawIfNeeded;

@end

NS_ASSUME_NONNULL_END
