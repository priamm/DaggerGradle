package dagger.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Multibindings;
import dagger.Provides;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.Production;
import dagger.producers.Produces;
import dagger.producers.internal.ProductionImplementation;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.inject.Provider;
import javax.inject.Qualifier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreTypes.asExecutable;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.MapKeys.getMapKey;
import static dagger.internal.codegen.MapKeys.getUnwrappedMapKeyType;
import static javax.lang.model.element.ElementKind.METHOD;

@AutoValue
abstract class Key {

  interface HasKey {
    Key key();
  }

  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedQualifier();

  abstract Equivalence.Wrapper<TypeMirror> wrappedType();

  abstract Optional<SourceElement> bindingMethod();

  Optional<AnnotationMirror> qualifier() {
    return unwrapOptionalEquivalence(wrappedQualifier());
  }

  TypeMirror type() {
    return wrappedType().get();
  }

  private static TypeMirror normalize(Types types, TypeMirror type) {
    TypeKind kind = type.getKind();
    return kind.isPrimitive() ? types.boxedClass((PrimitiveType) type).asType() : type;
  }

  private Key withType(Types types, TypeMirror newType) {
    return new AutoValue_Key(
        wrappedQualifier(),
        MoreTypes.equivalence().wrap(normalize(types, newType)),
        bindingMethod());
  }

  private Key withBindingMethod(SourceElement bindingMethod) {
    return new AutoValue_Key(wrappedQualifier(), wrappedType(), Optional.of(bindingMethod));
  }

  Key withoutBindingMethod() {
    return new AutoValue_Key(wrappedQualifier(), wrappedType(), Optional.<SourceElement>absent());
  }

  boolean isValidMembersInjectionKey() {
    return !qualifier().isPresent() && !type().getKind().equals(TypeKind.WILDCARD);
  }

  boolean isValidImplicitProvisionKey(final Types types) {
    if (qualifier().isPresent()) {
      return false;
    }

    return type().accept(new SimpleTypeVisitor6<Boolean, Void>() {
      @Override protected Boolean defaultAction(TypeMirror e, Void p) {
        return false;
      }

      @Override public Boolean visitDeclared(DeclaredType type, Void ignored) {
        TypeElement element = MoreElements.asType(type.asElement());
        if (!element.getKind().equals(ElementKind.CLASS)
            || element.getModifiers().contains(Modifier.ABSTRACT)) {
          return false;
        }

        for (TypeMirror arg : type.getTypeArguments()) {
          if (arg.getKind() != TypeKind.DECLARED) {
            return false;
          }
        }

        return MoreTypes.asDeclared(element.asType()).getTypeArguments().isEmpty()
            || !types.isSameType(types.erasure(element.asType()), type());
      }
    }, null);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(Key.class)
        .omitNullValues()
        .add("qualifier", qualifier().orNull())
        .add("type", type())
        .add("bindingMethod", bindingMethod().orNull())
        .toString();
  }

  static <T extends HasKey> ImmutableSetMultimap<Key, T> indexByKey(Iterable<T> haveKeys) {
    return ImmutableSetMultimap.copyOf(
        Multimaps.index(
            haveKeys,
            new Function<HasKey, Key>() {
              @Override
              public Key apply(HasKey hasKey) {
                return hasKey.key();
              }
            }));
  }

  private static <T> Optional<Equivalence.Wrapper<T>> wrapOptionalInEquivalence(
      Equivalence<T> equivalence, Optional<T> optional) {
    return optional.isPresent()
        ? Optional.of(equivalence.wrap(optional.get()))
        : Optional.<Equivalence.Wrapper<T>>absent();
  }

  private static <T> Optional<T> unwrapOptionalEquivalence(
      Optional<Equivalence.Wrapper<T>> wrappedOptional) {
    return wrappedOptional.isPresent()
        ? Optional.of(wrappedOptional.get().get())
        : Optional.<T>absent();
  }

  static final class Factory {
    private final Types types;
    private final Elements elements;

    Factory(Types types, Elements elements) {
      this.types = checkNotNull(types);
      this.elements = checkNotNull(elements);
    }

    private TypeElement getSetElement() {
      return elements.getTypeElement(Set.class.getCanonicalName());
    }

    private TypeElement getMapElement() {
      return elements.getTypeElement(Map.class.getCanonicalName());
    }

    private TypeElement getProviderElement() {
      return elements.getTypeElement(Provider.class.getCanonicalName());
    }

    private TypeElement getProducerElement() {
      return elements.getTypeElement(Producer.class.getCanonicalName());
    }

    private TypeElement getClassElement(Class<?> cls) {
      return elements.getTypeElement(cls.getCanonicalName());
    }

