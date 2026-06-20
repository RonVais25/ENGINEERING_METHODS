package server.control;

import common.dto.VisitType;

/**
 * Computes the price of a reservation, in the same unit as
 * {@code reservation.price_cents}. Stateless and pure: the result depends only
 * on the arguments, so a single shared instance is safe across all client
 * threads.
 *
 * <p>The five pricing tiers (the booking flow only reaches the two pre-ordered
 * ones; the casual tiers are for walk-in Entry/Exit handled in a later session):
 * <ol>
 *   <li><b>Individual/family, pre-ordered</b> — 15% off.</li>
 *   <li><b>Individual/family, casual</b> — full price.</li>
 *   <li><b>Group, pre-ordered</b> — 25% off, plus a further 12% off when paid in
 *       advance; the guide is not charged (one person drops off the paying
 *       count).</li>
 *   <li><b>Group, casual</b> — 10% off; the guide pays like everyone else.</li>
 *   <li><b>Member</b> — an extra 10% off that stacks additively on top of
 *       whichever tier above applies.</li>
 * </ol>
 *
 * <p>The discounts are expressed as plain {@code if}/{@code else} arithmetic
 * rather than a table or stream pipeline so each tier can be read off the code
 * line by line.
 */
public class PricingService {

    /**
     * Full, undiscounted price charged per paying visitor, in cents.
     *
     * <p>Hard-coded for now; this could later be promoted to a
     * department-manager-editable configuration value without touching the tier
     * logic below.
     */
    private static final int FULL_PRICE_PER_VISITOR = 8000;

    /**
     * Computes the total price for a party.
     *
     * <p>Two independent adjustments are combined:
     * <ul>
     *   <li><b>Paying count</b> — everyone in the party pays, except that a
     *       pre-ordered group does not pay for its guide, so one person is
     *       dropped (floored at 0 for the degenerate guide-only party).</li>
     *   <li><b>Discount</b> — the per-tier fraction described on the class, with
     *       the member bonus added on top.</li>
     * </ul>
     *
     * @param visitType  the visit category; INDIVIDUAL and FAMILY share the same
     *                   rates, GROUP is identified through {@code isGroup}
     * @param isGroup    whether this booking is an organised (guide-led) group
     * @param partySize  number of people in the party (guide included)
     * @param preOrdered whether the visit was booked ahead (vs. a casual walk-in)
     * @param prePaid    whether the booking is paid in advance (only deepens the
     *                   discount for a pre-ordered group)
     * @param isMember   whether the owning visitor holds a subscription
     * @return the total price in cents (same unit as {@code price_cents})
     */
    public int calculate(VisitType visitType, boolean isGroup, int partySize,
                         boolean preOrdered, boolean prePaid, boolean isMember) {
        return calculate(visitType, isGroup, partySize, preOrdered, prePaid, isMember, 0);
    }

    /**
     * Computes the total price and also applies an approved park special-sale
     * discount percentage. The final discount is capped at 90% so a stack of
     * discounts never creates a negative or zero-fee visit by mistake.
     *
     * @param visitType  the visit category
     * @param isGroup    whether this booking is an organised group
     * @param partySize  number of people in the party
     * @param preOrdered whether the visit was booked ahead
     * @param prePaid    whether the booking is paid in advance
     * @param isMember   whether the owner is a subscriber
     * @param specialDiscountPercent approved park special-sale discount percent
     * @return the total price in cents
     */
    public int calculate(VisitType visitType, boolean isGroup, int partySize,
                         boolean preOrdered, boolean prePaid, boolean isMember,
                         int specialDiscountPercent) {

        // Guide-free perk: a pre-ordered group doesn't pay for its guide, so one
        // person drops off the paying count (never below zero). Everyone else
        // pays for the whole party.
        int payingCount;
        if (isGroup && preOrdered) {
            payingCount = partySize - 1;
            if (payingCount < 0) {
                payingCount = 0;
            }
        } else {
            payingCount = partySize;
        }

        // Tier discount as a fraction of the full price.
        double discount;
        if (isGroup) {
            if (preOrdered) {
                discount = 0.25;                 // group, pre-ordered
                if (prePaid) {
                    discount = discount + 0.12;  // extra cut when paid in advance
                }
            } else {
                discount = 0.10;                 // group, casual
            }
        } else {
            if (preOrdered) {
                discount = 0.15;                 // individual/family, pre-ordered
            } else {
                discount = 0.0;                  // individual/family, casual: full price
            }
        }

        // Member bonus stacks additively on top of the applicable tier.
        if (isMember) {
            discount = discount + 0.10;
        }

        if (specialDiscountPercent > 0) {
            discount = discount + (specialDiscountPercent / 100.0);
        }
        if (discount > 0.90) {
            discount = 0.90;
        }

        long net = Math.round(payingCount * FULL_PRICE_PER_VISITOR * (1 - discount));
        return (int) net;
    }
}
