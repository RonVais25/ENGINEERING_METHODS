package server.pattern.pricingStrategy;

public interface PricingStrategy {
    double calculatePrice(double basePrice, int visitorsCount);
    String getPricingType();
}
