package server.control;

import common.model.PaymentBill;
import common.model.Reservation;
import server.control.pricing.PricingService;
import server.dao.InMemoryDatabase;

public class BillingController {
    private PricingService pricingService = new PricingService();

    public PaymentBill generateBillForReservation(String reservationId) {
        Reservation reservation = InMemoryDatabase.reservations.get(reservationId);

        if (reservation == null) {
            System.out.println("Reservation not found. Cannot generate bill.");
            return null;
        }

        double amount = pricingService.calculateReservedPrice(reservation.getNumberOfVisitors());

        PaymentBill bill = new PaymentBill(reservationId, amount);
        InMemoryDatabase.bills.add(bill);

        System.out.println("Payment bill generated.");
        System.out.println(bill);

        return bill;
    }

    public PaymentBill generateWalkInBill(int visitorsCount) {
        double amount = pricingService.calculateWalkInPrice(visitorsCount);

        PaymentBill bill = new PaymentBill("WALK-IN", amount);
        InMemoryDatabase.bills.add(bill);

        System.out.println("Walk-in payment bill generated.");
        System.out.println(bill);

        return bill;
    }
}