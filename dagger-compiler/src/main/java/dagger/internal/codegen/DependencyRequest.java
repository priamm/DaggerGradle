package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Provides;
import dagger.producers.Produced;
import dagger.producers.Producer;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import static com.google.auto.common.MoreTypes.isTypeOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.type.TypeKind.DECLARED;

@AutoValue
abstract class DependencyRequest {
  enum Kind {
    INSTANCE,
    PROVIDER,
    LAZY,
    MEMBERS_INJECTOR,
    PRODUCER,
    PRODUCED,
    FUTURE,
  }

  abstract Kind kind();
  abstract Key key();

  BindingKey bindingKey() {
    switch (kind()) {
      case INSTANCE:
      case LAZY:
      case PROVIDER:
      case PRODUCER:
      case PRODUCED:
      case FUTURE:
        return BindingKey.create(BindingKey.Kind.CONTRIBUTION, key());
      case MEMBERS_INJECTOR:
        return BindingKey.create(BindingKey.Kind.MEMBERS_INJECTION, key());
      default:
        throw new AssertionError();
    }
  }

  abstract Element requestElement();

  abstract DeclaredType enclosingType();

  abstract boolean isNullable();

  static final class Factory {
    private final Key.Factory keyFactory;

    Factory(Key.Factory keyFactory) {
      this.keyFactory = keyFactory;
    }

    ImmutableSet<DependencyRequest> forRequiredResolvedVariables(DeclaredType container,
        List<? extends VariableElement> variables, List<? extends TypeMirror> resolvedTypes) {
      checkState(resolvedTypes.size() == variables.size());
      ImmutableSet.Builder<DependencyRequest> builder = ImmutableSet.builder();
      for (int i = 0; i < variables.size(); i++) {
        builder.add(forRequiredResolvedVariable(container, variables.get(i), resolvedTypes.get(i)));
      }
      return builder.build();
    }

    ImmutableSet<DependencyRequest> forRequiredVariables(
        List<? extends VariableElement> variables) {
      return FluentIterable.from(variables)
          .transform(new Function<VariableElement, DependencyRequest>() {
            @Override public DependencyRequest apply(VariableElement input) {
              return forRequiredVariable(input);
            }
          })
          .toSet();
    }

    DependencyRequest forImplicitMapBinding(DependencyRequest delegatingRequest, Key delegateKey) {
      checkNotNull(delegatingRequest);
      return new AutoValue_DependencyRequest(Kind.PROVIDER, delegateKey,
          delegatingRequest.requestElement(),
          getEnclosingType(delegatingRequest.requestElement()),
          false);
    }

    DependencyRequest forRequiredVariable(VariableElement variableElement) {
      checkNotNull(variableElement);
      TypeMirror type = variableElement.asType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(variableElement, type, qualifier,
          getEnclosingType(variableElement));
    }

    DependencyRequest forRequiredResolvedVariable(DeclaredType container,
        VariableElement variableElement,
        TypeMirror resolvedType) {
      checkNotNull(variableElement);
      checkNotNull(resolvedType);
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(variableElement, resolvedType, qualifier, container);
    }

    DependencyRequest forComponentProvisionMethod(ExecutableElement provisionMethod,
        ExecutableType provisionMethodType) {
      checkNotNull(provisionMethod);
      checkNotNull(provisionMethodType);
      checkArgument(provisionMethod.getParameters().isEmpty(),
          "Component provision methods must be empty: " + provisionMethod);
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(provisionMethod);
      return newDependencyRequest(provisionMethod, provisionMethodType.getReturnType(), qualifier,
          getEnclosingType(provisionMethod));
    }

