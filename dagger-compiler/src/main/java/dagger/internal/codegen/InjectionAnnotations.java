package dagger.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import javax.inject.Qualifier;
import javax.inject.Scope;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import static com.google.common.base.Preconditions.checkNotNull;

final class InjectionAnnotations {
  static Optional<AnnotationMirror> getQualifier(Element e) {
    checkNotNull(e);
    ImmutableSet<? extends AnnotationMirror> qualifierAnnotations = getQualifiers(e);
    switch (qualifierAnnotations.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.<AnnotationMirror>of(qualifierAnnotations.iterator().next());
      default:
        throw new IllegalArgumentException(
            e + " was annotated with more than one @Qualifier annotation");
    }
  }

  static ImmutableSet<? extends AnnotationMirror> getQualifiers(Element element) {
    return AnnotationMirrors.getAnnotatedAnnotations(element, Qualifier.class);
  }

  static ImmutableSet<? extends AnnotationMirror> getScopes(Element element) {
    return AnnotationMirrors.getAnnotatedAnnotations(element, Scope.class);
  }

  private InjectionAnnotations() {}
}