    Key forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      TypeMirror returnType = normalize(types, componentMethod.getReturnType());
      return forMethod(componentMethod, returnType);
    }

    Key forProductionComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      TypeMirror returnType = normalize(types, componentMethod.getReturnType());
      TypeMirror keyType = returnType;
      if (MoreTypes.isTypeOf(ListenableFuture.class, returnType)) {
        keyType = Iterables.getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments());
      }
      return forMethod(componentMethod, keyType);
    }

    Key forSubcomponentBuilderMethod(
        ExecutableElement subcomponentBuilderMethod, DeclaredType declaredContainer) {
      checkNotNull(subcomponentBuilderMethod);
      checkArgument(subcomponentBuilderMethod.getKind().equals(METHOD));
      ExecutableType resolvedMethod =
          asExecutable(types.asMemberOf(declaredContainer, subcomponentBuilderMethod));
      TypeMirror returnType = normalize(types, resolvedMethod.getReturnType());
      return forMethod(subcomponentBuilderMethod, returnType);
    }

    Key forProvidesMethod(SourceElement sourceElement) {
      checkArgument(sourceElement.element().getKind().equals(METHOD));
      ExecutableElement method = MoreElements.asExecutable(sourceElement.element());
      ExecutableType methodType =
          MoreTypes.asExecutable(sourceElement.asMemberOfContributingType(types));
      Provides providesAnnotation = method.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      TypeMirror returnType = normalize(types, methodType.getReturnType());
      TypeMirror keyType =
          providesOrProducesKeyType(
              returnType,
              method,
              Optional.of(providesAnnotation.type()),
              Optional.<Produces.Type>absent());
      Key key = forMethod(method, keyType);
      return providesAnnotation.type().equals(Provides.Type.UNIQUE)
          ? key
          : key.withBindingMethod(sourceElement);
    }

    Key forProducesMethod(SourceElement sourceElement) {
      checkArgument(sourceElement.element().getKind().equals(METHOD));
      ExecutableElement method = MoreElements.asExecutable(sourceElement.element());
      ExecutableType methodType =
          MoreTypes.asExecutable(sourceElement.asMemberOfContributingType(types));
      Produces producesAnnotation = method.getAnnotation(Produces.class);
      checkArgument(producesAnnotation != null);
      TypeMirror returnType = normalize(types, methodType.getReturnType());
      TypeMirror unfuturedType = returnType;
      if (MoreTypes.isTypeOf(ListenableFuture.class, returnType)) {
        unfuturedType =
            Iterables.getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments());
      }
      TypeMirror keyType =
          providesOrProducesKeyType(
              unfuturedType,
              method,
              Optional.<Provides.Type>absent(),
              Optional.of(producesAnnotation.type()));
      Key key = forMethod(method, keyType);
      return producesAnnotation.type().equals(Produces.Type.UNIQUE)
          ? key
          : key.withBindingMethod(sourceElement);
    }

    Key forMultibindingsMethod(
        BindingType bindingType, ExecutableType executableType, ExecutableElement method) {
      checkArgument(method.getKind().equals(METHOD), "%s must be a method", method);
      TypeElement factoryType =
          elements.getTypeElement(bindingType.frameworkClass().getCanonicalName());
      TypeMirror returnType = normalize(types, executableType.getReturnType());
      TypeMirror keyType =
          MapType.isMap(returnType)
              ? mapOfFrameworkType(
                  MapType.from(returnType).keyType(),
                  factoryType,
                  MapType.from(returnType).valueType())
              : returnType;
      return forMethod(method, keyType);
    }

    private TypeMirror providesOrProducesKeyType(
        TypeMirror returnType,
        ExecutableElement method,
        Optional<Provides.Type> providesType,
        Optional<Produces.Type> producesType) {
      switch (providesType.isPresent()
          ? providesType.get()
          : Provides.Type.valueOf(producesType.get().name())) {
        case UNIQUE:
          return returnType;
        case SET:
          return types.getDeclaredType(getSetElement(), returnType);
        case MAP:
          return mapOfFrameworkType(
              mapKeyType(method),
              providesType.isPresent() ? getProviderElement() : getProducerElement(),
              returnType);
        case SET_VALUES:
          checkArgument(MoreTypes.isType(returnType) && MoreTypes.isTypeOf(Set.class, returnType));
          return returnType;
        default:
          throw new AssertionError();
      }
    }

    private TypeMirror mapOfFrameworkType(
        TypeMirror keyType, TypeElement frameworkType, TypeMirror valueType) {
      return types.getDeclaredType(
          getMapElement(), keyType, types.getDeclaredType(frameworkType, valueType));
    }

    private TypeMirror mapKeyType(ExecutableElement method) {
      AnnotationMirror mapKeyAnnotation = getMapKey(method).get();
      return MapKeys.unwrapValue(mapKeyAnnotation).isPresent()
          ? getUnwrappedMapKeyType(mapKeyAnnotation.getAnnotationType(), types)
          : mapKeyAnnotation.getAnnotationType();
    }

    private Key forMethod(ExecutableElement method, TypeMirror keyType) {
      return new AutoValue_Key(
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(method)),
          MoreTypes.equivalence().wrap(keyType),
          Optional.<SourceElement>absent());
    }

    Key forInjectConstructorWithResolvedType(TypeMirror type) {
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(type),
          Optional.<SourceElement>absent());
    }

    Key forComponent(TypeMirror type) {
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(normalize(types, type)),
          Optional.<SourceElement>absent());
    }

    Key forMembersInjectedType(TypeMirror type) {
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(normalize(types, type)),
          Optional.<SourceElement>absent());
    }

    Key forQualifiedType(Optional<AnnotationMirror> qualifier, TypeMirror type) {
      return new AutoValue_Key(
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), qualifier),
          MoreTypes.equivalence().wrap(normalize(types, type)),
          Optional.<SourceElement>absent());
    }

    Key forProductionExecutor() {
      return forQualifiedType(
          Optional.of(SimpleAnnotationMirror.of(getClassElement(Production.class))),
          getClassElement(Executor.class).asType());
    }

    Key forProductionImplementationExecutor() {
      return forQualifiedType(
          Optional.of(SimpleAnnotationMirror.of(getClassElement(ProductionImplementation.class))),
          getClassElement(Executor.class).asType());
    }

    Optional<Key> implicitMapProviderKeyFrom(Key possibleMapKey) {
      return maybeWrapMapValue(possibleMapKey, Provider.class);
    }

    Optional<Key> implicitMapProducerKeyFrom(Key possibleMapKey) {
      return maybeRewrapMapValue(possibleMapKey, Produced.class, Producer.class)
          .or(maybeWrapMapValue(possibleMapKey, Producer.class));
    }

    private Optional<Key> maybeRewrapMapValue(
        Key possibleMapKey, Class<?> currentWrappingClass, Class<?> newWrappingClass) {
      checkArgument(!currentWrappingClass.equals(newWrappingClass));
      if (MapType.isMap(possibleMapKey.type())) {
        MapType mapType = MapType.from(possibleMapKey.type());
        if (mapType.valuesAreTypeOf(currentWrappingClass)) {
          TypeElement wrappingElement = getClassElement(newWrappingClass);
          if (wrappingElement == null) {
            return Optional.absent();
          }
          DeclaredType wrappedValueType =
              types.getDeclaredType(
                  wrappingElement, mapType.unwrappedValueType(currentWrappingClass));
          TypeMirror wrappedMapType =
              types.getDeclaredType(getMapElement(), mapType.keyType(), wrappedValueType);
          return Optional.of(possibleMapKey.withType(types, wrappedMapType));
        }
      }
      return Optional.absent();
    }

    private Optional<Key> maybeWrapMapValue(Key possibleMapKey, Class<?> wrappingClass) {
      if (MapType.isMap(possibleMapKey.type())) {
        MapType mapType = MapType.from(possibleMapKey.type());
        if (!mapType.valuesAreTypeOf(wrappingClass)) {
          TypeElement wrappingElement = getClassElement(wrappingClass);
          if (wrappingElement == null) {
            return Optional.absent();
          }
          DeclaredType wrappedValueType =
              types.getDeclaredType(wrappingElement, mapType.valueType());
          TypeMirror wrappedMapType =
              types.getDeclaredType(getMapElement(), mapType.keyType(), wrappedValueType);
          return Optional.of(possibleMapKey.withType(types, wrappedMapType));
        }
      }
      return Optional.absent();
    }

    Optional<Key> implicitSetKeyFromProduced(Key possibleSetOfProducedKey) {
      if (MoreTypes.isType(possibleSetOfProducedKey.type())
          && MoreTypes.isTypeOf(Set.class, possibleSetOfProducedKey.type())) {
        TypeMirror argType =
            MoreTypes.asDeclared(possibleSetOfProducedKey.type()).getTypeArguments().get(0);
        if (MoreTypes.isType(argType) && MoreTypes.isTypeOf(Produced.class, argType)) {
          TypeMirror producedArgType = MoreTypes.asDeclared(argType).getTypeArguments().get(0);
          TypeMirror setType = types.getDeclaredType(getSetElement(), producedArgType);
          return Optional.of(possibleSetOfProducedKey.withType(types, setType));
        }
      }
      return Optional.absent();
    }
  }
}
