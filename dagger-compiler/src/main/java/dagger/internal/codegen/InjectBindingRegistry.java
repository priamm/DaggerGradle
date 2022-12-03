package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import dagger.Component;
import dagger.Provides;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.INJECT_MEMBERS;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static javax.lang.model.util.ElementFilter.constructorsIn;

final class InjectBindingRegistry {
  private final Elements elements;
  private final Types types;
  private final Messager messager;
  private final InjectConstructorValidator injectConstructorValidator;
  private final MembersInjectedTypeValidator membersInjectedTypeValidator;
  private final Key.Factory keyFactory;
  private final ProvisionBinding.Factory provisionBindingFactory;
  private final MembersInjectionBinding.Factory membersInjectionBindingFactory;

  final class BindingsCollection<B extends Binding> {
    private final BindingType bindingType;
    private final Map<Key, B> bindingsByKey = Maps.newLinkedHashMap();
    private final Deque<B> bindingsRequiringGeneration = new ArrayDeque<>();
    private final Set<Key> materializedBindingKeys = Sets.newLinkedHashSet();
    
    BindingsCollection(BindingType bindingType) {
      this.bindingType = bindingType;
    }

    void generateBindings(JavaPoetSourceFileGenerator<B> generator)
        throws SourceFileGenerationException {
      for (B binding = bindingsRequiringGeneration.poll();
          binding != null;
          binding = bindingsRequiringGeneration.poll()) {
        checkState(!binding.unresolved().isPresent());
        generator.generate(binding);
        materializedBindingKeys.add(binding.key());
      }
      bindingsByKey.clear();
    }

    B getBinding(Key key) {
      return bindingsByKey.get(key);
    }

    void tryRegisterBinding(B binding, boolean warnIfNotAlreadyGenerated) {
      tryToCacheBinding(binding);
      tryToGenerateBinding(binding, warnIfNotAlreadyGenerated);
    }

    void tryToGenerateBinding(B binding, boolean warnIfNotAlreadyGenerated) {
      if (shouldGenerateBinding(binding, generatedClassNameForBinding(binding))) {
        bindingsRequiringGeneration.offer(binding);
        if (warnIfNotAlreadyGenerated) {
          messager.printMessage(
              Kind.NOTE,
              String.format(
                  "Generating a %s for %s. "
                      + "Prefer to run the dagger processor over that class instead.",
                  bindingType.frameworkClass().getSimpleName(),
                  types.erasure(binding.key().type())));
        }
      }
    }

    private boolean shouldGenerateBinding(B binding, ClassName factoryName) {
      return !binding.unresolved().isPresent()
          && elements.getTypeElement(factoryName.toString()) == null
          && !materializedBindingKeys.contains(binding.key())
          && !bindingsRequiringGeneration.contains(binding);
    }

    private void tryToCacheBinding(B binding) {
      if (binding.unresolved().isPresent()
          || binding.bindingTypeElement().getTypeParameters().isEmpty()) {
        Key key = binding.key();
        Binding previousValue = bindingsByKey.put(key, binding);
        checkState(previousValue == null || binding.equals(previousValue),
            "couldn't register %s. %s was already registered for %s",
            binding, previousValue, key);
      }
    }
  }

  private final BindingsCollection<ProvisionBinding> provisionBindings =
      new BindingsCollection<>(BindingType.PROVISION);
  private final BindingsCollection<MembersInjectionBinding> membersInjectionBindings =
      new BindingsCollection<>(BindingType.MEMBERS_INJECTION);

  InjectBindingRegistry(
      Elements elements,
      Types types,
      Messager messager,
      InjectConstructorValidator injectConstructorValidator,
      MembersInjectedTypeValidator membersInjectedTypeValidator,
      Key.Factory keyFactory,
      ProvisionBinding.Factory provisionBindingFactory,
      MembersInjectionBinding.Factory membersInjectionBindingFactory) {
    this.elements = elements;
    this.types = types;
    this.messager = messager;
    this.injectConstructorValidator = injectConstructorValidator;
    this.membersInjectedTypeValidator = membersInjectedTypeValidator;
    this.keyFactory = keyFactory;
    this.provisionBindingFactory = provisionBindingFactory;
    this.membersInjectionBindingFactory = membersInjectionBindingFactory;
  }

  void generateSourcesForRequiredBindings(FactoryGenerator factoryGenerator,
      MembersInjectorGenerator membersInjectorGenerator) throws SourceFileGenerationException {
    provisionBindings.generateBindings(factoryGenerator);
    membersInjectionBindings.generateBindings(membersInjectorGenerator);
  }

  private void registerBinding(ProvisionBinding binding, boolean warnIfNotAlreadyGenerated) {
    provisionBindings.tryRegisterBinding(binding, warnIfNotAlreadyGenerated);
    if (binding.unresolved().isPresent()) {
      provisionBindings.tryToGenerateBinding(binding.unresolved().get(), warnIfNotAlreadyGenerated);
    }
  }

