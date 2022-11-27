package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeTraverser;
import dagger.Component;
import dagger.Subcomponent;
import dagger.internal.codegen.BindingType.HasBindingType;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.producers.ProductionComponent;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.in;
import static com.google.common.base.Verify.verify;
import static dagger.internal.codegen.ComponentDescriptor.isComponentContributionMethod;
import static dagger.internal.codegen.ComponentDescriptor.isComponentProductionMethod;
import static dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor.isOfKind;
import static dagger.internal.codegen.ComponentDescriptor.ComponentMethodKind.PRODUCTION_SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.ComponentDescriptor.ComponentMethodKind.SUBCOMPONENT_BUILDER;
import static dagger.internal.codegen.ComponentDescriptor.Kind.PRODUCTION_COMPONENT;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ContributionBinding.Kind.IS_SYNTHETIC_MULTIBINDING_KIND;
import static dagger.internal.codegen.Key.indexByKey;
import static javax.lang.model.element.Modifier.STATIC;

@AutoValue
abstract class BindingGraph {
  abstract ComponentDescriptor componentDescriptor();
  abstract ImmutableMap<BindingKey, ResolvedBindings> resolvedBindings();
  abstract ImmutableMap<ExecutableElement, BindingGraph> subgraphs();

  abstract ImmutableSet<ModuleDescriptor> ownedModules();

  ImmutableSet<TypeElement> ownedModuleTypes() {
    return FluentIterable.from(ownedModules())
        .transform(ModuleDescriptor.getModuleElement())
        .toSet();
  }

  private static final TreeTraverser<BindingGraph> SUBGRAPH_TRAVERSER =
      new TreeTraverser<BindingGraph>() {
        @Override
        public Iterable<BindingGraph> children(BindingGraph node) {
          return node.subgraphs().values();
        }
      };

  ImmutableSet<TypeElement> componentRequirements() {
    return SUBGRAPH_TRAVERSER
        .preOrderTraversal(this)
        .transformAndConcat(
            new Function<BindingGraph, Iterable<ResolvedBindings>>() {
              @Override
              public Iterable<ResolvedBindings> apply(BindingGraph input) {
                return input.resolvedBindings().values();
              }
            })
        .transformAndConcat(
            new Function<ResolvedBindings, Set<ContributionBinding>>() {
              @Override
              public Set<ContributionBinding> apply(ResolvedBindings input) {
                return input.contributionBindings();
              }
            })
        .transformAndConcat(
            new Function<ContributionBinding, Set<TypeElement>>() {
              @Override
              public Set<TypeElement> apply(ContributionBinding input) {
                return input.bindingElement().getModifiers().contains(STATIC)
                    ? ImmutableSet.<TypeElement>of()
                    : input.contributedBy().asSet();
              }
            })
        .filter(in(ownedModuleTypes()))
        .append(componentDescriptor().dependencies())
        .append(componentDescriptor().executorDependency().asSet())
        .toSet();
  }

  ImmutableSet<ComponentDescriptor> componentDescriptors() {
    return SUBGRAPH_TRAVERSER
        .preOrderTraversal(this)
        .transform(
            new Function<BindingGraph, ComponentDescriptor>() {
              @Override
              public ComponentDescriptor apply(BindingGraph graph) {
                return graph.componentDescriptor();
              }
            })
        .toSet();
  }

  ImmutableSet<TypeElement> availableDependencies() {
    return new ImmutableSet.Builder<TypeElement>()
        .addAll(componentDescriptor().transitiveModuleTypes())
        .addAll(componentDescriptor().dependencies())
        .addAll(componentDescriptor().executorDependency().asSet())
        .build();
  }

  static final class Factory {
    private final Elements elements;
    private final InjectBindingRegistry injectBindingRegistry;
    private final Key.Factory keyFactory;
    private final ProvisionBinding.Factory provisionBindingFactory;
    private final ProductionBinding.Factory productionBindingFactory;

