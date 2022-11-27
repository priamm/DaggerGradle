package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;

final class KeyFormatter extends Formatter<Key> {
  
  private final MethodSignatureFormatter methodSignatureFormatter;

  KeyFormatter(MethodSignatureFormatter methodSignatureFormatter) {
    this.methodSignatureFormatter = methodSignatureFormatter;
  }

  @Override public String format(Key request) {
    if (request.bindingMethod().isPresent()) {
      SourceElement bindingMethod = request.bindingMethod().get();
      return methodSignatureFormatter.format(
          MoreElements.asExecutable(bindingMethod.element()),
          Optional.of(MoreTypes.asDeclared(bindingMethod.contributedBy().get().asType())));
    }
    StringBuilder builder = new StringBuilder();
    if (request.qualifier().isPresent()) {
      builder.append(request.qualifier().get());
      builder.append(' ');
    }
    builder.append(request.type());
    return builder.toString();
  }
}
