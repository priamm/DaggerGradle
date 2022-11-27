package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import dagger.internal.DelegateFactory;
import dagger.internal.MapFactory;
import dagger.internal.MapProviderFactory;
import dagger.internal.Preconditions;
import dagger.internal.SetFactory;
import dagger.internal.codegen.ComponentDescriptor.BuilderSpec;
import dagger.internal.codegen.ComponentDescriptor.ComponentMethodDescriptor;
import dagger.producers.Produced;
import dagger.producers.Producer;
import dagger.producers.internal.MapOfProducerProducer;
import dagger.producers.internal.MapProducer;
import dagger.producers.internal.SetOfProducedProducer;
import dagger.producers.internal.SetProducer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.DELEGATED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.INITIALIZED;
import static dagger.internal.codegen.AbstractComponentWriter.InitializationState.UNINITIALIZED;
import static dagger.internal.codegen.AnnotationSpecs.SUPPRESS_WARNINGS_UNCHECKED;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.ContributionBinding.FactoryCreationStrategy.ENUM_INSTANCE;
import static dagger.internal.codegen.ContributionBinding.Kind.PROVISION;
import static dagger.internal.codegen.ErrorMessages.CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD;
import static dagger.internal.codegen.FrameworkDependency.frameworkDependenciesForBinding;
import static dagger.internal.codegen.MapKeys.getMapKeyExpression;
import static dagger.internal.codegen.MemberSelect.emptyFrameworkMapFactory;
import static dagger.internal.codegen.MemberSelect.emptySetProvider;
import static dagger.internal.codegen.MemberSelect.localField;
import static dagger.internal.codegen.MemberSelect.noOpMembersInjector;
import static dagger.internal.codegen.MemberSelect.staticMethod;
import static dagger.internal.codegen.MembersInjectionBinding.Strategy.NO_OP;
import static dagger.internal.codegen.SourceFiles.frameworkTypeUsageStatement;
import static dagger.internal.codegen.SourceFiles.generatedClassNameForBinding;
import static dagger.internal.codegen.SourceFiles.membersInjectorNameForType;
import static dagger.internal.codegen.TypeNames.DELEGATE_FACTORY;
import static dagger.internal.codegen.TypeNames.FACTORY;
import static dagger.internal.codegen.TypeNames.ILLEGAL_STATE_EXCEPTION;
import static dagger.internal.codegen.TypeNames.INSTANCE_FACTORY;
import static dagger.internal.codegen.TypeNames.LISTENABLE_FUTURE;
import static dagger.internal.codegen.TypeNames.MAP_FACTORY;
import static dagger.internal.codegen.TypeNames.MAP_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_OF_PRODUCER_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_PROVIDER_FACTORY;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTORS;
import static dagger.internal.codegen.TypeNames.PRODUCER;
import static dagger.internal.codegen.TypeNames.PRODUCERS;
import static dagger.internal.codegen.TypeNames.SCOPED_PROVIDER;
import static dagger.internal.codegen.TypeNames.SET_FACTORY;
import static dagger.internal.codegen.TypeNames.SET_OF_PRODUCED_PRODUCER;
import static dagger.internal.codegen.TypeNames.SET_PRODUCER;
import static dagger.internal.codegen.TypeNames.STRING;
import static dagger.internal.codegen.TypeNames.UNSUPPORTED_OPERATION_EXCEPTION;
import static dagger.internal.codegen.TypeNames.providerOf;
import static dagger.internal.codegen.TypeSpecs.addSupertype;
import static dagger.internal.codegen.Util.componentCanMakeNewInstances;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.type.TypeKind.DECLARED;
import static javax.lang.model.type.TypeKind.VOID;

abstract class AbstractComponentWriter {
  private static final String NOOP_BUILDER_METHOD_JAVADOC =
      "This module is declared, but an instance is not used in the component. This method is a "
          + "no-op. For more, see https://google.github.io/dagger/unused-modules.\n";

  protected final Elements elements;
  protected final Types types;
  protected final Key.Factory keyFactory;
  protected final CompilerOptions compilerOptions;
  protected final ClassName name;
  protected final BindingGraph graph;
  protected final ImmutableMap<ComponentDescriptor, String> subcomponentNames;
  private final Map<BindingKey, InitializationState> initializationStates = new HashMap<>();
  protected TypeSpec.Builder component;
  private final UniqueNameSet componentFieldNames = new UniqueNameSet();
  private final Map<BindingKey, MemberSelect> memberSelects = new HashMap<>();
  protected final MethodSpec.Builder constructor = constructorBuilder().addModifiers(PRIVATE);
  protected Optional<ClassName> builderName = Optional.absent();

