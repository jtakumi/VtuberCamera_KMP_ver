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
/// Left-eye blink amount normalized to 0.0...1.0.
@property (nonatomic) float leftEyeBlink;
/// Right-eye blink amount normalized to 0.0...1.0.
@property (nonatomic) float rightEyeBlink;
/// Jaw-open amount normalized to 0.0...1.0.
@property (nonatomic) float jawOpen;
/// Smile amount normalized to 0.0...1.0.
@property (nonatomic) float mouthSmile;
/// Whether the face is currently being tracked. Drives the camera parallax influence.
@property (nonatomic) BOOL isTracking;
/// Tracking confidence normalized to 0.0...1.0. Ignored while `isTracking` is `NO`.
@property (nonatomic) float trackingConfidence;

@end

/// Expression channels the renderer can drive from the shared render state.
typedef NS_ENUM(NSInteger, VTCAvatarMorphChannel) {
    VTCAvatarMorphChannelBlinkLeft = 0,
    VTCAvatarMorphChannelBlinkRight = 1,
    VTCAvatarMorphChannelJawOpen = 2,
    VTCAvatarMorphChannelSmile = 3,
};

/// A resolved binding from one expression channel to one glTF morph target.
@interface VTCAvatarMorphBind : NSObject

/// The expression channel that drives this morph target.
@property (nonatomic) VTCAvatarMorphChannel channel;
/// The glTF node index that owns the morph target.
@property (nonatomic) NSInteger nodeIndex;
/// The morph target index within the node's primitives.
@property (nonatomic) NSInteger morphTargetIndex;
/// The maximum weight contributed when the expression channel is fully active.
@property (nonatomic) float weight;

@end

/// The bridge returns `NO` with `VTCFilamentRendererErrorCodeUnavailable` from
/// `loadAvatarData:headNodeIndex:morphBinds:error:` whenever the Filament SDK has not been set up
/// via `scripts/setup_filament_ios.sh`, so callers can keep the static preview path alive.
@interface VTCFilamentRendererBridge : NSObject

@property (nonatomic, readonly) UIView *renderView;
@property (nonatomic, readonly) VTCAvatarRenderState *latestAvatarState;

/// Returns whether Filament headers were discoverable at compile time for this target.
/// Linking is validated separately by the local Filament.local.xcconfig setup.
@property (class, nonatomic, readonly) BOOL isFilamentRuntimeAvailable;

- (instancetype)init;
/// Loads the GLB/VRM bytes into the Filament scene and binds the head bone plus morph targets.
/// Pass a negative `headNodeIndex` when the asset has no resolvable humanoid head bone.
/// Returns `NO` and populates `error` when the SDK is unavailable or the asset cannot be loaded;
/// any partially loaded asset is destroyed before returning.
- (BOOL)loadAvatarData:(NSData *)data
         headNodeIndex:(NSInteger)headNodeIndex
            morphBinds:(NSArray<VTCAvatarMorphBind *> *)morphBinds
                 error:(NSError * _Nullable __autoreleasing * _Nullable)error;
/// Removes the currently loaded avatar from the scene and releases its Filament resources.
- (void)clearAvatar;
/// Applies the latest normalized avatar pose and expression state to the renderer.
- (void)updateAvatarState:(VTCAvatarRenderState *)state;
/// Keeps the native render surface in sync with the host view bounds and scale.
- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale;
/// Renders one frame when an avatar is loaded and the render surface is ready.
- (void)drawIfNeeded;

@end

NS_ASSUME_NONNULL_END