    Factory(Elements elements,
        InjectBindingRegistry injectBindingRegistry,
        Key.Factory keyFactory,
        ProvisionBinding.Factory provisionBindingFactory,
        ProductionBinding.Factory productionBindingFactory) {
      this.elements = elements;
      this.injectBindingRegistry = injectBindingRegistry;
      this.keyFactory = keyFactory;
      this.provisionBindingFactory = provisionBindingFactory;
      this.productionBindingFactory = productionBindingFactory;
    }

    BindingGraph create(ComponentDescriptor componentDescriptor) {
      return create(Optional.<Resolver>absent(), componentDescriptor);
    }

    private BindingGraph create(
        Optional<Resolver> parentResolver, ComponentDescriptor componentDescriptor) {
      ImmutableSet.Builder<ContributionBinding> explicitBindingsBuilder = ImmutableSet.builder();

      TypeElement componentDefinitionType = componentDescriptor.componentDefinitionType();
      explicitBindingsBuilder.add(provisionBindingFactory.forComponent(componentDefinitionType));

      if (componentDescriptor.executorDependency().isPresent()) {
        explicitBindingsBuilder.add(
            provisionBindingFactory.forExecutorDependency(componentDefinitionType));
      }

      Optional<AnnotationMirror> componentMirror =
          getAnnotationMirror(componentDefinitionType, Component.class)
              .or(getAnnotationMirror(componentDefinitionType, ProductionComponent.class));
      ImmutableSet<TypeElement> componentDependencyTypes = componentMirror.isPresent()
          ? MoreTypes.asTypeElements(getComponentDependencies(componentMirror.get()))
          : ImmutableSet.<TypeElement>of();
      for (TypeElement componentDependency : componentDependencyTypes) {
        explicitBindingsBuilder.add(provisionBindingFactory.forComponent(componentDependency));
        List<ExecutableElement> dependencyMethods =
            ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
        for (ExecutableElement method : dependencyMethods) {
          if (isComponentContributionMethod(elements, method)) {
            explicitBindingsBuilder.add(
                componentDescriptor.kind().equals(PRODUCTION_COMPONENT)
                        && isComponentProductionMethod(elements, method)
                    ? productionBindingFactory.forComponentMethod(method)
                    : provisionBindingFactory.forComponentMethod(method));
          }
        }
      }

      for (ComponentMethodDescriptor subcomponentMethodDescriptor :
          Iterables.filter(
              componentDescriptor.subcomponents().keySet(),
              isOfKind(SUBCOMPONENT_BUILDER, PRODUCTION_SUBCOMPONENT_BUILDER))) {
        explicitBindingsBuilder.add(
            provisionBindingFactory.forSubcomponentBuilderMethod(
                subcomponentMethodDescriptor.methodElement(),
                componentDescriptor.componentDefinitionType()));
      }

      ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarations =
          ImmutableSet.builder();

      for (ModuleDescriptor moduleDescriptor : componentDescriptor.transitiveModules()) {
        explicitBindingsBuilder.addAll(moduleDescriptor.bindings());
        multibindingDeclarations.addAll(moduleDescriptor.multibindingDeclarations());
      }

      Resolver requestResolver =
          new Resolver(
              parentResolver,
              componentDescriptor,
              indexByKey(explicitBindingsBuilder.build()),
              indexByKey(multibindingDeclarations.build()));
      for (ComponentMethodDescriptor componentMethod : componentDescriptor.componentMethods()) {
        Optional<DependencyRequest> componentMethodRequest = componentMethod.dependencyRequest();
        if (componentMethodRequest.isPresent()) {
          requestResolver.resolve(componentMethodRequest.get());
        }
      }

      ImmutableMap.Builder<ExecutableElement, BindingGraph> subgraphsBuilder =
          ImmutableMap.builder();
      for (Entry<ComponentMethodDescriptor, ComponentDescriptor> subcomponentEntry :
          componentDescriptor.subcomponents().entrySet()) {
        subgraphsBuilder.put(
            subcomponentEntry.getKey().methodElement(),
            create(Optional.of(requestResolver), subcomponentEntry.getValue()));
      }

      for (ResolvedBindings resolvedBindings : requestResolver.getResolvedBindings().values()) {
        verify(
            resolvedBindings.owningComponent().equals(componentDescriptor),
            "%s is not owned by %s",
            resolvedBindings,
            componentDescriptor);
      }

      return new AutoValue_BindingGraph(
          componentDescriptor,
          requestResolver.getResolvedBindings(),
          subgraphsBuilder.build(),
          requestResolver.getOwnedModules());
    }

