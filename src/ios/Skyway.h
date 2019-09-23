#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>

@interface CDVClipboard : CDVPlugin {}

- (void)createPeer:(CDVInvokedUrlCommand*)command;
@end
