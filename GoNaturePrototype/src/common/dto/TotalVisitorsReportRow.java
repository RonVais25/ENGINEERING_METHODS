package common.dto;

import java.io.Serializable;

/**
 * One grouped row of the Total-Visitors-by-Type report: the total number of
 * <em>visitors</em> in a single category over the report's date range, for one park.
 *
 * <p>Categories collapse the three booking {@link VisitType}s into the two the
 * park manager reports on: {@code "GROUPS"} for {@link VisitType#GROUP} visits and
 * {@code "INDIVIDUALS"} for {@link VisitType#INDIVIDUAL} and {@link VisitType#FAMILY}
 * together.
 *
 * <p><strong>The metric is a headcount, not a visit count.</strong> The spec asks
 * for the <em>number of visitors</em>, so {@code visitorCount} is the sum of each
 * visit's {@code headcount} (party size), not {@code COUNT(*)} of the visits — one
 * group visit of 30 people contributes 30, not 1. A category with no visits in the
 * window is zero-filled ({@code visitorCount = 0}).
 *
 * <p>Built server-side by {@link server.dao.ReportDAO#totalVisitorsByType} and
 * shipped inside a {@link TotalVisitorsReportDTO}; immutable and {@link Serializable}.
 */
public class TotalVisitorsReportRow implements Serializable {
    /** Serialization-format version identifier. */
    private static final long serialVersionUID = 1L;

    /** Visitor category: {@code "INDIVIDUALS"} or {@code "GROUPS"}. */
    private final String category;
    /** Number of visitors (sum of headcounts) in this category over the range. */
    private final int visitorCount;

    /**
     * Creates a fully populated report row.
     *
     * @param category     visitor category ({@code "INDIVIDUALS"} or {@code "GROUPS"})
     * @param visitorCount number of visitors (sum of headcounts) in this category
     */
    public TotalVisitorsReportRow(String category, int visitorCount) {
        this.category = category;
        this.visitorCount = visitorCount;
    }

    /** {@return the visitor category ({@code "INDIVIDUALS"} or {@code "GROUPS"})} */
    public String getCategory() {
        return category;
    }

    /** {@return the number of visitors (sum of headcounts) in this category} */
    public int getVisitorCount() {
        return visitorCount;
    }
}
