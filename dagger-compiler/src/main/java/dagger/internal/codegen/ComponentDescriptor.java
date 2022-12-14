package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.squareup.javapoet.ClassName;
import dagger.Component;
import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Module;
import dagger.Subcomponent;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ConfigurationAnnotations.enclosedBuilders;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentDependencies;
import static dagger.internal.codegen.ConfigurationAnnotations.getComponentModules;
import static dagger.internal.codegen.InjectionAnnotations.getQualifier;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

@AutoValue
abstract class ComponentDescriptor {
  ComponentDescriptor() {}

  enum Kind {
    COMPONENT(Component.class, Component.Builder.class, true),
    SUBCOMPONENT(Subcomponent.class, Subcomponent.Builder.class, false),
    PRODUCTION_COMPONENT(ProductionComponent.class, ProductionComponent.Builder.class, true),
    PRODUCTION_SUBCOMPONENT(
        ProductionSubcomponent.class, ProductionSubcomponent.Builder.class, false);

    private final Class<? extends Annotation> annotationType;
    private final Class<? extends Annotation> builderType;
    private final boolean isTopLevel;

    static Optional<Kind> forAnnotatedElement(TypeElement element) {
      Set<Kind> kinds = EnumSet.noneOf(Kind.class);
      for (Kind kind : values()) {
        if (MoreElements.isAnnotationPresent(element, kind.annotationType())) {
          kinds.add(kind);
        }
      }
      checkArgument(
          kinds.size() <= 1, "%s cannot be annotated with more than one of %s", element, kinds);
      return Optional.fromNullable(getOnlyElement(kinds, null));
    }

    static Optional<Kind> forAnnotatedBuilderElement(TypeElement element) {
      Set<Kind> kinds = EnumSet.noneOf(Kind.class);
      for (Kind kind : values()) {
        if (MoreElements.isAnnotationPresent(element, kind.builderAnnotationType())) {
          kinds.add(kind);
        }
      }
      checkArgument(
          kinds.size() <= 1, "%s cannot be annotated with more than one of %s", element, kinds);
      return Optional.fromNullable(getOnlyElement(kinds, null));
    }

    Kind(
        Class<? extends Annotation> annotationType,
        Class<? extends Annotation> builderType,
        boolean isTopLevel) {
      this.annotationType = annotationType;
      this.builderType = builderType;
      this.isTopLevel = isTopLevel;
    }

    Class<? extends Annotation> annotationType() {
      return annotationType;
    }

    Class<? extends Annotation> builderAnnotationType() {
      return builderType;
    }

    ImmutableSet<ModuleDescriptor.Kind> moduleKinds() {
      switch (this) {
        case COMPONENT:
        case SUBCOMPONENT:
          return Sets.immutableEnumSet(ModuleDescriptor.Kind.MODULE);
        case PRODUCTION_COMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          return Sets.immutableEnumSet(
              ModuleDescriptor.Kind.MODULE, ModuleDescriptor.Kind.PRODUCER_MODULE);
        default:
          throw new AssertionError(this);
      }
    }

    ImmutableSet<Kind> subcomponentKinds() {
      switch (this) {
        case COMPONENT:
        case SUBCOMPONENT:
          return ImmutableSet.of(SUBCOMPONENT, PRODUCTION_SUBCOMPONENT);
        case PRODUCTION_COMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          return ImmutableSet.of(PRODUCTION_SUBCOMPONENT);
        default:
          throw new AssertionError();
      }
    }

    boolean isTopLevel() {
      return isTopLevel;
    }

    boolean isProducer() {
      switch (this) {
        case COMPONENT:
        case SUBCOMPONENT:
          return false;
        case PRODUCTION_COMPONENT:
        case PRODUCTION_SUBCOMPONENT:
          return true;
        default:
          throw new AssertionError();
      }
    }

    private static final Function<Kind, Class<? extends Annotation>> TO_ANNOTATION_TYPE =
        new Function<Kind, Class<? extends Annotation>>() {
          @Override
          public Class<? extends Annotation> apply(Kind kind) {
            return kind.annotationType();
          }
        };

    static Function<Kind, Class<? extends Annotation>> toAnnotationType() {
      return TO_ANNOTATION_TYPE;
    }

