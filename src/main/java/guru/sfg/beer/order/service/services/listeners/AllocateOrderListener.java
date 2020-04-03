package guru.sfg.beer.order.service.services.listeners;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.brewery.model.events.AllocateOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class AllocateOrderListener {

    private final BeerOrderManager beerOrderManager;

    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_RESPONSE_QUEUE)
    public void listen(AllocateOrderResponse response){

        if (!response.getAllocationError() && !response.getPendingInventory()){
            //Allocation success
            beerOrderManager.allocateOrderPassed(response.getBeerOrderDto());
        }
        else if (!response.getAllocationError() && response.getPendingInventory()){
            //Pending Inventory
            beerOrderManager.pendingInventory(response.getBeerOrderDto());
        }
        else if (response.getAllocationError()){
            //Allocation exception
            beerOrderManager.allocateOrderFailed(response.getBeerOrderDto());
        }

    }
}
