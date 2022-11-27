package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Component;
import dagger.internal.codegen.ComponentDescriptor.Factory;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

final class ComponentProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final ComponentValidator componentValidator;
  private final BindingGraphValidator bindingGraphValidator;
  private final ComponentDescriptor.Factory componentDescriptorFactory;
  private final BindingGraph.Factory bindingGraphFactory;
  private final ComponentGenerator componentGenerator;

  ComponentProcessingStep(
      Messager messager,
      ComponentValidator componentValidator,
      BindingGraphValidator bindingGraphValidator,
      Factory componentDescriptorFactory,
      BindingGraph.Factory bindingGraphFactory,
      ComponentGenerator componentGenerator) {
    this.messager = messager;
    this.componentValidator = componentValidator;
    this.bindingGraphValidator = bindingGraphValidator;
    this.componentDescriptorFactory = componentDescriptorFactory;
    this.bindingGraphFactory = bindingGraphFactory;
    this.componentGenerator = componentGenerator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(Component.class);
  }

  @Override
  public Set<? extends Element> process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    Set<? extends Element> componentElements = elementsByAnnotation.get(Component.class);

    for (Element element : componentElements) {
      TypeElement componentTypeElement = MoreElements.asType(element);
      ValidationReport<TypeElement> componentReport =
          componentValidator.validate(componentTypeElement);
      componentReport.printMessagesTo(messager);
      if (componentReport.isClean()) {
        ComponentDescriptor componentDescriptor =
            componentDescriptorFactory.forComponent(componentTypeElement);
        BindingGraph bindingGraph = bindingGraphFactory.create(componentDescriptor);
        ValidationReport<BindingGraph> graphReport =
            bindingGraphValidator.validate(bindingGraph);
        graphReport.printMessagesTo(messager);
        if (graphReport.isClean()) {
          try {
            componentGenerator.generate(bindingGraph);
          } catch (SourceFileGenerationException e) {
            e.printMessageTo(messager);
          }
        }
      }
    }
    return null;
  }
}
