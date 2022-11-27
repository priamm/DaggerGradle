package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_CONSTRUCTOR_ON_INNER_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_INTO_PRIVATE_CLASS;
import static dagger.internal.codegen.ErrorMessages.INJECT_ON_PRIVATE_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_INJECT_CONSTRUCTORS;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.MULTIPLE_SCOPES;
import static dagger.internal.codegen.ErrorMessages.QUALIFIER_ON_INJECT_CONSTRUCTOR;
import static dagger.internal.codegen.ErrorMessages.provisionMayNotDependOnProducerType;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger.internal.codegen.InjectionAnnotations.getScopes;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

final class InjectConstructorValidator {
  ValidationReport<TypeElement> validate(ExecutableElement constructorElement) {
    ValidationReport.Builder<TypeElement> builder =
        ValidationReport.about(MoreElements.asType(constructorElement.getEnclosingElement()));
    if (constructorElement.getModifiers().contains(PRIVATE)) {
      builder.addError(INJECT_ON_PRIVATE_CONSTRUCTOR, constructorElement);
    }

    for (AnnotationMirror qualifier : getQualifiers(constructorElement)) {
      builder.addError(QUALIFIER_ON_INJECT_CONSTRUCTOR, constructorElement, qualifier);
    }

    for (VariableElement parameter : constructorElement.getParameters()) {
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(parameter);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          builder.addError(MULTIPLE_QUALIFIERS, constructorElement, qualifier);
        }
      }
      if (FrameworkTypes.isProducerType(parameter.asType())) {
        builder.addError(provisionMayNotDependOnProducerType(parameter.asType()), parameter);
      }
    }

    TypeElement enclosingElement =
        MoreElements.asType(constructorElement.getEnclosingElement());
    Set<Modifier> typeModifiers = enclosingElement.getModifiers();

    if (typeModifiers.contains(PRIVATE)) {
      builder.addError(INJECT_INTO_PRIVATE_CLASS, constructorElement);
    }

    if (typeModifiers.contains(ABSTRACT)) {
      builder.addError(INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS, constructorElement);
    }

    if (enclosingElement.getNestingKind().isNested()
        && !typeModifiers.contains(STATIC)) {
      builder.addError(INJECT_CONSTRUCTOR_ON_INNER_CLASS, constructorElement);
    }

    FluentIterable<ExecutableElement> injectConstructors = FluentIterable.from(
        ElementFilter.constructorsIn(enclosingElement.getEnclosedElements()))
            .filter(new Predicate<ExecutableElement>() {
              @Override public boolean apply(ExecutableElement input) {
                return isAnnotationPresent(input, Inject.class);
              }
            });

    if (injectConstructors.size() > 1) {
      builder.addError(MULTIPLE_INJECT_CONSTRUCTORS, constructorElement);
    }

    ImmutableSet<? extends AnnotationMirror> scopes = getScopes(enclosingElement);
    if (scopes.size() > 1) {
      for (AnnotationMirror scope : scopes) {
        builder.addError(MULTIPLE_SCOPES, enclosingElement, scope);
      }
    }

    return builder.build();
  }
}
