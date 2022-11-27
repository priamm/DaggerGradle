package dagger.internal.codegen;

import com.google.common.base.Optional;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreTypes.asDeclared;

final class ProductionBindingFormatter extends Formatter<ProductionBinding> {
  private final MethodSignatureFormatter methodSignatureFormatter;

  ProductionBindingFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override public String format(ProductionBinding binding) {
    switch (binding.bindingKind()) {
      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        return methodSignatureFormatter.format(asExecutable(binding.bindingElement()),
            Optional.of(asDeclared(binding.contributedBy().get().asType())));
      case COMPONENT_PRODUCTION:
        return methodSignatureFormatter.format(asExecutable(binding.bindingElement()));
      default:
        throw new UnsupportedOperationException(
            "Not yet supporting " + binding.bindingKind() + " binding types.");
    }
  }
}