    private static final Function<Kind, Class<? extends Annotation>> TO_BUILDER_ANNOTATION_TYPE =
        new Function<Kind, Class<? extends Annotation>>() {
          @Override
          public Class<? extends Annotation> apply(Kind kind) {
            return kind.builderAnnotationType();
          }
        };

    static Function<Kind, Class<? extends Annotation>> toBuilderAnnotationType() {
      return TO_BUILDER_ANNOTATION_TYPE;
    }
  }

  abstract Kind kind();

  abstract AnnotationMirror componentAnnotation();

  abstract TypeElement componentDefinitionType();

  abstract ImmutableSet<TypeElement> dependencies();

  abstract ImmutableSet<ModuleDescriptor> modules();

  ImmutableSet<ModuleDescriptor> transitiveModules() {
    Set<ModuleDescriptor> transitiveModules = new LinkedHashSet<>();
    for (ModuleDescriptor module : modules()) {
      addTransitiveModules(transitiveModules, module);
    }
    return ImmutableSet.copyOf(transitiveModules);
  }

  ImmutableSet<TypeElement> transitiveModuleTypes() {
    return FluentIterable.from(transitiveModules())
        .transform(ModuleDescriptor.getModuleElement())
        .toSet();
  }

  @CanIgnoreReturnValue
  private static Set<ModuleDescriptor> addTransitiveModules(
      Set<ModuleDescriptor> transitiveModules, ModuleDescriptor module) {
    if (transitiveModules.add(module)) {
      for (ModuleDescriptor includedModule : module.includedModules()) {
        addTransitiveModules(transitiveModules, includedModule);
      }
    }
    return transitiveModules;
  }

  abstract ImmutableMap<ExecutableElement, TypeElement> dependencyMethodIndex();

  abstract Optional<TypeElement> executorDependency();

  abstract ImmutableSet<Scope> scopes();

  abstract ImmutableMap<ComponentMethodDescriptor, ComponentDescriptor> subcomponents();

  abstract ImmutableSet<ComponentMethodDescriptor> componentMethods();

  abstract Optional<BuilderSpec> builderSpec();

  @AutoValue
  static abstract class ComponentMethodDescriptor {
    abstract ComponentMethodKind kind();
    abstract Optional<DependencyRequest> dependencyRequest();
    abstract ExecutableElement methodElement();

    static Predicate<ComponentMethodDescriptor> isOfKind(ComponentMethodKind... kinds) {
      final ImmutableSet<ComponentMethodKind> kindSet = ImmutableSet.copyOf(kinds);
      return new Predicate<ComponentMethodDescriptor>() {
        @Override
        public boolean apply(ComponentMethodDescriptor descriptor) {
          return kindSet.contains(descriptor.kind());
        }
      };
    }

    static ComponentMethodDescriptor create(
        ComponentMethodKind kind,
        Optional<DependencyRequest> dependencyRequest,
        ExecutableElement methodElement) {
      return new AutoValue_ComponentDescriptor_ComponentMethodDescriptor(
          kind, dependencyRequest, methodElement);
    }

    static ComponentMethodDescriptor forProvision(
        ExecutableElement methodElement, DependencyRequest dependencyRequest) {
      return create(ComponentMethodKind.PROVISION, Optional.of(dependencyRequest), methodElement);
    }

    static ComponentMethodDescriptor forMembersInjection(
        ExecutableElement methodElement, DependencyRequest dependencyRequest) {
      return create(
          ComponentMethodKind.MEMBERS_INJECTION, Optional.of(dependencyRequest), methodElement);
    }

    static ComponentMethodDescriptor forSubcomponent(
        ComponentMethodKind kind, ExecutableElement methodElement) {
      return create(kind, Optional.<DependencyRequest>absent(), methodElement);
    }
  }

  enum ComponentMethodKind {
    PROVISION,
    PRODUCTION,
    MEMBERS_INJECTION,
    SUBCOMPONENT,
    SUBCOMPONENT_BUILDER,
    PRODUCTION_SUBCOMPONENT,
    PRODUCTION_SUBCOMPONENT_BUILDER;

    Kind componentKind() {
      switch (this) {
        case SUBCOMPONENT:
        case SUBCOMPONENT_BUILDER:
          return Kind.SUBCOMPONENT;
        case PRODUCTION_SUBCOMPONENT:
        case PRODUCTION_SUBCOMPONENT_BUILDER:
          return Kind.PRODUCTION_SUBCOMPONENT;
        default:
          throw new IllegalStateException("no component associated with method " + this);
      }
    }
  }