    private final class Resolver {
      final Optional<Resolver> parentResolver;
      final ComponentDescriptor componentDescriptor;
      final ImmutableSetMultimap<Key, ContributionBinding> explicitBindings;
      final ImmutableSet<ContributionBinding> explicitBindingsSet;
      final ImmutableSetMultimap<Key, ContributionBinding> explicitMultibindings;
      final ImmutableSetMultimap<Key, MultibindingDeclaration> multibindingDeclarations;
      final Map<BindingKey, ResolvedBindings> resolvedBindings;
      final Deque<BindingKey> cycleStack = new ArrayDeque<>();
      final Cache<BindingKey, Boolean> dependsOnLocalMultibindingsCache =
          CacheBuilder.newBuilder().<BindingKey, Boolean>build();
      final Cache<Binding, Boolean> bindingDependsOnLocalMultibindingsCache =
          CacheBuilder.newBuilder().<Binding, Boolean>build();

      Resolver(
          Optional<Resolver> parentResolver,
          ComponentDescriptor componentDescriptor,
          ImmutableSetMultimap<Key, ContributionBinding> explicitBindings,
          ImmutableSetMultimap<Key, MultibindingDeclaration> multibindingDeclarations) {
        assert parentResolver != null;
        this.parentResolver = parentResolver;
        assert componentDescriptor != null;
        this.componentDescriptor = componentDescriptor;
        assert explicitBindings != null;
        this.explicitBindings = explicitBindings;
        this.explicitBindingsSet = ImmutableSet.copyOf(explicitBindings.values());
        assert multibindingDeclarations != null;
        this.multibindingDeclarations = multibindingDeclarations;
        this.resolvedBindings = Maps.newLinkedHashMap();

        ImmutableSetMultimap.Builder<Key, ContributionBinding> explicitMultibindingsBuilder =
            ImmutableSetMultimap.builder();
        for (ContributionBinding binding : explicitBindingsSet) {
          if (binding.key().bindingMethod().isPresent()) {
            explicitMultibindingsBuilder.put(binding.key().withoutBindingMethod(), binding);
          }
        }
        this.explicitMultibindings = explicitMultibindingsBuilder.build();
      }

