package dagger.internal.codegen;

import com.google.common.base.Optional;

import static com.google.auto.common.MoreElements.asExecutable;
import static com.google.auto.common.MoreTypes.asDeclared;

final class ProvisionBindingFormatter extends Formatter<ProvisionBinding> {
  private final MethodSignatureFormatter methodSignatureFormatter;
  
  ProvisionBindingFormatter(MethodSignatureFormatter methodSignatureFormatter) { 
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override public String format(ProvisionBinding binding) {
    switch (binding.bindingKind()) {
      case PROVISION:
        return methodSignatureFormatter.format(asExecutable(binding.bindingElement()),
            Optional.of(asDeclared(binding.contributedBy().get().asType())));
      case COMPONENT_PROVISION:
        return methodSignatureFormatter.format(asExecutable(binding.bindingElement()));
      default:
        throw new UnsupportedOperationException(
            "Not yet supporting " + binding.bindingKind() + " binding types.");
    }
  }
}
