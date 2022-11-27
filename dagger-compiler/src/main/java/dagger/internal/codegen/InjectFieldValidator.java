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
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;

final class InjectFieldValidator implements Validator<VariableElement> {
  @Override
  public ValidationReport<VariableElement> validate(VariableElement fieldElement) {
    ValidationReport.Builder<VariableElement> builder =
        ValidationReport.Builder.about(fieldElement);
    Set<Modifier> modifiers = fieldElement.getModifiers();
    if (modifiers.contains(FINAL)) {
      builder.addItem(FINAL_INJECT_FIELD, fieldElement);
    }

    if (modifiers.contains(PRIVATE)) {
      builder.addItem(PRIVATE_INJECT_FIELD, fieldElement);
    }

    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(fieldElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addItem(MULTIPLE_QUALIFIERS, fieldElement, qualifier);
      }
    }

    return builder.build();
  }
}