      ResolvedBindings lookUpBindings(DependencyRequest request) {
        BindingKey bindingKey = request.bindingKey();
        switch (bindingKey.kind()) {
          case CONTRIBUTION:
            Set<ContributionBinding> contributionBindings = new LinkedHashSet<>();
            Set<ContributionBinding> multibindings = new LinkedHashSet<>();
            ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarationsBuilder =
                ImmutableSet.builder();

            contributionBindings.addAll(getExplicitBindings(bindingKey.key()));
            multibindings.addAll(getExplicitMultibindings(bindingKey.key()));
            multibindingDeclarationsBuilder.addAll(getMultibindingDeclarations(bindingKey.key()));

            Optional<Key> implicitSetKey = keyFactory.implicitSetKeyFromProduced(bindingKey.key());
            contributionBindings.addAll(getExplicitBindings(implicitSetKey));
            multibindings.addAll(getExplicitMultibindings(implicitSetKey));
            multibindingDeclarationsBuilder.addAll(getMultibindingDeclarations(implicitSetKey));

            ImmutableSet<MultibindingDeclaration> multibindingDeclarations =
                multibindingDeclarationsBuilder.build();

            Optional<Key> implicitMapProviderKey =
                keyFactory.implicitMapProviderKeyFrom(bindingKey.key());
            ImmutableSet<ContributionBinding> explicitProviderMapBindings =
                getExplicitMultibindings(implicitMapProviderKey);
            ImmutableSet<MultibindingDeclaration> explicitProviderMultibindingDeclarations =
                getMultibindingDeclarations(implicitMapProviderKey);

            Optional<Key> implicitMapProducerKey =
                keyFactory.implicitMapProducerKeyFrom(bindingKey.key());
            ImmutableSet<ContributionBinding> explicitProducerMapBindings =
                getExplicitMultibindings(implicitMapProducerKey);
            ImmutableSet<MultibindingDeclaration> explicitProducerMultibindingDeclarations =
                getMultibindingDeclarations(implicitMapProducerKey);

            if (!explicitProducerMapBindings.isEmpty()
                || !explicitProducerMultibindingDeclarations.isEmpty()) {

              contributionBindings.add(
                  productionBindingFactory.implicitMapOfProducerBinding(request));
            } else if (!explicitProviderMapBindings.isEmpty()
                || !explicitProviderMultibindingDeclarations.isEmpty()) {
              contributionBindings.add(
                  provisionBindingFactory.implicitMapOfProviderBinding(request));
            }

            Iterable<? extends HasBindingType> multibindingsAndDeclarations =
                Iterables.concat(multibindings, multibindingDeclarations);
            if (Iterables.any(
                multibindingsAndDeclarations, BindingType.isOfType(BindingType.PRODUCTION))) {
              contributionBindings.add(
                  productionBindingFactory.syntheticMultibinding(request, multibindings));
            } else if (Iterables.any(
                multibindingsAndDeclarations, BindingType.isOfType(BindingType.PROVISION))) {
              contributionBindings.add(
                  provisionBindingFactory.syntheticMultibinding(request, multibindings));
            }

            if (contributionBindings.isEmpty()
                && multibindings.isEmpty()
                && multibindingDeclarations.isEmpty()) {
              contributionBindings.addAll(
                  injectBindingRegistry.getOrFindProvisionBinding(bindingKey.key()).asSet());
            }

            return ResolvedBindings.forContributionBindings(
                bindingKey,
                componentDescriptor,
                indexBindingsByOwningComponent(request, ImmutableSet.copyOf(contributionBindings)),
                multibindingDeclarations);

          case MEMBERS_INJECTION:
            Optional<MembersInjectionBinding> binding =
                injectBindingRegistry.getOrFindMembersInjectionBinding(bindingKey.key());
            return binding.isPresent()
                ? ResolvedBindings.forMembersInjectionBinding(
                    bindingKey, componentDescriptor, binding.get())
                : ResolvedBindings.noBindings(bindingKey, componentDescriptor);
          default:
            throw new AssertionError();
        }
      }

      private ImmutableSetMultimap<ComponentDescriptor, ContributionBinding>
          indexBindingsByOwningComponent(
              DependencyRequest request, Iterable<? extends ContributionBinding> bindings) {
        ImmutableSetMultimap.Builder<ComponentDescriptor, ContributionBinding> index =
            ImmutableSetMultimap.builder();
        for (ContributionBinding binding : bindings) {
          index.put(getOwningComponent(request, binding), binding);
        }
        return index.build();
      }

      private ComponentDescriptor getOwningComponent(
          DependencyRequest request, ContributionBinding binding) {
        return isResolvedInParent(request, binding)
                && !new MultibindingDependencies().dependsOnLocalMultibindings(binding)
            ? getOwningResolver(binding).get().componentDescriptor
            : componentDescriptor;
      }

      private boolean isResolvedInParent(DependencyRequest request, ContributionBinding binding) {
        Optional<Resolver> owningResolver = getOwningResolver(binding);
        if (owningResolver.isPresent() && !owningResolver.get().equals(this)) {
          parentResolver.get().resolve(request);
          return true;
        } else {
          return false;
        }
      }