  private void registerBinding(MembersInjectionBinding binding, boolean warnIfNotAlreadyGenerated) {
    warnIfNotAlreadyGenerated =
        warnIfNotAlreadyGenerated
            && (!injectedConstructors(binding.bindingElement()).isEmpty()
                ? !binding.injectionSites().isEmpty()
                : binding.hasLocalInjectionSites());
    membersInjectionBindings.tryRegisterBinding(binding, warnIfNotAlreadyGenerated);
    if (binding.unresolved().isPresent()) {
      membersInjectionBindings.tryToGenerateBinding(
          binding.unresolved().get(), warnIfNotAlreadyGenerated);
    }
  }

  @CanIgnoreReturnValue
  Optional<ProvisionBinding> tryRegisterConstructor(ExecutableElement constructorElement) {
    return tryRegisterConstructor(constructorElement, Optional.<TypeMirror>absent(), false);
  }

  @CanIgnoreReturnValue
  private Optional<ProvisionBinding> tryRegisterConstructor(
      ExecutableElement constructorElement,
      Optional<TypeMirror> resolvedType,
      boolean warnIfNotAlreadyGenerated) {
    TypeElement typeElement = MoreElements.asType(constructorElement.getEnclosingElement());
    DeclaredType type = MoreTypes.asDeclared(typeElement.asType());
    Key key = keyFactory.forInjectConstructorWithResolvedType(type);
    ProvisionBinding cachedBinding = provisionBindings.getBinding(key);
    if (cachedBinding != null) {
      return Optional.of(cachedBinding);
    }

    ValidationReport<TypeElement> report = injectConstructorValidator.validate(constructorElement);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      ProvisionBinding binding =
          provisionBindingFactory.forInjectConstructor(constructorElement, resolvedType);
      registerBinding(binding, warnIfNotAlreadyGenerated);
      if (membersInjectionBindingFactory.hasInjectedMembers(type)) {
        tryRegisterMembersInjectedType(typeElement, resolvedType, warnIfNotAlreadyGenerated);
      }
      return Optional.of(binding);
    }
    return Optional.absent();
  }

  @CanIgnoreReturnValue
  Optional<MembersInjectionBinding> tryRegisterMembersInjectedType(TypeElement typeElement) {
    return tryRegisterMembersInjectedType(typeElement, Optional.<TypeMirror>absent(), false);
  }

  @CanIgnoreReturnValue
  private Optional<MembersInjectionBinding> tryRegisterMembersInjectedType(
      TypeElement typeElement,
      Optional<TypeMirror> resolvedType,
      boolean warnIfNotAlreadyGenerated) {
    DeclaredType type = MoreTypes.asDeclared(typeElement.asType());
    Key key = keyFactory.forInjectConstructorWithResolvedType(type);
    MembersInjectionBinding cachedBinding = membersInjectionBindings.getBinding(key);
    if (cachedBinding != null) {
      return Optional.of(cachedBinding);
    }

    ValidationReport<TypeElement> report = membersInjectedTypeValidator.validate(typeElement);
    report.printMessagesTo(messager);
    if (report.isClean()) {
      MembersInjectionBinding binding =
          membersInjectionBindingFactory.forInjectedType(type, resolvedType);
      registerBinding(binding, warnIfNotAlreadyGenerated);
      if (binding.parentKey().isPresent() && binding.injectionStrategy().equals(INJECT_MEMBERS)) {
        getOrFindMembersInjectionBinding(binding.parentKey().get());
      }
      return Optional.of(binding);
    }
    return Optional.absent();
  }

  @CanIgnoreReturnValue
  Optional<ProvisionBinding> getOrFindProvisionBinding(Key key) {
    checkNotNull(key);
    if (!key.isValidImplicitProvisionKey(types)) {
      return Optional.absent();
    }
    ProvisionBinding binding = provisionBindings.getBinding(key);
    if (binding != null) {
      return Optional.of(binding);
    }

    TypeElement element = MoreElements.asType(types.asElement(key.type()));
    ImmutableSet<ExecutableElement> injectConstructors = injectedConstructors(element);
    switch (injectConstructors.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return tryRegisterConstructor(
            Iterables.getOnlyElement(injectConstructors), Optional.of(key.type()), true);
      default:
        throw new IllegalStateException("Found multiple @Inject constructors: "
            + injectConstructors);
    }
  }

  private ImmutableSet<ExecutableElement> injectedConstructors(TypeElement element) {
    return FluentIterable.from(constructorsIn(element.getEnclosedElements()))
        .filter(
            new Predicate<ExecutableElement>() {
              @Override
              public boolean apply(ExecutableElement constructor) {
                return isAnnotationPresent(constructor, Inject.class);
              }
            })
        .toSet();
  }

  @CanIgnoreReturnValue
  Optional<MembersInjectionBinding> getOrFindMembersInjectionBinding(Key key) {
    checkNotNull(key);
    checkArgument(key.isValidMembersInjectionKey());
    MembersInjectionBinding binding = membersInjectionBindings.getBinding(key);
    if (binding != null) {
      return Optional.of(binding);
    }
    Optional<MembersInjectionBinding> newBinding =
        tryRegisterMembersInjectedType(
            MoreTypes.asTypeElement(key.type()), Optional.of(key.type()), true);
    return newBinding;
  }
}