  @AutoValue
  static abstract class BuilderSpec {
    abstract TypeElement builderDefinitionType();
    abstract Map<TypeElement, ExecutableElement> methodMap();
    abstract ExecutableElement buildMethod();
    abstract TypeMirror componentType();
  }

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final DependencyRequest.Factory dependencyRequestFactory;
    private final ModuleDescriptor.Factory moduleDescriptorFactory;

    Factory(
        Elements elements,
        Types types,
        DependencyRequest.Factory dependencyRequestFactory,
        ModuleDescriptor.Factory moduleDescriptorFactory) {
      this.elements = elements;
      this.types = types;
      this.dependencyRequestFactory = dependencyRequestFactory;
      this.moduleDescriptorFactory = moduleDescriptorFactory;
    }

    ComponentDescriptor forComponent(TypeElement componentDefinitionType) {
      Optional<Kind> kind = Kind.forAnnotatedElement(componentDefinitionType);
      checkArgument(
          kind.isPresent() && kind.get().isTopLevel(),
          "%s must be annotated with @Component or @ProductionComponent",
          componentDefinitionType);
      return create(componentDefinitionType, kind.get(), Optional.<Kind>absent());
    }

    private ComponentDescriptor create(
        TypeElement componentDefinitionType, Kind kind, Optional<Kind> parentKind) {
      DeclaredType declaredComponentType = MoreTypes.asDeclared(componentDefinitionType.asType());
      AnnotationMirror componentMirror =
          getAnnotationMirror(componentDefinitionType, kind.annotationType()).get();
      ImmutableSet<TypeElement> componentDependencyTypes =
          kind.isTopLevel()
              ? MoreTypes.asTypeElements(getComponentDependencies(componentMirror))
              : ImmutableSet.<TypeElement>of();

      ImmutableMap.Builder<ExecutableElement, TypeElement> dependencyMethodIndex =
          ImmutableMap.builder();

      for (TypeElement componentDependency : componentDependencyTypes) {
        List<ExecutableElement> dependencyMethods =
            ElementFilter.methodsIn(elements.getAllMembers(componentDependency));
        for (ExecutableElement dependencyMethod : dependencyMethods) {
          if (isComponentContributionMethod(elements, dependencyMethod)) {
            dependencyMethodIndex.put(dependencyMethod, componentDependency);
          }
        }
      }

      ImmutableSet.Builder<ModuleDescriptor> modules = ImmutableSet.builder();
      for (TypeMirror moduleIncludesType : getComponentModules(componentMirror)) {
        modules.add(moduleDescriptorFactory.create(MoreTypes.asTypeElement(moduleIncludesType)));
      }
      if (kind.equals(Kind.PRODUCTION_COMPONENT)
          || (kind.equals(Kind.PRODUCTION_SUBCOMPONENT)
              && parentKind.isPresent()
              && (parentKind.get().equals(Kind.COMPONENT)
                  || parentKind.get().equals(Kind.SUBCOMPONENT)))) {
        modules.add(descriptorForMonitoringModule(componentDefinitionType));
        modules.add(descriptorForProductionExecutorModule(componentDefinitionType));
      }

      ImmutableSet<ExecutableElement> unimplementedMethods =
          Util.getUnimplementedMethods(elements, componentDefinitionType);

      ImmutableSet.Builder<ComponentMethodDescriptor> componentMethodsBuilder =
          ImmutableSet.builder();

      ImmutableMap.Builder<ComponentMethodDescriptor, ComponentDescriptor> subcomponentDescriptors =
          ImmutableMap.builder();
      for (ExecutableElement componentMethod : unimplementedMethods) {
        ExecutableType resolvedMethod =
            MoreTypes.asExecutable(types.asMemberOf(declaredComponentType, componentMethod));
        ComponentMethodDescriptor componentMethodDescriptor =
            getDescriptorForComponentMethod(componentDefinitionType, kind, componentMethod);
        componentMethodsBuilder.add(componentMethodDescriptor);
        switch (componentMethodDescriptor.kind()) {
          case SUBCOMPONENT:
          case PRODUCTION_SUBCOMPONENT:
            subcomponentDescriptors.put(
                componentMethodDescriptor,
                create(
                    MoreElements.asType(MoreTypes.asElement(resolvedMethod.getReturnType())),
                    componentMethodDescriptor.kind().componentKind(),
                    Optional.of(kind)));
            break;
          case SUBCOMPONENT_BUILDER:
          case PRODUCTION_SUBCOMPONENT_BUILDER:
            subcomponentDescriptors.put(
                componentMethodDescriptor,
                create(
                    MoreElements.asType(
                        MoreTypes.asElement(resolvedMethod.getReturnType()).getEnclosingElement()),
                    componentMethodDescriptor.kind().componentKind(),
                    Optional.of(kind)));
            break;
          default:
        }

      }

      ImmutableList<DeclaredType> enclosedBuilders = kind.builderAnnotationType() == null
          ? ImmutableList.<DeclaredType>of()
          : enclosedBuilders(componentDefinitionType, kind.builderAnnotationType());
      Optional<DeclaredType> builderType =
          Optional.fromNullable(getOnlyElement(enclosedBuilders, null));
      Optional<BuilderSpec> builderSpec = createBuilderSpec(builderType);

      ImmutableSet<Scope> scopes = Scope.scopesOf(componentDefinitionType);
      if (kind.isProducer()) {
        scopes = FluentIterable.from(scopes).append(Scope.productionScope(elements)).toSet();
      }

      Optional<TypeElement> executorDependency = createExecutorDependency(kind, builderSpec);
      return new AutoValue_ComponentDescriptor(
          kind,
          componentMirror,
          componentDefinitionType,
          componentDependencyTypes,
          modules.build(),
          dependencyMethodIndex.build(),
          executorDependency,
          scopes,
          subcomponentDescriptors.build(),
          componentMethodsBuilder.build(),
          builderSpec);
    }

