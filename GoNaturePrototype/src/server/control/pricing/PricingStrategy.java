package server.control.pricing;

public interface PricingStrategy {
    double calculatePrice(int visitorsCount, double fullPrice);
}