  private ImmutableMap<TypeElement, FieldSpec> builderFields = ImmutableMap.of();

  protected final Map<TypeElement, MemberSelect> componentContributionFields = Maps.newHashMap();

  AbstractComponentWriter(
      Types types,
      Elements elements,
      Key.Factory keyFactory,
      CompilerOptions compilerOptions,
      ClassName name,
      BindingGraph graph,
      ImmutableMap<ComponentDescriptor, String> subcomponentNames) {
    this.types = types;
    this.elements = elements;
    this.keyFactory = keyFactory;
    this.compilerOptions = compilerOptions;
    this.name = name;
    this.graph = graph;
    this.subcomponentNames = subcomponentNames;
  }

  protected final TypeElement componentDefinitionType() {
    return graph.componentDescriptor().componentDefinitionType();
  }

  protected final ClassName componentDefinitionTypeName() {
    return ClassName.get(componentDefinitionType());
  }

  private CodeBlock getComponentContributionExpression(TypeElement contributionType) {
    if (builderFields.containsKey(contributionType)) {
      return CodeBlocks.format("builder.$N", builderFields.get(contributionType));
    } else {
      Optional<CodeBlock> codeBlock =
          getOrCreateComponentContributionFieldExpression(contributionType);
      checkState(codeBlock.isPresent(), "no builder or component field for %s", contributionType);
      return codeBlock.get();
    }
  }

  protected Optional<CodeBlock> getOrCreateComponentContributionFieldExpression(
      TypeElement contributionType) {
    MemberSelect fieldSelect = componentContributionFields.get(contributionType);
    if (fieldSelect == null) {
      if (!builderFields.containsKey(contributionType)) {
        return Optional.absent();
      }
      FieldSpec componentField =
          componentField(ClassName.get(contributionType), simpleVariableName(contributionType))
              .addModifiers(PRIVATE, FINAL)
              .build();
      component.addField(componentField);
      constructor.addCode(
          "this.$N = builder.$N;", componentField, builderFields.get(contributionType));
      fieldSelect = localField(name, componentField.name);
      componentContributionFields.put(contributionType, fieldSelect);
    }
    return Optional.of(fieldSelect.getExpressionFor(name));
  }

  protected final FieldSpec.Builder componentField(TypeName type, String name) {
    return FieldSpec.builder(type, componentFieldNames.getUniqueName(name));
  }

  private CodeBlock getMemberSelectExpression(BindingKey key) {
    return getMemberSelect(key).getExpressionFor(name);
  }

  protected MemberSelect getMemberSelect(BindingKey key) {
    return memberSelects.get(key);
  }

  protected InitializationState getInitializationState(BindingKey bindingKey) {
    return initializationStates.containsKey(bindingKey)
        ? initializationStates.get(bindingKey)
        : UNINITIALIZED;
  }

  private void setInitializationState(BindingKey bindingKey, InitializationState state) {
    initializationStates.put(bindingKey, state);
  }

  final TypeSpec.Builder write() {
    checkState(component == null, "ComponentWriter has already been generated.");
    component = createComponentClass();
    addBuilder();
    addFactoryMethods();
    addFields();
    initializeFrameworkTypes();
    implementInterfaceMethods();
    addSubcomponents();
    component.addMethod(constructor.build());
    return component;
  }

  protected abstract TypeSpec.Builder createComponentClass();

  protected void addBuilder() {
    builderName = Optional.of(builderName());
    TypeSpec.Builder componentBuilder =
        createBuilder(builderName.get().simpleName()).addModifiers(FINAL);

    Optional<BuilderSpec> builderSpec = graph.componentDescriptor().builderSpec();
    if (builderSpec.isPresent()) {
      componentBuilder.addModifiers(PRIVATE);
      addSupertype(componentBuilder, builderSpec.get().builderDefinitionType());
    } else {
      componentBuilder
          .addModifiers(PUBLIC)
          .addMethod(constructorBuilder().addModifiers(PRIVATE).build());
    }

    builderFields = addBuilderFields(componentBuilder);
    addBuildMethod(componentBuilder, builderSpec);
    addBuilderMethods(componentBuilder, builderSpec);
    addBuilderClass(componentBuilder.build());

    constructor.addParameter(builderName.get(), "builder");
    constructor.addStatement("assert builder != null");
  }

  protected abstract void addBuilderClass(TypeSpec builder);

