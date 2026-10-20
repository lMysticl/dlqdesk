package io.github.lmysticl.dlqdesk;

import java.io.Serial;

public final class DeskFailure extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  public enum Kind {
    INVALID_INPUT,
    NOT_FOUND,
    CONFLICT,
    UNAVAILABLE
  }

  private final Kind kind;

  public DeskFailure(Kind kind, String detail) {
    super(detail);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
