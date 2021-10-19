//
//  LocationManager.m
//  geolocator
//
//  Created by Maurits van Beusekom on 20/06/2020.
//

#import "GeolocationHandler.h"
#import "PermissionHandler.h"
#import "../Constants/ErrorCodes.h"

@interface GeolocationHandler() <CLLocationManagerDelegate>

@property (strong, nonatomic) CLLocationManager *locationManager;
@property (strong, nonatomic) GeolocatorError errorHandler;
@property (strong, nonatomic) GeolocatorResult resultHandler;

@property (strong, nonatomic) CLLocation *latestLocation;
@property (assign, nonatomic) CLLocationDirection latestCourse;

@end

@implementation GeolocationHandler

- (CLLocation *)getLastKnownPosition {
    return [self.locationManager location];
}

- (void)requestPosition:(GeolocatorResult _Nonnull)resultHandler
           errorHandler:(GeolocatorError _Nonnull)errorHandler {
  self.errorHandler = errorHandler;
  self.resultHandler = resultHandler;
  
  if (@available(iOS 9.0, macOS 10.14, *)) {
    [self.locationManager requestLocation];
    return;
  }
  
  [self startUpdatingLocationWithDesiredAccuracy:kCLLocationAccuracyBest
                                  distanceFilter:kCLDistanceFilterNone];
}

- (void)startListeningWithDesiredAccuracy:(CLLocationAccuracy)desiredAccuracy
                           distanceFilter:(CLLocationDistance)distanceFilter
                            resultHandler:(GeolocatorResult _Nonnull )resultHandler
                             errorHandler:(GeolocatorError _Nonnull)errorHandler {
    
    self.errorHandler = errorHandler;
    self.resultHandler = resultHandler;
    
  [self startUpdatingLocationWithDesiredAccuracy:desiredAccuracy
                                  distanceFilter:distanceFilter];
}

- (void)startUpdatingLocationWithDesiredAccuracy:(CLLocationAccuracy)desiredAccuracy
                                  distanceFilter:(CLLocationDistance)distanceFilter {
  CLLocationManager *locationManager = self.locationManager;

  self.latestLocation = nil;
  self.latestCourse = -1;

  locationManager.desiredAccuracy = desiredAccuracy;
  locationManager.distanceFilter = distanceFilter;
  locationManager.headingFilter = 1;
  
#if TARGET_OS_IOS
  if (@available(iOS 9.0, macOS 11.0, *)) {
    locationManager.allowsBackgroundLocationUpdates = [GeolocationHandler shouldEnableBackgroundLocationUpdates];

    if (@available(iOS 11.0, *)) {
        locationManager.showsBackgroundLocationIndicator = [GeolocationHandler shouldEnableBackgroundLocationUpdates];
    }
  }
  locationManager.pausesLocationUpdatesAutomatically = NO;
#endif
  
  [locationManager startUpdatingLocation];
  [locationManager startUpdatingHeading];
}

- (void)stopListening {
    [self.locationManager stopUpdatingHeading];
    [self.locationManager stopUpdatingLocation];
    
    self.errorHandler = nil;
    self.resultHandler = nil;
}

- (CLLocationManager *) locationManager {
    if (!_locationManager) {
        _locationManager = [[CLLocationManager alloc] init];
        _locationManager.delegate = self;
    }
    return _locationManager;
}

- (void)locationManager:(CLLocationManager *)manager 
       didUpdateHeading:(CLHeading *)newHeading {
    CLLocationDirection heading = newHeading.trueHeading > 0 ? newHeading.trueHeading : newHeading.magneticHeading;
    self.latestCourse = heading;

    [self notifyLocationUpdate];
}

- (void)locationManager:(CLLocationManager *)manager
     didUpdateLocations:(NSArray<CLLocation *> *)locations {
    if (!self.resultHandler) return;
    
    if ([locations lastObject]) {
        self.latestLocation = [locations lastObject];

        [self notifyLocationUpdate];
    }
}

- (void)notifyLocationUpdate {
    CLLocation *location = [self mappedLocation];

    if (location) {
        self.resultHandler(location);
    }
}

- (CLLocation *)mappedLocation {
    if (!self.latestLocation) {
        return nil;
    }

    CLLocationDirection heading;

    if (self.latestLocation.course > 0) {
        heading = self.latestLocation.course;
    } else if (self.latestCourse > 0) {
        heading = self.latestCourse;
    } else {
        heading = -1;
    }

    CLLocation *location = [[CLLocation alloc] 
        initWithCoordinate:self.latestLocation.coordinate
                  altitude:self.latestLocation.altitude
        horizontalAccuracy:self.latestLocation.horizontalAccuracy
          verticalAccuracy:self.latestLocation.verticalAccuracy
                    course:heading
            courseAccuracy:self.latestLocation.courseAccuracy
                     speed:self.latestLocation.speed 
             speedAccuracy:self.latestLocation.speedAccuracy
                 timestamp:self.latestLocation.timestamp];

    return location;
}

- (void)locationManager:(CLLocationManager *)manager
       didFailWithError:(nonnull NSError *)error {
    NSLog(@"LOCATION UPDATE FAILURE:"
          "Error reason: %@"
          "Error description: %@", error.localizedFailureReason, error.localizedDescription);
    
    if([error.domain isEqualToString:kCLErrorDomain] && error.code == kCLErrorLocationUnknown) {
        return;
    }
    
    [self stopListening];
    
    if (self.errorHandler) {
        self.errorHandler(GeolocatorErrorLocationUpdateFailure, error.localizedDescription);
    }
}

+ (BOOL) shouldEnableBackgroundLocationUpdates {
    if (@available(iOS 9.0, *)) {
        return [[NSBundle.mainBundle objectForInfoDictionaryKey:@"UIBackgroundModes"] containsObject: @"location"];
    } else {
        return NO;
    }
}
@end
