package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.Types;

@AutoValue
abstract class SourceElement {

  interface HasSourceElement {
    SourceElement sourceElement();
  }

  abstract Element element();

  abstract Optional<TypeElement> contributedBy();

  TypeElement enclosingTypeElement() {
    return BINDING_TYPE_ELEMENT.visit(element());
  }

  TypeMirror asMemberOfContributingType(Types types) {
    return contributedBy().isPresent()
        ? types.asMemberOf(MoreTypes.asDeclared(contributedBy().get().asType()), element())
        : element().asType();
  }

  private static final ElementVisitor<TypeElement, Void> BINDING_TYPE_ELEMENT =
      new SimpleElementVisitor6<TypeElement, Void>() {
        @Override
        protected TypeElement defaultAction(Element e, Void p) {
          return visit(e.getEnclosingElement());
        }

        @Override
        public TypeElement visitType(TypeElement e, Void p) {
          return e;
        }
      };

  static SourceElement forElement(Element element) {
    return new AutoValue_SourceElement(element, Optional.<TypeElement>absent());
  }

  static SourceElement forElement(Element element, TypeElement contributedBy) {
    return new AutoValue_SourceElement(element, Optional.of(contributedBy));
  }
}
