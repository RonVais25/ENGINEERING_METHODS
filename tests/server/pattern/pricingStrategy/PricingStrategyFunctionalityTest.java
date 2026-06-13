package server.pattern.pricingStrategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PricingStrategyFunctionalityTest {

    @Test
    void regularVisitorShouldApplyFifteenPercentDiscount() {
        PricingStrategy strategy = new RegularVisitorPricingStrategy();

        assertEquals(85.0, strategy.calculatePrice(50.0, 2), 0.0001);
        assertEquals("INDIVIDUAL_PREBOOKED", strategy.getPricingType());
    }

    @Test
    void groupReservationShouldApplyTwentyFivePercentDiscount() {
        PricingStrategy strategy = new GroupReservationPricingStrategy();

        assertEquals(150.0, strategy.calculatePrice(50.0, 4), 0.0001);
        assertEquals("GROUP_PREBOOKED", strategy.getPricingType());
    }

    @Test
    void subscriberShouldApplyTwentyFivePercentDiscount() {
        PricingStrategy strategy = new SubscriberPricingStrategy();

        assertEquals(112.5, strategy.calculatePrice(50.0, 3), 0.0001);
        assertEquals("SUBSCRIBER", strategy.getPricingType());
    }

    @Test
    void walkInShouldReturnFullPrice() {
        PricingStrategy strategy = new WalkInPricingStrategy();

        assertEquals(150.0, strategy.calculatePrice(50.0, 3), 0.0001);
        assertEquals("WALK_IN", strategy.getPricingType());
    }

    @Test
    void promotionShouldApplyTenPercentDiscount() {
        PricingStrategy strategy = new PromotionPricingStrategy();

        assertEquals(90.0, strategy.calculatePrice(50.0, 2), 0.0001);
        assertEquals("PROMOTION", strategy.getPricingType());
    }
}