  private ImmutableMap<TypeElement, FieldSpec> addBuilderFields(TypeSpec.Builder componentBuilder) {
    UniqueNameSet builderFieldNames = new UniqueNameSet();
    ImmutableMap.Builder<TypeElement, FieldSpec> builderFields = ImmutableMap.builder();
    for (TypeElement contributionElement : graph.componentRequirements()) {
      String contributionName =
          builderFieldNames.getUniqueName(simpleVariableName(contributionElement));
      FieldSpec builderField =
          FieldSpec.builder(ClassName.get(contributionElement), contributionName, PRIVATE).build();
      componentBuilder.addField(builderField);
      builderFields.put(contributionElement, builderField);
    }
    return builderFields.build();
  }

  private void addBuildMethod(
      TypeSpec.Builder componentBuilder, Optional<BuilderSpec> builderSpec) {
    MethodSpec.Builder buildMethod;
    if (builderSpec.isPresent()) {
      ExecutableElement specBuildMethod = builderSpec.get().buildMethod();
      buildMethod =
          methodBuilder(specBuildMethod.getSimpleName().toString()).addAnnotation(Override.class);
    } else {
      buildMethod = methodBuilder("build");
    }
    buildMethod.returns(componentDefinitionTypeName()).addModifiers(PUBLIC);

    for (Map.Entry<TypeElement, FieldSpec> builderFieldEntry : builderFields.entrySet()) {
      FieldSpec builderField = builderFieldEntry.getValue();
      if (componentCanMakeNewInstances(builderFieldEntry.getKey())) {
        buildMethod.addCode(
            "if ($1N == null) { this.$1N = new $2T(); }", builderField, builderField.type);
      } else {
        buildMethod.addCode(
            "if ($N == null) { throw new $T($T.class.getCanonicalName() + $S); }",
            builderField,
            ILLEGAL_STATE_EXCEPTION,
            builderField.type,
            " must be set");
      }
    }
    buildMethod.addStatement("return new $T(this)", name);
    componentBuilder.addMethod(buildMethod.build());
  }

  private void addBuilderMethods(
      TypeSpec.Builder componentBuilder, Optional<BuilderSpec> builderSpec) {
    if (builderSpec.isPresent()) {
      UniqueNameSet parameterNames = new UniqueNameSet();
      for (Map.Entry<TypeElement, ExecutableElement> builderMethodEntry :
          builderSpec.get().methodMap().entrySet()) {
        TypeElement builderMethodType = builderMethodEntry.getKey();
        ExecutableElement specMethod = builderMethodEntry.getValue();
        MethodSpec.Builder builderMethod = addBuilderMethodFromSpec(specMethod);
        String parameterName =
            parameterNames.getUniqueName(
                Iterables.getOnlyElement(specMethod.getParameters()).getSimpleName());
        builderMethod.addParameter(ClassName.get(builderMethodType), parameterName);
        if (graph.componentRequirements().contains(builderMethodType)) {
          builderMethod.addStatement(
              "this.$N = $T.checkNotNull($L)",
              builderFields.get(builderMethodType),
              Preconditions.class,
              parameterName);
          addBuilderMethodReturnStatementForSpec(specMethod, builderMethod);
        } else if (graph.ownedModuleTypes().contains(builderMethodType)) {
          builderMethod.addJavadoc(NOOP_BUILDER_METHOD_JAVADOC);
          addBuilderMethodReturnStatementForSpec(specMethod, builderMethod);
        } else {
          builderMethod.addStatement(
              "throw new $T($T.format($S, $T.class.getCanonicalName()))",
              UNSUPPORTED_OPERATION_EXCEPTION,
              STRING,
              "%s cannot be set because it is inherited from the enclosing component",
              ClassName.get(builderMethodType));
        }
        componentBuilder.addMethod(builderMethod.build());
      }
    } else {
      for (TypeElement componentRequirement : graph.availableDependencies()) {
        String componentRequirementName = simpleVariableName(componentRequirement);
        MethodSpec.Builder builderMethod =
            methodBuilder(componentRequirementName)
                .returns(builderName.get())
                .addModifiers(PUBLIC)
                .addParameter(ClassName.get(componentRequirement), componentRequirementName);
        if (graph.componentRequirements().contains(componentRequirement)) {
          builderMethod.addStatement(
              "this.$N = $T.checkNotNull($L)",
              builderFields.get(componentRequirement),
              Preconditions.class,
              componentRequirementName);
        } else {
          builderMethod.addStatement("$T.checkNotNull($L)",
              Preconditions.class,
              componentRequirementName);
          builderMethod.addJavadoc("@deprecated " + NOOP_BUILDER_METHOD_JAVADOC);
          builderMethod.addAnnotation(Deprecated.class);
        }
        builderMethod.addStatement("return this");
        componentBuilder.addMethod(builderMethod.build());
      }
    }
  }

