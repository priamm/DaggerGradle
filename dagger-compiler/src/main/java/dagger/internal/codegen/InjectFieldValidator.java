package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import static dagger.internal.codegen.ErrorMessages.FINAL_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.PRIVATE_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.STATIC_INJECT_FIELD;
import static dagger.internal.codegen.ErrorMessages.provisionMayNotDependOnProducerType;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

final class InjectFieldValidator {
  private CompilerOptions compilerOptions;

  public InjectFieldValidator(CompilerOptions compilerOptions) {
    this.compilerOptions = compilerOptions;
  }

  ValidationReport<VariableElement> validate(VariableElement fieldElement) {
    ValidationReport.Builder<VariableElement> builder = ValidationReport.about(fieldElement);
    Set<Modifier> modifiers = fieldElement.getModifiers();
    if (modifiers.contains(FINAL)) {
      builder.addError(FINAL_INJECT_FIELD, fieldElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(
          PRIVATE_INJECT_FIELD, compilerOptions.privateMemberValidationKind(), fieldElement);
    }

    if (modifiers.contains(STATIC)) {
      builder.addItem(
          STATIC_INJECT_FIELD, compilerOptions.staticMemberValidationKind(), fieldElement);
    }
    
    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(fieldElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addError(MULTIPLE_QUALIFIERS, fieldElement, qualifier);
      }
    }

    if (FrameworkTypes.isProducerType(fieldElement.asType())) {
      builder.addError(provisionMayNotDependOnProducerType(fieldElement.asType()), fieldElement);
    }

    return builder.build();
  }
}
