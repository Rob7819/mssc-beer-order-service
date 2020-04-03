package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventsEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.sm.OrderStateChangeInterceptor;
import guru.sfg.brewery.model.BeerOrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

//KNIGHT OF LOGIC Web Solutions
//Solutions adapted by RB

@Slf4j
@RequiredArgsConstructor
@Service
public class BeerOrderManagerImpl implements BeerOrderManager {

    private final StateMachineFactory<BeerOrderStatusEnum, BeerOrderEventsEnum> stateMachineFactory;
    private final BeerOrderRepository beerOrderRepository;
    public static final String ORDER_ID_HEADER = "order_id";
    private final OrderStateChangeInterceptor orderStateChangeInterceptor;

    @Transactional
    @Override
    public void cancelOrder(UUID id) {

        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(id);

        beerOrderOptional.ifPresentOrElse(beerOrder -> {

            sendBeerOrderEvent(beerOrder, BeerOrderEventsEnum.CANCEL_ORDER);

        }, () -> log.error("Order Manager(cancelOrder): Beer order not found with id: " + id));

    }

    @Transactional
    @Override
    public void beerOrderPickedUp(UUID id) {

        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(id);

        beerOrderOptional.ifPresentOrElse(beerOrder -> {

            sendBeerOrderEvent(beerOrder, BeerOrderEventsEnum.ORDER_PICKED_UP);

        }, () -> log.error("Order Manager(beerOrderPickedUp): Beer order not found with id: " + id));

    }

    @Override
    @Transactional
    public BeerOrder newBeerOrder(BeerOrder beerOrder) {

        beerOrder.setId(null);
        beerOrder.setOrderStatus(BeerOrderStatusEnum.NEW);

        BeerOrder savedBeerOrder = beerOrderRepository.saveAndFlush(beerOrder);

        sendBeerOrderEvent(savedBeerOrder, BeerOrderEventsEnum.VALIDATE_ORDER);
        return savedBeerOrder;
    }

    @Override
    @Transactional
    public void processValidation(UUID beerOrderId, boolean isValid){

        log.debug("Order Manager(processValidation) Result for beerOrderId: " + beerOrderId + " Valid? " + isValid);

        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderId);

        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            if(isValid){
                sendBeerOrderEvent(beerOrder, BeerOrderEventsEnum.VALIDATION_PASSED);

                awaitForStatus(beerOrderId, BeerOrderStatusEnum.VALIDATED);

                BeerOrder validatedOrder = beerOrderRepository.findById(beerOrderId).get();

                sendBeerOrderEvent(validatedOrder, BeerOrderEventsEnum.ALLOCATE_ORDER);

            } else {
                sendBeerOrderEvent(beerOrder, BeerOrderEventsEnum.VALIDATION_FAILED);
            }
        }, () -> log.error("Order Manager(processValidation): Order Not Found. Id: " + beerOrderId));


    }

    @Transactional
    @Override
    public void allocateOrderPassed(BeerOrderDto beerOrderDto) {
        Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            sendBeerOrderEvent(beerOrder, BeerOrderEventsEnum.ALLOCATION_SUCCESS);
            awaitForStatus(beerOrder.getId(), BeerOrderStatusEnum.ALLOCATED);
            updateAllocation(beerOrderDto);
        }, () -> log.error("Order Manager(allocateOrderPassed): Order Id Not Found. Id: " + beerOrderDto.getId()));


    }

    @Transactional
    @Override
    public void allocateOrderFailed(BeerOrderDto beerOrderDto) {
        Optional <BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        beerOrderOptional.ifPresentOrElse(beerOrder -> {
            sendBeerOrderEvent(beerOrder, BeerOrderEventsEnum.ALLOCATION_FAILED);
        }, () -> log.error("Order Manager(allocateOrderFailed): Order Not Found. Id: " + beerOrderDto.getId()));


    }

    @Transactional
    @Override
    public void pendingInventory(BeerOrderDto beerOrderDto) {
        Optional <BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        beerOrderOptional.ifPresentOrElse(beerOrder -> {

            sendBeerOrderEvent(beerOrder, BeerOrderEventsEnum.ALLOCATION_NO_INVENTORY);
            awaitForStatus(beerOrder.getId(), BeerOrderStatusEnum.PENDING_INVENTORY);
            updateAllocation(beerOrderDto);

        }, () -> log.error("Order Manager(pendingInventory): Order Id Not Found. Id: " + beerOrderDto.getId()));

    }

    private void updateAllocation(BeerOrderDto beerOrderDto){

        Optional<BeerOrder> allocatedOrderOptional = beerOrderRepository.findById(beerOrderDto.getId());

        allocatedOrderOptional.ifPresentOrElse(allocatedOrder -> {

            allocatedOrder.getBeerOrderLines().forEach(beerOrderLine -> {
                beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
                    if(beerOrderLine.getId().equals(beerOrderLineDto.getId())){
                        beerOrderLine.setQuantityAllocated(beerOrderLineDto.getQuantityAllocated());
                    }
                });
            });

            //JT had passed second method parameter BeerOrder to saveAndFlush???
            beerOrderRepository.saveAndFlush(allocatedOrder);

        }, () -> log.error("Order Manager(updateAllocation): Order Not Found. Id: " + beerOrderDto.getId()));



    }

    private void sendBeerOrderEvent(BeerOrder beerOrder, BeerOrderEventsEnum eventEnum){

        StateMachine<BeerOrderStatusEnum, BeerOrderEventsEnum> sm = build(beerOrder);

        Message msg = MessageBuilder.withPayload(eventEnum)
                .setHeader(ORDER_ID_HEADER, beerOrder.getId().toString())//change here
                .build();

        sm.sendEvent(msg);

    }

    private StateMachine<BeerOrderStatusEnum, BeerOrderEventsEnum> build(BeerOrder beerOrder){

        StateMachine<BeerOrderStatusEnum, BeerOrderEventsEnum> sm = stateMachineFactory.getStateMachine(beerOrder.getId());

        sm.stop();

        sm.getStateMachineAccessor()
                .doWithAllRegions(sma -> {
                    sma.addStateMachineInterceptor(orderStateChangeInterceptor);
                    sma.resetStateMachine(new DefaultStateMachineContext<>(beerOrder.getOrderStatus(),null,null,null));

                });

        sm.start();

        return sm;

    }

    private void awaitForStatus(UUID beerOrderId, BeerOrderStatusEnum statusEnum) {

        AtomicBoolean found = new AtomicBoolean(false);
        AtomicInteger loopCount = new AtomicInteger(0);

        while (!found.get()) {
            if (loopCount.incrementAndGet() > 10) {
                found.set(true);
                log.debug("awaitForStatus: Loop Retries exceeded");
            }

            beerOrderRepository.findById(beerOrderId).ifPresentOrElse(beerOrder -> {
                if (beerOrder.getOrderStatus().equals(statusEnum)) {
                    found.set(true);
                    log.debug("awaitForStatus: Order Found");
                } else {
                    log.debug("awaitForStatus: Order Status Not Equal. Expected: " + statusEnum.name() + " Found: " + beerOrder.getOrderStatus().name());
                }
            }, () -> {
                log.debug("awaitForStatus: Order Id Not Found");
            });

            if (!found.get()) {
                try {
                    log.debug("awaitForStatus: Sleeping for retry");
                    Thread.sleep(100);
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
    }

}
