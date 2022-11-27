package dagger.internal.codegen;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import dagger.internal.codegen.BindingType.HasBindingType;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.internal.codegen.Key.HasKey;
import dagger.internal.codegen.SourceElement.HasSourceElement;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ContributionType.indexByContributionType;

@AutoValue
abstract class ResolvedBindings implements HasBindingType, HasContributionType, HasKey {

  abstract BindingKey bindingKey();

  abstract ComponentDescriptor owningComponent();

  abstract ImmutableSetMultimap<ComponentDescriptor, ContributionBinding> allContributionBindings();

  abstract ImmutableMap<ComponentDescriptor, MembersInjectionBinding> allMembersInjectionBindings();

  @Override
  public Key key() {
    return bindingKey().key();
  }

  abstract ImmutableSet<MultibindingDeclaration> multibindingDeclarations();

  ImmutableSet<? extends Binding> bindings() {
    switch (bindingKey().kind()) {
      case CONTRIBUTION:
        return contributionBindings();

      case MEMBERS_INJECTION:
        return ImmutableSet.copyOf(membersInjectionBinding().asSet());

      default:
        throw new AssertionError(bindingKey());
    }
  }

  boolean isEmpty() {
    return bindings().isEmpty() && multibindingDeclarations().isEmpty();
  }

  ImmutableSet<? extends Binding> ownedBindings() {
    switch (bindingKey().kind()) {
      case CONTRIBUTION:
        return ownedContributionBindings();

      case MEMBERS_INJECTION:
        return ImmutableSet.copyOf(ownedMembersInjectionBinding().asSet());

      default:
        throw new AssertionError(bindingKey());
    }
  }

  ImmutableSet<ContributionBinding> contributionBindings() {
    return ImmutableSet.copyOf(allContributionBindings().values());
  }

  ImmutableSet<ContributionBinding> ownedContributionBindings() {
    return allContributionBindings().get(owningComponent());
  }

  Optional<MembersInjectionBinding> membersInjectionBinding() {
    ImmutableSet<MembersInjectionBinding> membersInjectionBindings =
        FluentIterable.from(allMembersInjectionBindings().values()).toSet();
    return membersInjectionBindings.isEmpty()
        ? Optional.<MembersInjectionBinding>absent()
        : Optional.of(Iterables.getOnlyElement(membersInjectionBindings));
  }

  Optional<MembersInjectionBinding> ownedMembersInjectionBinding() {
    return Optional.fromNullable(allMembersInjectionBindings().get(owningComponent()));
  }

  static ResolvedBindings forContributionBindings(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      Multimap<ComponentDescriptor, ? extends ContributionBinding> contributionBindings,
      Iterable<MultibindingDeclaration> multibindings) {
    checkArgument(bindingKey.kind().equals(BindingKey.Kind.CONTRIBUTION));
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>copyOf(contributionBindings),
        ImmutableMap.<ComponentDescriptor, MembersInjectionBinding>of(),
        ImmutableSet.copyOf(multibindings));
  }

  static ResolvedBindings forContributionBindings(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      ContributionBinding... ownedContributionBindings) {
    return forContributionBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>builder()
            .putAll(owningComponent, ownedContributionBindings)
            .build(),
        ImmutableSet.<MultibindingDeclaration>of());
  }

  static ResolvedBindings forMembersInjectionBinding(
      BindingKey bindingKey,
      ComponentDescriptor owningComponent,
      MembersInjectionBinding ownedMembersInjectionBinding) {
    checkArgument(bindingKey.kind().equals(BindingKey.Kind.MEMBERS_INJECTION));
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>of(),
        ImmutableMap.of(owningComponent, ownedMembersInjectionBinding),
        ImmutableSet.<MultibindingDeclaration>of());
  }

  static ResolvedBindings noBindings(BindingKey bindingKey, ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        bindingKey,
        owningComponent,
        ImmutableSetMultimap.<ComponentDescriptor, ContributionBinding>of(),
        ImmutableMap.<ComponentDescriptor, MembersInjectionBinding>of(),
        ImmutableSet.<MultibindingDeclaration>of());
  }

  ResolvedBindings asInheritedIn(ComponentDescriptor owningComponent) {
    return new AutoValue_ResolvedBindings(
        bindingKey(),
        owningComponent,
        allContributionBindings(),
        allMembersInjectionBindings(),
        multibindingDeclarations());
  }

  boolean isMultibindingContribution() {
    return contributionBindings().size() == 1
        && contributionBinding().contributionType().isMultibinding();
  }

  ContributionBinding contributionBinding() {
    return getOnlyElement(contributionBindings());
  }

  @Override
  public BindingType bindingType() {
    checkState(!isEmpty(), "empty bindings for %s", bindingKey());
    ImmutableSet<BindingType> bindingTypes =
        FluentIterable.from(concat(bindings(), multibindingDeclarations()))
            .transform(BindingType.BINDING_TYPE)
            .toSet();
    checkState(bindingTypes.size() == 1, "conflicting binding types: %s", this);
    return getOnlyElement(bindingTypes);
  }

  @Override
  public ContributionType contributionType() {
    ImmutableSet<ContributionType> types = contributionTypes();
    checkState(!types.isEmpty(), "no bindings or declarations for %s", bindingKey());
    checkState(
        types.size() == 1,
        "More than one binding present of different types for %s: %s",
        bindingKey(),
        bindingsAndDeclarationsByContributionType());
    return getOnlyElement(types);
  }

  ImmutableSet<ContributionType> contributionTypes() {
    return bindingsAndDeclarationsByContributionType().keySet();
  }

  ImmutableListMultimap<ContributionType, HasSourceElement>
      bindingsAndDeclarationsByContributionType() {
    return new ImmutableListMultimap.Builder<ContributionType, HasSourceElement>()
        .putAll(indexByContributionType(contributionBindings()))
        .putAll(indexByContributionType(multibindingDeclarations()))
        .build();
  }

  Optional<String> bindingPackage() {
    ImmutableSet.Builder<String> bindingPackagesBuilder = ImmutableSet.builder();
    for (Binding binding : bindings()) {
      bindingPackagesBuilder.addAll(binding.bindingPackage().asSet());
    }
    ImmutableSet<String> bindingPackages = bindingPackagesBuilder.build();
    switch (bindingPackages.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.of(bindingPackages.iterator().next());
      default:
        throw new IllegalArgumentException();
    }
  }

  Class<?> frameworkClass() {
    return bindingType().frameworkClass();
  }
}
