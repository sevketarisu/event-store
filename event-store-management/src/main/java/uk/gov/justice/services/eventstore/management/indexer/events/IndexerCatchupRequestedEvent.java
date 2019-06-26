package uk.gov.justice.services.eventstore.management.indexer.events;

import uk.gov.justice.services.jmx.command.SystemCommand;

import java.time.ZonedDateTime;
import java.util.Objects;

public class IndexerCatchupRequestedEvent {

    private final SystemCommand target;
    private final ZonedDateTime catchupRequestedAt;

    public IndexerCatchupRequestedEvent(final SystemCommand target,final ZonedDateTime catchupRequestedAt) {
        this.target = target;
        this.catchupRequestedAt = catchupRequestedAt;
    }

    public SystemCommand getTarget() {
        return target;
    }

    public ZonedDateTime getCatchupRequestedAt() {
        return catchupRequestedAt;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof IndexerCatchupRequestedEvent)) return false;
        final IndexerCatchupRequestedEvent that = (IndexerCatchupRequestedEvent) o;
        return Objects.equals(target, that.target) &&
                Objects.equals(catchupRequestedAt, that.catchupRequestedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, catchupRequestedAt);
    }

    @Override
    public String toString() {
        return "IndexerCatchupRequestedEvent{" +
                "target=" + target +
                ", catchupRequestedAt=" + catchupRequestedAt +
                '}';
    }
}