/********* Skyway.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import "ViewController.h"

#define ROOTVIEW [[[UIApplication sharedApplication] keyWindow] rootViewController]

@interface Skyway : CDVPlugin {
  // Member variables go here.
}

@property (nonatomic, copy) NSString *callbackId;

@end

@implementation Skyway

@synthesize callbackId;

- (void)createPeer:(CDVInvokedUrlCommand*)command 
{
    NSLog(@"createPeer");
    self.callbackId = command.callbackId;
    
    NSDictionary *options = command.arguments[0];
    NSString *myId = options[@"peerId"] ?: nil;
    NSString *partnerId = options[@"targetPeerId"] ?: nil;
    NSString *apiKey = options[@"apiKey"] ?: nil;
    NSString *domain = options[@"domain"] ?: nil;
    NSString *browserUrl = options[@"browserUrl"] ?: nil;
    NSString *intervalReconnectS = options[@"intervalReconnect"] ?: nil;
    NSString *showLocalVideoS = options[@"showLocalVideo"] ?: nil;
    NSInteger intervalReconnect = intervalReconnectS ? [intervalReconnectS integerValue] : 0;
    intervalReconnect = intervalReconnect / 1000;//it's miliseconds
    BOOL showLocalVideo = showLocalVideoS ? [showLocalVideoS boolValue] : NO;
    NSString *enableSpeakerS = options[@"enableSpeaker"] ?: nil;
    BOOL enableSpeaker = enableSpeakerS ? [enableSpeakerS boolValue] : NO;
    NSString *debugModeS = options[@"debugMode"] ?: nil;
    BOOL debugMode = debugModeS ? [debugModeS boolValue] : NO;
    NSString *inCallUrl = options[@"inCallUrl"] ?: nil;
    NSDictionary *inCallHeader = options[@"inCallHeader"] ?: nil;
    NSDictionary *timeLimittingConfig = options[@"timeLimitingConfig"] ?: nil;
    NSString *isSelfCallingS = options[@"selfCalling"] ?: nil;
    BOOL isSelfCalling = isSelfCallingS ? [isSelfCallingS boolValue] : NO;
    NSString *errorMessageWhenPeerIdUnavailable = options[@"errorPeerIdUnavailable"] ?: nil;
    
    ViewController *vc = [[ViewController alloc] initWithNibName:@"ViewController" bundle:nil];
    vc.myId = myId;
    vc.partnerId = partnerId;
    vc.apiKey = apiKey;
    vc.domain = domain;
    vc.intervalReconnect = intervalReconnect;
    vc.showLocalVideo = showLocalVideo;
    vc.enableSpeaker = enableSpeaker;
    vc.isDebugMode = debugMode;
    vc.browserUrl = browserUrl;
    vc.inCallUrl = inCallUrl;
    vc.inCallHeader = inCallHeader;
    vc.timeLimitConfig = timeLimittingConfig;
    vc.isSelfCalling = isSelfCalling;
    vc.errorMessageWhenPeerIdUnavailable = errorMessageWhenPeerIdUnavailable;
    
    [vc setSuccessBlock:^(NSUInteger start, NSUInteger end, BOOL isSelfHangup) {
        NSLog(@"block call: %d %d", start, end);
        NSString *startStr = [[NSString alloc] initWithFormat:@"%lu", (unsigned long)start];
        NSString *endStr = [[NSString alloc] initWithFormat:@"%lu", (unsigned long)end];

        NSDictionary *timeDict = @{@"event": @"skyway_hangup", @"start_time": startStr, @"end_time": endStr, @"is_hangup": [NSNumber numberWithBool:isSelfHangup]};
        [self fireEventJson:timeDict];
//        [self fireEvent:@"" event:@"skyway_hangup" withData:[self generateJsonStringFromDictionary:timeDict]];

        // CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:timeDict];
        // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        }];
    [vc setResetBlock:^{
        NSDictionary *data = @{@"event": @"skyway_reset_peer"};
        [self fireEventJson:data];
//        [self fireEvent:@"" event:@"skyway_reset_peer" withData:nil];
    }];
    [ROOTVIEW presentViewController:vc animated:YES completion:^{}];
}

- (NSString *)generateJsonStringFromDictionary:(NSDictionary *)dict {
    NSError *error;
    NSData *jsonData = [NSJSONSerialization dataWithJSONObject:dict
                                                       options:NSJSONWritingPrettyPrinted // Pass 0 if you don't care about the readability of the generated string
                                                         error:&error];
    
    if (! jsonData) {
        NSLog(@"Got an error: %@", error);
        return @"";
    } else {
        NSString *jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
        return jsonString;
    }
}

- (void)fireEvent:(NSString *)obj event:(NSString *)eventName withData:(NSString *)jsonStr {
    NSString* js;
    if(obj && [obj isEqualToString:@"window"]) {
        js = [NSString stringWithFormat:@"var evt=document.createEvent(\"UIEvents\");evt.initUIEvent(\"%@\",true,false,window,0);window.dispatchEvent(evt);", eventName];
    } else if(jsonStr && [jsonStr length]>0) {
        js = [NSString stringWithFormat:@"javascript:cordova.fireDocumentEvent('%@',%@);", eventName, jsonStr];
    } else {
        js = [NSString stringWithFormat:@"javascript:cordova.fireDocumentEvent('%@');", eventName];
    }
    [self.commandDelegate evalJs:js];
}

- (void)fireEventJson:(NSDictionary *)jso {
    if (self.callbackId != nil) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:jso];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
    }
}

@end
