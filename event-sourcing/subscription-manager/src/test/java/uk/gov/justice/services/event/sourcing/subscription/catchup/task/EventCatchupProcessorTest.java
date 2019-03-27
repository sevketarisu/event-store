package uk.gov.justice.services.event.sourcing.subscription.catchup.task;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.core.lifecycle.catchup.events.CatchupCompletedForSubscriptionEvent;
import uk.gov.justice.services.core.lifecycle.catchup.events.CatchupStartedForSubscriptionEvent;
import uk.gov.justice.services.event.sourcing.subscription.manager.EventSourceProvider;
import uk.gov.justice.services.event.sourcing.subscription.startup.manager.EventStreamConsumerManager;
import uk.gov.justice.services.eventsourcing.source.core.EventSource;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.subscription.ProcessedEventTrackingService;
import uk.gov.justice.subscription.domain.subscriptiondescriptor.Subscription;

import java.time.ZonedDateTime;
import java.util.List;

import javax.enterprise.event.Event;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventCatchupProcessorTest {

    @Mock
    private ProcessedEventTrackingService processedEventTrackingService;

    @Mock
    private EventSourceProvider eventSourceProvider;

    @Mock
    private EventStreamConsumerManager eventStreamConsumerManager;

    @Mock
    private Event<CatchupStartedForSubscriptionEvent> catchupStartedForSubscriptionEventFirer;

    @Mock
    private Event<CatchupCompletedForSubscriptionEvent> catchupCompletedForSubscriptionEventFirer;

    @Mock
    private UtcClock clock;

    private EventCatchupProcessor eventCatchupProcessor;

    @Before
    public void createClassUnderTest() {
        eventCatchupProcessor = new EventCatchupProcessor(
                processedEventTrackingService,
                eventSourceProvider,
                eventStreamConsumerManager,
                catchupStartedForSubscriptionEventFirer,
                catchupCompletedForSubscriptionEventFirer,
                clock
        );
    }

    @Test
    public void shouldFetchAllMissingEventsAndProcess() throws Exception {

        final String eventSourceName = "event source";
        final long eventNumber = 983745987L;

        final ZonedDateTime catchupStartedAt = new UtcClock().now();
        final ZonedDateTime catchupCompetedAt = catchupStartedAt.plusMinutes(23);

        final Subscription subscription = mock(Subscription.class);
        final EventSource eventSource = mock(EventSource.class);

        final JsonEnvelope event_1 = mock(JsonEnvelope.class);
        final JsonEnvelope event_2 = mock(JsonEnvelope.class);
        final JsonEnvelope event_3 = mock(JsonEnvelope.class);

        final List<JsonEnvelope> events = asList(event_1, event_2, event_3);

        when(subscription.getEventSourceName()).thenReturn(eventSourceName);
        when(clock.now()).thenReturn(catchupStartedAt, catchupCompetedAt);
        when(eventSourceProvider.getEventSource(eventSourceName)).thenReturn(eventSource);
        when(processedEventTrackingService.getLatestProcessedEventNumber(eventSourceName)).thenReturn(eventNumber);
        when(eventSource.findEventsSince(eventNumber)).thenReturn(events.stream());
        when(eventStreamConsumerManager.add(event_1)).thenReturn(1);
        when(eventStreamConsumerManager.add(event_2)).thenReturn(1);
        when(eventStreamConsumerManager.add(event_3)).thenReturn(1);

        eventCatchupProcessor.performEventCatchup(subscription);

        final InOrder inOrder = inOrder(
                catchupStartedForSubscriptionEventFirer,
                eventStreamConsumerManager,
                catchupCompletedForSubscriptionEventFirer);

        inOrder.verify(catchupStartedForSubscriptionEventFirer).fire(new CatchupStartedForSubscriptionEvent(
                eventSourceName,
                catchupStartedAt));

        inOrder.verify(eventStreamConsumerManager).add(event_1);
        inOrder.verify(eventStreamConsumerManager).add(event_2);
        inOrder.verify(eventStreamConsumerManager).add(event_3);
        inOrder.verify(eventStreamConsumerManager).waitForCompletion();

        inOrder.verify(catchupCompletedForSubscriptionEventFirer).fire(new CatchupCompletedForSubscriptionEvent(
                eventSourceName,
                events.size(),
                catchupCompetedAt));
    }
}