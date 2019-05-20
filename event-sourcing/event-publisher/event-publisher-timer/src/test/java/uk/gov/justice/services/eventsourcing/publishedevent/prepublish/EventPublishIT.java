package uk.gov.justice.services.eventsourcing.publishedevent.prepublish;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static uk.gov.justice.services.core.postgres.OpenEjbConfigurationBuilder.createOpenEjbConfigurationBuilder;

import uk.gov.justice.services.cdi.LoggerProducer;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.common.util.Clock;
import uk.gov.justice.services.common.util.UtcClock;
import uk.gov.justice.services.eventsource.DefaultEventDestinationResolver;
import uk.gov.justice.services.eventsourcing.publishedevent.jdbc.EventDeQueuer;
import uk.gov.justice.services.eventsourcing.publishedevent.jdbc.PrePublishRepository;
import uk.gov.justice.services.eventsourcing.publishedevent.jdbc.PublishedEventQueries;
import uk.gov.justice.services.eventsourcing.publishedevent.jdbc.PublishedEventRepository;
import uk.gov.justice.services.eventsourcing.publishedevent.prepublish.helpers.DummyEventPublisher;
import uk.gov.justice.services.eventsourcing.publishedevent.prepublish.helpers.EventFactory;
import uk.gov.justice.services.eventsourcing.publishedevent.prepublish.helpers.TestEventInserter;
import uk.gov.justice.services.eventsourcing.publishedevent.prepublish.helpers.TestEventStreamInserter;
import uk.gov.justice.services.eventsourcing.publishedevent.prepublish.helpers.TestGlobalValueProducer;
import uk.gov.justice.services.eventsourcing.publishedevent.publish.PublishedEventDeQueuerAndPublisher;
import uk.gov.justice.services.eventsourcing.publishedevent.publishing.PublisherTimerBean;
import uk.gov.justice.services.eventsourcing.publishedevent.publishing.PublisherTimerConfig;
import uk.gov.justice.services.eventsourcing.publisher.jms.EventDestinationResolver;
import uk.gov.justice.services.eventsourcing.publisher.jms.EventPublisher;
import uk.gov.justice.services.eventsourcing.repository.jdbc.EventInsertionStrategyProducer;
import uk.gov.justice.services.eventsourcing.repository.jdbc.PostgresSQLEventLogInsertionStrategy;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.Event;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.EventConverter;
import uk.gov.justice.services.eventsourcing.repository.jdbc.event.EventJdbcRepository;
import uk.gov.justice.services.eventsourcing.source.core.EventStoreDataSourceProvider;
import uk.gov.justice.services.eventsourcing.util.jee.timer.StopWatchFactory;
import uk.gov.justice.services.eventsourcing.util.jee.timer.TimerCanceler;
import uk.gov.justice.services.eventsourcing.util.jee.timer.TimerConfigFactory;
import uk.gov.justice.services.eventsourcing.util.jee.timer.TimerServiceManager;
import uk.gov.justice.services.jdbc.persistence.JdbcDataSourceProvider;
import uk.gov.justice.services.jdbc.persistence.JdbcResultSetStreamer;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapper;
import uk.gov.justice.services.jdbc.persistence.PreparedStatementWrapperFactory;
import uk.gov.justice.services.messaging.DefaultJsonObjectEnvelopeConverter;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.JsonObjectEnvelopeConverter;
import uk.gov.justice.services.messaging.jms.DefaultEnvelopeConverter;
import uk.gov.justice.services.messaging.jms.EnvelopeConverter;
import uk.gov.justice.services.messaging.jms.JmsEnvelopeSender;
import uk.gov.justice.services.messaging.logging.DefaultTraceLogger;
import uk.gov.justice.services.test.utils.core.eventsource.EventStoreInitializer;
import uk.gov.justice.services.test.utils.core.messaging.Poller;
import uk.gov.justice.services.test.utils.messaging.jms.DummyJmsEnvelopeSender;
import uk.gov.justice.services.test.utils.persistence.OpenEjbEventStoreDataSourceProvider;
import uk.gov.justice.services.yaml.YamlParser;
import uk.gov.justice.services.yaml.YamlSchemaLoader;
import uk.gov.justice.subscription.EventSourcesParser;
import uk.gov.justice.subscription.ParserProducer;
import uk.gov.justice.subscription.SubscriptionHelper;
import uk.gov.justice.subscription.YamlFileFinder;
import uk.gov.justice.subscription.domain.eventsource.DefaultEventSourceDefinitionFactory;
import uk.gov.justice.subscription.registry.EventSourceDefinitionRegistry;
import uk.gov.justice.subscription.registry.EventSourceDefinitionRegistryProducer;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.openejb.jee.WebApp;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Application;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Configuration;
import org.apache.openejb.testing.Module;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;

@RunWith(ApplicationComposer.class)
public class EventPublishIT {

    @Inject
    private DummyEventPublisher dummyEventPublisher;

    @Inject
    private EventStoreDataSourceProvider eventStoreDataSourceProvider;

    private final EventFactory eventFactory = new EventFactory();
    private final TestEventInserter testEventInserter = new TestEventInserter();
    private final TestEventStreamInserter testEventStreamInserter = new TestEventStreamInserter();
    private final Poller poller = new Poller(10, 3_000L);
    private final EventStoreInitializer eventStoreInitializer = new EventStoreInitializer();
    private final Clock clock = new UtcClock();

