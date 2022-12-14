package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import dagger.Provides;
import dagger.producers.Produces;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.lang.model.element.ElementKind.METHOD;

@AutoValue
abstract class ProductionBinding extends ContributionBinding {

  @Override
  public BindingType bindingType() {
    return BindingType.PRODUCTION;
  }

  @Override
  Optional<ProductionBinding> unresolved() {
    return Optional.absent();
  }

  @Override
  Provides.Type provisionType() {
    return Provides.Type.valueOf(productionType().name());
  }

  @Override
  Set<DependencyRequest> implicitDependencies() {
    if (!executorRequest().isPresent() && !monitorRequest().isPresent()) {
      return super.implicitDependencies();
    } else {
      return Sets.union(
          Sets.union(executorRequest().asSet(), monitorRequest().asSet()),
          super.implicitDependencies());
    }
  }

  abstract Produces.Type productionType();

  abstract ImmutableList<? extends TypeMirror> thrownTypes();

  abstract Optional<DependencyRequest> executorRequest();

  abstract Optional<DependencyRequest> monitorRequest();

  static final class Factory {
    private final Types types;
    private final Key.Factory keyFactory;
    private final DependencyRequest.Factory dependencyRequestFactory;

    Factory(
        Types types, Key.Factory keyFactory, DependencyRequest.Factory dependencyRequestFactory) {
      this.types = types;
      this.keyFactory = keyFactory;
      this.dependencyRequestFactory = dependencyRequestFactory;
    }

    ProductionBinding forProducesMethod(
        ExecutableElement producesMethod, TypeElement contributedBy) {
      checkArgument(producesMethod.getKind().equals(METHOD));
      Produces producesAnnotation = producesMethod.getAnnotation(Produces.class);
      checkArgument(producesAnnotation != null);
      SourceElement sourceElement = SourceElement.forElement(producesMethod, contributedBy);
      Key key = keyFactory.forProducesMethod(sourceElement);
      ExecutableType resolvedMethod =
          MoreTypes.asExecutable(sourceElement.asMemberOfContributingType(types));
      ImmutableSet<DependencyRequest> dependencies =
          dependencyRequestFactory.forRequiredResolvedVariables(
              MoreTypes.asDeclared(contributedBy.asType()),
              producesMethod.getParameters(),
              resolvedMethod.getParameterTypes());
      DependencyRequest executorRequest =
          dependencyRequestFactory.forProductionImplementationExecutor();
      DependencyRequest monitorRequest =
          dependencyRequestFactory.forProductionComponentMonitorProvider();
      Kind kind = MoreTypes.isTypeOf(ListenableFuture.class, producesMethod.getReturnType())
          ? Kind.FUTURE_PRODUCTION
          : Kind.IMMEDIATE;
      return new AutoValue_ProductionBinding(
          sourceElement,
          key,
          dependencies,
          findBindingPackage(key),
          ConfigurationAnnotations.getNullableType(producesMethod),
          Optional.<DependencyRequest>absent(),
          kind,
          producesAnnotation.type(),
          ImmutableList.copyOf(producesMethod.getThrownTypes()),
          Optional.of(executorRequest),
          Optional.of(monitorRequest));
    }

    ProductionBinding implicitMapOfProducerBinding(DependencyRequest mapOfValueRequest) {
      checkNotNull(mapOfValueRequest);
      Optional<Key> implicitMapOfProducerKey =
          keyFactory.implicitMapProducerKeyFrom(mapOfValueRequest.key());
      checkArgument(
          implicitMapOfProducerKey.isPresent(), "%s is not for a Map<K, V>", mapOfValueRequest);
      DependencyRequest implicitMapOfProducerRequest =
          dependencyRequestFactory.forImplicitMapBinding(
              mapOfValueRequest, implicitMapOfProducerKey.get());
      return new AutoValue_ProductionBinding(
          SourceElement.forElement(implicitMapOfProducerRequest.requestElement()),
          mapOfValueRequest.key(),
          ImmutableSet.of(implicitMapOfProducerRequest),
          findBindingPackage(mapOfValueRequest.key()),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          Kind.SYNTHETIC_MAP,
          Produces.Type.UNIQUE,
          ImmutableList.<TypeMirror>of(),
          Optional.<DependencyRequest>absent(),
          Optional.<DependencyRequest>absent());
    }

    ProductionBinding syntheticMultibinding(
        final DependencyRequest request, Iterable<ContributionBinding> multibindingContributions) {
      return new AutoValue_ProductionBinding(
          SourceElement.forElement(request.requestElement()),
          request.key(),
          dependencyRequestFactory.forMultibindingContributions(request, multibindingContributions),
          findBindingPackage(request.key()),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          Kind.forMultibindingRequest(request),
          Produces.Type.UNIQUE,
          ImmutableList.<TypeMirror>of(),
          Optional.<DependencyRequest>absent(),
          Optional.<DependencyRequest>absent());
    }

    ProductionBinding forComponentMethod(ExecutableElement componentMethod) {
      checkNotNull(componentMethod);
      checkArgument(componentMethod.getKind().equals(METHOD));
      checkArgument(componentMethod.getParameters().isEmpty());
      checkArgument(MoreTypes.isTypeOf(ListenableFuture.class, componentMethod.getReturnType()));
      return new AutoValue_ProductionBinding(
          SourceElement.forElement(componentMethod),
          keyFactory.forProductionComponentMethod(componentMethod),
          ImmutableSet.<DependencyRequest>of(),
          Optional.<String>absent(),
          Optional.<DeclaredType>absent(),
          Optional.<DependencyRequest>absent(),
          Kind.COMPONENT_PRODUCTION,
          Produces.Type.UNIQUE,
          ImmutableList.copyOf(componentMethod.getThrownTypes()),
          Optional.<DependencyRequest>absent(),
          Optional.<DependencyRequest>absent());
    }
  }
}