  private void addBuilderMethodReturnStatementForSpec(
      ExecutableElement specMethod, MethodSpec.Builder builderMethod) {
    if (!specMethod.getReturnType().getKind().equals(VOID)) {
      builderMethod.addStatement("return this");
    }
  }

  private MethodSpec.Builder addBuilderMethodFromSpec(ExecutableElement method) {
    TypeMirror returnType = method.getReturnType();
    MethodSpec.Builder builderMethod =
        methodBuilder(method.getSimpleName().toString())
            .addAnnotation(Override.class)
            .addModifiers(Sets.difference(method.getModifiers(), ImmutableSet.of(ABSTRACT)));
    if (!returnType.getKind().equals(TypeKind.VOID)) {
      builderMethod.returns(builderName.get());
    }
    return builderMethod;
  }

  protected abstract TypeSpec.Builder createBuilder(String builderName);

  protected abstract ClassName builderName();

  protected abstract void addFactoryMethods();

  private void addFields() {
    for (ResolvedBindings resolvedBindings : graph.resolvedBindings().values()) {
      addField(resolvedBindings);
    }
  }

  private void addField(ResolvedBindings resolvedBindings) {
    BindingKey bindingKey = resolvedBindings.bindingKey();

    Optional<MemberSelect> staticMemberSelect = staticMemberSelect(resolvedBindings);
    if (staticMemberSelect.isPresent()) {
      memberSelects.put(bindingKey, staticMemberSelect.get());
      return;
    }

    if (resolvedBindings.ownedBindings().isEmpty()) {
      return;
    }

    FieldSpec frameworkField = addFrameworkField(resolvedBindings);
    memberSelects.put(
        bindingKey,
        localField(name, frameworkField.name));
  }

  private FieldSpec addFrameworkField(ResolvedBindings resolvedBindings) {
    boolean useRawType = useRawType(resolvedBindings);

    FrameworkField contributionBindingField =
        FrameworkField.createForResolvedBindings(resolvedBindings);
    FieldSpec.Builder contributionField =
        componentField(
            useRawType
                ? contributionBindingField.frameworkType().rawType
                : contributionBindingField.frameworkType(),
            contributionBindingField.name());
    contributionField.addModifiers(PRIVATE);
    if (useRawType) {
      contributionField.addAnnotation(AnnotationSpecs.SUPPRESS_WARNINGS_RAWTYPES);
    }

    FieldSpec field = contributionField.build();
    component.addField(field);
    return field;
  }

  private boolean useRawType(ResolvedBindings resolvedBindings) {
    Optional<String> bindingPackage = resolvedBindings.bindingPackage();
    return bindingPackage.isPresent() && !bindingPackage.get().equals(name.packageName());
  }

  private Optional<MemberSelect> staticMemberSelect(ResolvedBindings resolvedBindings) {
    switch (resolvedBindings.bindingKey().kind()) {
      case CONTRIBUTION:
        ContributionBinding contributionBinding = resolvedBindings.contributionBinding();
        if (contributionBinding.factoryCreationStrategy().equals(ENUM_INSTANCE)
            && !contributionBinding.scope().isPresent()) {
          switch (contributionBinding.bindingKind()) {
            case SYNTHETIC_MULTIBOUND_MAP:
              BindingType bindingType = contributionBinding.bindingType();
              MapType mapType = MapType.from(contributionBinding.key().type());
              return Optional.of(
                  emptyFrameworkMapFactory(
                      frameworkMapFactoryClassName(bindingType),
                      mapType.keyType(),
                      mapType.unwrappedValueType(bindingType.frameworkClass())));

            case SYNTHETIC_MULTIBOUND_SET:
              return Optional.of(
                  emptySetFactoryStaticMemberSelect(
                      contributionBinding.bindingType(), contributionBinding.key()));

            default:
              return Optional.of(
                  staticMethod(
                      generatedClassNameForBinding(contributionBinding),
                      CodeBlocks.format("create()")));
          }
        }
        break;

      case MEMBERS_INJECTION:
        Optional<MembersInjectionBinding> membersInjectionBinding =
            resolvedBindings.membersInjectionBinding();
        if (membersInjectionBinding.isPresent()
            && membersInjectionBinding.get().injectionStrategy().equals(NO_OP)) {
          return Optional.of(noOpMembersInjector(membersInjectionBinding.get().key().type()));
        }
        break;

      default:
        throw new AssertionError();
    }
    return Optional.absent();
  }

  private static MemberSelect emptySetFactoryStaticMemberSelect(BindingType bindingType, Key key) {
    return emptySetProvider(setFactoryClassName(bindingType, key), SetType.from(key.type()));
  }

