#import "VTCFilamentRendererBridge.h"

#import <QuartzCore/CAMetalLayer.h>

#if __has_include(<filament/Engine.h>)
#define VTC_FILAMENT_HEADERS_AVAILABLE 1
#else
#define VTC_FILAMENT_HEADERS_AVAILABLE 0
#endif

typedef NS_ENUM(NSInteger, VTCFilamentRendererErrorCode) {
    VTCFilamentRendererErrorCodeInvalidInput = 0,
    VTCFilamentRendererErrorCodeUnavailable = 1,
};

static NSString *const VTCFilamentRendererErrorDomain = @"io.github.jtakumi.VtuberCamera_KMP_ver.filament";

@interface VTCMetalContainerView : UIView
@end

@implementation VTCMetalContainerView

+ (Class)layerClass {
    return [CAMetalLayer class];
}

@end

@implementation VTCAvatarRenderState
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
    return copy;
}

@interface VTCFilamentRendererBridge ()

@property (nonatomic, strong) UIView *renderView;
@property (nonatomic, strong) VTCAvatarRenderState *latestAvatarState;

@end

@implementation VTCFilamentRendererBridge

+ (BOOL)isFilamentSdkConfigured {
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

- (BOOL)loadAvatarAtURL:(NSURL *)url error:(NSError * _Nullable __autoreleasing *)error {
    if (!url.isFileURL) {
        if (error != nil) {
            *error = [NSError errorWithDomain:VTCFilamentRendererErrorDomain
                                         code:VTCFilamentRendererErrorCodeInvalidInput
                                     userInfo:@{
                                         NSLocalizedDescriptionKey: @"Avatar URLs must point to a local file.",
                                     }];
        }
        return NO;
    }

    // Placeholder until the Filament-backed avatar loading path is implemented.
    if (error != nil) {
#if VTC_FILAMENT_HEADERS_AVAILABLE
        NSString *message = @"Avatar loading is not implemented yet.";
#else
        NSString *message = @"Filament SDK headers are not configured for iosApp yet.";
#endif
        *error = [NSError errorWithDomain:VTCFilamentRendererErrorDomain
                                     code:VTCFilamentRendererErrorCodeUnavailable
                                 userInfo:@{
                                     NSLocalizedDescriptionKey: message,
                                 }];
    }
    return NO;
}

- (void)updateAvatarState:(VTCAvatarRenderState *)state {
    self.latestAvatarState = VTCCopyAvatarRenderState(state);
    // The future Filament-backed implementation should consume latestAvatarState here to apply
    // head pose and expression channels to the loaded avatar.
}

- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale {
    self.renderView.frame = bounds;
    // The future Filament-backed implementation will also need to resize its Metal surfaces here.
    CGFloat fallbackScale = self.renderView.window.screen.scale;
    if (fallbackScale <= 0) {
        fallbackScale = 1.0;
    }
    self.renderView.contentScaleFactor = contentScale > 0 ? contentScale : fallbackScale;
}

- (void)drawIfNeeded {
}

@end
