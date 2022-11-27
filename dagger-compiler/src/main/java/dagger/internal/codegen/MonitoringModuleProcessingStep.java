package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.producers.ProductionComponent;
import dagger.producers.ProductionSubcomponent;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

final class MonitoringModuleProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final MonitoringModuleGenerator monitoringModuleGenerator;

  MonitoringModuleProcessingStep(
      Messager messager, MonitoringModuleGenerator monitoringModuleGenerator) {
    this.messager = messager;
    this.monitoringModuleGenerator = monitoringModuleGenerator;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(ProductionComponent.class, ProductionSubcomponent.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Element element : elementsByAnnotation.values()) {
      try {
        monitoringModuleGenerator.generate(MoreElements.asType(element));
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }
    return ImmutableSet.of();
  }
}