      private Optional<Resolver> getOwningResolver(ContributionBinding binding) {
        for (Resolver requestResolver : getResolverLineage().reverse()) {
          if (requestResolver.explicitBindingsSet.contains(binding)) {
            return Optional.of(requestResolver);
          }
        }

        Optional<Scope> bindingScope = binding.scope();
        if (bindingScope.isPresent()) {
          for (Resolver requestResolver : getResolverLineage().reverse()) {
            if (requestResolver.componentDescriptor.scopes().contains(bindingScope.get())) {
              return Optional.of(requestResolver);
            }
          }
        }
        return Optional.absent();
      }

      private ImmutableList<Resolver> getResolverLineage() {
        List<Resolver> resolverList = Lists.newArrayList();
        for (Optional<Resolver> currentResolver = Optional.of(this);
            currentResolver.isPresent();
            currentResolver = currentResolver.get().parentResolver) {
          resolverList.add(currentResolver.get());
        }
        return ImmutableList.copyOf(Lists.reverse(resolverList));
      }

      private ImmutableSet<ContributionBinding> getExplicitBindings(Key requestKey) {
        ImmutableSet.Builder<ContributionBinding> explicitBindingsForKey = ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          explicitBindingsForKey.addAll(resolver.explicitBindings.get(requestKey));
        }
        return explicitBindingsForKey.build();
      }

      private ImmutableSet<ContributionBinding> getExplicitBindings(Optional<Key> optionalKey) {
        return optionalKey.isPresent()
            ? getExplicitBindings(optionalKey.get())
            : ImmutableSet.<ContributionBinding>of();
      }

      private ImmutableSet<ContributionBinding> getExplicitMultibindings(Key requestKey) {
        ImmutableSet.Builder<ContributionBinding> explicitMultibindingsForKey =
            ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          explicitMultibindingsForKey.addAll(resolver.explicitMultibindings.get(requestKey));
        }
        return explicitMultibindingsForKey.build();
      }

      private ImmutableSet<ContributionBinding> getExplicitMultibindings(
          Optional<Key> optionalKey) {
        return optionalKey.isPresent()
            ? getExplicitMultibindings(optionalKey.get())
            : ImmutableSet.<ContributionBinding>of();
      }

      private ImmutableSet<MultibindingDeclaration> getMultibindingDeclarations(Key key) {
        ImmutableSet.Builder<MultibindingDeclaration> multibindingDeclarations =
            ImmutableSet.builder();
        for (Resolver resolver : getResolverLineage()) {
          multibindingDeclarations.addAll(resolver.multibindingDeclarations.get(key));
        }
        return multibindingDeclarations.build();
      }

      private ImmutableSet<MultibindingDeclaration> getMultibindingDeclarations(
          Optional<Key> optionalKey) {
        return optionalKey.isPresent()
            ? getMultibindingDeclarations(optionalKey.get())
            : ImmutableSet.<MultibindingDeclaration>of();
      }

      private Optional<ResolvedBindings> getPreviouslyResolvedBindings(
          final BindingKey bindingKey) {
        Optional<ResolvedBindings> result = Optional.fromNullable(resolvedBindings.get(bindingKey));
        if (result.isPresent()) {
          return result;
        } else if (parentResolver.isPresent()) {
          return parentResolver.get().getPreviouslyResolvedBindings(bindingKey);
        } else {
          return Optional.absent();
        }
      }

      void resolve(DependencyRequest request) {
        BindingKey bindingKey = request.bindingKey();

        if (cycleStack.contains(bindingKey)) {
          return;
        }

        if (resolvedBindings.containsKey(bindingKey)) {
          return;
        }

        if (getPreviouslyResolvedBindings(bindingKey).isPresent()
            && !new MultibindingDependencies().dependsOnLocalMultibindings(bindingKey)
            && getExplicitBindings(bindingKey.key()).isEmpty()) {
          parentResolver.get().resolve(request);
          ResolvedBindings inheritedBindings =
              getPreviouslyResolvedBindings(bindingKey).get().asInheritedIn(componentDescriptor);
          resolvedBindings.put(bindingKey, inheritedBindings);
          return;
        }

        cycleStack.push(bindingKey);
        try {
          ResolvedBindings bindings = lookUpBindings(request);
          for (Binding binding : bindings.ownedBindings()) {
            for (DependencyRequest dependency : binding.implicitDependencies()) {
              resolve(dependency);
            }
          }
          resolvedBindings.put(bindingKey, bindings);
        } finally {
          cycleStack.pop();
        }
      }

