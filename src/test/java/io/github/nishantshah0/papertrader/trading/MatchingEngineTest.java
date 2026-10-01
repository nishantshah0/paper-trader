package io.github.nishantshah0.papertrader.trading;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MatchingEngineTest {
    @Test void eachNewEngineLoadsDurableOrdersAndContinuesAfterAnIndividualFailure() {
        var repository=mock(OrderRepository.class); var service=mock(OrderService.class);
        var first=Order.limit(1L,"AAPL",OrderSide.BUY,1,new BigDecimal("90"));
        var second=Order.limit(2L,"MSFT",OrderSide.BUY,1,new BigDecimal("90"));
        ReflectionTestUtils.setField(first,"id",11L);ReflectionTestUtils.setField(second,"id",12L);
        when(repository.findByStatusOrderByCreatedAtAscIdAsc(OrderStatus.OPEN)).thenReturn(List.of(first,second));
        doThrow(new IllegalStateException("temporary failure")).when(service).match(1L,11L);
        new MatchingEngine(repository,service).tick();
        verify(service).match(2L,12L);
        new MatchingEngine(repository,service).tick();
        verify(repository,times(2)).findByStatusOrderByCreatedAtAscIdAsc(OrderStatus.OPEN);
        verify(service,times(2)).match(2L,12L);
    }
}
