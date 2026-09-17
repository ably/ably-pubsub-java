package io.ably.pubsub.http;

import io.ably.pubsub.util.JsonUtils;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class DeviceDetailsTest {

    @Test
    public void shouldIgnoreUnrelatedRecipientFields() {
        DeviceDetails details = DeviceDetails.fromJsonObject(JsonUtils.object()
                .add("id", "testDeviceDetails")
                .add("platform", "ios")
                .add("formFactor", "phone")
                .add("metadata", JsonUtils.object())
                .add("push", JsonUtils.object()
                        .add("recipient", JsonUtils.object()
                                .add("transportType", "apns")
                                .add("deviceToken", "foo")
                                .add("apnsDeviceTokens", JsonUtils.object().add("default", "foo"))))
                .toJson());

        DeviceDetails otherDetails = DeviceDetails.fromJsonObject(JsonUtils.object()
                .add("id", "testDeviceDetails")
                .add("platform", "ios")
                .add("formFactor", "phone")
                .add("metadata", JsonUtils.object())
                .add("push", JsonUtils.object()
                        .add("recipient", JsonUtils.object()
                                .add("transportType", "apns")
                                .add("deviceToken", "foo")))
                .toJson());

        assertTrue("Should ignore `apnsDeviceTokens` field",  details.equals(otherDetails));
    }
}
