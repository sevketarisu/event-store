package uk.gov.justice.services.eventstore.management.catchup.process;

import uk.gov.justice.services.eventstore.management.commands.CatchupCommand;
import uk.gov.justice.services.eventstore.management.events.catchup.SubscriptionCatchupDetails;

import java.util.Objects;
import java.util.UUID;

public class CatchupSubscriptionContext {

    private final UUID commandId;
    private final String componentName;
    private final SubscriptionCatchupDetails subscriptionCatchupDefinition;
    private final CatchupCommand catchupCommand;

    public CatchupSubscriptionContext(
            final UUID commandId,
            final String componentName,
            final SubscriptionCatchupDetails subscriptionCatchupDefinition,
            final CatchupCommand catchupCommand) {
        this.commandId = commandId;
        this.componentName = componentName;
        this.subscriptionCatchupDefinition = subscriptionCatchupDefinition;
        this.catchupCommand = catchupCommand;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public String getComponentName() {
        return componentName;
    }

    public SubscriptionCatchupDetails getSubscriptionCatchupDefinition() {
        return subscriptionCatchupDefinition;
    }

    public CatchupCommand getCatchupCommand() {
        return catchupCommand;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof CatchupSubscriptionContext)) return false;
        final CatchupSubscriptionContext that = (CatchupSubscriptionContext) o;
        return Objects.equals(commandId, that.commandId) &&
                Objects.equals(componentName, that.componentName) &&
                Objects.equals(subscriptionCatchupDefinition, that.subscriptionCatchupDefinition) &&
                Objects.equals(catchupCommand, that.catchupCommand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandId, componentName, subscriptionCatchupDefinition, catchupCommand);
    }

    @Override
    public String toString() {
        return "CatchupSubscriptionContext{" +
                "commandId=" + commandId +
                ", componentName='" + componentName + '\'' +
                ", catchupFor=" + subscriptionCatchupDefinition +
                ", catchupCommand=" + catchupCommand +
                '}';
    }
}
