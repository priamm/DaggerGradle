package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@AutoService(Processor.class)
public final class ComponentProcessor extends BasicAnnotationProcessor {
  private InjectBindingRegistry injectBindingRegistry;
  private FactoryGenerator factoryGenerator;
  private MembersInjectorGenerator membersInjectorGenerator;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedOptions() {
    return CompilerOptions.SUPPORTED_OPTIONS;
  }

  @Override
  protected Iterable<? extends ProcessingStep> initSteps() {
    Messager messager = processingEnv.getMessager();
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();
    Filer filer = processingEnv.getFiler();

    CompilerOptions compilerOptions = CompilerOptions.create(processingEnv, elements);

    MethodSignatureFormatter methodSignatureFormatter = new MethodSignatureFormatter(types);
    HasSourceElementFormatter hasSourceElementFormatter =
        new HasSourceElementFormatter(methodSignatureFormatter);
    DependencyRequestFormatter dependencyRequestFormatter = new DependencyRequestFormatter(types);
    KeyFormatter keyFormatter = new KeyFormatter(methodSignatureFormatter);

    InjectConstructorValidator injectConstructorValidator = new InjectConstructorValidator();
    InjectFieldValidator injectFieldValidator = new InjectFieldValidator(compilerOptions);
    InjectMethodValidator injectMethodValidator = new InjectMethodValidator(compilerOptions);
    MembersInjectedTypeValidator membersInjectedTypeValidator =
        new MembersInjectedTypeValidator(injectFieldValidator, injectMethodValidator);
    ModuleValidator moduleValidator =
        new ModuleValidator(types, elements, methodSignatureFormatter);
    BuilderValidator builderValidator = new BuilderValidator(elements, types);
    ComponentValidator subcomponentValidator =
        ComponentValidator.createForSubcomponent(
            elements, types, moduleValidator, builderValidator);
    ComponentValidator componentValidator =
        ComponentValidator.createForComponent(
            elements, types, moduleValidator, subcomponentValidator, builderValidator);
    MapKeyValidator mapKeyValidator = new MapKeyValidator();
    ProvidesMethodValidator providesMethodValidator = new ProvidesMethodValidator(elements, types);
    ProducesMethodValidator producesMethodValidator = new ProducesMethodValidator(elements, types);

    Key.Factory keyFactory = new Key.Factory(types, elements);

    MultibindingsValidator multibindingsValidator =
        new MultibindingsValidator(elements, keyFactory, keyFormatter, methodSignatureFormatter);

    this.factoryGenerator = new FactoryGenerator(filer, elements, compilerOptions);
    this.membersInjectorGenerator = new MembersInjectorGenerator(filer, elements);
    ComponentGenerator componentGenerator =
        new ComponentGenerator(filer, elements, types, keyFactory, compilerOptions);
    ProducerFactoryGenerator producerFactoryGenerator =
        new ProducerFactoryGenerator(filer, elements, compilerOptions);
    MonitoringModuleGenerator monitoringModuleGenerator =
        new MonitoringModuleGenerator(filer, elements);
    ProductionExecutorModuleGenerator productionExecutorModuleGenerator =
        new ProductionExecutorModuleGenerator(filer, elements);

    DependencyRequest.Factory dependencyRequestFactory =
        new DependencyRequest.Factory(elements, keyFactory);
    ProvisionBinding.Factory provisionBindingFactory =
        new ProvisionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);
    ProductionBinding.Factory productionBindingFactory =
        new ProductionBinding.Factory(types, keyFactory, dependencyRequestFactory);
    MultibindingDeclaration.Factory multibindingDeclarationFactory =
        new MultibindingDeclaration.Factory(elements, types, keyFactory);

    MembersInjectionBinding.Factory membersInjectionBindingFactory =
        new MembersInjectionBinding.Factory(elements, types, keyFactory, dependencyRequestFactory);

    this.injectBindingRegistry =
        new InjectBindingRegistry(
            elements,
            types,
            messager,
            injectConstructorValidator,
            membersInjectedTypeValidator,
            keyFactory,
            provisionBindingFactory,
            membersInjectionBindingFactory);

    ModuleDescriptor.Factory moduleDescriptorFactory =
        new ModuleDescriptor.Factory(
            elements,
            provisionBindingFactory,
            productionBindingFactory,
            multibindingDeclarationFactory);

    ComponentDescriptor.Factory componentDescriptorFactory = new ComponentDescriptor.Factory(
        elements, types, dependencyRequestFactory, moduleDescriptorFactory);

    BindingGraph.Factory bindingGraphFactory =
        new BindingGraph.Factory(
            elements,
            injectBindingRegistry,
            keyFactory,
            provisionBindingFactory,
            productionBindingFactory);

    MapKeyGenerator mapKeyGenerator = new MapKeyGenerator(filer, elements);
    ComponentHierarchyValidator componentHierarchyValidator = new ComponentHierarchyValidator();
    BindingGraphValidator bindingGraphValidator =
        new BindingGraphValidator(
            elements,
            types,
            compilerOptions,
            injectBindingRegistry,
            hasSourceElementFormatter,
            methodSignatureFormatter,
            dependencyRequestFormatter,
            keyFormatter,
            keyFactory);

    return ImmutableList.of(
        new MapKeyProcessingStep(messager, types, mapKeyValidator, mapKeyGenerator),
        new InjectProcessingStep(injectBindingRegistry),
        new MonitoringModuleProcessingStep(messager, monitoringModuleGenerator),
        new ProductionExecutorModuleProcessingStep(messager, productionExecutorModuleGenerator),
        new MultibindingsProcessingStep(messager, multibindingsValidator),
        new ModuleProcessingStep(
            messager,
            moduleValidator,
            providesMethodValidator,
            provisionBindingFactory,
            factoryGenerator),
        new ComponentProcessingStep(
            ComponentDescriptor.Kind.COMPONENT,
            messager,
            componentValidator,
            subcomponentValidator,
            builderValidator,
            componentHierarchyValidator,
            bindingGraphValidator,
            componentDescriptorFactory,
            bindingGraphFactory,
            componentGenerator),
        new ProducerModuleProcessingStep(
            messager,
            moduleValidator,
            producesMethodValidator,
            productionBindingFactory,
            producerFactoryGenerator),
        new ComponentProcessingStep(
            ComponentDescriptor.Kind.PRODUCTION_COMPONENT,
            messager,
            componentValidator,
            subcomponentValidator,
            builderValidator,
            componentHierarchyValidator,
            bindingGraphValidator,
            componentDescriptorFactory,
            bindingGraphFactory,
            componentGenerator));
  }

  @Override
  protected void postProcess() {
    try {
      injectBindingRegistry.generateSourcesForRequiredBindings(
          factoryGenerator, membersInjectorGenerator);
    } catch (SourceFileGenerationException e) {
      e.printMessageTo(processingEnv.getMessager());
    }
  }
}