  private static ClassName setFactoryClassName(BindingType bindingType, Key key) {
    if (bindingType.equals(BindingType.PROVISION)) {
      return SET_FACTORY;
    } else {
      SetType setType = SetType.from(key.type());
      return setType.elementsAreTypeOf(Produced.class) ? SET_OF_PRODUCED_PRODUCER : SET_PRODUCER;
    }
  }

  private static ClassName mapFactoryClassName(ContributionBinding binding) {
    switch (binding.bindingType()) {
      case PRODUCTION:
        return MapType.from(binding.key().type()).valuesAreTypeOf(Produced.class)
            ? MAP_OF_PRODUCED_PRODUCER : MAP_PRODUCER;

      case PROVISION:
      case MEMBERS_INJECTION:
        return MAP_FACTORY;

      default:
        throw new AssertionError(binding.toString());
    }
  }

  private static ClassName frameworkMapFactoryClassName(BindingType bindingType) {
    return bindingType.equals(BindingType.PRODUCTION)
        ? MAP_OF_PRODUCER_PRODUCER : MAP_PROVIDER_FACTORY;
  }

  private void implementInterfaceMethods() {
    Set<MethodSignature> interfaceMethods = Sets.newHashSet();
    for (ComponentMethodDescriptor componentMethod :
        graph.componentDescriptor().componentMethods()) {
      if (componentMethod.dependencyRequest().isPresent()) {
        DependencyRequest interfaceRequest = componentMethod.dependencyRequest().get();
        ExecutableElement requestElement =
            MoreElements.asExecutable(interfaceRequest.requestElement());
        ExecutableType requestType = MoreTypes.asExecutable(types.asMemberOf(
            MoreTypes.asDeclared(componentDefinitionType().asType()), requestElement));
        MethodSignature signature = MethodSignature.fromExecutableType(
            requestElement.getSimpleName().toString(), requestType);
        if (!interfaceMethods.contains(signature)) {
          interfaceMethods.add(signature);
          MethodSpec.Builder interfaceMethod =
              methodBuilder(requestElement.getSimpleName().toString())
                  .addAnnotation(Override.class)
                  .addModifiers(PUBLIC)
                  .returns(TypeName.get(requestType.getReturnType()));
          BindingKey bindingKey = interfaceRequest.bindingKey();
          MemberSelect memberSelect = getMemberSelect(bindingKey);
          CodeBlock memberSelectCodeBlock = memberSelect.getExpressionFor(name);
          switch (interfaceRequest.kind()) {
            case MEMBERS_INJECTOR:
              List<? extends VariableElement> parameters = requestElement.getParameters();
              if (parameters.isEmpty()) {
                interfaceMethod.addStatement("return $L", memberSelectCodeBlock);
              } else {
                Name parameterName = Iterables.getOnlyElement(parameters).getSimpleName();
                interfaceMethod.addParameter(
                    TypeName.get(Iterables.getOnlyElement(requestType.getParameterTypes())),
                    parameterName.toString());
                interfaceMethod.addStatement(
                    "$L.injectMembers($L)", memberSelectCodeBlock, parameterName);
                if (!requestType.getReturnType().getKind().equals(VOID)) {
                  interfaceMethod.addStatement("return $L", parameterName);
                }
              }
              break;
            case INSTANCE:
              if (memberSelect.staticMember()
                  && bindingKey.key().type().getKind().equals(DECLARED)
                  && !((DeclaredType) bindingKey.key().type()).getTypeArguments().isEmpty()) {
                TypeName factoryType = providerOf(TypeName.get(requestType.getReturnType()));
                interfaceMethod
                    .addStatement("$T factory = $L", factoryType, memberSelectCodeBlock)
                    .addStatement("return factory.get()");
                break;
              }
            case LAZY:
            case PRODUCED:
            case PRODUCER:
            case PROVIDER:
            case FUTURE:
              interfaceMethod.addStatement(
                  "return $L",
                  frameworkTypeUsageStatement(memberSelectCodeBlock, interfaceRequest.kind()));
              break;
            default:
              throw new AssertionError();
          }
          component.addMethod(interfaceMethod.build());
        }
      }
    }
  }

  private void addSubcomponents() {
    for (Map.Entry<ExecutableElement, BindingGraph> subgraphEntry : graph.subgraphs().entrySet()) {
      SubcomponentWriter subcomponent =
          new SubcomponentWriter(this, subgraphEntry.getKey(), subgraphEntry.getValue());
      component.addType(subcomponent.write().build());
    }
  }

  private static final int INITIALIZATIONS_PER_INITIALIZE_METHOD = 100;

