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

@interface VTCFilamentRendererBridge ()

@property (nonatomic, strong) UIView *renderView;

@end

@implementation VTCFilamentRendererBridge

+ (BOOL)isFilamentSdkConfigured {
    return VTC_FILAMENT_HEADERS_AVAILABLE;
}

- (instancetype)init {
    self = [super init];
    if (self != nil) {
        _renderView = [[VTCMetalContainerView alloc] initWithFrame:CGRectZero];
    }
    return self;
}

- (BOOL)loadAvatarAtURL:(NSURL *)url error:(NSError * _Nullable __autoreleasing *)error {
    if (!url.fileURL) {
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
    // Placeholder until tracked avatar pose and expression state drives the renderer.
    (void)state;
}

- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale {
    self.renderView.frame = bounds;
    // The future Filament-backed implementation will also need to resize its Metal surfaces here.
    self.renderView.contentScaleFactor = contentScale > 0 ? contentScale : UIScreen.mainScreen.scale;
}

- (void)drawIfNeeded {
}

@end
