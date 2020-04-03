package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.brewery.model.BeerOrderDto;

import java.util.UUID;

public interface BeerOrderManager {

    BeerOrder newBeerOrder (BeerOrder beerOrder);

    void processValidation(UUID orderId, boolean isValid);

    void allocateOrderPassed(BeerOrderDto beerOrderDto);

    void allocateOrderFailed(BeerOrderDto beerOrderDto);

    void pendingInventory(BeerOrderDto beerOrderDto);

    void cancelOrder(UUID id);

    void beerOrderPickedUp(UUID id);
}
