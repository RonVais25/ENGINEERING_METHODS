package server.pattern.pricingStrategy;

public class PromotionPricingStrategy implements PricingStrategy {
    public double calculatePrice(double basePrice, int visitorsCount) {
        double discount = 0.10;
        return basePrice * visitorsCount * (1.0 - discount);
    }
    public String getPricingType() { return "PROMOTION"; }
}
