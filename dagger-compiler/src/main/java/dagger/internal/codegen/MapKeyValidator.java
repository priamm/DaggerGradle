package dagger.internal.codegen;

import dagger.MapKey;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

import static dagger.internal.codegen.ErrorMessages.MAPKEY_WITHOUT_MEMBERS;
import static dagger.internal.codegen.ErrorMessages.UNWRAPPED_MAP_KEY_WITH_ARRAY_MEMBER;
import static dagger.internal.codegen.ErrorMessages.UNWRAPPED_MAP_KEY_WITH_TOO_MANY_MEMBERS;
import static javax.lang.model.util.ElementFilter.methodsIn;

final class MapKeyValidator {
  ValidationReport<Element> validate(Element element) {
    ValidationReport.Builder<Element> builder = ValidationReport.about(element);
    List<ExecutableElement> members = methodsIn(((TypeElement) element).getEnclosedElements());
    if (members.isEmpty()) {
      builder.addError(MAPKEY_WITHOUT_MEMBERS, element);
    } else if (element.getAnnotation(MapKey.class).unwrapValue()) {
      if (members.size() > 1) {
        builder.addError(UNWRAPPED_MAP_KEY_WITH_TOO_MANY_MEMBERS, element);
      } else if (members.get(0).getReturnType().getKind() == TypeKind.ARRAY) {
        builder.addError(UNWRAPPED_MAP_KEY_WITH_ARRAY_MEMBER, element);
      }
    }
    return builder.build();
  }
}
