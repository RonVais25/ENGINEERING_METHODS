package common.model;

public class PaymentBill {
    private String reservationId;
    private double amount;

    public PaymentBill(String reservationId, double amount) {
        this.reservationId = reservationId;
        this.amount = amount;
    }

    public String getReservationId() {
        return reservationId;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public String toString() {
        return "Bill for reservation " + reservationId + ": " + amount + " ₪";
    }
}