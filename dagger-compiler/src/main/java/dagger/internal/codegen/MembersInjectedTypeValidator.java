package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

final class MembersInjectedTypeValidator {
  private final InjectFieldValidator fieldValidator;
  private final InjectMethodValidator methodValidator;

  MembersInjectedTypeValidator(
      InjectFieldValidator fieldValidator, InjectMethodValidator methodValidator) {
    this.fieldValidator = fieldValidator;
    this.methodValidator = methodValidator;
  }

  ValidationReport<TypeElement> validate(TypeElement typeElement) {
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(typeElement);
    for (VariableElement element : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        ValidationReport<VariableElement> report = fieldValidator.validate(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    for (ExecutableElement element : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      if (MoreElements.isAnnotationPresent(element, Inject.class)) {
        ValidationReport<ExecutableElement> report = methodValidator.validate(element);
        if (!report.isClean()) {
          builder.addSubreport(report);
        }
      }
    }
    TypeMirror superclass = typeElement.getSuperclass();
    if (!superclass.getKind().equals(TypeKind.NONE)) {
      ValidationReport<TypeElement> report = validate(MoreTypes.asTypeElement(superclass));
      if (!report.isClean()) {
        builder.addSubreport(report);
      }
    }
    return builder.build();
  }
}
