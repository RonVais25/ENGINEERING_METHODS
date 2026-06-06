package server.control.billing;

import server.pattern.pricingStrategy.*;

public class PricingController {
    public PricingStrategy selectStrategy(String visitorType) {
        if ("SUBSCRIBER".equals(visitorType)) return new SubscriberPricingStrategy();
        if ("GROUP_PREBOOKED".equals(visitorType)) return new GroupReservationPricingStrategy();
        if ("WALK_IN".equals(visitorType)) return new WalkInPricingStrategy();
        return new RegularVisitorPricingStrategy();
    }

    public double calculatePrice(String visitorType, double basePrice, int visitorsCount) {
        return selectStrategy(visitorType).calculatePrice(basePrice, visitorsCount);
    }
}
