package server.pattern.pricingStrategy;

public class WalkInPricingStrategy implements PricingStrategy {
    public double calculatePrice(double basePrice, int visitorsCount) {
        double discount = 0.0;
        return basePrice * visitorsCount * (1.0 - discount);
    }
    public String getPricingType() { return "WALK_IN"; }
}