    DependencyRequest forComponentProductionMethod(ExecutableElement productionMethod,
        ExecutableType productionMethodType) {
      checkNotNull(productionMethod);
      checkNotNull(productionMethodType);
      checkArgument(productionMethod.getParameters().isEmpty(),
          "Component production methods must be empty: %s", productionMethod);
      TypeMirror type = productionMethodType.getReturnType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(productionMethod);
      DeclaredType container = getEnclosingType(productionMethod);
      if (isTypeOf(ListenableFuture.class, type)) {
        return new AutoValue_DependencyRequest(
            Kind.FUTURE,
            keyFactory.forQualifiedType(qualifier,
                Iterables.getOnlyElement(((DeclaredType) type).getTypeArguments())),
            productionMethod,
            container,
            false);
      } else {
        return newDependencyRequest(productionMethod, type, qualifier, container);
      }
    }

    DependencyRequest forComponentMembersInjectionMethod(ExecutableElement membersInjectionMethod,
        ExecutableType membersInjectionMethodType) {
      checkNotNull(membersInjectionMethod);
      checkNotNull(membersInjectionMethodType);
      Optional<AnnotationMirror> qualifier =
          InjectionAnnotations.getQualifier(membersInjectionMethod);
      checkArgument(!qualifier.isPresent());
      TypeMirror returnType = membersInjectionMethodType.getReturnType();
      if (returnType.getKind().equals(DECLARED)
          && MoreTypes.isTypeOf(MembersInjector.class, returnType)) {
        return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
            keyFactory.forMembersInjectedType(
                Iterables.getOnlyElement(((DeclaredType) returnType).getTypeArguments())),
                membersInjectionMethod,
                getEnclosingType(membersInjectionMethod),
                false);
      } else {
        return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
            keyFactory.forMembersInjectedType(
                Iterables.getOnlyElement(membersInjectionMethodType.getParameterTypes())),
                membersInjectionMethod,
                getEnclosingType(membersInjectionMethod),
                false);
      }
    }

    DependencyRequest forMembersInjectedType(DeclaredType type) {
      return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
          keyFactory.forMembersInjectedType(type),
          type.asElement(),
          type,
          false);
    }

    private DependencyRequest newDependencyRequest(Element requestElement,
        TypeMirror type, Optional<AnnotationMirror> qualifier, DeclaredType container) {
      KindAndType kindAndType = extractKindAndType(type);
      if (kindAndType.kind().equals(Kind.MEMBERS_INJECTOR)) {
        checkArgument(!qualifier.isPresent());
      }

      boolean allowsNull = !kindAndType.kind().equals(Kind.INSTANCE)
          || ConfigurationAnnotations.getNullableType(requestElement).isPresent();
      return new AutoValue_DependencyRequest(kindAndType.kind(),
          keyFactory.forQualifiedType(qualifier, kindAndType.type()),
          requestElement,
          container,
          allowsNull);
    }

    @AutoValue
    static abstract class KindAndType {
      abstract Kind kind();
      abstract TypeMirror type();
    }

    static KindAndType extractKindAndType(TypeMirror type) {
      if (type.getKind().equals(TypeKind.TYPEVAR)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.INSTANCE, type);
      } else if (isTypeOf(Provider.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.PROVIDER,
            Iterables.getOnlyElement(((DeclaredType) type).getTypeArguments()));
      } else if (isTypeOf(Lazy.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.LAZY,
            Iterables.getOnlyElement(((DeclaredType) type).getTypeArguments()));
      } else if (isTypeOf(MembersInjector.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.MEMBERS_INJECTOR,
            Iterables.getOnlyElement(((DeclaredType) type).getTypeArguments()));
      } else if (isTypeOf(Producer.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.PRODUCER,
            Iterables.getOnlyElement(((DeclaredType) type).getTypeArguments()));
      } else if (isTypeOf(Produced.class, type)) {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.PRODUCED,
            Iterables.getOnlyElement(((DeclaredType) type).getTypeArguments()));
      } else {
        return new AutoValue_DependencyRequest_Factory_KindAndType(Kind.INSTANCE, type);
      }
    }

    static DeclaredType getEnclosingType(Element element) {
      while (!MoreElements.isType(element)) {
        element = element.getEnclosingElement();
      }
      return MoreTypes.asDeclared(element.asType());
    }
  }
}
