package common.dto;

import java.io.Serializable;

/**
 * One grouped row of the Visits-by-Type report: the aggregate figures for a
 * single visitor category over the report's date range and park filter.
 *
 * <p>Categories collapse the three booking {@link VisitType}s into the two the
 * department manager reports on: {@code "GROUPS"} for {@link VisitType#GROUP}
 * visits and {@code "INDIVIDUALS"} for {@link VisitType#INDIVIDUAL} and
 * {@link VisitType#FAMILY} together.
 *
 * <p>{@code visitCount} counts every visit in the category (open or closed), but
 * {@code avgStayMinutes} averages only the visits that have been checked out — an
 * open visit (no {@code exited_at}) contributes to the count yet has no stay
 * length to average. A category with no visits at all is zero-filled
 * ({@code visitCount = 0}, {@code avgStayMinutes = 0}).
 *
 * <p>Built server-side by {@link server.dao.ReportDAO#visitsByType} and shipped
 * inside a {@link VisitsReportDTO}; immutable and {@link Serializable}.
 */
public class VisitsReportRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Visitor category: {@code "INDIVIDUALS"} or {@code "GROUPS"}. */
    private final String category;
    /** Number of visits in this category (open and closed). */
    private final int visitCount;
    /** Average stay in minutes over the <em>closed</em> visits (0 if none closed). */
    private final double avgStayMinutes;

    /**
     * Creates a fully populated report row.
     *
     * @param category       visitor category ({@code "INDIVIDUALS"} or {@code "GROUPS"})
     * @param visitCount     number of visits in this category (open and closed)
     * @param avgStayMinutes average stay in minutes over the closed visits (0 if none closed)
     */
    public VisitsReportRow(String category, int visitCount, double avgStayMinutes) {
        this.category = category;
        this.visitCount = visitCount;
        this.avgStayMinutes = avgStayMinutes;
    }

    /** @return the visitor category ({@code "INDIVIDUALS"} or {@code "GROUPS"}) */
    public String getCategory() {
        return category;
    }

    /** @return the number of visits in this category (open and closed) */
    public int getVisitCount() {
        return visitCount;
    }

    /** @return the average stay in minutes over the closed visits (0 if none closed) */
    public double getAvgStayMinutes() {
        return avgStayMinutes;
    }
}
