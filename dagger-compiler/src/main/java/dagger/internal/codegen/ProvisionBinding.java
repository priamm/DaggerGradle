package dagger.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dagger.Provides;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static dagger.internal.codegen.InjectionAnnotations.getScopeAnnotation;
import static dagger.internal.codegen.ProvisionBinding.Kind.INJECTION;
import static dagger.internal.codegen.Util.unwrapOptionalEquivalence;
import static dagger.internal.codegen.Util.wrapOptionalInEquivalence;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.ElementKind.METHOD;

@AutoValue
abstract class ProvisionBinding extends ContributionBinding {
  @Override
  ImmutableSet<DependencyRequest> implicitDependencies() {
    return new ImmutableSet.Builder<DependencyRequest>()
        .addAll(memberInjectionRequest().asSet())
        .addAll(dependencies())
        .build();
  }

  enum Kind {
    INJECTION,
    PROVISION,
    SYNTHETIC_PROVISON,
    COMPONENT,
    COMPONENT_PROVISION,
  }

  abstract Kind bindingKind();

  abstract Provides.Type provisionType();

  Optional<AnnotationMirror> scope() {
    return unwrapOptionalEquivalence(wrappedScope());
  }

  abstract Optional<Equivalence.Wrapper<AnnotationMirror>> wrappedScope();

  abstract Optional<DependencyRequest> memberInjectionRequest();

  @Override
  BindingType bindingType() {
    switch (provisionType()) {
      case SET:
      case SET_VALUES:
        return BindingType.SET;
      case MAP:
        return BindingType.MAP;
      case UNIQUE:
        return BindingType.UNIQUE;
      default:
        throw new IllegalStateException("Unknown provision type: " + provisionType());
    }
  }

  @Override
  boolean isSyntheticBinding() {
    return bindingKind().equals(Kind.SYNTHETIC_PROVISON);
  }

  @Override
  Class<?> frameworkClass() {
    return Provider.class;
  }

  enum FactoryCreationStrategy {
    ENUM_INSTANCE,
    CLASS_CONSTRUCTOR,
  }

