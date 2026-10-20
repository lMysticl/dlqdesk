package io.github.lmysticl.dlqdesk.web;

import io.github.lmysticl.dlqdesk.DeskFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(annotations = DeskEndpoint.class)
public final class ApiErrors {
  @ExceptionHandler(DeskFailure.class)
  public ProblemDetail failure(DeskFailure failure) {
    return ProblemDetail.forStatusAndDetail(
        switch (failure.kind()) {
          case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
          case NOT_FOUND -> HttpStatus.NOT_FOUND;
          case CONFLICT -> HttpStatus.CONFLICT;
          case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        },
        failure.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail invalid(IllegalArgumentException failure) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
  }
}
