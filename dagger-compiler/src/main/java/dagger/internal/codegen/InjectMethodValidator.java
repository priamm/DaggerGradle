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
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;

final class InjectMethodValidator implements Validator<ExecutableElement> {
  @Override
  public ValidationReport<ExecutableElement> validate(ExecutableElement methodElement) {
    ValidationReport.Builder<ExecutableElement> builder =
        ValidationReport.Builder.about(methodElement);
    Set<Modifier> modifiers = methodElement.getModifiers();
    if (modifiers.contains(ABSTRACT)) {
      builder.addItem(ABSTRACT_INJECT_METHOD, methodElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(PRIVATE_INJECT_METHOD, methodElement);
    }

    if (!methodElement.getTypeParameters().isEmpty()) {
      builder.addItem(GENERIC_INJECT_METHOD, methodElement);
    }

    for (VariableElement parameter : methodElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addItem(MULTIPLE_QUALIFIERS, methodElement, qualifier);
        }
      }
    }

    return builder.build();
  }
}
