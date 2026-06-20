package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object describing the live occupancy of a single park.
 *
 * <p>Created server-side by {@link server.control.VisitController} in response to
 * {@link RequestType#CURRENT_OCCUPANCY} and serialized across the socket inside a
 * {@link ServerResponse}. Unlike available-capacity calculation (which
 * reasons about <em>booked</em> party sizes for a future date), the figures here
 * come from <em>physical</em> presence: {@code current} is the sum of the
 * headcounts of all visits that are still open (not yet exited).
 *
 * <p>The bookable ceiling is {@code maxCapacity - gapSize}; {@code available} is
 * that ceiling minus {@code current}, and may go negative if the park is somehow
 * over its gap-adjusted limit.
 */
public class OccupancyDTO implements Serializable {
/** Serialization identifier for this class. */
    private static final long serialVersionUID = 1L;

    /** Identifier of the park this snapshot describes. */
    private int parkId;
    /** People currently inside the park (sum of open-visit headcounts). */
    private int current;
    /** The park's maximum physical capacity. */
    private int maxCapacity;
    /** The reserved capacity buffer held back from {@code maxCapacity}. */
    private int gapSize;
    /** Remaining headroom: {@code maxCapacity - gapSize - current} (may be negative). */
    private int available;

    /**
     * Creates a fully populated occupancy snapshot.
     *
     * @param parkId      identifier of the park
     * @param current     people currently inside (sum of open-visit headcounts)
     * @param maxCapacity the park's maximum physical capacity
     * @param gapSize     the reserved capacity buffer
     * @param available   remaining headroom ({@code maxCapacity - gapSize - current})
     */
    public OccupancyDTO(int parkId, int current, int maxCapacity, int gapSize, int available) {
        this.parkId = parkId;
        this.current = current;
        this.maxCapacity = maxCapacity;
        this.gapSize = gapSize;
        this.available = available;
    }

    /** @return the identifier of the park */
    public int getParkId() {
        return parkId;
    }

    /** @return the number of people currently inside the park */
    public int getCurrent() {
        return current;
    }

    /** @return the park's maximum physical capacity */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /** @return the reserved capacity buffer */
    public int getGapSize() {
        return gapSize;
    }

    /** @return the remaining headroom (may be negative if over the gap-adjusted limit) */
    public int getAvailable() {
        return available;
    }
}
