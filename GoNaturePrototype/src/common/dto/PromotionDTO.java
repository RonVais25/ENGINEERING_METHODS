package common.dto;

import java.io.Serializable;

/**
 * Immutable data transfer object representing a {@code promotion} row — the unit
 * of the park-promotion approval workflow.
 *
 * <p>A {@code PARK_MANAGER} defines one for their own park (status
 * {@link ChangeStatus#PENDING}); a {@code DEPT_MANAGER} approves or rejects it.
 * Once {@link ChangeStatus#APPROVED} and active by the visit date, it grants an
 * extra additive discount on that park's visits (see
 * {@code server.control.PricingService}). This mirrors
 * {@link ParameterChangeRequestDTO}: {@code parkName} and {@code definedByName}
 * are <strong>not</strong> columns — {@link server.dao.PromotionDAO} joins them
 * in from {@code park} / {@code user} purely for display, so the approval screen
 * can show them without a second lookup.
 */
public class PromotionDTO implements Serializable {
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** Unique promotion identifier (primary key). */
    private int id;
    /** Identifier of the park the promotion applies to. */
    private int parkId;
    /** Display name of the target park, joined in for convenience. */
    private String parkName;
    /** Human-friendly name of the promotion (e.g. "Summer Special"). */
    private String name;
    /** Discount percentage off (0..100), applied additively when active and approved. */
    private int discountPercent;
    /** First date the promotion is active (inclusive), ISO {@code yyyy-MM-dd}. */
    private String startDate;
    /** Last date the promotion is active (inclusive), ISO {@code yyyy-MM-dd}. */
    private String endDate;
    /** Current lifecycle status of the promotion. */
    private ChangeStatus status;
    /** Park manager who defined it ({@code user.id}). */
    private int definedBy;
    /** Full name of the defining park manager, joined in from {@code user} for display. */
    private String definedByName;
    /** Department manager who decided it ({@code user.id}), or {@code null} while PENDING. */
    private Integer approvedBy;
    /** When the promotion was created (string form), or {@code null}. */
    private String createdAt;

    /**
     * Creates a fully populated promotion.
     *
     * @param id              unique promotion identifier
     * @param parkId          identifier of the target park
     * @param parkName        display name of the target park (joined in for convenience)
     * @param name            human-friendly name of the promotion
     * @param discountPercent discount percentage off (0..100)
     * @param startDate       first active date (inclusive), ISO {@code yyyy-MM-dd}
     * @param endDate         last active date (inclusive), ISO {@code yyyy-MM-dd}
     * @param status          current lifecycle status
     * @param definedBy       id of the park manager who defined it
     * @param definedByName   full name of the defining park manager (joined in for display)
     * @param approvedBy      id of the deciding department manager, or {@code null} while PENDING
     * @param createdAt       creation timestamp (string form), or {@code null}
     */
    public PromotionDTO(int id, int parkId, String parkName, String name, int discountPercent,
                        String startDate, String endDate, ChangeStatus status,
                        int definedBy, String definedByName, Integer approvedBy, String createdAt) {
        this.id = id;
        this.parkId = parkId;
        this.parkName = parkName;
        this.name = name;
        this.discountPercent = discountPercent;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.definedBy = definedBy;
        this.definedByName = definedByName;
        this.approvedBy = approvedBy;
        this.createdAt = createdAt;
    }

    /** {@return the unique promotion identifier} */
    public int getId() {
        return id;
    }

    /** {@return the identifier of the target park} */
    public int getParkId() {
        return parkId;
    }

    /** {@return the display name of the target park} */
    public String getParkName() {
        return parkName;
    }

    /** {@return the human-friendly name of the promotion} */
    public String getName() {
        return name;
    }

    /** {@return the discount percentage off (0..100)} */
    public int getDiscountPercent() {
        return discountPercent;
    }

    /** {@return the first active date (inclusive), ISO {@code yyyy-MM-dd}} */
    public String getStartDate() {
        return startDate;
    }

    /** {@return the last active date (inclusive), ISO {@code yyyy-MM-dd}} */
    public String getEndDate() {
        return endDate;
    }

    /** {@return the current lifecycle status of the promotion} */
    public ChangeStatus getStatus() {
        return status;
    }

    /** {@return the id of the park manager who defined the promotion} */
    public int getDefinedBy() {
        return definedBy;
    }

    /** {@return the full name of the defining park manager (joined in for display)} */
    public String getDefinedByName() {
        return definedByName;
    }

    /** {@return the id of the deciding department manager, or {@code null} while PENDING} */
    public Integer getApprovedBy() {
        return approvedBy;
    }

    /** {@return the creation timestamp (string form), or {@code null}} */
    public String getCreatedAt() {
        return createdAt;
    }
}
