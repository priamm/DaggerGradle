package dagger.internal.codegen;

import com.google.common.base.Optional;
import javax.tools.Diagnostic;

enum ValidationType {
  ERROR,
  WARNING,
  NONE;

  Optional<Diagnostic.Kind> diagnosticKind() {
    switch (this) {
      case ERROR:
        return Optional.of(Diagnostic.Kind.ERROR);
      case WARNING:
        return Optional.of(Diagnostic.Kind.WARNING);
      default:
        return Optional.absent();
    }
  }
}
