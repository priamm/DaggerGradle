package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Multibindings;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import java.util.Collection;
import java.util.Map;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.MoreTypes.asExecutable;
import static dagger.internal.codegen.ErrorMessages.DUPLICATE_SIZE_LIMIT;
import static dagger.internal.codegen.ErrorMessages.MultibindingsMessages.METHOD_MUST_RETURN_MAP_OR_SET;
import static dagger.internal.codegen.ErrorMessages.MultibindingsMessages.MUST_BE_INTERFACE;
import static dagger.internal.codegen.ErrorMessages.MultibindingsMessages.MUST_BE_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.MultibindingsMessages.MUST_NOT_HAVE_TYPE_PARAMETERS;
import static dagger.internal.codegen.ErrorMessages.MultibindingsMessages.TOO_MANY_QUALIFIERS;
import static dagger.internal.codegen.ErrorMessages.MultibindingsMessages.tooManyMethodsForKey;
import static dagger.internal.codegen.InjectionAnnotations.getQualifiers;
import static javax.lang.model.element.ElementKind.INTERFACE;

final class MultibindingsValidator {
  private final Elements elements;
  private final Key.Factory keyFactory;
  private final KeyFormatter keyFormatter;
  private final MethodSignatureFormatter methodSignatureFormatter;
  private final TypeElement objectElement;

  MultibindingsValidator(
      Elements elements,
      Key.Factory keyFactory,
      KeyFormatter keyFormatter,
      MethodSignatureFormatter methodSignatureFormatter) {
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.keyFormatter = keyFormatter;
    this.methodSignatureFormatter = methodSignatureFormatter;
    this.objectElement = elements.getTypeElement(Object.class.getCanonicalName());
  }

  public ValidationReport<TypeElement> validate(TypeElement multibindingsType) {
    ValidationReport.Builder<TypeElement> validation = ValidationReport.about(multibindingsType);
    if (!multibindingsType.getKind().equals(INTERFACE)) {
      validation.addError(MUST_BE_INTERFACE, multibindingsType);
    }
    if (!multibindingsType.getTypeParameters().isEmpty()) {
      validation.addError(MUST_NOT_HAVE_TYPE_PARAMETERS, multibindingsType);
    }
    Optional<BindingType> bindingType = bindingType(multibindingsType);
    if (!bindingType.isPresent()) {
      validation.addError(MUST_BE_IN_MODULE, multibindingsType);
    }

    ImmutableListMultimap.Builder<Key, ExecutableElement> methodsByKey =
        ImmutableListMultimap.builder();
    for (ExecutableElement method : getLocalAndInheritedMethods(multibindingsType, elements)) {
      if (method.getEnclosingElement().equals(objectElement)) {
        continue;
      }
      if (!isPlainMap(method.getReturnType()) && !isPlainSet(method.getReturnType())) {
        validation.addError(METHOD_MUST_RETURN_MAP_OR_SET, method);
        continue;
      }
      ImmutableSet<? extends AnnotationMirror> qualifiers = getQualifiers(method);
      if (qualifiers.size() > 1) {
        for (AnnotationMirror qualifier : qualifiers) {
          validation.addError(TOO_MANY_QUALIFIERS, method, qualifier);
        }
        continue;
      }
      if (bindingType.isPresent()) {
        methodsByKey.put(
            keyFactory.forMultibindingsMethod(
                bindingType.get(), asExecutable(method.asType()), method),
            method);
      }
    }
    for (Map.Entry<Key, Collection<ExecutableElement>> entry :
        methodsByKey.build().asMap().entrySet()) {
      Collection<ExecutableElement> methods = entry.getValue();
      if (methods.size() > 1) {
        Key key = entry.getKey();
        validation.addError(tooManyMultibindingsMethodsForKey(key, methods), multibindingsType);
      }
    }
    return validation.build();
  }

  private String tooManyMultibindingsMethodsForKey(Key key, Collection<ExecutableElement> methods) {
    StringBuilder builder = new StringBuilder(tooManyMethodsForKey(keyFormatter.format(key)));
    builder.append(':');
    methodSignatureFormatter.formatIndentedList(builder, methods, 1, DUPLICATE_SIZE_LIMIT);
    return builder.toString();
  }

  private Optional<BindingType> bindingType(TypeElement multibindingsType) {
    if (isAnnotationPresent(multibindingsType.getEnclosingElement(), Module.class)) {
      return Optional.of(BindingType.PROVISION);
    } else if (isAnnotationPresent(multibindingsType.getEnclosingElement(), ProducerModule.class)) {
      return Optional.of(BindingType.PRODUCTION);
    } else {
      return Optional.<BindingType>absent();
    }
  }

  private boolean isPlainMap(TypeMirror returnType) {
    if (!MapType.isMap(returnType)) {
      return false;
    }
    MapType mapType = MapType.from(returnType);
    return !mapType.isRawType()
        && MoreTypes.isType(mapType.valueType())
        && !mapType.valuesAreTypeOf(Provider.class)
        && !mapType.valuesAreTypeOf(Producer.class)
        && !mapType.valuesAreTypeOf(Produced.class);
  }

  private boolean isPlainSet(TypeMirror returnType) {
    if (!SetType.isSet(returnType)) {
      return false;
    }
    SetType setType = SetType.from(returnType);
    return !setType.isRawType()
        && MoreTypes.isType(setType.elementType())
        && !setType.elementsAreTypeOf(Provider.class)
        && !setType.elementsAreTypeOf(Producer.class)
        && !setType.elementsAreTypeOf(Produced.class);
  }
}