      ImmutableMap<BindingKey, ResolvedBindings> getResolvedBindings() {
        ImmutableMap.Builder<BindingKey, ResolvedBindings> resolvedBindingsBuilder =
            ImmutableMap.builder();
        resolvedBindingsBuilder.putAll(resolvedBindings);
        if (parentResolver.isPresent()) {
          Collection<ResolvedBindings> bindingsResolvedInParent =
              Maps.difference(parentResolver.get().getResolvedBindings(), resolvedBindings)
                  .entriesOnlyOnLeft()
                  .values();
          for (ResolvedBindings resolvedInParent : bindingsResolvedInParent) {
            resolvedBindingsBuilder.put(
                resolvedInParent.bindingKey(),
                resolvedInParent.asInheritedIn(componentDescriptor));
          }
        }
        return resolvedBindingsBuilder.build();
      }

      ImmutableSet<ModuleDescriptor> getInheritedModules() {
        return parentResolver.isPresent()
            ? Sets.union(
                    parentResolver.get().getInheritedModules(),
                    parentResolver.get().componentDescriptor.transitiveModules())
                .immutableCopy()
            : ImmutableSet.<ModuleDescriptor>of();
      }

      ImmutableSet<ModuleDescriptor> getOwnedModules() {
        return Sets.difference(componentDescriptor.transitiveModules(), getInheritedModules())
            .immutableCopy();
      }

      private final class MultibindingDependencies {
        private final Set<Object> cycleChecker = new HashSet<>();

        boolean dependsOnLocalMultibindings(final BindingKey bindingKey) {
          checkArgument(
              getPreviouslyResolvedBindings(bindingKey).isPresent(),
              "no previously resolved bindings in %s for %s",
              Resolver.this,
              bindingKey);
          if (!cycleChecker.add(bindingKey)) {
            return false;
          }
          try {
            return dependsOnLocalMultibindingsCache.get(
                bindingKey,
                new Callable<Boolean>() {
                  @Override
                  public Boolean call() {
                    ResolvedBindings previouslyResolvedBindings =
                        getPreviouslyResolvedBindings(bindingKey).get();
                    if (isMultibindingsWithLocalContributions(previouslyResolvedBindings)) {
                      return true;
                    }

                    for (Binding binding : previouslyResolvedBindings.bindings()) {
                      if (dependsOnLocalMultibindings(binding)) {
                        return true;
                      }
                    }
                    return false;
                  }
                });
          } catch (ExecutionException e) {
            throw new AssertionError(e);
          }
        }

        boolean dependsOnLocalMultibindings(final Binding binding) {
          if (!cycleChecker.add(binding)) {
            return false;
          }
          try {
            return bindingDependsOnLocalMultibindingsCache.get(
                binding,
                new Callable<Boolean>() {
                  @Override
                  public Boolean call() {
                    if (!binding.scope().isPresent()
                        && !binding.bindingType().equals(BindingType.PRODUCTION)) {
                      for (DependencyRequest dependency : binding.implicitDependencies()) {
                        if (dependsOnLocalMultibindings(dependency.bindingKey())) {
                          return true;
                        }
                      }
                    }
                    return false;
                  }
                });
          } catch (ExecutionException e) {
            throw new AssertionError(e);
          }
        }

        private boolean isMultibindingsWithLocalContributions(ResolvedBindings resolvedBindings) {
          return FluentIterable.from(resolvedBindings.contributionBindings())
                  .transform(ContributionBinding.KIND)
                  .anyMatch(IS_SYNTHETIC_MULTIBINDING_KIND)
              && explicitMultibindings.containsKey(resolvedBindings.key());
        }
      }
    }
  }
}
