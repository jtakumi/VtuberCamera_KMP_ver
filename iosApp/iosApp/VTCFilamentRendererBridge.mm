#import "VTCFilamentRendererBridge.h"

#import <QuartzCore/CAMetalLayer.h>

#if __has_include(<filament/Engine.h>)
#define VTC_FILAMENT_HEADERS_AVAILABLE 1
#else
#define VTC_FILAMENT_HEADERS_AVAILABLE 0
#endif

static NSErrorDomain const VTCFilamentRendererErrorDomain = @"com.example.vtubercamera_kmp_ver.filament";

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
    (void)url;
    if (error != nil) {
#if VTC_FILAMENT_HEADERS_AVAILABLE
        NSString *message = @"Avatar loading is not implemented yet.";
#else
        NSString *message = @"Filament SDK headers are not configured for iosApp yet.";
#endif
        *error = [NSError errorWithDomain:VTCFilamentRendererErrorDomain
                                     code:1
                                 userInfo:@{
                                     NSLocalizedDescriptionKey: message,
                                 }];
    }
    return NO;
}

- (void)updateAvatarState:(VTCAvatarRenderState *)state {
    (void)state;
}

- (void)resizeToBounds:(CGRect)bounds contentScale:(CGFloat)contentScale {
    self.renderView.frame = bounds;
    self.renderView.contentScaleFactor = contentScale;
}

- (void)drawIfNeeded {
}

@end
