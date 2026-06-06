package server.pattern.pricingStrategy;

public class RegularVisitorPricingStrategy implements PricingStrategy {
    public double calculatePrice(double basePrice, int visitorsCount) {
        double discount = 0.15;
        return basePrice * visitorsCount * (1.0 - discount);
    }
    public String getPricingType() { return "INDIVIDUAL_PREBOOKED"; }
}
