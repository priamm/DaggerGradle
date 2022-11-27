package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import static dagger.internal.codegen.ErrorMessages.ABSTRACT_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.GENERIC_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.STATIC_INJECT_METHOD;
import static dagger.internal.codegen.ErrorMessages.provisionMayNotDependOnProducerType;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

final class InjectMethodValidator {
  private CompilerOptions compilerOptions;

  public InjectMethodValidator(CompilerOptions compilerOptions) {
    this.compilerOptions = compilerOptions;
  }

  ValidationReport<ExecutableElement> validate(ExecutableElement methodElement) {
    ValidationReport.Builder<ExecutableElement> builder = ValidationReport.about(methodElement);
    Set<Modifier> modifiers = methodElement.getModifiers();
    if (modifiers.contains(ABSTRACT)) {
      builder.addError(ABSTRACT_INJECT_METHOD, methodElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(
          PRIVATE_INJECT_METHOD, compilerOptions.privateMemberValidationKind(), methodElement);
    }
    
    if (modifiers.contains(STATIC)) {
      builder.addItem(
          STATIC_INJECT_METHOD, compilerOptions.staticMemberValidationKind(), methodElement);
    }

    if (!methodElement.getTypeParameters().isEmpty()) {
      builder.addError(GENERIC_INJECT_METHOD, methodElement);
    }

    for (VariableElement parameter : methodElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addError(MULTIPLE_QUALIFIERS, methodElement, qualifier);
        }
      }
      if (FrameworkTypes.isProducerType(parameter.asType())) {
        builder.addError(provisionMayNotDependOnProducerType(parameter.asType()), parameter);
      }
    }

    return builder.build();
  }
}
