package dagger.internal.codegen;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import java.util.Collection;
import javax.inject.Provider;
import javax.lang.model.element.Element;

import static com.google.common.collect.Iterables.getOnlyElement;

@AutoValue
abstract class FrameworkDependency {

  abstract BindingKey bindingKey();

  abstract Class<?> frameworkClass();

  abstract ImmutableSet<DependencyRequest> dependencyRequests();

  static ImmutableSet<FrameworkDependency> frameworkDependenciesForBinding(Binding binding) {
    DependencyRequestMapper dependencyRequestMapper =
        DependencyRequestMapper.forBindingType(binding.bindingType());
    ImmutableSet.Builder<FrameworkDependency> frameworkDependencies = ImmutableSet.builder();
    for (Collection<DependencyRequest> requests : groupByUnresolvedKey(binding)) {
      frameworkDependencies.add(
          new AutoValue_FrameworkDependency(
              getOnlyElement(
                  FluentIterable.from(requests)
                      .transform(DependencyRequest.BINDING_KEY_FUNCTION)
                      .toSet()),
              dependencyRequestMapper.getFrameworkClass(requests),
              ImmutableSet.copyOf(requests)));
    }
    return frameworkDependencies.build();
  }

  private static ImmutableList<Collection<DependencyRequest>> groupByUnresolvedKey(
      Binding binding) {
    if (!binding.unresolved().isPresent()) {
      return groupByKey(binding, Functions.<DependencyRequest>identity());
    }

    final ImmutableMap<Element, DependencyRequest> resolvedDependencies =
        Maps.uniqueIndex(
            binding.implicitDependencies(),
            new Function<DependencyRequest, Element>() {
              @Override
              public Element apply(DependencyRequest dependencyRequest) {
                return dependencyRequest.requestElement();
              }
            });
    return groupByKey(
        binding.unresolved().get(),
        new Function<DependencyRequest, DependencyRequest>() {
          @Override
          public DependencyRequest apply(DependencyRequest unresolvedRequest) {
            return resolvedDependencies.get(unresolvedRequest.requestElement());
          }
        });
  }

  private static ImmutableList<Collection<DependencyRequest>> groupByKey(
      Binding binding, Function<DependencyRequest, DependencyRequest> transformer) {
    ImmutableSetMultimap.Builder<BindingKey, DependencyRequest> dependenciesByKeyBuilder =
        ImmutableSetMultimap.builder();
    for (DependencyRequest dependency : binding.implicitDependencies()) {
      dependenciesByKeyBuilder.put(dependency.bindingKey(), transformer.apply(dependency));
    }
    return ImmutableList.copyOf(
        dependenciesByKeyBuilder
            .orderValuesBy(SourceFiles.DEPENDENCY_ORDERING)
            .build()
            .asMap()
            .values());
  }
}
