package br.com.jopes.pedidocerto.adapter.in.web;

import br.com.jopes.pedidocerto.adapter.in.web.OrderController.InvalidIdempotencyKeyException;
import br.com.jopes.pedidocerto.adapter.in.web.PixWebhookHandler.InvalidSignatureException;
import br.com.jopes.pedidocerto.application.port.in.IdempotencyKeyReusedException;
import br.com.jopes.pedidocerto.application.port.in.OrderNotFoundException;
import br.com.jopes.pedidocerto.domain.order.InvalidOrderTransitionException;
import br.com.jopes.pedidocerto.domain.order.PaymentAmountMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Traduz exceções em respostas RFC 9457 (Problem Details). Erros de validação do Spring já
 * saem nesse formato pela classe base.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail notFound(OrderNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "pedido-nao-encontrado", e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail keyReused(IdempotencyKeyReusedException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-reutilizada", e.getMessage());
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    ProblemDetail invalidKey(InvalidIdempotencyKeyException e) {
        return problem(HttpStatus.BAD_REQUEST, "idempotency-key-invalida", e.getMessage());
    }

    @ExceptionHandler(PaymentAmountMismatchException.class)
    ProblemDetail amountMismatch(PaymentAmountMismatchException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "valor-divergente", e.getMessage());
    }

    @ExceptionHandler(InvalidOrderTransitionException.class)
    ProblemDetail invalidTransition(InvalidOrderTransitionException e) {
        return problem(HttpStatus.CONFLICT, "transicao-invalida", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "alteracao-concorrente",
                "O pedido foi alterado ao mesmo tempo por outra requisição. Tente de novo.");
    }

    @ExceptionHandler(InvalidSignatureException.class)
    ProblemDetail invalidSignature(InvalidSignatureException e) {
        return problem(HttpStatus.UNAUTHORIZED, "assinatura-invalida", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "requisicao-invalida", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://github.com/JoaoOliveira07/pedido-certo/blob/main/docs/erros.md#" + type));
        return problem;
    }
}
