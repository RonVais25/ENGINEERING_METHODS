package server.control.reservation;

import server.dao.ParkDAO;

public class AvailabilityController {
    public boolean checkAvailability(int parkId, int visitorsCount) {
        return new ParkDAO().hasCapacity(parkId, visitorsCount);
    }
}
