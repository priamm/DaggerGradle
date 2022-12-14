package dagger.internal.codegen;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.Module;
import dagger.Provides;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_NOT_MAP_HAS_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_TYPE_PARAMETER;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_MULTIPLE_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.BINDING_METHOD_WITH_NO_MAP_KEY;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_THROWS;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.provisionMayNotDependOnProducerType;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static dagger.internal.codegen.MapKeys.getMapKeys;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.type.TypeKind.ARRAY;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.TYPEVAR;
import static javax.lang.model.type.TypeKind.VOID;

import javax.lang.model.util.Types;

final class ProvidesMethodValidator {
  private final Elements elements;
  private final Types types;

  ProvidesMethodValidator(Elements elements, Types types) {
    this.elements = checkNotNull(elements);
    this.types = checkNotNull(types);
  }

  private TypeElement getSetElement() {
    return elements.getTypeElement(Set.class.getCanonicalName());
  }

  ValidationReport<ExecutableElement> validate(ExecutableElement providesMethodElement) {
    ValidationReport.Builder<ExecutableElement> builder =
        ValidationReport.about(providesMethodElement);

    Provides providesAnnotation = providesMethodElement.getAnnotation(Provides.class);
    checkArgument(providesAnnotation != null);

    Element enclosingElement = providesMethodElement.getEnclosingElement();
    if (!isAnnotationPresent(enclosingElement, Module.class)) {
      builder.addError(
          formatModuleErrorMessage(BINDING_METHOD_NOT_IN_MODULE), providesMethodElement);
    }

    if (!providesMethodElement.getTypeParameters().isEmpty()) {
      builder.addError(formatErrorMessage(BINDING_METHOD_TYPE_PARAMETER), providesMethodElement);
    }

    Set<Modifier> modifiers = providesMethodElement.getModifiers();
    if (modifiers.contains(PRIVATE)) {
      builder.addError(formatErrorMessage(BINDING_METHOD_PRIVATE), providesMethodElement);
    }
    if (modifiers.contains(ABSTRACT)) {
      builder.addError(formatErrorMessage(BINDING_METHOD_ABSTRACT), providesMethodElement);
    }

    TypeMirror returnType = providesMethodElement.getReturnType();
    TypeKind returnTypeKind = returnType.getKind();
    if (returnTypeKind.equals(VOID)) {
      builder.addError(
          formatErrorMessage(BINDING_METHOD_MUST_RETURN_A_VALUE), providesMethodElement);
    }

    TypeMirror runtimeExceptionType =
        elements.getTypeElement(RuntimeException.class.getCanonicalName()).asType();
    TypeMirror errorType = elements.getTypeElement(Error.class.getCanonicalName()).asType();
    for (TypeMirror thrownType : providesMethodElement.getThrownTypes()) {
      if (!types.isSubtype(thrownType, runtimeExceptionType)
          && !types.isSubtype(thrownType, errorType)) {
        builder.addError(PROVIDES_METHOD_THROWS, providesMethodElement);
        break;
      }
    }

    for (VariableElement parameter : providesMethodElement.getParameters()) {
      if (FrameworkTypes.isProducerType(parameter.asType())) {
        builder.addError(provisionMayNotDependOnProducerType(parameter.asType()), parameter);
      }
    }

    if (!providesAnnotation.type().equals(Provides.Type.MAP)
        && !getMapKeys(providesMethodElement).isEmpty()) {
      builder.addError(
          formatErrorMessage(BINDING_METHOD_NOT_MAP_HAS_MAP_KEY), providesMethodElement);
    }

    validateMethodQualifiers(builder, providesMethodElement);

    switch (providesAnnotation.type()) {
      case UNIQUE:
      case SET:
        validateKeyType(builder, returnType);
        break;
      case MAP:
        validateKeyType(builder, returnType);
        ImmutableSet<? extends AnnotationMirror> mapKeys = getMapKeys(providesMethodElement);
        switch (mapKeys.size()) {
          case 0:
            builder.addError(
                formatErrorMessage(BINDING_METHOD_WITH_NO_MAP_KEY), providesMethodElement);
            break;
          case 1:
            break;
          default:
            builder.addError(
                formatErrorMessage(BINDING_METHOD_WITH_MULTIPLE_MAP_KEY), providesMethodElement);
            break;
        }
        break;
      case SET_VALUES:
        if (!returnTypeKind.equals(DECLARED)) {
          builder.addError(PROVIDES_METHOD_SET_VALUES_RETURN_SET, providesMethodElement);
        } else {
          DeclaredType declaredReturnType = (DeclaredType) returnType;
          if (!declaredReturnType.asElement().equals(getSetElement())) {
            builder.addError(PROVIDES_METHOD_SET_VALUES_RETURN_SET, providesMethodElement);
          } else if (declaredReturnType.getTypeArguments().isEmpty()) {
            builder.addError(
                formatErrorMessage(BINDING_METHOD_SET_VALUES_RAW_SET), providesMethodElement);
          } else {
            validateKeyType(builder,
                Iterables.getOnlyElement(declaredReturnType.getTypeArguments()));
          }
        }
        break;
      default:
        throw new AssertionError();
    }

    return builder.build();
  }

  static void validateMethodQualifiers(ValidationReport.Builder<ExecutableElement> builder,
      ExecutableElement methodElement) {
    ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(methodElement);
    if (qualifiers.size() > 1) {
      for (AnnotationMirror qualifier : qualifiers) {
        builder.addError(PROVIDES_OR_PRODUCES_METHOD_MULTIPLE_QUALIFIERS, methodElement, qualifier);
      }
    }
  }

  private String formatErrorMessage(String msg) {
    return String.format(msg, Provides.class.getSimpleName());
  }

  private String formatModuleErrorMessage(String msg) {
    return String.format(msg, Provides.class.getSimpleName(), Module.class.getSimpleName());
  }

  private void validateKeyType(ValidationReport.Builder<? extends Element> reportBuilder,
      TypeMirror type) {
    TypeKind kind = type.getKind();
    if (!(kind.isPrimitive()
        || kind.equals(DECLARED)
        || kind.equals(ARRAY)
        || kind.equals(TYPEVAR))) {
      reportBuilder.addError(PROVIDES_METHOD_RETURN_TYPE, reportBuilder.getSubject());
    }
  }
}
