package server.control.pricing;

public class ReservedPricingStrategy implements PricingStrategy {
    @Override
    public double calculatePrice(int visitorsCount, double fullPrice) {
        return visitorsCount * fullPrice * 0.85; // 15% pre-booked discount
    }
}