  FactoryCreationStrategy factoryCreationStrategy() {
    return (bindingKind().equals(INJECTION)
          && implicitDependencies().isEmpty())
          ? FactoryCreationStrategy.ENUM_INSTANCE
          : FactoryCreationStrategy.CLASS_CONSTRUCTOR;
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(Elements elements, Types types, Key.Factory keyFactory,
        DependencyRequest.Factory dependencyRequestFactory) {
      this.elements = elements;
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ProvisionBinding unresolve(ProvisionBinding binding) {
      checkState(binding.hasNonDefaultTypeParameters());
      return forInjectConstructor((ExecutableElement) binding.bindingElement(),
          Optional.<TypeMirror>absent());
    }

    ProvisionBinding forInjectConstructor(ExecutableElement constructorElement,
        Optional<TypeMirror> resolvedType) {
      checkNotNull(constructorElement);
      checkArgument(constructorElement.getKind().equals(CONSTRUCTOR));
      checkArgument(isAnnotationPresent(constructorElement, Inject.class));
      checkArgument(!getQualifier(constructorElement).isPresent());

      ExecutableType cxtorType = MoreTypes.asExecutable(constructorElement.asType());
      DeclaredType enclosingCxtorType =
          MoreTypes.asDeclared(constructorElement.getEnclosingElement().asType());
      if (!enclosingCxtorType.getTypeArguments().isEmpty() && resolvedType.isPresent()) {
        DeclaredType resolved = MoreTypes.asDeclared(resolvedType.get());
        checkState(types.isSameType(types.erasure(resolved), types.erasure(enclosingCxtorType)),
            "erased expected type: %s, erased actual type: %s",
            types.erasure(resolved), types.erasure(enclosingCxtorType));
        cxtorType = MoreTypes.asExecutable(types.asMemberOf(resolved, constructorElement));
        enclosingCxtorType = resolved;
      }

      Key key = keyFactory.forInjectConstructorWithResolvedType(enclosingCxtorType);
      checkArgument(!key.qualifier().isPresent());
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(enclosingCxtorType,
              constructorElement.getParameters(),
              cxtorType.getParameterTypes());
      Optional<DependencyRequest> membersInjectionRequest =
          membersInjectionRequest(enclosingCxtorType);
      Optional<AnnotationMirror> scope =
          getScopeAnnotation(constructorElement.getEnclosingElement());

      TypeElement bindingTypeElement =
          MoreElements.asType(constructorElement.getEnclosingElement());

      return new AutoValue_ProvisionBinding(
          key,
          constructorElement,
          dependencies,
          findBindingPackage(key),
          hasNonDefaultTypeParameters(bindingTypeElement, key.type(), types),
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Kind.INJECTION,
          Provides.Type.UNIQUE,
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          membersInjectionRequest);
    }

    private static final ImmutableSet<ElementKind> MEMBER_KINDS =
        Sets.immutableEnumSet(METHOD, FIELD);

    private Optional<DependencyRequest> membersInjectionRequest(DeclaredType type) {
      TypeElement typeElement = MoreElements.asType(type.asElement());
      if (!types.isSameType(elements.getTypeElement(Object.class.getCanonicalName()).asType(),
          typeElement.getSuperclass())) {
        return Optional.of(dependencyRequestFactory.forMembersInjectedType(type));
      }
      for (Element enclosedElement : typeElement.getEnclosedElements()) {
        if (MEMBER_KINDS.contains(enclosedElement.getKind())
            && (isAnnotationPresent(enclosedElement, Inject.class))) {
          return Optional.of(dependencyRequestFactory.forMembersInjectedType(type));
        }
      }
      return Optional.absent();
    }

    ProvisionBinding forProvidesMethod(ExecutableElement providesMethod, TypeMirror contributedBy) {
      checkNotNull(providesMethod);
      checkArgument(providesMethod.getKind().equals(METHOD));
      checkArgument(contributedBy.getKind().equals(TypeKind.DECLARED));
      Provides providesAnnotation = providesMethod.getAnnotation(Provides.class);
      checkArgument(providesAnnotation != null);
      DeclaredType declaredContainer = MoreTypes.asDeclared(contributedBy);
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(types.asMemberOf(declaredContainer, providesMethod));
      Key key = keyFactory.forProvidesMethod(resolvedMethod, providesMethod);
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              declaredContainer,
              providesMethod.getParameters(),
              resolvedMethod.getParameterTypes());
      Optional<AnnotationMirror> scope = getScopeAnnotation(providesMethod);
      return new AutoValue_ProvisionBinding(
          key,
          providesMethod,
          dependencies,
          findBindingPackage(key),
          false,
          ConfigurationAnnotations.getNullableType(providesMethod),
          Optional.of(MoreTypes.asTypeElement(declaredContainer)),
          Kind.PROVISION,
          providesAnnotation.type(),
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forImplicitMapBinding(DependencyRequest explicitRequest,
        DependencyRequest implicitRequest) {
      checkNotNull(explicitRequest);
      checkNotNull(implicitRequest);
      ImmutableSet<DependencyRequest> dependencies = ImmutableSet.of(implicitRequest);
      Optional<AnnotationMirror> scope = getScopeAnnotation(implicitRequest.requestElement());
      return new AutoValue_ProvisionBinding(
          explicitRequest.key(),
          implicitRequest.requestElement(),
          dependencies,
          findBindingPackage(explicitRequest.key()),
          false,
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Kind.SYNTHETIC_PROVISON,
          Provides.Type.MAP,
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forComponent(TypeElement componentDefinitionType) {
      checkNotNull(componentDefinitionType);
      return new AutoValue_ProvisionBinding(
          keyFactory.forComponent(componentDefinitionType.asType()),
          componentDefinitionType,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          false,
          Optional.<DeclaredType>absent(),
          Optional.<TypeElement>absent(),
          Kind.COMPONENT,
          Provides.Type.UNIQUE,
          Optional.<Equivalence.Wrapper<AnnotationMirror>>absent(),
          Optional.<DependencyRequest>absent());
    }

    ProvisionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      Optional<AnnotationMirror> scope = getScopeAnnotation(componentMethod);
      return new AutoValue_ProvisionBinding(
          keyFactory.forComponentMethod(componentMethod),
          componentMethod,
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          false,
          ConfigurationAnnotations.getNullableType(componentMethod),
          Optional.<TypeElement>absent(),
          Kind.COMPONENT_PROVISION,
          Provides.Type.UNIQUE,
          wrapOptionalInEquivalence(AnnotationMirrors.equivalence(), scope),
          Optional.<DependencyRequest>absent());
    }
  }
}
