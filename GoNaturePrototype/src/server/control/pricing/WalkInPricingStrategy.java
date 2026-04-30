package server.control.pricing;

public class WalkInPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(int visitorsCount, double fullPrice) {
        return visitorsCount * fullPrice; // full price
    }
}