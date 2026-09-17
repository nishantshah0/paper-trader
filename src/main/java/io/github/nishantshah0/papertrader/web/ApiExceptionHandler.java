package io.github.nishantshah0.papertrader.web;

import io.github.nishantshah0.papertrader.NotFoundException;
import io.github.nishantshah0.papertrader.account.UsernameTakenException;
import io.github.nishantshah0.papertrader.quote.UnknownSymbolException;
import io.github.nishantshah0.papertrader.trading.OrderRejectedException;
import io.github.nishantshah0.papertrader.trading.OrderStateException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({NotFoundException.class, UnknownSymbolException.class})
    ProblemDetail notFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(OrderRejectedException.class)
    ProblemDetail rejected(OrderRejectedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler({OrderStateException.class, UsernameTakenException.class})
    ProblemDetail conflict(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    // unique constraint backstop, e.g. two requests racing to create the same username
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "conflicts with existing data");
    }
}
