package io.ably.lib.test.android;

import io.ably.lib.push.ActivationStateMachine.CalledActivate;
import io.ably.lib.push.ActivationStateMachine.CalledDeactivate;
import io.ably.lib.push.ActivationStateMachine.Deregistered;
import io.ably.lib.push.ActivationStateMachine.Event;
import io.ably.lib.push.ActivationStateMachine.GotDeviceRegistration;
import io.ably.lib.push.ActivationStateMachine.GotPushDeviceDetails;
import io.ably.lib.push.ActivationStateMachine.RegistrationSynced;
import io.ably.lib.push.ActivationStateMachine.GettingDeviceRegistrationFailed;
import io.ably.lib.push.ActivationStateMachine.GettingPushDeviceDetailsFailed;
import io.ably.lib.push.ActivationStateMachine.SyncRegistrationFailed;
import io.ably.lib.push.ActivationStateMachine.DeregistrationFailed;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class EventTest {

    @Test
    public void events_subclasses_correctly_constructed_by_name() throws ClassNotFoundException, InstantiationException {

        CalledActivate calledActivateEvent = new CalledActivate();
        Event calledActivateReconstructed = Event.constructEventByName(calledActivateEvent.getPersistedName());
        assertEquals(calledActivateEvent.getClass(), calledActivateReconstructed.getClass());

        CalledDeactivate calledDeactivateEvent = new CalledDeactivate();
        Event calledDeactivateReconstructed = Event.constructEventByName(calledDeactivateEvent.getPersistedName());
        assertEquals(calledDeactivateEvent.getClass(), calledDeactivateReconstructed.getClass());

        GotPushDeviceDetails gotPushDeviceDetailsEvent = new GotPushDeviceDetails();
        Event gotPushDeviceDetailsReconstructed = Event.constructEventByName(gotPushDeviceDetailsEvent.getPersistedName());
        assertEquals(gotPushDeviceDetailsEvent.getClass(), gotPushDeviceDetailsReconstructed.getClass());

        RegistrationSynced registrationSyncedEvent = new RegistrationSynced();
        Event registrationSyncedReconstructed = Event.constructEventByName(registrationSyncedEvent.getPersistedName());
        assertEquals(registrationSyncedEvent.getClass(), registrationSyncedReconstructed.getClass());

        Deregistered DeregisteredEvent = new Deregistered();
        Event DeregisteredReconstructed = Event.constructEventByName(DeregisteredEvent.getPersistedName());
        assertEquals(DeregisteredEvent.getClass(), DeregisteredReconstructed.getClass());
    }

    @Test
    public void events_with_constructor_parameter_do_not_have_persisted_name() {
        assertNull(new GotDeviceRegistration(null, null).getPersistedName());
        assertNull(new GettingDeviceRegistrationFailed(null).getPersistedName());
        assertNull(new GettingPushDeviceDetailsFailed(null).getPersistedName());
        assertNull(new SyncRegistrationFailed(null).getPersistedName());
        assertNull(new DeregistrationFailed(null).getPersistedName());
    }

    @Test
    public void unknown_events_cannot_be_constructed_by_name() {
        assertNull(Event.constructEventByName("notDefinedName"));
    }
}
