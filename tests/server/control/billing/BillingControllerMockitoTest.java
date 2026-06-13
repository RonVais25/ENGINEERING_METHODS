package server.control.billing;

import org.junit.jupiter.api.Test;
import testutil.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingControllerMockitoTest {

    @Test
    void generatePaymentBillShouldDelegateToPricingController() {
        PricingController pricingController = mock(PricingController.class);
        when(pricingController.calculatePrice("INDIVIDUAL_PREBOOKED", 50.0, 3)).thenReturn(127.5);

        BillingController controller = new BillingController();
        ReflectionTestUtils.setField(controller, "pricingController", pricingController);

        double result = controller.generatePaymentBill("INDIVIDUAL_PREBOOKED", 50.0, 3);

        assertEquals(127.5, result, 0.0001);
        verify(pricingController).calculatePrice("INDIVIDUAL_PREBOOKED", 50.0, 3);
    }
}
