/********* Skyway.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import "ViewController.h"

#define ROOTVIEW [[[UIApplication sharedApplication] keyWindow] rootViewController]

@interface Skyway : CDVPlugin {
  // Member variables go here.
}

- (void)coolMethod:(CDVInvokedUrlCommand*)command;
@end

@implementation Skyway

- (void)coolMethod:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = nil;
    NSString* echo = [command.arguments objectAtIndex:0];

    if (echo != nil && [echo length] > 0) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:echo];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)createPeer:(CDVInvokedUrlCommand*)command 
{
    NSLog(@"startTrip");
    NSDictionary *options = command.arguments[0];
    NSString *myId = options[@"myId"] ?: nil;
    NSString *partnerId = options[@"partnerId"] ?: nil;
    
    ViewController *vc = [[ViewController alloc] initWithNibName:@"ViewController" bundle:nil];
    vc.myId = myId;
    vc.partnerId = partnerId;

    [vc setSuccessBlock:^(NSUInteger start, NSUInteger end) {
        NSLog(@"block call: %lu %lu", start, end);
        NSString *startStr = [[NSString alloc] initWithFormat:@"%lu", (unsigned long)start];
        NSString *endStr = [[NSString alloc] initWithFormat:@"%lu", (unsigned long)end];

        NSDictionary *timeDict = @{@"startTime": startStr, @"endTime": endStr};
        
        [self fireEvent:@"" event:@"skyway_hangup" withData:[self generateJsonStringFromDictionary:timeDict]];

        // CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:timeDict];
        // [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
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

@end
