package server.control.pricing;

public class PricingService {
    private static final double FULL_PRICE = 100.0;

    public double calculateReservedPrice(int visitorsCount) {
        PricingStrategy strategy = new ReservedPricingStrategy();
        return strategy.calculatePrice(visitorsCount, FULL_PRICE);
    }

    public double calculateWalkInPrice(int visitorsCount) {
        PricingStrategy strategy = new WalkInPricingStrategy();
        return strategy.calculatePrice(visitorsCount, FULL_PRICE);
    }
}