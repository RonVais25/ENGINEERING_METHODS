package server.pattern.pricingStrategy;

public class SubscriberPricingStrategy implements PricingStrategy {
    public double calculatePrice(double basePrice, int visitorsCount) {
        double discount = 0.25;
        return basePrice * visitorsCount * (1.0 - discount);
    }
    public String getPricingType() { return "SUBSCRIBER"; }
}