  private void initializeFrameworkTypes() {
    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    for (BindingKey bindingKey : graph.resolvedBindings().keySet()) {
      codeBlocks.addAll(initializeFrameworkType(bindingKey).asSet());
    }
    List<List<CodeBlock>> partitions =
        Lists.partition(codeBlocks.build(), INITIALIZATIONS_PER_INITIALIZE_METHOD);

    UniqueNameSet methodNames = new UniqueNameSet();
    for (List<CodeBlock> partition : partitions) {
      String methodName = methodNames.getUniqueName("initialize");
      MethodSpec.Builder initializeMethod =
          methodBuilder(methodName)
              .addModifiers(PRIVATE)
              .addAnnotation(SUPPRESS_WARNINGS_UNCHECKED)
              .addCode(CodeBlocks.concat(partition));
      if (builderName.isPresent()) {
        initializeMethod.addParameter(builderName.get(), "builder", FINAL);
        constructor.addStatement("$L(builder)", methodName);
      } else {
        constructor.addStatement("$L()", methodName);
      }
      component.addMethod(initializeMethod.build());
    }
  }

  private Optional<CodeBlock> initializeFrameworkType(BindingKey bindingKey) {
    MemberSelect memberSelect = getMemberSelect(bindingKey);
    if (memberSelect.staticMember() || !memberSelect.owningClass().equals(name)) {
      return Optional.absent();
    }

    switch (bindingKey.kind()) {
      case CONTRIBUTION:
        return initializeContributionBinding(bindingKey);

      case MEMBERS_INJECTION:
        return initializeMembersInjectionBinding(bindingKey);

      default:
        throw new AssertionError();
    }
  }

  private Optional<CodeBlock> initializeContributionBinding(BindingKey bindingKey) {
    ContributionBinding binding = graph.resolvedBindings().get(bindingKey).contributionBinding();
    if (binding.factoryCreationStrategy().equals(ENUM_INSTANCE) && !binding.scope().isPresent()) {
      return Optional.absent();
    }

    return Optional.of(
        CodeBlocks.concat(
            ImmutableList.of(
                initializeDelegateFactoriesForUninitializedDependencies(binding),
                initializeMember(bindingKey, initializeFactoryForContributionBinding(binding)))));
  }

  private Optional<CodeBlock> initializeMembersInjectionBinding(BindingKey bindingKey) {
    MembersInjectionBinding binding =
        graph.resolvedBindings().get(bindingKey).membersInjectionBinding().get();

    if (binding.injectionStrategy().equals(MembersInjectionBinding.Strategy.NO_OP)) {
      return Optional.absent();
    }

    return Optional.of(
        CodeBlocks.concat(
            ImmutableList.of(
                initializeDelegateFactoriesForUninitializedDependencies(binding),
                initializeMember(bindingKey, initializeMembersInjectorForBinding(binding)))));
  }

  private CodeBlock initializeDelegateFactoriesForUninitializedDependencies(Binding binding) {
    ImmutableList.Builder<CodeBlock> initializations = ImmutableList.builder();

    for (BindingKey dependencyKey :
        FluentIterable.from(binding.implicitDependencies())
            .transform(DependencyRequest.BINDING_KEY_FUNCTION)
            .toSet()) {
      if (!getMemberSelect(dependencyKey).staticMember()
          && getInitializationState(dependencyKey).equals(UNINITIALIZED)) {
        initializations.add(
            CodeBlocks.format(
                "this.$L = new $T();", getMemberSelectExpression(dependencyKey), DELEGATE_FACTORY));
        setInitializationState(dependencyKey, DELEGATED);
      }
    }

    return CodeBlocks.concat(initializations.build());
  }

  private CodeBlock initializeMember(BindingKey bindingKey, CodeBlock initializationCodeBlock) {
    ImmutableList.Builder<CodeBlock> initializations = ImmutableList.builder();

    CodeBlock memberSelect = getMemberSelectExpression(bindingKey);
    CodeBlock delegateFactoryVariable = delegateFactoryVariableExpression(bindingKey);
    if (getInitializationState(bindingKey).equals(DELEGATED)) {
      initializations.add(
          CodeBlocks.format(
              "$1T $2L = ($1T) $3L;", DELEGATE_FACTORY, delegateFactoryVariable, memberSelect));
    }
    initializations.add(
        CodeBlocks.format("this.$L = $L;", memberSelect, initializationCodeBlock));
    if (getInitializationState(bindingKey).equals(DELEGATED)) {
      initializations.add(
          CodeBlocks.format("$L.setDelegatedProvider($L);", delegateFactoryVariable, memberSelect));
    }
    setInitializationState(bindingKey, INITIALIZED);

    return CodeBlocks.concat(initializations.build());
  }

