package io.github.nishantshah0.papertrader.trading;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/{accountId}")
class OrderController {

    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse place(@PathVariable Long accountId, @Valid @RequestBody PlaceOrderRequest request) {
        return OrderResponse.from(service.place(accountId, request));
    }

    @GetMapping("/orders")
    List<OrderResponse> list(@PathVariable Long accountId, @RequestParam(required = false) OrderStatus status) {
        return service.list(accountId, status).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/orders/{orderId}")
    OrderResponse get(@PathVariable Long accountId, @PathVariable Long orderId) {
        return OrderResponse.from(service.get(accountId, orderId));
    }

    @DeleteMapping("/orders/{orderId}")
    OrderResponse cancel(@PathVariable Long accountId, @PathVariable Long orderId) {
        return OrderResponse.from(service.cancel(accountId, orderId));
    }

    @GetMapping("/trades")
    List<TradeResponse> trades(@PathVariable Long accountId) {
        return service.trades(accountId).stream().map(TradeResponse::from).toList();
    }
}
