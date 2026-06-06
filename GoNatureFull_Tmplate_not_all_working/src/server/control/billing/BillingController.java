package server.control.billing;

public class BillingController {
    private PricingController pricingController = new PricingController();

    public double generatePaymentBill(String visitorType, double basePrice, int visitorsCount) {
        return pricingController.calculatePrice(visitorType, basePrice, visitorsCount);
    }
}
