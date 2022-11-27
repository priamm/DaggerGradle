package dagger.internal.codegen;

import dagger.MapKey;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static dagger.internal.codegen.ErrorMessages.MAPKEY_WITHOUT_FIELDS;

final class MapKeyValidator implements Validator<Element> {
  @Override
  public ValidationReport<Element> validate(Element element) {
    ValidationReport.Builder<Element> builder = ValidationReport.Builder.about(element);
    if (((TypeElement) element).getEnclosedElements().isEmpty()) {
      builder.addItem(MAPKEY_WITHOUT_FIELDS, element);
    }
    return builder.build();
  }
}
