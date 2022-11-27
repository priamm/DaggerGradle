package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor.ProcessingStep;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.Multibindings;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static javax.lang.model.util.ElementFilter.typesIn;

class MultibindingsProcessingStep implements ProcessingStep {
  private final Messager messager;
  private final MultibindingsValidator multibindingsValidator;

  MultibindingsProcessingStep(Messager messager, MultibindingsValidator multibindingsValidator) {
    this.messager = messager;
    this.multibindingsValidator = multibindingsValidator;
  }

  @Override
  public Set<? extends Class<? extends Annotation>> annotations() {
    return ImmutableSet.of(Multibindings.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (TypeElement element : typesIn(elementsByAnnotation.values())) {
      multibindingsValidator.validate(element).printMessagesTo(messager);
    }
    return ImmutableSet.of();
  }
}
