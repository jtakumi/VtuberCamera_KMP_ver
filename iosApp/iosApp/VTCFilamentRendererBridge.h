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

@end

@interface VTCFilamentRendererBridge : NSObject

@property (nonatomic, readonly) UIView *renderView;

+ (BOOL)isFilamentSdkConfigured;

- (instancetype)init;
/// Prepares avatar loading once the Filament-backed renderer implementation is added.
- (BOOL)loadAvatarAtURL:(NSURL *)url error:(NSError * _Nullable * _Nullable)error;
/// Applies the latest normalized avatar pose and expression state to the renderer.
- (void)updateAvatarState:(VTCAvatarRenderState *)state;
/// Keeps the native render surface in sync with the host view bounds and scale.
- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale;
/// Draws a frame when the future renderer implementation needs one.
- (void)drawIfNeeded;

@end

NS_ASSUME_NONNULL_END