    private ComponentMethodDescriptor getDescriptorForComponentMethod(
        TypeElement componentElement, Kind componentKind, ExecutableElement componentMethod) {
      ExecutableType resolvedComponentMethod =
          MoreTypes.asExecutable(
              types.asMemberOf(MoreTypes.asDeclared(componentElement.asType()), componentMethod));
      TypeMirror returnType = resolvedComponentMethod.getReturnType();
      if (returnType.getKind().equals(DECLARED)) {
        if (MoreTypes.isTypeOf(Provider.class, returnType)
            || MoreTypes.isTypeOf(Lazy.class, returnType)) {
          return ComponentMethodDescriptor.forProvision(
              componentMethod,
              dependencyRequestFactory.forComponentProvisionMethod(
                  componentMethod, resolvedComponentMethod));
        } else if (MoreTypes.isTypeOf(MembersInjector.class, returnType)) {
          return ComponentMethodDescriptor.forMembersInjection(
              componentMethod,
              dependencyRequestFactory.forComponentMembersInjectionMethod(
                  componentMethod, resolvedComponentMethod));
        } else if (!getQualifier(componentMethod).isPresent()) {
          if (isAnnotationPresent(MoreTypes.asElement(returnType), Subcomponent.class)) {
            return ComponentMethodDescriptor.forSubcomponent(
                ComponentMethodKind.SUBCOMPONENT, componentMethod);
          } else if (isAnnotationPresent(
              MoreTypes.asElement(returnType), ProductionSubcomponent.class)) {
            return ComponentMethodDescriptor.forSubcomponent(
                ComponentMethodKind.PRODUCTION_SUBCOMPONENT, componentMethod);
          } else if (isAnnotationPresent(
              MoreTypes.asElement(returnType), Subcomponent.Builder.class)) {
            return ComponentMethodDescriptor.forSubcomponent(
                ComponentMethodKind.SUBCOMPONENT_BUILDER, componentMethod);
          } else if (isAnnotationPresent(
              MoreTypes.asElement(returnType), ProductionSubcomponent.Builder.class)) {
            return ComponentMethodDescriptor.forSubcomponent(
                ComponentMethodKind.PRODUCTION_SUBCOMPONENT_BUILDER, componentMethod);
          }
        }
      }

      if (componentMethod.getParameters().isEmpty()
          && !componentMethod.getReturnType().getKind().equals(VOID)) {
        switch (componentKind) {
          case COMPONENT:
          case SUBCOMPONENT:
            return ComponentMethodDescriptor.forProvision(
                componentMethod,
                dependencyRequestFactory.forComponentProvisionMethod(
                    componentMethod, resolvedComponentMethod));
          case PRODUCTION_COMPONENT:
          case PRODUCTION_SUBCOMPONENT:
            return ComponentMethodDescriptor.forProvision(
                componentMethod,
                dependencyRequestFactory.forComponentProductionMethod(
                    componentMethod, resolvedComponentMethod));
          default:
            throw new AssertionError();
        }
      }

      List<? extends TypeMirror> parameterTypes = resolvedComponentMethod.getParameterTypes();
      if (parameterTypes.size() == 1
          && (returnType.getKind().equals(VOID)
              || MoreTypes.equivalence().equivalent(returnType, parameterTypes.get(0)))) {
        return ComponentMethodDescriptor.forMembersInjection(
            componentMethod,
            dependencyRequestFactory.forComponentMembersInjectionMethod(
                componentMethod, resolvedComponentMethod));
      }

      throw new IllegalArgumentException("not a valid component method: " + componentMethod);
    }

