#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface VTCAvatarRenderState : NSObject

@property (nonatomic) float headYawDegrees;
@property (nonatomic) float headPitchDegrees;
@property (nonatomic) float headRollDegrees;
@property (nonatomic) float leftEyeBlink;
@property (nonatomic) float rightEyeBlink;
@property (nonatomic) float jawOpen;
@property (nonatomic) float mouthSmile;

@end

@interface VTCFilamentRendererBridge : NSObject

@property (nonatomic, readonly) UIView *renderView;

+ (BOOL)isFilamentSdkConfigured;

- (instancetype)init;
- (BOOL)loadAvatarAtURL:(NSURL *)url error:(NSError * _Nullable * _Nullable)error;
- (void)updateAvatarState:(VTCAvatarRenderState *)state;
- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale;
- (void)drawIfNeeded;

@end

NS_ASSUME_NONNULL_END
