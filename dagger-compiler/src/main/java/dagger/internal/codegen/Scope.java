package dagger.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.producers.ProductionScope;
import javax.inject.Singleton;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;
import static dagger.internal.codegen.InjectionAnnotations.getScopes;

final class Scope {

  private final AnnotationMirror annotationMirror;

  private Scope(AnnotationMirror annotationMirror) {
    this.annotationMirror = checkNotNull(annotationMirror);
  }

  static ImmutableSet<Scope> scopesOf(Element element) {
    return FluentIterable.from(getScopes(element)).
        transform(new Function<AnnotationMirror, Scope>() {
          @Override public Scope apply(AnnotationMirror annotationMirror) {
            return new Scope(annotationMirror);
          }
        }).toSet();
  }

  static Optional<Scope> uniqueScopeOf(Element element) {
    ImmutableSet<? extends AnnotationMirror> scopeAnnotations = getScopes(element);
    if (scopeAnnotations.isEmpty()) {
      return Optional.absent();
    }
    return Optional.of(new Scope(Iterables.getOnlyElement(scopeAnnotations)));
  }

  static Scope productionScope(Elements elements) {
    return new Scope(
        SimpleAnnotationMirror.of(
            elements.getTypeElement(ProductionScope.class.getCanonicalName())));
  }

  static Scope singletonScope(Elements elements) {
    return new Scope(
        SimpleAnnotationMirror.of(
            elements.getTypeElement(Singleton.class.getCanonicalName())));
  }

  public String getReadableSource() {
    return stripCommonTypePrefixes("@" + getQualifiedName());
  }

  public String getQualifiedName() {
    Preconditions.checkState(annotationMirror != null,
        "Cannot create a stripped source representation of no annotation");
    TypeElement typeElement = MoreTypes.asTypeElement(annotationMirror.getAnnotationType());
    return typeElement.getQualifiedName().toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    } else if (obj instanceof Scope) {
      Scope that = (Scope) obj;
      return AnnotationMirrors.equivalence()
        .equivalent(this.annotationMirror, that.annotationMirror);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return AnnotationMirrors.equivalence().hash(annotationMirror);
  }

  @Override
  public String toString() {
    return annotationMirror.toString();
  }
}