    private Optional<BuilderSpec> createBuilderSpec(Optional<DeclaredType> builderType) {
      if (!builderType.isPresent()) {
        return Optional.absent();
      }
      TypeElement element = MoreTypes.asTypeElement(builderType.get());
      ImmutableSet<ExecutableElement> methods = Util.getUnimplementedMethods(elements, element);
      ImmutableMap.Builder<TypeElement, ExecutableElement> map = ImmutableMap.builder();
      ExecutableElement buildMethod = null;
      for (ExecutableElement method : methods) {
        if (method.getParameters().isEmpty()) {
          buildMethod = method;
        } else {
          ExecutableType resolved =
              MoreTypes.asExecutable(types.asMemberOf(builderType.get(), method));
          map.put(MoreTypes.asTypeElement(getOnlyElement(resolved.getParameterTypes())), method);
        }
      }
      verify(buildMethod != null);
      return Optional.<BuilderSpec>of(new AutoValue_ComponentDescriptor_BuilderSpec(element,
          map.build(), buildMethod, element.getEnclosingElement().asType()));
    }

    private Optional<TypeElement> createExecutorDependency(
        Kind componentKind, Optional<BuilderSpec> builderSpec) {
      if (!componentKind.isProducer()) {
        return Optional.absent();
      }
      TypeElement executorTypeElement = elements.getTypeElement(Executor.class.getCanonicalName());
      if (!builderSpec.isPresent()) {
        return componentKind.equals(Kind.PRODUCTION_COMPONENT)
            ? Optional.of(executorTypeElement)
            : Optional.<TypeElement>absent();
      }
      return builderSpec.get().methodMap().containsKey(executorTypeElement)
          ? Optional.of(executorTypeElement)
          : Optional.<TypeElement>absent();
    }

    private ModuleDescriptor descriptorForMonitoringModule(TypeElement componentDefinitionType) {
      ClassName monitoringModuleName =
          SourceFiles.generatedMonitoringModuleName(componentDefinitionType);
      String generatedMonitorModuleName = monitoringModuleName.toString();
      TypeElement monitoringModule = elements.getTypeElement(generatedMonitorModuleName);
      if (monitoringModule == null) {
        throw new TypeNotPresentException(generatedMonitorModuleName, null);
      }
      return moduleDescriptorFactory.create(monitoringModule);
    }

    private ModuleDescriptor descriptorForProductionExecutorModule(
        TypeElement componentDefinitionType) {
      ClassName productionExecutorModuleName =
          SourceFiles.generatedProductionExecutorModuleName(componentDefinitionType);
      String generatedProductionExecutorModuleName = productionExecutorModuleName.toString();
      TypeElement productionExecutorModule =
          elements.getTypeElement(generatedProductionExecutorModuleName);
      if (productionExecutorModule == null) {
        throw new TypeNotPresentException(generatedProductionExecutorModuleName, null);
      }
      return moduleDescriptorFactory.create(productionExecutorModule);
    }
  }

  static boolean isComponentContributionMethod(Elements elements, ExecutableElement method) {
    return method.getParameters().isEmpty()
        && !method.getReturnType().getKind().equals(VOID)
        && !elements.getTypeElement(Object.class.getCanonicalName())
            .equals(method.getEnclosingElement());
  }

  static boolean isComponentProductionMethod(Elements elements, ExecutableElement method) {
    return isComponentContributionMethod(elements, method)
        && MoreTypes.isTypeOf(ListenableFuture.class, method.getReturnType());
  }
}
