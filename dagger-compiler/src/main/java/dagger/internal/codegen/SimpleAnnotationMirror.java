package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static com.google.common.base.Preconditions.checkArgument;

final class SimpleAnnotationMirror implements AnnotationMirror {
  private final DeclaredType type;

  private SimpleAnnotationMirror(DeclaredType type) {
    this.type = type;
  }

  @Override
  public DeclaredType getAnnotationType() {
    return type;
  }

  @Override
  public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValues() {
    return ImmutableMap.of();
  }

  @Override
  public String toString() {
    return "@" + type;
  }

  static AnnotationMirror of(TypeElement element) {
    checkArgument(element.getKind().equals(ElementKind.ANNOTATION_TYPE));
    checkArgument(element.getEnclosedElements().isEmpty());
    return new SimpleAnnotationMirror(MoreTypes.asDeclared(element.asType()));
  }
}
