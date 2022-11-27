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

final class ProductionExecutorModuleProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final ProductionExecutorModuleGenerator productionExecutorModuleGenerator;

  ProductionExecutorModuleProcessingStep(
      Messager messager, ProductionExecutorModuleGenerator productionExecutorModuleGenerator) {
    this.messager = messager;
    this.productionExecutorModuleGenerator = productionExecutorModuleGenerator;
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
        productionExecutorModuleGenerator.generate(MoreElements.asType(element));
      } catch (SourceFileGenerationException e) {
        e.printMessageTo(messager);
      }
    }
    return ImmutableSet.of();
  }
}
