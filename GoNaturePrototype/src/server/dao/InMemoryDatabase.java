package server.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.model.Park;
import common.model.PaymentBill;
import common.model.Reservation;
import common.model.VisitRecord;

public class InMemoryDatabase {
    public static Map<String, Park> parks = new HashMap<>();
    public static Map<String, Reservation> reservations = new HashMap<>();
    public static Map<String, VisitRecord> visits = new HashMap<>();
    public static List<PaymentBill> bills = new ArrayList<>();

    static {
        parks.put("Carmel Forest", new Park("Carmel Forest", 100));
        parks.put("Ein Gedi", new Park("Ein Gedi", 50));
        parks.put("Banias", new Park("Banias", 70));
    }
}