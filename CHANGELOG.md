# Change Log

## [1.8.1](https://github.com/ably/ably-java/tree/v1.8.1)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.8.0...v1.8.1)

### What's Changed

- Bring the LiveObjects implementation into line with the reconciled objects spec and reorganise the shared unified test suite (UTS) infrastructure [#1228](https://github.com/ably/ably-java/pull/1228). Inbound operation handling is hardened so a single malformed operation no longer discards its batch siblings, a missing counter `number`/`count` is now representable and behaves consistently across transports, and the channel-state data lifecycle (RTO27) and root-object safeguards (RTO10c1b1/RTLO4e10) are honoured. Alongside this, `:uts` becomes a shared test-infra module and the spec-derived UTS suites move into the modules that own the code they exercise. This work was delivered through the following PRs:
  - Make `:uts` a shared test-infra module and move UTS suites to their owning modules [#1231](https://github.com/ably/ably-java/pull/1231)
  - Make `:uts` a shared test-infra module, move UTS suites to owning modules, and add objects spec-conformance [#1229](https://github.com/ably/ably-java/pull/1229)

## [1.8.0](https://github.com/ably/ably-java/tree/v1.8.0)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.7.2...v1.8.0)

### What's Changed

- Introduce the new path-based LiveObjects API [#1214](https://github.com/ably/ably-java/pull/1214). LiveObjects data is now accessed through `channel.object`, which returns a `RealtimeObject` exposing `PathObject`s — stable references to locations within the channel object that resolve to values dynamically at runtime, instead of explicit `LiveMap`/`LiveCounter` instances. See the [PathObject documentation](https://ably.com/docs/liveobjects/concepts/path-object) for details. This feature was delivered through the following PRs:
  - Implement the path-based LiveObjects public API interfaces [#1213](https://github.com/ably/ably-java/pull/1213)
  - Add the path-based `RealtimeObject` and the `channel.object` accessor [#1216](https://github.com/ably/ably-java/pull/1216)
  - Add basic implementation for the `PathObject` and `Instance` interfaces [#1217](https://github.com/ably/ably-java/pull/1217)
  - Rename the `object` package to `liveobjects` (`io.ably.lib.liveobjects`) [#1220](https://github.com/ably/ably-java/pull/1220)
  - Update the LiveObjects `uts-to-kotlin` skill to generate tests for the new API [#1221](https://github.com/ably/ably-java/pull/1221)
  - Implement core `LiveObjects` functionality on the new path-based API (Kotlin) [#1223](https://github.com/ably/ably-java/pull/1223)
  - Implement the remaining path-based API — parent references, event bubbling and related behaviour [#1224](https://github.com/ably/ably-java/pull/1224)
  - Migrate the LiveObjects example app to the path-based API [#1225](https://github.com/ably/ably-java/pull/1225)

### Breaking changes

This release replaces the previous experimental instance-based LiveObjects API with the new path-based API:

- The `channel.getObjects()` accessor (returning `RealtimeObjects`) has been replaced by the `channel.object` field (returning `RealtimeObject`)
- The `io.ably.lib.objects` package has been renamed to `io.ably.lib.liveobjects`
- Instead of obtaining and operating on explicit `LiveMap`/`LiveCounter` instances, data is accessed and mutated through `PathObject` references

## [1.7.2](https://github.com/ably/ably-java/tree/v1.7.2)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.7.1...v1.7.2)

### What's Changed

- Fix preferred host persistence in `HttpScheduler` retry logic [#1206](https://github.com/ably/ably-java/pull/1206)

## [1.7.1](https://github.com/ably/ably-java/tree/v1.7.1)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.7.0...v1.7.1)

### What's Changed

- Use server-provided GC grace period for LiveObjects garbage collection [#1158](https://github.com/ably/ably-java/pull/1158)
- Fix channel state updating before connection state listeners are notified (RTL3d1) [#1202](https://github.com/ably/ably-java/pull/1202)

## [1.7.0](https://github.com/ably/ably-java/tree/v1.7.0)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.6.1...v1.7.0)

### What's Changed

- Introduce version 6 of the Ably protocol; add operation-specific payload fields to `ObjectOperation` and typed value fields to `ObjectData` [#1195](https://github.com/ably/ably-java/pull/1195) 
- Add support for partial sync of LiveObjects state [#1197](https://github.com/ably/ably-java/pull/1197) 
- Introduce ACK-based local application of LiveObjects ops [#1194](https://github.com/ably/ably-java/pull/1194)
- Add support for server-initiated MAP_CLEAR object operation [#1198](https://github.com/ably/ably-java/pull/1198)

## [1.6.1](https://github.com/ably/ably-java/tree/v1.6.1)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.6.0...v1.6.1)

### What's Changed

- Upgrade msgpack-core to 0.9.11 to address security vulnerability [#1188](https://github.com/ably/ably-java/pull/1188)

## [1.6.0](https://github.com/ably/ably-java/tree/v1.6.0)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.5.0...v1.6.0)

### What's Changed

* Message Updates and Deletes updated, Appends API added in [#1182](https://github.com/ably/ably-java/pull/1182)
* Mutable Message API over Realtime added in [#1183](https://github.com/ably/ably-java/pull/1183)
* Updated publish API to include message serials in the response in [#1184](https://github.com/ably/ably-java/pull/1184)

### Breaking changes

This release includes several breaking changes to experimental Mutable Message features:

#### REST API Changes

* `updateMessage()` and `deleteMessage()` now return `UpdateDeleteResult` instead of `void`, containing the updated message version serial
* `updateMessageAsync()` and `deleteMessageAsync()` callback parameter changed from `CompletionListener` to `Callback<UpdateDeleteResult>`
* Added `appendMessage()` and `appendMessageAsync()` methods returning `UpdateDeleteResult`

#### Realtime API Changes

* `updateMessage()`, `deleteMessage()`, and `appendMessage()` are now asynchronous methods (void return) instead of synchronous (returning `UpdateDeleteResult`)
* All message mutation methods now accept an optional `Callback<UpdateDeleteResult>` parameter for async result handling
* Removed `*Async` suffix from method names - all Realtime methods are now async by default and throw `AblyException`
* `updateMessage()`, `deleteMessage()`, and `appendMessage()` now use WebSocket transport to send data rather than REST requests

#### Migration Guide

**REST API:**
```java
// Before (v1.5.0)
channel.updateMessageAsync(message, new Callback<UpdateDeleteResult>() {
  public void onSuccess() { ... }
}});

// After (v1.6.0)
channel.updateMessageAsync(message, new Callback<UpdateDeleteResult>() {
    public void onSuccess(UpdateDeleteResult result) { ... }
});
```

**Realtime API:**
```java
// Before (v1.5.0)
channel.updateMessageAsync(message, new Callback<UpdateDeleteResult>() {
  public void onSuccess() { ... }
}});

// After (v1.6.0)
channel.updateMessage(message, new Callback<UpdateDeleteResult>() {
    public void onSuccess(UpdateDeleteResult result) { ... }
});
```

## [1.5.0](https://github.com/ably/ably-java/tree/v1.5.0)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.4.2...v1.5.0)

### What's Changed

* Message Updates and Deletes API have been added in https://github.com/ably/ably-java/pull/1179
* The default http client has been changed to OkHttp in https://github.com/ably/ably-java/pull/1180

## [1.4.2](https://github.com/ably/ably-java/tree/v1.4.2)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.4.1...v1.4.2)

### What's Changed

* Fixed an issue where async requests were not working after the realtime client reconnected following a manual close in https://github.com/ably/ably-java/pull/1176

## [1.4.1](https://github.com/ably/ably-java/tree/v1.4.1)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.4.0...v1.4.1)

### What's Changed

* Fixed an error in the log when `data` field in `Message` is `null` in https://github.com/ably/ably-java/pull/1168
* Fixed an issue where presence would not send enter messages when the channel re-attaches after a disconnection in https://github.com/ably/ably-java/pull/1171
* Added`toString` method in `ChannelStateChange` class for improved debugging in https://github.com/ably/ably-java/pull/1169
* Fixed orphan Timer instances on release in https://github.com/ably/ably-java/pull/1173

## [1.4.0](https://github.com/ably/ably-java/tree/v1.4.0)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.3.0...v1.4.0)

### What's Changed

- Protocol v4 support for enhanced message annotations and versioning [\#1163](https://github.com/ably/ably-java/pull/1163)
- Support for clipped client ID lists and enhanced aggregation handling [\#1164](https://github.com/ably/ably-java/pull/1164)
- Improved WebSocket transport lifecycle and activity management [\#1165](https://github.com/ably/ably-java/pull/1165)

## [1.3.0](https://github.com/ably/ably-java/tree/v1.3.0)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.54...v1.3.0)

### What's Changed

* Introduces support for our new LiveObjects Swift Plugin. LiveObjects provides a simple way to build collaborative applications with synchronized state across multiple clients in real-time.

Learn [about Ably LiveObjects.](https://ably.com/docs/liveobjects)

[Getting started with LiveObjects in Java.](https://ably.com/docs/liveobjects/quickstart/java)

## [1.2.54](https://github.com/ably/ably-java/tree/v1.2.54)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.53...v1.2.54)

This release includes minor improvements and bug fixes.

**Implemented enhancements:**

- Prevents NPE in the connectivity check  [\#1111](https://github.com/ably/ably-java/issues/1111)
- Fixes async connection state transition side effects  [\#1119](https://github.com/ably/ably-java/issues/1119)

## [1.2.53](https://github.com/ably/ably-java/tree/v1.2.53)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.52...v1.2.53)

**Implemented enhancements:**

- Adds `ANNOTATION_PUBLISH` and `ANNOTATION_SUBSCRIBE` channel modes
- Adds support for message annotations via `channel.annotations` 
- The message action `meta.occupancy` is now renamed to `meta`. Similarly, `MessageActions.META_OCCUPANCY` is now `MessageActions.META`

## [1.2.52](https://github.com/ably/ably-java/tree/v1.2.52)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.51...v1.2.52)

**Closed issues:**

- `Java-WebSocket` holds lock while invoking listeners, it may cause deadlock [\#1079](https://github.com/ably/ably-java/issues/1079)

## [1.2.51](https://github.com/ably/ably-java/tree/v1.2.51)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.50...v1.2.51)

**Implemented enhancements:**

- Made query params URL-encoded in the request API by default [\#1075](https://github.com/ably/ably-java/issues/1075)
- Implemented RTN11d spec point [\#1074](https://github.com/ably/ably-java/issues/1074)

## [1.2.50](https://github.com/ably/ably-java/tree/v1.2.50)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.49...v1.2.50)

**Closed issues:**

- Warning on Android Proguard [\#1067](https://github.com/ably/ably-java/issues/1067)

**Implemented enhancements:**

- Added internal Kotlin Wrapper for the SDK 

## [1.2.49](https://github.com/ably/ably-java/tree/v1.2.49)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.48...v1.2.49)

**Closed issues:**

- Support message edits and deletes  [\#1058](https://github.com/ably/ably-java/issues/1058)

**Merged pull requests:**

- chore: upgrade github actions versions [\#1061](https://github.com/ably/ably-java/pull/1061) ([ttypic](https://github.com/ttypic))
- [ECO-5193] Support message edits and deletes [\#1059](https://github.com/ably/ably-java/pull/1059) ([sacOO7](https://github.com/sacOO7))

## [1.2.48](https://github.com/ably/ably-java/tree/v1.2.48)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.47...v1.2.48)

**Closed issues:**

- Flaky realtime tests for RealtimeChannelTest [\#1055](https://github.com/ably/ably-java/issues/1055)
- \[RTL13\] Handle server sent `DETACHED` event [\#1051](https://github.com/ably/ably-java/issues/1051)

**Merged pull requests:**

- \[ECO-5188\] MessageAction enum changes [\#1056](https://github.com/ably/ably-java/pull/1056) ([SimonWoolf](https://github.com/SimonWoolf))


## [1.2.47](https://github.com/ably/ably-java/tree/v1.2.47)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.46...v1.2.47)

**Fixed bugs:**

- Java SDK - Duplicate messages on rewind after 1.2.34 [\#1050](https://github.com/ably/ably-java/issues/1050)

## [1.2.46](https://github.com/ably/ably-java/tree/v1.2.46)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.45...v1.2.46)

**Implemented enhancements:**

- New experimental `Message` fields (`action`, `serial`, `createdAt`) have been added. 
  **Note:** These fields are not stable and are introduced to support the [Chat SDK](https://github.com/ably/ably-chat-kotlin). 
  Use with caution, as they may change in future releases.

**Merged pull requests:**

- \[ECO-5139\] feat: add `action` and `serial` fields [\#1048](https://github.com/ably/ably-java/pull/1048) ([ttypic](https://github.com/ttypic))


## [1.2.45](https://github.com/ably/ably-java/tree/v1.2.45)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.44...v1.2.45)

**Closed issues:**

- [RTL5] Incomplete spec implementation for channel DETACH/ATTACH [\#1045](https://github.com/ably/ably-java/issues/1045)
- [RTL7h] Throw exception for optional callback [\#1040](https://github.com/ably/ably-java/issues/1040)

**Merged pull requests:**

- [ECO-5117] Fix channel ATTACH/DETACH state checks [\#1046](https://github.com/ably/ably-java/pull/1046) ([sacOO7](https://github.com/sacOO7))

## [1.2.44](https://github.com/ably/ably-java/tree/v1.2.44)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.43...v1.2.44)

**Fixed bugs:**

- Race condition when calling`AblyRealtime#connect()` on terminated state [\#1041](https://github.com/ably/ably-java/issues/1041)

## [1.2.43](https://github.com/ably/ably-java/tree/v1.2.43)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.42...v1.2.43)

**Implemented enhancements:**

- Expand proxy support: Authenticated Proxy and Websockets \(`HTTP CONNECT` tunnel\) [\#120](https://github.com/ably/ably-java/issues/120)

**Merged pull requests:**

- feat: introduced retry rules for flaky android push tests [\#1036](https://github.com/ably/ably-java/pull/1036) ([ttypic](https://github.com/ttypic))
- feat: OkHttp implementation for making HTTP calls and WebSocket connections [\#1035](https://github.com/ably/ably-java/pull/1035) ([ttypic](https://github.com/ttypic))
- chore: update gradle wrapper [\#1034](https://github.com/ably/ably-java/pull/1034) ([ttypic](https://github.com/ttypic))

## [1.2.42](https://github.com/ably/ably-java/tree/v1.2.42)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.41...v1.2.42)

**Implemented enhancements:**

-  Implement the `attachOnSubscribe` channel option \(TB4\) [\#1027](https://github.com/ably/ably-java/issues/1027)

**Merged pull requests:**

- Fix implicit attach on subscribe [\#1028](https://github.com/ably/ably-java/pull/1028) ([sacOO7](https://github.com/sacOO7))
- ci: enable workflow\_dispatch [\#1025](https://github.com/ably/ably-java/pull/1025) ([owenpearson](https://github.com/owenpearson))
- tests: Assert connection error code rather than message [\#1023](https://github.com/ably/ably-java/pull/1023) ([lmars](https://github.com/lmars))

## [1.2.41](https://github.com/ably/ably-java/tree/v1.2.41)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.40...v1.2.41)

**Closed issues:**

-  For REST clients, all requests should include an `X-Ably-ClientId` header when basic auth is to be used \(RSA7e2\) [\#1015](https://github.com/ably/ably-java/issues/1015)

**Merged pull requests:**

- chore\(Auth\): get rid of unnecessary padding removal for Auth tokens [\#1021](https://github.com/ably/ably-java/pull/1021) ([ttypic](https://github.com/ttypic))
- feat: add Google SDK console verification [\#1020](https://github.com/ably/ably-java/pull/1020) ([ttypic](https://github.com/ttypic))
- feat: include `X-Ably-ClientId` for each request \(RSA7e2\) [\#1019](https://github.com/ably/ably-java/pull/1019) ([ttypic](https://github.com/ttypic))


## [1.2.40](https://github.com/ably/ably-java/tree/v1.2.40)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.39...v1.2.40)

**Fixed bugs:**

- Connection remains open when close is sent immediately [\#1012](https://github.com/ably/ably-java/issues/1012)

**Merged pull requests:**

- \[ECO-4820\] fix\(ConnectionManager\): update the connection close implementation to follow RTN12f [\#1013](https://github.com/ably/ably-java/pull/1013) ([ttypic](https://github.com/ttypic))

## [1.2.39](https://github.com/ably/ably-java/tree/v1.2.39)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.38...v1.2.39)

**Fixed bugs:**

- onMessage Exception [\#1009](https://github.com/ably/ably-java/issues/1009)
- NullPointerException When Attempting to read from field 'java.lang.String io.ably.lib.types.ErrorInfo.message' [\#995](https://github.com/ably/ably-java/issues/995)

## [1.2.38](https://github.com/ably/ably-java/tree/v1.2.38)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.37...v1.2.38)

**Fixed bugs:**

- v1.2.34-v1.2.37 are incompatible with Android API versions < 24  [\#1004](https://github.com/ably/ably-java/issues/1004)
- REST client not attempting fallback hosts upon `httpOpenTimeout` expiry [\#997](https://github.com/ably/ably-java/issues/997)

**Closed issues:**

- Gracefully shutdown Ably resources [\#917](https://github.com/ably/ably-java/issues/917)
- Read timed out [\#850](https://github.com/ably/ably-java/issues/850)

## [1.2.37](https://github.com/ably/ably-java/tree/v1.2.37)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.36...v1.2.37)

**Fixed bugs:**

- Fix HttpRequest & HttpRetry timeouts [\#310](https://github.com/ably/ably-java/issues/310)

## [1.2.36](https://github.com/ably/ably-java/tree/v1.2.36)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.35...v1.2.36)

**Closed issues:**

- Push Notification corner cases  [\#993](https://github.com/ably/ably-java/issues/993)
- Protocol-v2: readd recoveryKey to make this a non-breaking change [\#868](https://github.com/ably/ably-java/issues/868)

**Merged pull requests:**

- \[ECO-4706\] fix: push notifications corner cases [\#994](https://github.com/ably/ably-java/pull/994) ([ttypic](https://github.com/ttypic))

## [1.2.35](https://github.com/ably/ably-java/tree/v1.2.35)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.34...v1.2.35)

**Closed issues:**

- Enable and fix tests in RealtimePresenceTest [\#869](https://github.com/ably/ably-java/issues/869)

**Merged pull requests:**

- Fix presence / ignored presence tests [\#989](https://github.com/ably/ably-java/pull/989) ([sacOO7](https://github.com/sacOO7))

## [1.2.34](https://github.com/ably/ably-java/tree/v1.2.34)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.33...v1.2.34)

**Fixed bugs:**

- Should send `DETACH` after receiving `ATTACHED` while in the `DETACHING` or `DETACHED` state \(`RTL5k`\) [\#846](https://github.com/ably/ably-java/issues/846)

**Closed issues:**

- LocalDevice reset will cause ClassCastException [\#985](https://github.com/ably/ably-java/issues/985)
- Implement no-connection-serial [\#981](https://github.com/ably/ably-java/issues/981)
- DeviceSecret key is required by protocol v2.0 [\#845](https://github.com/ably/ably-java/issues/845)

**Merged pull requests:**

- Fix shared pref storage [\#986](https://github.com/ably/ably-java/pull/986) ([sacOO7](https://github.com/sacOO7))
- Feature/no connection serial [\#983](https://github.com/ably/ably-java/pull/983) ([sacOO7](https://github.com/sacOO7))

## [1.2.33](https://github.com/ably/ably-java/tree/v1.2.33)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.32...v1.2.33)

**Closed issues:**

- Throw exception on `released` Ably Channel methods [\#971](https://github.com/ably/ably-java/issues/971)

**Merged pull requests:**

- fix: prevent reattaching of detached channels [\#977](https://github.com/ably/ably-java/pull/977) ([ttypic](https://github.com/ttypic))
- feat: throw exception when trying to attach on released channel [\#973](https://github.com/ably/ably-java/pull/973) ([ttypic](https://github.com/ttypic))
- fix: deviceId and deviceToken consistence [\#972](https://github.com/ably/ably-java/pull/972) ([ttypic](https://github.com/ttypic))

## [1.2.32](https://github.com/ably/ably-java/tree/v1.2.32)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.31...v1.2.32)

**Fixed bugs:**

- Create Cipher instance in place, do not store it in `ChannelOptions` [\#969](https://github.com/ably/ably-java/pull/969)
- Late Disconnection [\#937](https://github.com/ably/ably-java/issues/937)

**Closed issues:**

- Stack traces not being sent to error logs [\#963](https://github.com/ably/ably-java/issues/963)

## [1.2.31](https://github.com/ably/ably-java/tree/v1.2.31)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.30...v1.2.31)

**Fixed bugs:**

- Update error code for channel attachment timed out [\#959](https://github.com/ably/ably-java/issues/959)
- Update error code for message decoding failure [\#958](https://github.com/ably/ably-java/issues/958)
- Fix incremental backoff while reconnecting [\#954](https://github.com/ably/ably-java/issues/954)
- Add `suspendedRetryTimeout` and `httpMaxRetryDuration` client options [\#956](https://github.com/ably/ably-java/issues/956)

**Merged pull requests:**

- fix: use appropriate error code for channel attachment timeout [\#961](https://github.com/ably/ably-java/pull/961) ([AndyTWF](https://github.com/AndyTWF))
- fix: use error code 40013 for message decoding failures [\#960](https://github.com/ably/ably-java/pull/960) ([AndyTWF](https://github.com/AndyTWF))
- Fix incremental backoff jitter [\#955](https://github.com/ably/ably-java/pull/955) ([sacOO7](https://github.com/sacOO7))
- Add missing clientOptions [\#957](https://github.com/ably/ably-java/pull/957) ([sacOO7](https://github.com/sacOO7))

## [1.2.30](https://github.com/ably/ably-java/tree/v1.2.30)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.29...v1.2.30)

**Fixed bugs:**

- Connection manager switches to fallback hosts on close [\#950](https://github.com/ably/ably-java/issues/950)

**Merged pull requests:**

- fix: fallback hosts always being used on transport error [\#951](https://github.com/ably/ably-java/pull/951) ([AndyTWF](https://github.com/AndyTWF))

## [1.2.29](https://github.com/ably/ably-java/tree/v1.2.29)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.28...v1.2.29)

**Fixed bugs:**

- RTN23a: Transport not disconnecting after TTL passed [\#932](https://github.com/ably/ably-java/issues/932)

**Merged pull requests:**

- fix: transport not disconnecting after ttl passed [\#939](https://github.com/ably/ably-java/pull/939) ([AndyTWF](https://github.com/AndyTWF))
- fix\(ConnectionManager\): don't check state before sending close message [\#938](https://github.com/ably/ably-java/pull/938) ([owenpearson](https://github.com/owenpearson))

## [1.2.28](https://github.com/ably/ably-java/tree/v1.2.28)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.27...v1.2.28)

**Fixed bugs:**

- Realtime with authUrl with token in query string fails to connect [\#935](https://github.com/ably/ably-java/issues/935)

## [1.2.27](https://github.com/ably/ably-java/tree/v1.2.27)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.26...v1.2.27)

**Fixed bugs:**

- equals\(\) for TokenDetails is broken [\#926](https://github.com/ably/ably-java/issues/926)
- Long-lived connections are immediately transitioned to `SUSPENDED` after disconnection [\#925](https://github.com/ably/ably-java/issues/925)

**Merged pull requests:**

- Suspend timer is set when transport is unavailable and last state was connected [\#928](https://github.com/ably/ably-java/pull/928) ([AndyTWF](https://github.com/AndyTWF))
- Fix equals\(\) on token details  [\#927](https://github.com/ably/ably-java/pull/927) ([ikbalkaya](https://github.com/ikbalkaya))


## [1.2.26](https://github.com/ably/ably-java/tree/v1.2.26)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.25...v1.2.26)

**Fixed bugs:**

- Provide an error code and error message for failed queued messages [\#920](https://github.com/ably/ably-java/issues/920)

**Merged pull requests:**

- Add reason to pending message instead of creating an ErrorInfo [\#922](https://github.com/ably/ably-java/pull/922) ([ikbalkaya](https://github.com/ikbalkaya))

## [1.2.25](https://github.com/ably/ably-java/tree/v1.2.25)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.24...v1.2.25)

**Fixed bugs:**

- Released channel re-added to the channel map after DETACHED message [\#913](https://github.com/ably/ably-java/issues/913)

**Merged pull requests:**

- Drop messages where channel does not exist [\#914](https://github.com/ably/ably-java/pull/914) ([AndyTWF](https://github.com/AndyTWF))
- Improve `1.2`-series Release Process [\#912](https://github.com/ably/ably-java/pull/912) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix link formatting in changelog [\#911](https://github.com/ably/ably-java/pull/911) ([AndyTWF](https://github.com/AndyTWF))

## [1.2.24](https://github.com/ably/ably-java/tree/v1.2.24)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.23...v1.2.24)

**Fixed bugs:**

- Presence messages superseded whilst channel in attaching state [\#908](https://github.com/ably/ably-java/issues/908)
- A failed resume incorrectly retries queued messages prior to reattachment [\#905](https://github.com/ably/ably-java/issues/905)
- Pending messages are not failed when transitioning to suspended [\#904](https://github.com/ably/ably-java/issues/904)

**Merged pull requests:**

- Presence message superseded [\#909](https://github.com/ably/ably-java/pull/909) ([AndyTWF](https://github.com/AndyTWF))
- Improvements on connection resume failure [\#906](https://github.com/ably/ably-java/pull/906) ([ikbalkaya](https://github.com/ikbalkaya))


## [1.2.23](https://github.com/ably/ably-java/tree/v1.2.23)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.22...v1.2.23)

**Fixed bugs:**

- Re-attach fails due to previous detach request [\#885](https://github.com/ably/ably-java/issues/885)
- Lib is not re-sending pending messages on new transport after a resume [\#474](https://github.com/ably/ably-java/issues/474)

**Merged pull requests:**

- Connection resumption improvements [\#900](https://github.com/ably/ably-java/pull/900) ([ikbalkaya](https://github.com/ikbalkaya))
- Make EventEmitter.on\(\) documentation reflect implementation [\#889](https://github.com/ably/ably-java/pull/889) ([AndyTWF](https://github.com/AndyTWF))
- Fix attach/detach race condition [\#887](https://github.com/ably/ably-java/pull/887) ([ikbalkaya](https://github.com/ikbalkaya))

## [1.2.22](https://github.com/ably/ably-java/tree/v1.2.22)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.21...v1.2.22)

**Merged pull requests:**

- Skip checking WS hostname when not using SSL [\#883](https://github.com/ably/ably-java/pull/883) ([cruickshankpg](https://github.com/cruickshankpg))

## [1.2.21](https://github.com/ably/ably-java/tree/v1.2.21)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.20...v1.2.21)

**Fixed bugs:**

- Presence.endSync throws NullPointerException when processing a message [\#853](https://github.com/ably/ably-java/issues/853)

**Merged pull requests:**

- added null check to prevent NullPointerExceptions [\#873](https://github.com/ably/ably-java/pull/873) ([davyskiba](https://github.com/davyskiba))

## [1.2.20](https://github.com/ably/ably-java/tree/v1.2.20)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.19...v1.2.20)

Sorry for the release noise, but the big fix we thought we had made in [1.2.19](https://github.com/ably/ably-java/releases/tag/v1.2.19) turned out not to fix the problem...

**Second Attempt at Bug Fix:**
Automatic presence re-enter after network connection is back does not work [\#857](https://github.com/ably/ably-java/issues/857) in Revert to protocol 1.0 [\#864](https://github.com/ably/ably-java/pull/864) ([QuintinWillison](https://github.com/QuintinWillison))

## [1.2.19](https://github.com/ably/ably-java/tree/v1.2.19)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.18...v1.2.19)

**Implemented enhancements:**

- Implement incremental backoff and jitter [\#795](https://github.com/ably/ably-java/issues/795) in [\#852](https://github.com/ably/ably-java/pull/852) ([qsdigor](https://github.com/qsdigor))

**Fixed bugs:**

- Automatic presence re-enter after network connection is back does not work [\#857](https://github.com/ably/ably-java/issues/857) in Revert to protocol 1.1 [\#858](https://github.com/ably/ably-java/pull/858) ([KacperKluka](https://github.com/KacperKluka))

## [1.2.18](https://github.com/ably/ably-java/tree/v1.2.18)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.17...v1.2.18)

This release improves our Javadoc API commentaries for this SDK.
Other than that, there are no functional changes (features, bug fixes, etc..).

## [1.2.17](https://github.com/ably/ably-java/tree/v1.2.17)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.16...v1.2.17)

**Fixed bugs:**

- RSA4d is not implemented correctly [\#829](https://github.com/ably/ably-java/issues/829)
- JSONUtilsObject.add() silently discards data of unsupported type [\#501](https://github.com/ably/ably-java/issues/501)

**Merged pull requests:**

- Fail Ably connection if auth callback throws specific errors [\#834](https://github.com/ably/ably-java/pull/834) ([KacperKluka](https://github.com/KacperKluka))

## [1.2.16](https://github.com/ably/ably-java/tree/v1.2.16)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.15...v1.2.16)

In this release, we have fixed a bug that was introduced in 1.2.15 that caused the SDK to return early from the 
`Auth#renewAuth` method.

- call waiter.close() after breaking from while loop [\#825](https://github.com/ably/ably-java/pull/825) ([ikbalkaya](https://github.com/ikbalkaya))


## [1.2.15](https://github.com/ably/ably-java/tree/v1.2.15)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.14...v1.2.15)

In this release we have added a new method that provides a completion handler for renewing an authentication token. 
We also updated the documentation to clarify the thread policy for public method callbacks. 

- A new `renewAuth` method was added to `Auth` and the `renew` method was deprecated

**Implemented enhancements:**

- Add new renew async method [\#816](https://github.com/ably/ably-java/pull/816) ([ikbalkaya](https://github.com/ikbalkaya))

**Fixed bugs:**

- Early return from  onAuthUpdated creates issues [\#814](https://github.com/ably/ably-java/issues/814)

**Closed issues:**

- Invalid method implementation in README [\#819](https://github.com/ably/ably-java/issues/819)
- Document which thread is whole SDK or callbacks using [\#800](https://github.com/ably/ably-java/issues/800)

**Merged pull requests:**

- Update onChannelStateChanged readme with current implementation [\#820](https://github.com/ably/ably-java/pull/820) ([qsdigor](https://github.com/qsdigor))
- Document thread policy for callbacks and add missing documentation for callbacks [\#818](https://github.com/ably/ably-java/pull/818) ([qsdigor](https://github.com/qsdigor))

## [v1.2.14](https://github.com/ably/ably-java/tree/v1.2.14)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.13...v1.2.14)

We've made some changes to JDK and Android API Level minimum requirements in this release,
which might cause problems for those with very old build toolchains,
or application projects with really permissive minimum runtime requirements:

- Java source and target compatibility level increased from 1.7 to **1.8**
- Android minimum SDK API Level increased from 16 to **19 (4.4 KitKat)**

We've also fixed an oversight in our REST support whereby it previously was not possible to fully release resources
consumed by the background thread pool used for HTTP operations, neither explicitly nor passively via GC.
This was most noticeably a problem for applications which created several client instances during the lifespan of
their application process.

**Fixed bugs:**

- NoSuchMethodError in ably-android for API lower than 24 [\#802](https://github.com/ably/ably-java/issues/802), fixed by [\#808](https://github.com/ably/ably-java/pull/808) ([KacperKluka](https://github.com/KacperKluka))
- Threads remain in parked \(waiting\) state indefinitely when `AblyRest` instance is freed [\#801](https://github.com/ably/ably-java/issues/801), addressed by adding `finalize()` and `AutoCloseable` support to `AblyRest` instances [\#807](https://github.com/ably/ably-java/pull/807) ([QuintinWillison](https://github.com/QuintinWillison))
- Minimum API Level supported for Android is 19 \(KitKat, v.4.4\) [\#804](https://github.com/ably/ably-java/pull/804) ([QuintinWillison](https://github.com/QuintinWillison))

**Merged pull requests:**

- Increase minimum JRE version to 1.8 [\#805](https://github.com/ably/ably-java/pull/805) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.13](https://github.com/ably/ably-java/tree/v1.2.13)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.12...v1.2.13)

**Closed issues:**

- Update dependency: com.google.code.gson:gson [\#777](https://github.com/ably/ably-java/issues/777)
- Update dependency: org.java-websocket:Java-WebSocket [\#776](https://github.com/ably/ably-java/issues/776)

## [v1.2.12](https://github.com/ably/ably-java/tree/v1.2.12)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.11...v1.2.12)

**Fixed bugs:**

- Cannot automatically re-enter channel due to mismatched connectionId [\#761](https://github.com/ably/ably-java/issues/761)
- Ensure that weak SSL/TLS protocols are not used [\#749](https://github.com/ably/ably-java/issues/749)

## [v1.2.11](https://github.com/ably/ably-java/tree/v1.2.11)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.10...v1.2.11)

**Fixed bugs:**

- `ConcurrentModificationException` when `unsubscribe` then `detach` channel presence listener [\#743](https://github.com/ably/ably-java/issues/743), fixed in [\#744](https://github.com/ably/ably-java/pull/744) ([QuintinWillison](https://github.com/QuintinWillison))
- `IllegalStateException` in `Crypto` `CBCCipher`'s `decrypt` method [\#741](https://github.com/ably/ably-java/issues/741), fixed in [\#746](https://github.com/ably/ably-java/pull/746) ([QuintinWillison](https://github.com/QuintinWillison))
- Incorrect use of locale sensitive String APIs [\#713](https://github.com/ably/ably-java/issues/713), fixed in [\#722](https://github.com/ably/ably-java/pull/722) ([martin-morek](https://github.com/martin-morek))
- `push.listSubscriptionsImpl` method not respecting params [\#705](https://github.com/ably/ably-java/issues/705), fixed in [\#710](https://github.com/ably/ably-java/pull/710) ([martin-morek](https://github.com/martin-morek))

**Other merged pull requests:**

- Fix indentation and typos in authCallback example [\#724](https://github.com/ably/ably-java/pull/724) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.10](https://github.com/ably/ably-java/tree/v1.2.10)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.9...v1.2.10)

**Fixed bugs:**

- Using Firebase installation ID as registration token: Users cannot reactivate the device after deactivating [\#715](https://github.com/ably/ably-java/issues/715)

**Merged pull requests:**

- Fix: Use `FirebaseMessaging\#getToken\(\)` to get registration token [\#717](https://github.com/ably/ably-java/pull/717) ([ben-xD](https://github.com/ben-xD))

## [v1.2.9](https://github.com/ably/ably-java/tree/v1.2.9)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.8...v1.2.9)

**Fixed bugs:**

- IllegalArgumentException: No enum constant io.ably.lib.http.HttpAuth.Type.BASİC [\#711](https://github.com/ably/ably-java/issues/711)
- ProGuard warnings emitted by Android build against 1.1.6 [\#529](https://github.com/ably/ably-java/issues/529)

**Merged pull requests:**

- Fix incorrect parsing of HTTP auth type for some locales [\#712](https://github.com/ably/ably-java/pull/712) ([QuintinWillison](https://github.com/QuintinWillison))
- Suppressed warning in ProGuard [\#709](https://github.com/ably/ably-java/pull/709) ([martin-morek](https://github.com/martin-morek))

## [v1.2.8](https://github.com/ably/ably-java/tree/v1.2.8)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.7...v1.2.8)

**Implemented enhancements:**

- Update Stats fields with latest MessageTraffic types [\#394](https://github.com/ably/ably-java/issues/394)
- Replace ULID with Android's UUID [\#680](https://github.com/ably/ably-java/issues/680)

**Fixed bugs:**

- Push Activation State Machine exception handling needs improvement [\#685](https://github.com/ably/ably-java/issues/685)
- WebsocketNotConnectedException on send [\#430](https://github.com/ably/ably-java/issues/430)

**Merged pull requests:**

- Replaced ULID with UUID for deviceID [\#702](https://github.com/ably/ably-java/pull/702) ([martin-morek](https://github.com/martin-morek))
- Separate handling WebsocketNotConnectedException [\#701](https://github.com/ably/ably-java/pull/701) ([martin-morek](https://github.com/martin-morek))
- Updated Stats fields with the latest MessageTraffic types [\#698](https://github.com/ably/ably-java/pull/698) ([martin-morek](https://github.com/martin-morek))

## [v1.2.7](https://github.com/ably/ably-java/tree/v1.2.7)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.6...v1.2.7)

**Implemented enhancements:**

- Implement RSC7d \(Ably-Agent header\) [\#665](https://github.com/ably/ably-java/issues/665)
- Conform toString\(\) implementations [\#631](https://github.com/ably/ably-java/issues/631)

**Fixed bugs:**

- Remove use of forClass method in push activation state machine implementation [\#686](https://github.com/ably/ably-java/issues/686)
- Race condition releasing short lived channels [\#570](https://github.com/ably/ably-java/issues/570)
- Using a clientId should no longer be forcing token auth in the 1.1 spec [\#473](https://github.com/ably/ably-java/issues/473)
- Ensure correct feedback to developer when malformed key is supplied [\#382](https://github.com/ably/ably-java/issues/382)

**Closed issues:**

- Fail connection immediately if authorize\(\) called and 403 returned [\#620](https://github.com/ably/ably-java/issues/620)
- FCM getToken method is deprecated [\#597](https://github.com/ably/ably-java/issues/597)
- Support for encryption of shared preferences [\#593](https://github.com/ably/ably-java/issues/593)
- RSC7c TI1 addRequestIds on ClientOptions and requestId on ErrorInfo [\#574](https://github.com/ably/ably-java/issues/574)

**Merged pull requests:**

- Increase minimum SDK version to Android 4.1 \(Jelly Bean, API Level 16\) [\#691](https://github.com/ably/ably-java/pull/691) ([KacperKluka](https://github.com/KacperKluka))
- Throws exception when AuthOptions are initialized with an empty string [\#690](https://github.com/ably/ably-java/pull/690) ([martin-morek](https://github.com/martin-morek))
- Removed forName method  [\#689](https://github.com/ably/ably-java/pull/689) ([martin-morek](https://github.com/martin-morek))
- Updated Firebase cloud messaging dependency [\#687](https://github.com/ably/ably-java/pull/687) ([martin-morek](https://github.com/martin-morek))
- Unified custom toString\(\) method implementations to use curly bracket… [\#683](https://github.com/ably/ably-java/pull/683) ([martin-morek](https://github.com/martin-morek))
- Support for encryption of shared preferences [\#681](https://github.com/ably/ably-java/pull/681) ([martin-morek](https://github.com/martin-morek))
- Add request\_id query param if addRequestIds is enabled [\#678](https://github.com/ably/ably-java/pull/678) ([martin-morek](https://github.com/martin-morek))
- Using a clientId should no longer be forcing token auth [\#675](https://github.com/ably/ably-java/pull/675) ([martin-morek](https://github.com/martin-morek))
- Checking if error code is 403 and failing connection [\#672](https://github.com/ably/ably-java/pull/672) ([martin-morek](https://github.com/martin-morek))
- Add Ably-Agent header [\#671](https://github.com/ably/ably-java/pull/671) ([KacperKluka](https://github.com/KacperKluka))
- Changing Capability.addResource\(\) to take varargs as last parameter [\#664](https://github.com/ably/ably-java/pull/664) ([Thunderforge](https://github.com/Thunderforge))

## [v1.2.6](https://github.com/ably/ably-java/tree/v1.2.6)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.5...v1.2.6)

**Fixed bug:** channel presence members [\#669](https://github.com/ably/ably-java/pull/669) ([sacOO7](https://github.com/sacOO7))  
An issue affecting only users calling `get(boolean wait)` on `Presence` with `wait` set to `true`.

## [v1.2.5](https://github.com/ably/ably-java/tree/v1.2.5)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.4...v1.2.5)

**Fixed bugs:**

- Crypto.getRandomMessageId isn't working as intended [\#654](https://github.com/ably/ably-java/issues/654)
- Hosts class is not thread safe [\#650](https://github.com/ably/ably-java/issues/650)
- AblyBase.InternalChannels is not thread-safe [\#649](https://github.com/ably/ably-java/issues/649)

**Merged pull requests:**

- Makes the Hosts class safe to be called from any thread [\#657](https://github.com/ably/ably-java/pull/657) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix getRandomMessageId [\#656](https://github.com/ably/ably-java/pull/656) ([sacOO7](https://github.com/sacOO7))
- Improve channel map operations in respect of thread-safety [\#655](https://github.com/ably/ably-java/pull/655) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.4](https://github.com/ably/ably-java/tree/v1.2.4)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.3...v1.2.4)

**Fixed bugs:**

- Many instances of ConnectionWaiter spawned while app is running, with authentication token flow [\#651](https://github.com/ably/ably-java/issues/651)
- capability tokendetails adds to HTTP Request as a query parameter [\#647](https://github.com/ably/ably-java/issues/647)
- ClientOptions idempotentRestPublishing default may be wrong [\#590](https://github.com/ably/ably-java/issues/590)
- Presence blocking get sometimes has missing members [\#467](https://github.com/ably/ably-java/issues/467)
- Remove empty capability query parameter [\#648](https://github.com/ably/ably-java/pull/648) ([vzhikserg](https://github.com/vzhikserg))
- Add unit test for idempotentRestPublishing in ClientOptions [\#636](https://github.com/ably/ably-java/pull/636) ([vzhikserg](https://github.com/vzhikserg))
- Fix Member Presence [\#607](https://github.com/ably/ably-java/pull/607) ([sacOO7](https://github.com/sacOO7))

**Merged pull requests:**

- Unregister ConnectionWaiter listeners once connected [\#652](https://github.com/ably/ably-java/pull/652) ([QuintinWillison](https://github.com/QuintinWillison))
- Update references from 1 -\> l to match client spec [\#646](https://github.com/ably/ably-java/pull/646) ([natdempk](https://github.com/natdempk))
- Add workflow status badges [\#645](https://github.com/ably/ably-java/pull/645) ([QuintinWillison](https://github.com/QuintinWillison))
- Add maintainers file [\#644](https://github.com/ably/ably-java/pull/644) ([niksilver](https://github.com/niksilver))
- Add workflows [\#643](https://github.com/ably/ably-java/pull/643) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix CI pipeline [\#642](https://github.com/ably/ably-java/pull/642) ([vzhikserg](https://github.com/vzhikserg))
- Fix/doc 233 update readme [\#641](https://github.com/ably/ably-java/pull/641) ([tbedford](https://github.com/tbedford))
- Log error message to get clear understanding of exception [\#632](https://github.com/ably/ably-java/pull/632) ([sacOO7](https://github.com/sacOO7))
- Refactor MessageExtras [\#595](https://github.com/ably/ably-java/pull/595) ([sacOO7](https://github.com/sacOO7))

## [v1.2.3](https://github.com/ably/ably-java/tree/v1.2.3)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.2...v1.2.3)

**Enhancements and Bug Fixes:**

- Add connectionKey attribute missing from the Message object [\#630](https://github.com/ably/ably-java/pull/630) ([vzhikserg](https://github.com/vzhikserg)), fixing [\#614](https://github.com/ably/ably-java/issues/614)
- Improve error messages for channel attach when realtime is not active [\#627](https://github.com/ably/ably-java/pull/627) ([vzhikserg](https://github.com/vzhikserg)), fixing [\#594](https://github.com/ably/ably-java/issues/594)
- Add verbose logs in push notification related code [\#623](https://github.com/ably/ably-java/pull/623) ([QuintinWillison](https://github.com/QuintinWillison))
- Defaults: Generate environment fallbacks [\#618](https://github.com/ably/ably-java/pull/618) ([vzhikserg](https://github.com/vzhikserg)), fixing [\#603](https://github.com/ably/ably-java/issues/603)
- Rest.publishBatch: support overloaded method that takes params [\#604](https://github.com/ably/ably-java/pull/604) ([SimonWoolf](https://github.com/SimonWoolf))

**Code Quality Improvements:**

- Refactor unit tests related to hosts and environmental fallbacks [\#629](https://github.com/ably/ably-java/pull/629) ([vzhikserg](https://github.com/vzhikserg)), fixing [\#628](https://github.com/ably/ably-java/issues/628)
- Move tests for EventEmitter to unit tests [\#626](https://github.com/ably/ably-java/pull/626) ([vzhikserg](https://github.com/vzhikserg))
- Adopt more Groovy conventions in Gradle scripts [\#625](https://github.com/ably/ably-java/pull/625) ([QuintinWillison](https://github.com/QuintinWillison))
- Gradle conform and reformat [\#624](https://github.com/ably/ably-java/pull/624) ([QuintinWillison](https://github.com/QuintinWillison))
- Fix param and return javadoc statements [\#622](https://github.com/ably/ably-java/pull/622) ([vzhikserg](https://github.com/vzhikserg))
- Make Ably version more robust [\#619](https://github.com/ably/ably-java/pull/619) ([vzhikserg](https://github.com/vzhikserg))
- Remove unnecessary calls to the toString method [\#617](https://github.com/ably/ably-java/pull/617) ([vzhikserg](https://github.com/vzhikserg))
- Update EditorConfig [\#616](https://github.com/ably/ably-java/pull/616) ([QuintinWillison](https://github.com/QuintinWillison))
- Upgrade Gradle wrapper to version 6.6.1 [\#615](https://github.com/ably/ably-java/pull/615) ([QuintinWillison](https://github.com/QuintinWillison))
- Checkstyle: AvoidStarImport [\#613](https://github.com/ably/ably-java/pull/613) ([QuintinWillison](https://github.com/QuintinWillison))
- Checkstyle: UnusedImports [\#612](https://github.com/ably/ably-java/pull/612) ([QuintinWillison](https://github.com/QuintinWillison))
- Convert tabs to spaces in all Java source files [\#610](https://github.com/ably/ably-java/pull/610) ([QuintinWillison](https://github.com/QuintinWillison))
- Introduce Checkstyle [\#609](https://github.com/ably/ably-java/pull/609) ([QuintinWillison](https://github.com/QuintinWillison))
- Remove redundant public keywords in the interfaces' definitions [\#608](https://github.com/ably/ably-java/pull/608) ([vzhikserg](https://github.com/vzhikserg))

## [v1.2.2](https://github.com/ably/ably-java/tree/v1.2.2) (2020-09-17)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.1...v1.2.2)

**Fixed bugs:**

- Restoral of ActivationStateMachine events fails because not all event types have a no-argument constructor [\#598](https://github.com/ably/ably-java/issues/598) fixed by:
    - Discard persisted events with non-nullary constructors [\#599](https://github.com/ably/ably-java/pull/599) ([tcard](https://github.com/tcard))
- Fatal Exception on API level below 19 [\#596](https://github.com/ably/ably-java/issues/596) fixed by:
    - Replace use of StandardCharsets [\#601](https://github.com/ably/ably-java/pull/601) ([QuintinWillison](https://github.com/QuintinWillison))

**Other merged pull requests:**

- Rename master to main [\#592](https://github.com/ably/ably-java/pull/592) ([QuintinWillison](https://github.com/QuintinWillison))
- Bump protocol version to 1.2 [\#591](https://github.com/ably/ably-java/pull/591) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.1](https://github.com/ably/ably-java/tree/v1.2.1) (2020-06-15)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.2.0...v1.2.1)

**Fixed bugs:**

- Address impact of change to interface on extras field on Message [\#580](https://github.com/ably/ably-java/issues/580)

**Merged pull requests:**

- Support outbound message extras [\#581](https://github.com/ably/ably-java/pull/581) ([QuintinWillison](https://github.com/QuintinWillison))

## [v1.2.0](https://github.com/ably/ably-java/tree/v1.2.0) (2020-06-08)

Adds the capability to subscribe to a channel in delta mode.

Subscribing to a channel in delta mode enables [delta compression](https://www.ably.io/documentation/realtime/channels/channel-parameters/deltas). This is a way for a client to subscribe to a channel so that message payloads sent contain only the difference (ie the delta) between the present message and the previous message on the channel.

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.11...v1.2.0)

## [v1.1.11](https://github.com/ably/ably-java/tree/v1.1.11) (2020-05-18)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.10...v1.1.11)

**Merged pull requests:**

- Push Activation State Machine: validate an already-registered device on activation [\#543](https://github.com/ably/ably-java/pull/543) ([paddybyers](https://github.com/paddybyers))

## [v1.1.10](https://github.com/ably/ably-java/tree/v1.1.10) (2020-03-04)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.9...v1.1.10)

**Implemented enhancements:**

- Remove capability to bundle messages [\#567](https://github.com/ably/ably-java/pull/567) ([QuintinWillison](https://github.com/QuintinWillison))

**Closed issues:**

- Avoid message bundling, conforming to updated RTL6d [\#548](https://github.com/ably/ably-java/issues/548)

## [v1.1.9](https://github.com/ably/ably-java/tree/v1.1.9) (2020-03-03)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.8...v1.1.9)

**Implemented enhancements:**

- Upload to Maven Central [\#505](https://github.com/ably/ably-java/issues/505)
- Maven deployment: add task for deploy to staging [\#560](https://github.com/ably/ably-java/pull/560) ([paddybyers](https://github.com/paddybyers))

**Fixed bugs:**

- ConnectionManager.checkConnectivity\(\) fails every time for Android 9 [\#541](https://github.com/ably/ably-java/issues/541)
- ably-java sometimes failing to decrypt Messages [\#531](https://github.com/ably/ably-java/issues/531)
- Channels visibility improvements [\#558](https://github.com/ably/ably-java/pull/558) ([QuintinWillison](https://github.com/QuintinWillison))
- ConnectionManager: use HTTPS for the internet-up check [\#542](https://github.com/ably/ably-java/pull/542) ([paddybyers](https://github.com/paddybyers))

**Closed issues:**

- Remove develop branch [\#547](https://github.com/ably/ably-java/issues/547)

**Merged pull requests:**

- Get AndroidPushTest to pass again [\#553](https://github.com/ably/ably-java/pull/553) ([tcard](https://github.com/tcard))
- Fix reference to param that wasn't updated when param name changed. [\#552](https://github.com/ably/ably-java/pull/552) ([tcard](https://github.com/tcard))

## [v1.1.8](https://github.com/ably/ably-java/tree/v1.1.8)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.6...v1.1.8)

**Fixed bugs:**

- Rework and reinstate invalid ConnectionManager tests [\#524](https://github.com/ably/ably-java/issues/524)
- After loss of connectivity, and transport closure due to timeout, the ConnectionManager still thinks the transport is active [\#495](https://github.com/ably/ably-java/issues/495)

**Merged pull requests:**

- Connectionmanager synchronisation refactor [\#539](https://github.com/ably/ably-java/pull/539) ([paddybyers](https://github.com/paddybyers))
- RealtimeChannelTest.transient\_publish\_connected: fix regression… [\#538](https://github.com/ably/ably-java/pull/538) ([paddybyers](https://github.com/paddybyers))
- Fix transient pub race [\#528](https://github.com/ably/ably-java/pull/528) ([paddybyers](https://github.com/paddybyers))
- \[WIP\] Fix some ConnectionManager tests [\#526](https://github.com/ably/ably-java/pull/526) ([amihaiemil](https://github.com/amihaiemil))

## [v1.1.7](https://github.com/ably/ably-java/tree/v1.1.7)

Note: this release reverts the changes in 1.1.6 due to regressions in that release, and is functionally identical to 1.1.5.

## [v1.1.6](https://github.com/ably/ably-java/tree/v1.1.6)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.5...v1.1.6)

**Fixed bugs:**

- Unexpected exception in WsClient causing connection errors [\#519](https://github.com/ably/ably-java/issues/519)
- bad rsv 4 error from WebsocketClient if transport is forced to close during handshake [\#503](https://github.com/ably/ably-java/issues/503)
- fromCipherKey does not match spec [\#492](https://github.com/ably/ably-java/issues/492)

**Closed issues:**

- HttpScheduler.AsyncRequest\<T\> Ignores withCredentials Parameter [\#517](https://github.com/ably/ably-java/issues/517)
- AblyRealtime should implement Autocloseable [\#514](https://github.com/ably/ably-java/issues/514)

**Merged pull requests:**

- Update websocket dependency [\#520](https://github.com/ably/ably-java/pull/520) ([paddybyers](https://github.com/paddybyers))
- Fixes in HttpScheduler.AsyncRequest [\#518](https://github.com/ably/ably-java/pull/518) ([amihaiemil](https://github.com/amihaiemil))
- \#514 AblyRealtime implements Autocloseable [\#515](https://github.com/ably/ably-java/pull/515) ([amihaiemil](https://github.com/amihaiemil))
- ChannelOptions.withCipherKey + tests [\#513](https://github.com/ably/ably-java/pull/513) ([amihaiemil](https://github.com/amihaiemil))
- Added test for \#474 [\#511](https://github.com/ably/ably-java/pull/511) ([amihaiemil](https://github.com/amihaiemil))

## [v1.1.5](https://github.com/ably/ably-java/tree/v1.1.5)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.4...v1.1.5)

**Fixed bugs:**

- WebSocketTransport: don't null the wsConnection in onClose\(\) [\#500](https://github.com/ably/ably-java/pull/500) ([paddybyers](https://github.com/paddybyers))

## [v1.1.4](https://github.com/ably/ably-java/tree/v1.1.4)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.3...v1.1.4)

**Merged pull requests:**

- Connectionmanager deadlock fix [\#497](https://github.com/ably/ably-java/pull/497) ([paddybyers](https://github.com/paddybyers))

## [v1.1.3](https://github.com/ably/ably-java/tree/v1.1.3)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.2...v1.1.3)

**Merged pull requests:**

- Push: delete all locally persisted state when deregistering [\#494](https://github.com/ably/ably-java/pull/494) ([paddybyers](https://github.com/paddybyers))
- Async callback fix [\#493](https://github.com/ably/ably-java/pull/493) ([amsurana](https://github.com/amsurana))

## [v1.1.2](https://github.com/ably/ably-java/tree/v1.1.2)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.14...v1.1.1)

**Implemented enhancements:**

- ConnectionManager: ensure that cached token details are cleared on any connection error [\#487](https://github.com/ably/ably-java/pull/487) ([paddybyers](https://github.com/paddybyers))
- Add interactive test notes to the README [\#486](https://github.com/ably/ably-java/issues/486)

**Fixed bugs:**

- Push problems with push-subscribe permission [\#484](https://github.com/ably/ably-java/issues/484)
- Push: LocaDevice.deviceSecret serialisation issue [\#480](https://github.com/ably/ably-java/issues/480)
- Push: LocalDevice.reset\(\) doesn't clear persisted device state [\#478](https://github.com/ably/ably-java/issues/478)
- PUSH\_ACTIVATE intent broadcast is not always sent when activating push [\#477](https://github.com/ably/ably-java/issues/477)
- Stop using deprecated FirebaseInstanceIdService [\#475](https://github.com/ably/ably-java/issues/475)
- Expired token never renewed [\#470](https://github.com/ably/ably-java/issues/470)
- Presence: fix a couple test regressions [\#490](https://github.com/ably/ably-java/pull/490) ([paddybyers](https://github.com/paddybyers))

**Closed issues:**

- Add RTN20 support - react to operating system network connectivity events [\#415](https://github.com/ably/ably-java/issues/415)
- Exceptions when attempting to send with null WsClient [\#447](https://github.com/ably/ably-java/issues/447)

**Merged pull requests:**

- README: add a note about the push example/test app [\#491](https://github.com/ably/ably-java/pull/491) ([paddybyers](https://github.com/paddybyers))
- Reenable REST publish tests that depend on idempotency [\#489](https://github.com/ably/ably-java/pull/489) ([paddybyers](https://github.com/paddybyers))
- Push fixes for 112 [\#485](https://github.com/ably/ably-java/pull/485) ([paddybyers](https://github.com/paddybyers))
- Local device reset fix [\#479](https://github.com/ably/ably-java/pull/479) ([amsurana](https://github.com/amsurana))

## [v1.1.1](https://github.com/ably/ably-java/tree/v1.1.1) (2019-04-10)
[Full Changelog](https://github.com/ably/ably-java/compare/v1.1.0...v1.1.1)

**Merged pull requests:**

- NetworkConnectivity: ensure all accesses to listeners set are synchronised [\#469](https://github.com/ably/ably-java/pull/469) ([paddybyers](https://github.com/paddybyers))
- Truncated firebase ID \(registration token\) logging [\#466](https://github.com/ably/ably-java/pull/466) ([amsurana](https://github.com/amsurana))
- Auth RSA4b1 spec update: conditional token validity check [\#463](https://github.com/ably/ably-java/pull/463) ([paddybyers](https://github.com/paddybyers))
- Add some notes about log options [\#461](https://github.com/ably/ably-java/pull/461) ([paddybyers](https://github.com/paddybyers))
- Feature matrix linked from README [\#458](https://github.com/ably/ably-java/pull/458) ([Srushtika](https://github.com/Srushtika))

**Implemented enhancements:**

- Improve handling of clock skew [\#462](https://github.com/ably/ably-java/issues/462)

**Closed issues:**

- ConcurrentModificationException in 1.1 when running multiple library instances [\#468](https://github.com/ably/ably-java/issues/468)

## [v1.1.0](https://github.com/ably/ably-java/tree/v1.1.0)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.12...v1.1.0)

**Implemented enhancements:**

- Android push implementation [\#308](https://github.com/ably/ably-java/issues/308)

## [v1.0.12](https://github.com/ably/ably-java/tree/v1.0.12) (2019-02-13)
[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.11...v1.0.12)

**Merged pull requests:**

- Implemented feature Spec - TP4 [\#451](https://github.com/ably/ably-java/pull/451) ([amsurana](https://github.com/amsurana))
- Implemented Spec: TM3, Message.fromEncoded [\#446](https://github.com/ably/ably-java/pull/446) ([amsurana](https://github.com/amsurana))

## [v1.0.11](https://github.com/ably/ably-java/tree/v1.0.11)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.10...v1.0.11)

**Fixed bugs:**

- InternalError when attempting to create a reattach timer [\#452](https://github.com/ably/ably-java/issues/452)
- Realtime Channel: exceptions thrown when attempting attach do not result in the client listener being called [\#448](https://github.com/ably/ably-java/issues/448)

**Closed issues:**

- ConcurrentModificationException in 1.0 [\#321](https://github.com/ably/ably-java/issues/321)

**Merged pull requests:**

- Make the Channels collection a ConcurrentHashMap to permit mutation o… [\#454](https://github.com/ably/ably-java/pull/454) ([paddybyers](https://github.com/paddybyers))
- Wrap construction of Timer instances to handle exceptions … [\#453](https://github.com/ably/ably-java/pull/453) ([paddybyers](https://github.com/paddybyers))
- Attach exception handling [\#449](https://github.com/ably/ably-java/pull/449) ([paddybyers](https://github.com/paddybyers))

## [v1.0.10](https://github.com/ably/ably-java/tree/v1.0.10)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.9...v1.0.10)

**Implemented enhancements:**

- Add support for remembered REST fallback host [\#431](https://github.com/ably/ably-java/issues/431)
- Update idempotent REST according to spec [\#413](https://github.com/ably/ably-java/issues/413)

**Closed issues:**

- EventEmitter: mutations of `listeners` within a listener callback shouldn't crash [\#424](https://github.com/ably/ably-java/issues/424)

**Merged pull requests:**

- Implemented RTE6a specification [\#444](https://github.com/ably/ably-java/pull/444) ([amsurana](https://github.com/amsurana))
- Add .editorconfig [\#443](https://github.com/ably/ably-java/pull/443) ([paddybyers](https://github.com/paddybyers))
- Add support for bulk rest publish API [\#439](https://github.com/ably/ably-java/pull/439) ([paddybyers](https://github.com/paddybyers))

## [v1.0.9](https://github.com/ably/ably-java/tree/v1.0.9) (2018-12-11)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.8...v1.0.9)

**Closed issues:**

- Idempotent publishing is not enabled in the upcoming 1.1 release [\#438](https://github.com/ably/ably-java/issues/438)

**Merged pull requests:**

- Expose msgpack serialisers/deserialisers for Message, PresenceMessage [\#440](https://github.com/ably/ably-java/pull/440) ([paddybyers](https://github.com/paddybyers))
- RTL6c: implement transient realtime publishing [\#436](https://github.com/ably/ably-java/pull/436) ([paddybyers](https://github.com/paddybyers))
- Implement idempotent REST publishing [\#435](https://github.com/ably/ably-java/pull/435) ([paddybyers](https://github.com/paddybyers))
- Add support for ErrorInfo.href \(TI4/TI5\) [\#434](https://github.com/ably/ably-java/pull/434) ([paddybyers](https://github.com/paddybyers))
-  RSC15f: implement fallback affinity [\#433](https://github.com/ably/ably-java/pull/433) ([paddybyers](https://github.com/paddybyers))
- Pass the environment option into echoserver JWT requests [\#432](https://github.com/ably/ably-java/pull/432) ([paddybyers](https://github.com/paddybyers))

## [v1.0.8](https://github.com/ably/ably-java/tree/v1.0.8) (2018-11-03)
[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.7...v1.0.8)

**Implemented enhancements:**

- Ensure request method accepts UPDATE, PATCH & DELETE verbs [\#416](https://github.com/ably/ably-java/issues/416)

**Closed issues:**

- Error in release mode due to missing proguard exclusion [\#427](https://github.com/ably/ably-java/issues/427)
- Exception when failing to decode a message with unexpected payload type [\#425](https://github.com/ably/ably-java/issues/425)
- Recover resume not working [\#423](https://github.com/ably/ably-java/issues/423)

**Merged pull requests:**

- Proguard: exclude Auth inner classes [\#428](https://github.com/ably/ably-java/pull/428) ([paddybyers](https://github.com/paddybyers))
- Handle unexpected data type when decoding a message [\#426](https://github.com/ably/ably-java/pull/426) ([paddybyers](https://github.com/paddybyers))
- Update CHANGELOG generation instructions [\#421](https://github.com/ably/ably-java/pull/421) ([ORBAT](https://github.com/ORBAT))

## [v1.0.7](https://github.com/ably/ably-java/tree/v1.0.7) (2018-08-16)
[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.6...v1.0.7)

**Closed issues:**

- IllegalStateException scheduling transport activity timer [\#418](https://github.com/ably/ably-java/issues/418)

**Merged pull requests:**

- Handle exceptions in activity timer task, and when attempting to reschedule the timer [\#419](https://github.com/ably/ably-java/pull/419) ([paddybyers](https://github.com/paddybyers))

## [1.0.6](https://github.com/ably/ably-java/tree/1.0.6)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.5...1.0.6)

**Fixed bugs:**

- ably-java gets into a channel attach retry loop [\#410](https://github.com/ably/ably-java/issues/410)

**Merged pull requests:**

- RTL13b: ensure that detached+error responses form the server do not result in a busy loop of attach requests [\#411](https://github.com/ably/ably-java/pull/411) ([paddybyers](https://github.com/paddybyers))

## [1.0.5](https://github.com/ably/ably-java/tree/v1.0.5)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.4...v1.0.5)

**Fixed bugs:**

- Async HTTP thread pool issues [\#405](https://github.com/ably/ably-java/issues/405)

**Merged pull requests:**

- Fix problem with the asyncHttp threadpool [\#408](https://github.com/ably/ably-java/pull/408) ([paddybyers](https://github.com/paddybyers))
- Exit with a non zero code if any of the two suites \(realtime or rest\) fails [\#407](https://github.com/ably/ably-java/pull/407) ([funkyboy](https://github.com/funkyboy))
- Fix some flaky tests [\#406](https://github.com/ably/ably-java/pull/406) ([funkyboy](https://github.com/funkyboy))
- Fix cm thread exit [\#404](https://github.com/ably/ably-java/pull/404) ([paddybyers](https://github.com/paddybyers))
- Trigger Travis when a branch name ends with -ci [\#402](https://github.com/ably/ably-java/pull/402) ([funkyboy](https://github.com/funkyboy))
- Add fast forward description in release process [\#401](https://github.com/ably/ably-java/pull/401) ([funkyboy](https://github.com/funkyboy))
- Improve release description [\#400](https://github.com/ably/ably-java/pull/400) ([funkyboy](https://github.com/funkyboy))

## [1.0.4](https://github.com/ably/ably-java/tree/v1.0.4)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.3...v1.0.4)

**Implemented enhancements:**

- Add support for JWT [\#384](https://github.com/ably/ably-java/issues/384)
- Allow to specify a message id when publishing a message with REST [\#396](https://github.com/ably/ably-java/pull/396) ([paddybyers](https://github.com/paddybyers))

**Closed issues:**

- Maven devpendency failed [\#383](https://github.com/ably/ably-java/issues/383)

**Merged pull requests:**

- Add support for JWT [\#393](https://github.com/ably/ably-java/pull/393) ([funkyboy](https://github.com/funkyboy))

## [1.0.3](https://github.com/ably/ably-java/tree/1.0.3)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.2...1.0.3)

**Implemented enhancements:**

- Document supported platforms [\#363](https://github.com/ably/ably-java/issues/363)

**Fixed bugs:**

- Received messages have no event names [\#366](https://github.com/ably/ably-java/issues/366)
- Tests failing because of "no output in the last 10m" [\#330](https://github.com/ably/ably-java/issues/330)

**Merged pull requests:**

- Prevent Travis timeout on Android tests [\#391](https://github.com/ably/ably-java/pull/391) ([funkyboy](https://github.com/funkyboy))
- Add connection freshness check [\#390](https://github.com/ably/ably-java/pull/390) ([funkyboy](https://github.com/funkyboy))
- Add connectionStateTtl [\#389](https://github.com/ably/ably-java/pull/389) ([funkyboy](https://github.com/funkyboy))
- Fix invalid data test [\#385](https://github.com/ably/ably-java/pull/385) ([funkyboy](https://github.com/funkyboy))
- Update README with supported platforms [\#380](https://github.com/ably/ably-java/pull/380) ([funkyboy](https://github.com/funkyboy))
- Fix creation of ErrorInfo when authCallback is invalid [\#378](https://github.com/ably/ably-java/pull/378) ([funkyboy](https://github.com/funkyboy))
- Use exception instead of deprecation notice [\#376](https://github.com/ably/ably-java/pull/376) ([funkyboy](https://github.com/funkyboy))
- Add/fix Travis tests [\#372](https://github.com/ably/ably-java/pull/372) ([funkyboy](https://github.com/funkyboy))
- Fix android:assembleRelease [\#370](https://github.com/ably/ably-java/pull/370) ([paddybyers](https://github.com/paddybyers))

## [v1.0.2](https://github.com/ably/ably-java/tree/v1.0.2)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.1...v1.0.2)

**Implemented enhancements:**

- Implement AblyRealtime.connect\(\) [\#305](https://github.com/ably/ably-java/issues/305)
- Auth header included in HTTP requests [\#166](https://github.com/ably/ably-java/issues/166)
- autoConnect & useTokenAuth [\#27](https://github.com/ably/ably-java/issues/27)
- authParams & authMethod ClientOptions [\#25](https://github.com/ably/ably-java/issues/25)

**Fixed bugs:**

- When using token auth with client-side signing, renewing a token is broken [\#350](https://github.com/ably/ably-java/issues/350)
- Remove calls to System.xxx.println\(\) [\#217](https://github.com/ably/ably-java/issues/217)
- Race condition when lib is closed soon after being instantiated [\#319](https://github.com/ably/ably-java/issues/319)
- Crash inside a library [\#309](https://github.com/ably/ably-java/issues/309)
- Android System.out: \(ERROR\): io.ably.lib.transport.WebSocketTransport: No activity for 25000ms, closing connection [\#306](https://github.com/ably/ably-java/issues/306)
- RSC19 is not implemented according to the spec in 0.9 [\#278](https://github.com/ably/ably-java/issues/278)
- Invalid binary error message [\#247](https://github.com/ably/ably-java/issues/247)

**Merged pull requests:**

- Fix connectionmgr regressions [\#368](https://github.com/ably/ably-java/pull/368) ([paddybyers](https://github.com/paddybyers))
- Avoid depending on reference equality of interned strings and literals; this seems to fail sometimes on Android [\#367](https://github.com/ably/ably-java/pull/367) ([paddybyers](https://github.com/paddybyers))
- Update to latest gradle and tools plugins [\#362](https://github.com/ably/ably-java/pull/362) ([paddybyers](https://github.com/paddybyers))
- Auth.assertValidToken: always remove old token when force == true. [\#354](https://github.com/ably/ably-java/pull/354) ([tcard](https://github.com/tcard))
- Omit TTL in TokenRequest as JSON if unset. [\#353](https://github.com/ably/ably-java/pull/353) ([tcard](https://github.com/tcard))
- Add ability to generalize over a HTTP request being async or not. [\#347](https://github.com/ably/ably-java/pull/347) ([tcard](https://github.com/tcard))
- Implement and add test for AblyRealtime.connect\(\) [\#345](https://github.com/ably/ably-java/pull/345) ([paddybyers](https://github.com/paddybyers))
- Connectionmgr sync transport [\#344](https://github.com/ably/ably-java/pull/344) ([paddybyers](https://github.com/paddybyers))
- Fix issue where a close\(\) would not abort an existing in-progress connection [\#343](https://github.com/ably/ably-java/pull/343) ([paddybyers](https://github.com/paddybyers))
- New test RealtimeResumeTest.resume\_none [\#204](https://github.com/ably/ably-java/pull/204) ([trenouf](https://github.com/trenouf))

## [v1.0.1](https://github.com/ably/ably-java/tree/v1.0.1)

[Full Changelog](https://github.com/ably/ably-java/compare/v1.0.0...v1.0.1)

**Implemented enhancements:**

- Allow custom transportParams [\#327](https://github.com/ably/ably-java/issues/327)

**Fixed bugs:**

- authHeaders are being included in requests to non authUrl endpoints [\#331](https://github.com/ably/ably-java/issues/331)
- 1.0.0 sending v=0.9 [\#324](https://github.com/ably/ably-java/issues/324)
- 1.0 not automatically re-authing when token expires if initialized with key + clientId? [\#322](https://github.com/ably/ably-java/issues/322)

**Closed issues:**

- UTF-8 / ASCII detection issue in compile [\#334](https://github.com/ably/ably-java/issues/334)
- Allow authUrl to contain querystring params [\#328](https://github.com/ably/ably-java/issues/328)
- Dependency management for ably-android [\#316](https://github.com/ably/ably-java/issues/316)
- Exceptions thrown in client onMessage callbacks are silently swallowed [\#314](https://github.com/ably/ably-java/issues/314)

**Merged pull requests:**

- Spec RTC1f: implement support for ClientOptions.transportParams [\#342](https://github.com/ably/ably-java/pull/342) ([paddybyers](https://github.com/paddybyers))
- Implement spec for handling of queryParams in authURL [\#340](https://github.com/ably/ably-java/pull/340) ([paddybyers](https://github.com/paddybyers))
- Preemptive HTTP authentication [\#339](https://github.com/ably/ably-java/pull/339) ([paddybyers](https://github.com/paddybyers))
- Rest token renewal fix + tests [\#338](https://github.com/ably/ably-java/pull/338) ([paddybyers](https://github.com/paddybyers))
- Don't send authHeaders or authParams in calls to requestToken [\#337](https://github.com/ably/ably-java/pull/337) ([paddybyers](https://github.com/paddybyers))
- Replace StandardCharset.UTF-8 with Charset.forName\(“UTF-8”\) [\#333](https://github.com/ably/ably-java/pull/333) ([liuzhen2008](https://github.com/liuzhen2008))
- Crypto default 256 bit length like all other libraries [\#329](https://github.com/ably/ably-java/pull/329) ([mattheworiordan](https://github.com/mattheworiordan))
- Add log message if a client's listener throws an exception whilst handling a message [\#318](https://github.com/ably/ably-java/pull/318) ([paddybyers](https://github.com/paddybyers))

## [v1.0.0](https://github.com/ably/ably-java/tree/v1.0.0)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.11...v1.0.0)

**Implemented enhancements:**

This is the first release of the 1.0 client library specification, which contains many extensions
and fixes over the 0.8 specification.

For further details, see [a summary of the changes in the 1.0 API](https://github.com/ably/docs/issues/235).

## [v0.8.10](https://github.com/ably/ably-java/tree/v0.8.10)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.9...v0.8.10)

**Implemented enhancements:**

- Remove deprecated ProtocolMessage\#connectionKey [\#262](https://github.com/ably/ably-java/issues/262)
- Add Proguard support [\#223](https://github.com/ably/ably-java/issues/223)

**Closed issues:**

- Add proguard warning for org.msgpack.core.buffer.\*\* [\#279](https://github.com/ably/ably-java/issues/279)
- Add support for ConnectionDetails.connectionStateTtl [\#267](https://github.com/ably/ably-java/issues/267)
- Msgpack truncates data member [\#261](https://github.com/ably/ably-java/issues/261)

**Merged pull requests:**

- Remove proguard warnings for missing dependencies of msgpack library [\#281](https://github.com/ably/ably-java/pull/281) ([paddybyers](https://github.com/paddybyers))
- Update workaround for Android msgpack bugs [\#269](https://github.com/ably/ably-java/pull/269) ([paddybyers](https://github.com/paddybyers))

## [v0.8.9](https://github.com/ably/ably-java/tree/v0.8.9) (2017-01-01)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.8...v0.8.9)

**Fixed bugs:**

- Msgpack truncates data member [\#261](https://github.com/ably/ably-java/issues/261)

## [0.8.8](https://github.com/ably/ably-java/tree/v0.8.8)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.7...v0.8.8)

**Fixed bugs:**

- Fix bug causing infinite loop if exception thrown in Transport.send()
- Bump msgpack-core dependency to 0.8.11

## [v0.8.7](https://github.com/ably/ably-java/tree/v0.8.7)

[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.6...v0.8.7)

**Fixed bugs:**

- Transport state change events suppressed after loss of network [\#233](https://github.com/ably/ably-java/issues/233)

**Merged pull requests:**

- Fixed issue 233, made changes to allow ITransport mocking [\#236](https://github.com/ably/ably-java/pull/236) ([psolstice](https://github.com/psolstice))

## [v0.8.6](https://github.com/ably/ably-java/tree/v0.8.6)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.5...v0.8.6)

**Merged pull requests:**

- Relocated java-websocket library to bintray [\#229](https://github.com/ably/ably-java/pull/229) ([psolstice](https://github.com/psolstice))
- Made Auth.TokenRequest constructors public [\#228](https://github.com/ably/ably-java/pull/228) ([psolstice](https://github.com/psolstice))
- Fixed BuildConfig problems [\#227](https://github.com/ably/ably-java/pull/227) ([psolstice](https://github.com/psolstice))

## [v0.8.5](https://github.com/ably/ably-java/tree/v0.8.5)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.4...v0.8.5)

**Implemented enhancements:**

- Add reauth capability [\#129](https://github.com/ably/ably-java/issues/129)
- Remove unused HexDump file [\#81](https://github.com/ably/ably-java/issues/81)
- Final 0.8 spec updates [\#53](https://github.com/ably/ably-java/issues/53)
- HAS\_BACKLOG flag [\#6](https://github.com/ably/ably-java/issues/6)

**Fixed bugs:**

- ConnectionManager.failQueuedMessages() does not remove messages once the callback is called [\#222](https://github.com/ably/ably-java/issues/222)
- ConnectionManager.setSuspendTime() isn't called when a transport disconnects [\#220](https://github.com/ably/ably-java/issues/220)

**Closed issues:**

- "Trust anchor for certification path not found" exception on android [\#197](https://github.com/ably/ably-java/issues/197)
- travis jdk7 build gets buffer overflow fault [\#191](https://github.com/ably/ably-java/issues/191)
- never valid to provide both a restHost and environment value [\#187](https://github.com/ably/ably-java/issues/187)
- fallback problems [\#178](https://github.com/ably/ably-java/issues/178)
- Complete Android build work [\#148](https://github.com/ably/ably-java/issues/148)
- Add shutdown hook to close a connection when the VM exits [\#71](https://github.com/ably/ably-java/issues/71)
- AuthOptions constructor is not unambiguous [\#62](https://github.com/ably/ably-java/issues/62)

**Merged pull requests:**

- Messages are now removed from the queue after onError\(\) call [\#225](https://github.com/ably/ably-java/pull/225) ([psolstice](https://github.com/psolstice))
- Ensure that suspendTime is set on disconnection [\#221](https://github.com/ably/ably-java/pull/221) ([paddybyers](https://github.com/paddybyers))
- Added logging, clarified code [\#219](https://github.com/ably/ably-java/pull/219) ([psolstice](https://github.com/psolstice))
- RSL6b test, log errors [\#215](https://github.com/ably/ably-java/pull/215) ([psolstice](https://github.com/psolstice))
- Fixed travis crash when using OpenJDK 7 [\#213](https://github.com/ably/ably-java/pull/213) ([psolstice](https://github.com/psolstice))
- Fixed init\_default\_log\_output\_stream test on Windows [\#209](https://github.com/ably/ably-java/pull/209) ([psolstice](https://github.com/psolstice))
- Worked around RealtimeCryptoTest.set\_cipher\_params intermittent failure [\#203](https://github.com/ably/ably-java/pull/203) ([trenouf](https://github.com/trenouf))
- Fixed and re-enabled RestAppStatsTest [\#201](https://github.com/ably/ably-java/pull/201) ([trenouf](https://github.com/trenouf))
- Used hardcoded constant for protocol version [\#200](https://github.com/ably/ably-java/pull/200) ([trenouf](https://github.com/trenouf))
- Add note on proguard to readme [\#199](https://github.com/ably/ably-java/pull/199) ([SimonWoolf](https://github.com/SimonWoolf))
- useTokenAuth forces token authorization [\#196](https://github.com/ably/ably-java/pull/196) ([trenouf](https://github.com/trenouf))
- RSC7a: X-Ably-Version header [\#195](https://github.com/ably/ably-java/pull/195) ([trenouf](https://github.com/trenouf))
- HttpTest: fixed test to allow for fallback hosts with same IP [\#192](https://github.com/ably/ably-java/pull/192) ([trenouf](https://github.com/trenouf))
- Don't modify ClientOptions; Fixed tests to not set both host and environment [\#190](https://github.com/ably/ably-java/pull/190) ([trenouf](https://github.com/trenouf))
- TO3k2,TO3k3: disallow restHost/realtimeHost with environment [\#189](https://github.com/ably/ably-java/pull/189) ([trenouf](https://github.com/trenouf))
- Separate java and android builds [\#188](https://github.com/ably/ably-java/pull/188) ([trenouf](https://github.com/trenouf))
- Tests for host fallback behaviour on rest [\#184](https://github.com/ably/ably-java/pull/184) ([trenouf](https://github.com/trenouf))
- 0.8 authorisation changes [\#182](https://github.com/ably/ably-java/pull/182) ([trenouf](https://github.com/trenouf))
- Removed unused HexDump class [\#181](https://github.com/ably/ably-java/pull/181) ([trenouf](https://github.com/trenouf))
- issues/178: fix fallback [\#179](https://github.com/ably/ably-java/pull/179) ([trenouf](https://github.com/trenouf))
- custom fallback support [\#176](https://github.com/ably/ably-java/pull/176) ([trenouf](https://github.com/trenouf))
- RSC11 environment prefix [\#162](https://github.com/ably/ably-java/pull/162) ([VOstopolets](https://github.com/VOstopolets))
- Reauth capability [\#149](https://github.com/ably/ably-java/pull/149) ([VOstopolets](https://github.com/VOstopolets))
- RTN2g: Param "Lib" with header value \(RSC7b\) [\#147](https://github.com/ably/ably-java/pull/147) ([VOstopolets](https://github.com/VOstopolets))

## [v0.8.4](https://github.com/ably/ably-java/tree/v0.8.4) (2016-10-07)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.3...v0.8.4)

**Fixed bugs:**

- Connect whilst suspended does not appear to be connecting immediately [\#167](https://github.com/ably/ably-java/issues/167)
- Prep for 0.9 spec [\#145](https://github.com/ably/ably-java/issues/145)

**Closed issues:**

- RSC11: Environment option [\#160](https://github.com/ably/ably-java/issues/160)
- ably-java 0..8.3 release isn't available on jcenter [\#155](https://github.com/ably/ably-java/issues/155)

**Merged pull requests:**

- issues/170: Fixed message serial out of sync after recover [\#175](https://github.com/ably/ably-java/pull/175) ([trenouf](https://github.com/trenouf))
- heartbeat support [\#173](https://github.com/ably/ably-java/pull/173) ([trenouf](https://github.com/trenouf))
- tpr/issue167: 	Fixed explicit connect after connection has disconnected [\#172](https://github.com/ably/ably-java/pull/172) ([trenouf](https://github.com/trenouf))

## [v0.8.3](https://github.com/ably/ably-java/tree/v0.8.3) (2016-08-25)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.2...v0.8.3)

**Implemented enhancements:**

- README not complete [\#88](https://github.com/ably/ably-java/issues/88)
- authCallback must accept TokenDetails or token strings [\#34](https://github.com/ably/ably-java/issues/34)
- PaginatedResult\#isLast method missing [\#33](https://github.com/ably/ably-java/issues/33)

**Fixed bugs:**

- A post-suspend clean connection removes all channels instead of moving them to DETACHED [\#133](https://github.com/ably/ably-java/issues/133)
- Reauthentication on external URLs  [\#92](https://github.com/ably/ably-java/issues/92)
- Attach CompletionListener [\#84](https://github.com/ably/ably-java/issues/84)
- Implicit attach on Publish or Subscribe [\#45](https://github.com/ably/ably-java/issues/45)

**Closed issues:**

- Library doesn't seem to serialise Map objects properly [\#112](https://github.com/ably/ably-java/issues/112)
- Host ClientOptions [\#22](https://github.com/ably/ably-java/issues/22)

**Merged pull requests:**

- Detach on suspend [\#146](https://github.com/ably/ably-java/pull/146) ([paddybyers](https://github.com/paddybyers))
- Header X-Ably-Lib \(RSC7b\) [\#143](https://github.com/ably/ably-java/pull/143) ([VOstopolets](https://github.com/VOstopolets))
- Ensure interoperability with other libraries over JSON. [\#140](https://github.com/ably/ably-java/pull/140) ([tcard](https://github.com/tcard))
- Update README.md [\#138](https://github.com/ably/ably-java/pull/138) ([hauleth](https://github.com/hauleth))
- Ensure that messages with invalid data type are rejected. [\#137](https://github.com/ably/ably-java/pull/137) ([tcard](https://github.com/tcard))
- Add messages encoding fixtures test. [\#136](https://github.com/ably/ably-java/pull/136) ([tcard](https://github.com/tcard))
- Ensure graceful handling of DETACH and DISCONNECT. [\#131](https://github.com/ably/ably-java/pull/131) ([tcard](https://github.com/tcard))
- Proxy support [\#123](https://github.com/ably/ably-java/pull/123) ([paddybyers](https://github.com/paddybyers))
- RTN17 [\#122](https://github.com/ably/ably-java/pull/122) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Avoid stalled state from previous connection when reusing Realtime. [\#117](https://github.com/ably/ably-java/pull/117) ([tcard](https://github.com/tcard))
- AuthOptions javadoc enhancements and testcases [\#116](https://github.com/ably/ably-java/pull/116) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add implicit attach test cases for channel publish and subscribe [\#115](https://github.com/ably/ably-java/pull/115) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add isLast API to PaginatedResult [\#111](https://github.com/ably/ably-java/pull/111) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add CompletionListener to Channel's attach API [\#108](https://github.com/ably/ably-java/pull/108) ([gokhanbarisaker](https://github.com/gokhanbarisaker))

## [v0.8.2](https://github.com/ably/ably-java/tree/v0.8.2) (2016-03-14)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.1...v0.8.2)

**Implemented enhancements:**

- Lower case PresenceMessage.Action enum [\#90](https://github.com/ably/ably-java/issues/90)
- Switch arity of auth methods [\#44](https://github.com/ably/ably-java/issues/44)
- Realtime Presence and Channel untilAttach functionality is missing [\#36](https://github.com/ably/ably-java/issues/36)
- Proposal: errorReason instead of reason [\#30](https://github.com/ably/ably-java/issues/30)
- Presence subscribe with presence action [\#21](https://github.com/ably/ably-java/issues/21)
- Message publish overloaded without a listener [\#17](https://github.com/ably/ably-java/issues/17)
- Emit errors [\#16](https://github.com/ably/ably-java/issues/16)
- README to include code examples and follow common format [\#15](https://github.com/ably/ably-java/issues/15)

**Fixed bugs:**

- force is an attribute of AuthOptions, not an argument [\#103](https://github.com/ably/ably-java/issues/103)
- Lower case PresenceMessage.Action enum [\#90](https://github.com/ably/ably-java/issues/90)
- Presence enter, update, leave methods need to be overloaded [\#89](https://github.com/ably/ably-java/issues/89)
- Message constructor is inconsistent [\#87](https://github.com/ably/ably-java/issues/87)
- Channel state should be initialized not initialised for consistency [\#85](https://github.com/ably/ably-java/issues/85)
- Unsubscribe all and off all is missing [\#83](https://github.com/ably/ably-java/issues/83)
- Presence data assumed to be a string, Map not supported [\#75](https://github.com/ably/ably-java/issues/75)
- Host fallback for REST [\#54](https://github.com/ably/ably-java/issues/54)
- Switch arity of auth methods [\#44](https://github.com/ably/ably-java/issues/44)
- NullPointerException: Attempt to invoke interface method 'java.lang.String java.security.Principal.getName\(\)' on a null object reference [\#41](https://github.com/ably/ably-java/issues/41)
- Unable to deploy client lib in Android Studio project on OSX [\#39](https://github.com/ably/ably-java/issues/39)
- Realtime Presence and Channel untilAttach functionality is missing [\#36](https://github.com/ably/ably-java/issues/36)
- Java logLevel [\#26](https://github.com/ably/ably-java/issues/26)
- Timeout in test suite [\#24](https://github.com/ably/ably-java/issues/24)
- Presence subscribe with presence action [\#21](https://github.com/ably/ably-java/issues/21)

**Closed issues:**

- Fix missing JCE dependency on Travis [\#69](https://github.com/ably/ably-java/issues/69)
- Remove eclipse artifact [\#68](https://github.com/ably/ably-java/issues/68)
- Typo on Presence\#history javadoc [\#63](https://github.com/ably/ably-java/issues/63)
- Spec validation [\#23](https://github.com/ably/ably-java/issues/23)

**Merged pull requests:**

- 0.8.2 [\#119](https://github.com/ably/ably-java/pull/119) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Update changelog for v0.8.2 release [\#118](https://github.com/ably/ably-java/pull/118) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add information for listening specific connection state changes to readme [\#114](https://github.com/ably/ably-java/pull/114) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add null check [\#113](https://github.com/ably/ably-java/pull/113) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Move force argument to AuthOptions as a variable [\#107](https://github.com/ably/ably-java/pull/107) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Update MessageListener and PresenceListener interface [\#106](https://github.com/ably/ably-java/pull/106) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add until attach functionality to Presence & Channel [\#102](https://github.com/ably/ably-java/pull/102) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add unsubscribe all and off all [\#101](https://github.com/ably/ably-java/pull/101) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix channel state initialised spelling to initialized [\#100](https://github.com/ably/ably-java/pull/100) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix constructor signature [\#99](https://github.com/ably/ably-java/pull/99) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Overload publish APIs [\#98](https://github.com/ably/ably-java/pull/98) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add presence subscribe with presence action APIs [\#97](https://github.com/ably/ably-java/pull/97) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Update Auth\#requestToken signature for spec id RSA8e [\#96](https://github.com/ably/ably-java/pull/96) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Presence overloading [\#95](https://github.com/ably/ably-java/pull/95) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Convert enum variable naming to lowercase [\#94](https://github.com/ably/ably-java/pull/94) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix recover spec regex pattern [\#86](https://github.com/ably/ably-java/pull/86) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add httpMaxRetryCount && Simplify http fallback flow [\#80](https://github.com/ably/ably-java/pull/80) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Remove eclipse artifact [\#79](https://github.com/ably/ably-java/pull/79) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Upgrade gradle version [\#78](https://github.com/ably/ably-java/pull/78) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Upgrade dependencies [\#77](https://github.com/ably/ably-java/pull/77) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix leaking non-AblyExceptions on ConnectionManager\#onMessage callback [\#74](https://github.com/ably/ably-java/pull/74) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add custom test suite tasks to travis config [\#70](https://github.com/ably/ably-java/pull/70) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add maven package export script [\#67](https://github.com/ably/ably-java/pull/67) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Fix typo on Presence\#history javadoc [\#66](https://github.com/ably/ably-java/pull/66) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Readme enhancement [\#65](https://github.com/ably/ably-java/pull/65) ([gokhanbarisaker](https://github.com/gokhanbarisaker))
- Add Auth\#requestToken test cases [\#60](https://github.com/ably/ably-java/pull/60) ([gokhanbarisaker](https://github.com/gokhanbarisaker))

## [v0.8.1](https://github.com/ably/ably-java/tree/v0.8.1) (2016-01-01)
[Full Changelog](https://github.com/ably/ably-java/compare/v0.8.0...v0.8.1)

**Implemented enhancements:**

- Travis.CI support [\#4](https://github.com/ably/ably-java/issues/4)

**Fixed bugs:**

- Gradle build should be able to build library without Android SDK installed [\#46](https://github.com/ably/ably-java/issues/46)
- Token authentication "Request mac doesn't match" [\#40](https://github.com/ably/ably-java/issues/40)
- Re-enable temporarily disabled test [\#31](https://github.com/ably/ably-java/issues/31)
- Travis.CI support [\#4](https://github.com/ably/ably-java/issues/4)
- Key length case and ably common [\#35](https://github.com/ably/ably-java/pull/35) ([mattheworiordan](https://github.com/mattheworiordan))

**Closed issues:**

- Re-enable temporarily disabled test [\#32](https://github.com/ably/ably-java/issues/32)
- Additional encoding / decoding tests [\#1](https://github.com/ably/ably-java/issues/1)

**Merged pull requests:**

- Async http [\#59](https://github.com/ably/ably-java/pull/59) ([paddybyers](https://github.com/paddybyers))
- changes to run provided RestInit test case [\#58](https://github.com/ably/ably-java/pull/58) ([gorodechnyj](https://github.com/gorodechnyj))
- Allow connection manager thread to exit when closed or failed, and re… [\#50](https://github.com/ably/ably-java/pull/50) ([paddybyers](https://github.com/paddybyers))
- Add script for running tests in CI [\#49](https://github.com/ably/ably-java/pull/49) ([lmars](https://github.com/lmars))
- Publish implicit attach [\#48](https://github.com/ably/ably-java/pull/48) ([paddybyers](https://github.com/paddybyers))
- Make inclusion of android-test project conditional on whether or not … [\#47](https://github.com/ably/ably-java/pull/47) ([paddybyers](https://github.com/paddybyers))
- RSC1 and RSC2 - initialisation and default logging behaviour [\#43](https://github.com/ably/ably-java/pull/43) ([iliyakostadinov](https://github.com/iliyakostadinov))

## [v0.8.0](https://github.com/ably/ably-java/tree/v0.8.0) (2015-05-07)
**Implemented enhancements:**

- EventEmitter interface [\#11](https://github.com/ably/ably-java/issues/11)
- Change pagination API [\#10](https://github.com/ably/ably-java/issues/10)
- Stats types are out of date [\#7](https://github.com/ably/ably-java/issues/7)

**Fixed bugs:**

- CipherParams type [\#12](https://github.com/ably/ably-java/issues/12)
- Change pagination API [\#10](https://github.com/ably/ably-java/issues/10)
- Stats types are out of date [\#7](https://github.com/ably/ably-java/issues/7)

**Closed issues:**

- Builds are not failing with the correct exit code [\#5](https://github.com/ably/ably-java/issues/5)

**Merged pull requests:**

- Fix comment in connection failure test [\#3](https://github.com/ably/ably-java/pull/3) ([mattheworiordan](https://github.com/mattheworiordan))
- Allow recovery string that includes -1 serial [\#2](https://github.com/ably/ably-java/pull/2) ([mattheworiordan](https://github.com/mattheworiordan))



\* *This Change Log was automatically generated by [github_changelog_generator](https://github.com/skywinder/Github-Changelog-Generator)*
