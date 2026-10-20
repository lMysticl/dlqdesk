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
    this(kind, detail, null);
  }

  public DeskFailure(Kind kind, String detail, Throwable cause) {
    super(detail, cause);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
