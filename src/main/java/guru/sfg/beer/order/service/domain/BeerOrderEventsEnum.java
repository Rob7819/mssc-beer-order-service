package guru.sfg.beer.order.service.domain;

public enum BeerOrderEventsEnum {

    VALIDATE_ORDER, VALIDATION_PASSED, VALIDATION_FAILED,
    ALLOCATION_SUCCESS, ALLOCATION_NO_INVENTORY, ALLOCATION_FAILED,
    ALLOCATE_ORDER, ORDER_PICKED_UP, CANCEL_ORDER

}