    @Before
    public void initializeDatabase() throws Exception {
        eventStoreInitializer.initializeEventStore(eventStoreDataSourceProvider.getDefaultDataSource());
    }

    @Module
    @Classes(cdi = true, value = {
            PublishedEventDeQueuerAndPublisher.class,
            EventDeQueuer.class,
            EventPublisher.class,
            DummyEventPublisher.class,
            EventConverter.class,
            JmsEnvelopeSender.class,
            DummyJmsEnvelopeSender.class,
            Logger.class,
            EventDestinationResolver.class,
            StringToJsonObjectConverter.class,
            JdbcDataSourceProvider.class,
            OpenEjbEventStoreDataSourceProvider.class,
            EventSourceDefinitionRegistry.class,
            EventDestinationResolver.class,
            DefaultEventDestinationResolver.class,
            JsonObjectEnvelopeConverter.class,
            EventSourceDefinitionRegistryProducer.class,
            EnvelopeConverter.class,
            DefaultEnvelopeConverter.class,
            LoggerProducer.class,
            JsonObjectEnvelopeConverter.class,
            DefaultJsonObjectEnvelopeConverter.class,
            DefaultTraceLogger.class,
            ObjectMapperProducer.class,
            EventSourcesParser.class,
            ParserProducer.class,
            YamlFileFinder.class,
            YamlSchemaLoader.class,
            YamlParser.class,
            TimerConfigFactory.class,
            TestGlobalValueProducer.class,
            DefaultEventSourceDefinitionFactory.class,
            TimerServiceManager.class,
            TimerCanceler.class,
            PrePublishProcessor.class,
            EventPrePublisher.class,
            MetadataEventNumberUpdater.class,
            PrePublishRepository.class,
            UtcClock.class,
            PublishedEventFactory.class,
            PublisherTimerBean.class,
            PublisherTimerConfig.class,
            PrePublishTimerBean.class,
            PrePublishTimerConfig.class,
            SubscriptionHelper.class,
            PublishedEventQueries.class,
            EventJdbcRepository.class,
            JdbcResultSetStreamer.class,
            PreparedStatementWrapperFactory.class,
            PreparedStatementWrapper.class,
            PostgresSQLEventLogInsertionStrategy.class,
            EventInsertionStrategyProducer.class,
            PublishedEventRepository.class,
            PrePublishRepository.class,
            PublishedEventQueries.class,
            StopWatchFactory.class
    })
    public WebApp war() {
        return new WebApp()
                .contextRoot("EventPublishIT")
                .addServlet("App", Application.class.getName());
    }

    @Configuration
    public Properties configuration() {
        return createOpenEjbConfigurationBuilder()
                .addInitialContext()
                .addPostgresqlEventStore()
                .build();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void shouldPublishEventsInTheEventLogTable() throws Exception {
        final UUID streamId = randomUUID();
        final Event event_1 = eventFactory.createEvent(streamId, randomUUID(), "event_1", 1l, 1l);
        final Event event_2 = eventFactory.createEvent(streamId, randomUUID(), "event_2", 2l, 1l);
        final Event event_3 = eventFactory.createEvent(streamId, randomUUID(), "event_3", 3l, 1l);

        testEventInserter.insertIntoEventLog(event_1);
        testEventInserter.insertIntoEventLog(event_2);
        testEventInserter.insertIntoEventLog(event_3);

        testEventStreamInserter.insertIntoEventStream(streamId, 1, true, clock.now());

        final Optional<List<JsonEnvelope>> jsonEnvelopeOptional = poller.pollUntilFound(() -> {
            final List<JsonEnvelope> jsonEnvelopes = dummyEventPublisher.getJsonEnvelopes();
            if (jsonEnvelopes.size() > 2) {
                return of(jsonEnvelopes);
            }

            return empty();
        });

        if (jsonEnvelopeOptional.isPresent()) {
            final List<JsonEnvelope> envelopes = jsonEnvelopeOptional.get();

            assertThat(envelopes.size(), is(3));

            assertThat(envelopes.get(0).metadata().name(), is("event_1"));
            assertThat(envelopes.get(1).metadata().name(), is("event_2"));
            assertThat(envelopes.get(2).metadata().name(), is("event_3"));

            final String envelopeJson_1 = envelopes.get(0).toDebugStringPrettyPrint();
            final String envelopeJson_2 = envelopes.get(1).toDebugStringPrettyPrint();
            final String envelopeJson_3 = envelopes.get(2).toDebugStringPrettyPrint();

            with(envelopeJson_1)
                    .assertThat("$._metadata.event.previousEventNumber", is(0))
                    .assertThat("$._metadata.event.eventNumber", is(1))
            ;
            with(envelopeJson_2)
                    .assertThat("$._metadata.event.previousEventNumber", is(1))
                    .assertThat("$._metadata.event.eventNumber", is(2))
            ;
            with(envelopeJson_3)
                    .assertThat("$._metadata.event.previousEventNumber", is(2))
                    .assertThat("$._metadata.event.eventNumber", is(3))
            ;
        } else {
            fail();
        }
    }
}
