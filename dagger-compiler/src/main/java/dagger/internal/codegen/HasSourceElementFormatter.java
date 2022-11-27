package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import dagger.internal.codegen.SourceElement.HasSourceElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;

final class HasSourceElementFormatter extends Formatter<HasSourceElement> {
  private final MethodSignatureFormatter methodSignatureFormatter;

  HasSourceElementFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override
  public String format(HasSourceElement hasElement) {
    SourceElement sourceElement = hasElement.sourceElement();
    switch (sourceElement.element().asType().getKind()) {
      case EXECUTABLE:
        Optional<TypeElement> contributedBy = sourceElement.contributedBy();
        return methodSignatureFormatter.format(
            MoreElements.asExecutable(sourceElement.element()),
            contributedBy.isPresent()
                ? Optional.of(MoreTypes.asDeclared(contributedBy.get().asType()))
                : Optional.<DeclaredType>absent());
      case DECLARED:
        return stripCommonTypePrefixes(sourceElement.element().asType().toString());
      default:
        throw new IllegalArgumentException(
            "Formatting unsupported for element: " + sourceElement.element());
    }
  }
}
