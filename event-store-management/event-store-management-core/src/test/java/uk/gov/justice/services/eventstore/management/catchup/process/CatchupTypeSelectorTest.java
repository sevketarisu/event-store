package uk.gov.justice.services.eventstore.management.catchup.process;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import uk.gov.justice.services.eventstore.management.commands.EventCatchupCommand;
import uk.gov.justice.services.eventstore.management.commands.IndexerCatchupCommand;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CatchupTypeSelectorTest {

    @InjectMocks
    private CatchupTypeSelector catchupTypeSelector;

    @Test
    public void shouldReturnTrueIfTheComponentIsEventListenerAndTheTypeIsEventCatchup() throws Exception {

        final EventCatchupCommand eventCatchupCommand = new EventCatchupCommand();

        assertThat(catchupTypeSelector.isEventCatchup("MY_EVENT_LISTENER", eventCatchupCommand), is(true));
        assertThat(catchupTypeSelector.isEventCatchup("MY_EVENT_INDEXER", eventCatchupCommand), is(false));
        assertThat(catchupTypeSelector.isEventCatchup("MY_EVENT_PROCESSOR", eventCatchupCommand), is(false));
    }

    @Test
    public void shouldReturnTrueIfTheComponentIsEventIndexerAndTheTypeIsIndexCatchup() throws Exception {

        final IndexerCatchupCommand indexerCatchupCommand = new IndexerCatchupCommand();

        assertThat(catchupTypeSelector.isIndexerCatchup("MY_EVENT_INDEXER", indexerCatchupCommand), is(true));
        assertThat(catchupTypeSelector.isIndexerCatchup("MY_EVENT_LISTENER", indexerCatchupCommand), is(false));
        assertThat(catchupTypeSelector.isIndexerCatchup("MY_EVENT_PROCESSOR", indexerCatchupCommand), is(false));
    }
}