  private CodeBlock delegateFactoryVariableExpression(BindingKey key) {
    return CodeBlocks.format(
        "$LDelegate", getMemberSelectExpression(key).toString().replace('.', '_'));
  }

  private CodeBlock initializeFactoryForContributionBinding(ContributionBinding binding) {
    TypeName bindingKeyTypeName = TypeName.get(binding.key().type());
    switch (binding.bindingKind()) {
      case COMPONENT:
        return CodeBlocks.format(
            "$T.<$T>create($L)",
            INSTANCE_FACTORY,
            bindingKeyTypeName,
            bindingKeyTypeName.equals(componentDefinitionTypeName())
                ? "this"
                : getComponentContributionExpression(
                    MoreTypes.asTypeElement(binding.key().type())));

      case COMPONENT_PROVISION:
        {
          TypeElement bindingTypeElement =
              graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement());
          String localFactoryVariable = simpleVariableName(bindingTypeElement);
          CodeBlock callFactoryMethod =
              CodeBlocks.format(
                  "$L.$L()",
                  localFactoryVariable,
                  binding.bindingElement().getSimpleName().toString());

          CodeBlock getMethodBody =
              binding.nullableType().isPresent()
                      || compilerOptions.nullableValidationKind().equals(Diagnostic.Kind.WARNING)
                  ? CodeBlocks.format("return $L;", callFactoryMethod)
                  : CodeBlocks.format("return $T.checkNotNull($L, $S);",
                      Preconditions.class,
                      callFactoryMethod,
                      CANNOT_RETURN_NULL_FROM_NON_NULLABLE_COMPONENT_METHOD);
          return CodeBlocks.format(
              Joiner.on('\n')
                  .join(
                      "new $1T<$2T>() {",
                      "  private final $5T $6L = $3L;",
                      "  $4L@Override public $2T get() {",
                      "    $7L",
                      "  }",
                      "}"),
              FACTORY,
              bindingKeyTypeName,
              getComponentContributionExpression(bindingTypeElement),
              nullableAnnotation(binding.nullableType()),
              TypeName.get(bindingTypeElement.asType()),
              localFactoryVariable,
              getMethodBody);
        }

      case SUBCOMPONENT_BUILDER:
        return CodeBlocks.format(
            Joiner.on('\n')
                .join(
                    "new $1T<$2T>() {",
                    "  @Override public $2T get() {",
                    "    return $3L();",
                    "  }",
                    "}"),
            FACTORY,
            bindingKeyTypeName,
            binding.bindingElement().getSimpleName().toString());

      case INJECTION:
      case PROVISION:
        {
          List<CodeBlock> arguments =
              Lists.newArrayListWithCapacity(binding.dependencies().size() + 1);
          if (binding.bindingKind().equals(PROVISION)
              && !binding.bindingElement().getModifiers().contains(STATIC)) {
            arguments.add(getComponentContributionExpression(binding.contributedBy().get()));
          }
          arguments.addAll(getDependencyArguments(binding));

          CodeBlock factoryCreate =
              CodeBlocks.format(
                  "$T.create($L)",
                  generatedClassNameForBinding(binding),
                  makeParametersCodeBlock(arguments));
          return binding.scope().isPresent()
              ? CodeBlocks.format("$T.create($L)", SCOPED_PROVIDER, factoryCreate)
              : factoryCreate;
        }

      case EXECUTOR_DEPENDENCY:
        return CodeBlocks.format(
            "$T.<$T>create($L)",
            INSTANCE_FACTORY,
            bindingKeyTypeName,
            getComponentContributionExpression(
                graph.componentDescriptor().executorDependency().get()));

      case COMPONENT_PRODUCTION:
        {
          TypeElement bindingTypeElement =
              graph.componentDescriptor().dependencyMethodIndex().get(binding.bindingElement());
          return CodeBlocks.format(
              Joiner.on('\n')
                  .join(
                      "new $1T<$2T>() {",
                      "  private final $6T $7L = $4L;",
                      "  @Override public $3T<$2T> get() {",
                      "    return $7L.$5L();",
                      "  }",
                      "}"),
              /* 1 */ PRODUCER,
              /* 2 */ TypeName.get(binding.key().type()),
              /* 3 */ LISTENABLE_FUTURE,
              /* 4 */ getComponentContributionExpression(bindingTypeElement),
              /* 5 */ binding.bindingElement().getSimpleName().toString(),
              /* 6 */ TypeName.get(bindingTypeElement.asType()),
              /* 7 */ simpleVariableName(bindingTypeElement));
        }

      case IMMEDIATE:
      case FUTURE_PRODUCTION:
        {
          List<CodeBlock> arguments =
              Lists.newArrayListWithCapacity(binding.implicitDependencies().size() + 2);
          if (!binding.bindingElement().getModifiers().contains(STATIC)) {
            arguments.add(getComponentContributionExpression(binding.bindingTypeElement()));
          }
          arguments.addAll(getDependencyArguments(binding));

          return CodeBlocks.format(
              "new $T($L)",
              generatedClassNameForBinding(binding),
              makeParametersCodeBlock(arguments));
        }

      case SYNTHETIC_MAP:
        return CodeBlocks.format(
            "$T.create($L)",
            mapFactoryClassName(binding),
            getMemberSelectExpression(getOnlyElement(binding.dependencies()).bindingKey()));

      case SYNTHETIC_MULTIBOUND_SET:
        return initializeFactoryForSetMultibinding(binding);

      case SYNTHETIC_MULTIBOUND_MAP:
        return initializeFactoryForMapMultibinding(binding);

      default:
        throw new AssertionError(binding.toString());
    }
  }

  private CodeBlock nullableAnnotation(Optional<DeclaredType> nullableType) {
    return nullableType.isPresent()
        ? CodeBlocks.format("@$T ", TypeName.get(nullableType.get()))
        : CodeBlocks.format("");
  }

  private CodeBlock initializeMembersInjectorForBinding(MembersInjectionBinding binding) {
    switch (binding.injectionStrategy()) {
      case NO_OP:
        return CodeBlocks.format("$T.noOp()", MEMBERS_INJECTORS);
      case INJECT_MEMBERS:
        return CodeBlocks.format(
            "$T.create($L)",
            membersInjectorNameForType(binding.bindingElement()),
            makeParametersCodeBlock(getDependencyArguments(binding)));
      default:
        throw new AssertionError();
    }
  }

  private ImmutableList<CodeBlock> getDependencyArguments(
      Binding binding) {
    ImmutableList.Builder<CodeBlock> parameters = ImmutableList.builder();
    for (FrameworkDependency frameworkDependency : frameworkDependenciesForBinding(binding)) {
      parameters.add(getDependencyArgument(frameworkDependency));
    }
    return parameters.build();
  }

  private CodeBlock getDependencyArgument(FrameworkDependency frameworkDependency) {
    BindingKey requestedKey = frameworkDependency.bindingKey();
    CodeBlock frameworkExpression = getMemberSelectExpression(requestedKey);
    ResolvedBindings resolvedBindings = graph.resolvedBindings().get(requestedKey);
    if (resolvedBindings.frameworkClass().equals(Provider.class)
        && frameworkDependency.frameworkClass().equals(Producer.class)) {
      return CodeBlocks.format("$T.producerFromProvider($L)", PRODUCERS, frameworkExpression);
    } else {
      return frameworkExpression;
    }
  }

  private CodeBlock initializeFactoryForSetMultibinding(ContributionBinding binding) {
    return CodeBlocks.format(
        "$T.create($L)",
        setFactoryClassName(binding.bindingType(), binding.key()),
        makeParametersCodeBlock(getDependencyArguments(binding)));
  }

  private CodeBlock initializeFactoryForMapMultibinding(ContributionBinding binding) {
    ImmutableSet<FrameworkDependency> frameworkDependencies =
        FrameworkDependency.frameworkDependenciesForBinding(binding);

    ImmutableList.Builder<CodeBlock> codeBlocks = ImmutableList.builder();
    MapType mapType = MapType.from(binding.key().type());
    codeBlocks.add(
        CodeBlocks.format(
            "$T.<$T, $T>builder($L)",
            frameworkMapFactoryClassName(binding.bindingType()),
            TypeName.get(mapType.keyType()),
            TypeName.get(
                mapType.unwrappedValueType(binding.bindingType().frameworkClass())),
            frameworkDependencies.size()));

    for (FrameworkDependency frameworkDependency : frameworkDependencies) {
      BindingKey bindingKey = frameworkDependency.bindingKey();
      ContributionBinding contributionBinding =
          graph.resolvedBindings().get(bindingKey).contributionBinding();
      codeBlocks.add(
          CodeBlocks.format(
              ".put($L, $L)",
              getMapKeyExpression(contributionBinding.bindingElement()),
              getDependencyArgument(frameworkDependency)));
    }
    codeBlocks.add(CodeBlocks.format(".build()"));

    return CodeBlocks.concat(codeBlocks.build());
  }

  private static String simpleVariableName(TypeElement typeElement) {
    return UPPER_CAMEL.to(LOWER_CAMEL, typeElement.getSimpleName().toString());
  }

  enum InitializationState {
    UNINITIALIZED,

    DELEGATED,

    INITIALIZED;
  }
}
