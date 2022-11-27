package dagger.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.MapKey;
import dagger.Provides;
import dagger.producers.Producer;
import dagger.producers.Produces;
import java.util.Map;
import java.util.Set;
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.ConfigurationAnnotations.getMapKeys;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.Util.unwrapOptionalEquivalence;
import static dagger.internal.codegen.Util.wrapOptionalInEquivalence;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.type.TypeKind.DECLARED;

@AutoValue
abstract class Key {

  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedQualifier();

  abstract Equivalence.Wrapper<TypeMirror> wrappedType();

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

  Key withType(Types types, TypeMirror newType) {
    return new AutoValue_Key(wrappedQualifier(),
        MoreTypes.equivalence().wrap(normalize(types, newType)));
  }

  boolean isValidMembersInjectionKey() {
    return !qualifier().isPresent();
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
        .toString();
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
      return new AutoValue_Key(
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(componentMethod)),
          MoreTypes.equivalence().wrap(returnType));
    }

    Key forProductionComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      TypeMirror returnType = normalize(types, componentMethod.getReturnType());
      TypeMirror keyType = returnType;
      if (MoreTypes.isTypeOf(ListenableFuture.class, returnType)) {
        keyType = Iterables.getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments());
      }
      return new AutoValue_Key(
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(componentMethod)),
          MoreTypes.equivalence().wrap(keyType));
    }

    Key forProvidesMethod(ExecutableType executableType, ExecutableElement e) {
      checkNotNull(e);
      checkArgument(e.getKind().equals(METHOD));
      Provides providesAnnotation = e.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      TypeMirror returnType = normalize(types, executableType.getReturnType());
      switch (providesAnnotation.type()) {
        case UNIQUE:
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(returnType));
        case SET:
          TypeMirror setType = types.getDeclaredType(getSetElement(), returnType);
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(setType));
        case MAP:
          AnnotationMirror mapKeyAnnotation = Iterables.getOnlyElement(getMapKeys(e));
          MapKey mapKey =
              mapKeyAnnotation.getAnnotationType().asElement().getAnnotation(MapKey.class);
          TypeElement keyTypeElement =
              mapKey.unwrapValue() ? Util.getKeyTypeElement(mapKeyAnnotation, elements)
                  : (TypeElement) mapKeyAnnotation.getAnnotationType().asElement();
          TypeMirror valueType = types.getDeclaredType(getProviderElement(), returnType);
          TypeMirror mapType =
              types.getDeclaredType(getMapElement(), keyTypeElement.asType(), valueType);
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(mapType));
        case SET_VALUES:
          checkArgument(returnType.getKind().equals(DECLARED));
          checkArgument(((DeclaredType) returnType).asElement().equals(getSetElement()));
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(returnType));
        default:
          throw new AssertionError();
      }
    }

    Key forProducesMethod(ExecutableType executableType, ExecutableElement e) {
      checkNotNull(e);
      checkArgument(e.getKind().equals(METHOD));
      Produces producesAnnotation = e.getAnnotation(Produces.class);
      checkArgument(producesAnnotation != null);
      TypeMirror returnType = normalize(types, executableType.getReturnType());
      TypeMirror keyType = returnType;
      if (MoreTypes.isTypeOf(ListenableFuture.class, returnType)) {
        keyType = Iterables.getOnlyElement(MoreTypes.asDeclared(returnType).getTypeArguments());
      }
      switch (producesAnnotation.type()) {
        case UNIQUE:
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(keyType));
        case SET:
          TypeMirror setType = types.getDeclaredType(getSetElement(), keyType);
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(setType));
        case MAP:
          AnnotationMirror mapKeyAnnotation = Iterables.getOnlyElement(getMapKeys(e));
          MapKey mapKey =
              mapKeyAnnotation.getAnnotationType().asElement().getAnnotation(MapKey.class);
          TypeElement keyTypeElement =
              mapKey.unwrapValue() ? Util.getKeyTypeElement(mapKeyAnnotation, elements)
                  : (TypeElement) mapKeyAnnotation.getAnnotationType().asElement();
          TypeMirror valueType = types.getDeclaredType(getProducerElement(), keyType);
          TypeMirror mapType =
              types.getDeclaredType(getMapElement(), keyTypeElement.asType(), valueType);
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(mapType));
        case SET_VALUES:
          checkArgument(keyType.getKind().equals(DECLARED));
          checkArgument(((DeclaredType) keyType).asElement().equals(getSetElement()));
          return new AutoValue_Key(
              wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), getQualifier(e)),
              MoreTypes.equivalence().wrap(keyType));
        default:
          throw new AssertionError();
      }
    }

    Key forInjectConstructorWithResolvedType(TypeMirror type) {
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(type));
    }

    Key forComponent(TypeMirror type) {
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(normalize(types, type)));
    }

    Key forMembersInjectedType(TypeMirror type) {
      return new AutoValue_Key(
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          MoreTypes.equivalence().wrap(normalize(types, type)));
    }

    Key forQualifiedType(Optional<AnnotationMirror> qualifier, TypeMirror type) {
      return new AutoValue_Key(
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), qualifier),
          MoreTypes.equivalence().wrap(normalize(types, type)));
    }

    Optional<Key> implicitMapProviderKeyFrom(Key possibleMapKey) {
      return maybeWrapMapValue(possibleMapKey, Provider.class);
    }

    Optional<Key> implicitMapProducerKeyFrom(Key possibleMapKey) {
      return maybeWrapMapValue(possibleMapKey, Producer.class);
    }

    private Optional<Key> maybeWrapMapValue(Key possibleMapKey, Class<?> wrappingClass) {
      if (MoreTypes.isTypeOf(Map.class, possibleMapKey.type())) {
        DeclaredType declaredMapType = MoreTypes.asDeclared(possibleMapKey.type());
        TypeMirror mapValueType = Util.getValueTypeOfMap(declaredMapType);
        if (!MoreTypes.isTypeOf(wrappingClass, mapValueType)) {
          DeclaredType keyType = Util.getKeyTypeOfMap(declaredMapType);
          TypeElement wrappingElement = getClassElement(wrappingClass);
          if (wrappingElement == null) {
            return Optional.absent();
          }
          DeclaredType wrappedType = types.getDeclaredType(wrappingElement, mapValueType);
          TypeMirror mapType = types.getDeclaredType(getMapElement(), keyType, wrappedType);
          return Optional.<Key>of(new AutoValue_Key(
              possibleMapKey.wrappedQualifier(),
              MoreTypes.equivalence().wrap(mapType)));
        }
      }
      return Optional.absent();
    }
  }
}
