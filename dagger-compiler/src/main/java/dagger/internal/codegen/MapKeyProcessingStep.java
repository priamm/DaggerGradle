package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import dagger.MapKey;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

public class MapKeyProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
  private final Messager messager;
  private final MapKeyValidator mapKeyValidator;
  private final MapKeyGenerator mapKeyGenerator;

  MapKeyProcessingStep(Messager messager, MapKeyValidator mapKeyValidator,
      MapKeyGenerator mapKeyGenerator) {
    this.messager = messager;
    this.mapKeyValidator = mapKeyValidator;
    this.mapKeyGenerator = mapKeyGenerator;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(MapKey.class);
  }

  @Override
  public Set<? extends Element> process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    for (Element element : elementsByAnnotation.get(MapKey.class)) {
      ValidationReport<Element> mapKeyReport = mapKeyValidator.validate(element);
      mapKeyReport.printMessagesTo(messager);

      if (mapKeyReport.isClean()) {
        MapKey mapkey = element.getAnnotation(MapKey.class);
        if (!mapkey.unwrapValue()) {
          try {
            mapKeyGenerator.generate(element);
          } catch (SourceFileGenerationException e) {
            e.printMessageTo(messager);
          }
        }
      }
    }
    return null;
  }
}
