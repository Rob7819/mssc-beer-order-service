package guru.sfg.beer.order.service.sm;

import guru.sfg.beer.order.service.domain.BeerOrderEventsEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
public class BeerOrderStateMachineConfig extends StateMachineConfigurerAdapter<BeerOrderStatusEnum, BeerOrderEventsEnum>
{

    private final Action<BeerOrderStatusEnum, BeerOrderEventsEnum> validateOrderAction;
    private final Action<BeerOrderStatusEnum, BeerOrderEventsEnum> allocateOrderAction;
    private final Action<BeerOrderStatusEnum, BeerOrderEventsEnum> validationFailedAction;
    private final Action<BeerOrderStatusEnum, BeerOrderEventsEnum> allocationFailedAction;
    private final Action<BeerOrderStatusEnum, BeerOrderEventsEnum> deallocateOrderAction;

    @Override
    public void configure(StateMachineStateConfigurer<BeerOrderStatusEnum, BeerOrderEventsEnum> states) throws Exception {
        states.withStates()
                .initial(BeerOrderStatusEnum.NEW)
                .states(EnumSet.allOf(BeerOrderStatusEnum.class))
                .end(BeerOrderStatusEnum.PICKED_UP)
                .end(BeerOrderStatusEnum.DELIVERED)
                .end(BeerOrderStatusEnum.DELIVERY_EXCEPTION)
                .end(BeerOrderStatusEnum.VALIDATION_EXCEPTION)
                .end(BeerOrderStatusEnum.ALLOCATION_EXCEPTION)
                .end(BeerOrderStatusEnum.CANCELLED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<BeerOrderStatusEnum, BeerOrderEventsEnum> transitions) throws Exception {
            transitions.withExternal()
                    .source(BeerOrderStatusEnum.NEW)
                    .target(BeerOrderStatusEnum.VALIDATION_PENDING)
                    .event(BeerOrderEventsEnum.VALIDATE_ORDER).action(validateOrderAction)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.VALIDATION_PENDING)
                    .target(BeerOrderStatusEnum.VALIDATED)
                    .event(BeerOrderEventsEnum.VALIDATION_PASSED)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.VALIDATION_PENDING)
                    .target(BeerOrderStatusEnum.VALIDATION_EXCEPTION)
                    .event(BeerOrderEventsEnum.VALIDATION_FAILED)
                    .action(validationFailedAction)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.VALIDATED)
                    .target(BeerOrderStatusEnum.ALLOCATION_PENDING)
                    .event(BeerOrderEventsEnum.ALLOCATE_ORDER).action(allocateOrderAction)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.ALLOCATION_PENDING)
                    .target(BeerOrderStatusEnum.ALLOCATED)
                    .event(BeerOrderEventsEnum.ALLOCATION_SUCCESS)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.ALLOCATION_PENDING)
                    .target(BeerOrderStatusEnum.PENDING_INVENTORY)
                    .event(BeerOrderEventsEnum.ALLOCATION_NO_INVENTORY)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.ALLOCATION_PENDING)
                    .target(BeerOrderStatusEnum.ALLOCATION_EXCEPTION)
                    .event(BeerOrderEventsEnum.ALLOCATION_FAILED)
                    .action(allocationFailedAction)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.ALLOCATED)
                    .target(BeerOrderStatusEnum.PICKED_UP)
                    .event(BeerOrderEventsEnum.ORDER_PICKED_UP)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.VALIDATION_PENDING)
                    .target(BeerOrderStatusEnum.CANCELLED)
                    .event(BeerOrderEventsEnum.CANCEL_ORDER)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.VALIDATED)
                    .target(BeerOrderStatusEnum.CANCELLED)
                    .event(BeerOrderEventsEnum.CANCEL_ORDER)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.ALLOCATION_PENDING)
                    .target(BeerOrderStatusEnum.CANCELLED)
                    .event(BeerOrderEventsEnum.CANCEL_ORDER)
                .and().withExternal()
                    .source(BeerOrderStatusEnum.ALLOCATED)
                    .target(BeerOrderStatusEnum.CANCELLED)
                    .event(BeerOrderEventsEnum.CANCEL_ORDER)
                    .action(deallocateOrderAction);
    }
}
