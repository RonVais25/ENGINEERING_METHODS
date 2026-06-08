package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a {@code park} row.
 *
 * <p>Created server-side by {@link server.dao.ParkDAO} and serialized across the
 * socket to the client inside a {@link ServerResponse} — e.g. to populate the
 * booking park dropdown ({@link RequestType#LIST_PARKS}) or to show a park
 * manager their park's current parameters ({@link RequestType#GET_PARK}).
 */
public class ParkDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique park identifier (primary key). */
    private int id;
    /** Display name of the park. */
    private String name;
    /** Maximum visitor capacity. */
    private int maxCapacity;
    /** Reserved capacity buffer subtracted from {@code maxCapacity} when booking. */
    private int gapSize;
    /** Default visit length, in minutes. */
    private int defaultStayMinutes;
    /** Identifier of the park's manager ({@code user.id}), or {@code null} if none. */
    private Integer managerId;

    /**
     * Creates a fully populated park.
     *
     * @param id                 unique park identifier
     * @param name               display name of the park
     * @param maxCapacity        maximum visitor capacity
     * @param gapSize            reserved capacity buffer
     * @param defaultStayMinutes default visit length in minutes
     * @param managerId          identifier of the park manager, or {@code null} if none
     */
    public ParkDTO(int id, String name, int maxCapacity, int gapSize,
                   int defaultStayMinutes, Integer managerId) {
        this.id = id;
        this.name = name;
        this.maxCapacity = maxCapacity;
        this.gapSize = gapSize;
        this.defaultStayMinutes = defaultStayMinutes;
        this.managerId = managerId;
    }

    /** @return the unique park identifier */
    public int getId() {
        return id;
    }

    /** @return the display name of the park */
    public String getName() {
        return name;
    }

    /** @return the maximum visitor capacity */
    public int getMaxCapacity() {
        return maxCapacity;
    }

    /** @return the reserved capacity buffer */
    public int getGapSize() {
        return gapSize;
    }

    /** @return the default visit length in minutes */
    public int getDefaultStayMinutes() {
        return defaultStayMinutes;
    }

    /** @return the identifier of the park manager, or {@code null} if none */
    public Integer getManagerId() {
        return managerId;
    }
}
