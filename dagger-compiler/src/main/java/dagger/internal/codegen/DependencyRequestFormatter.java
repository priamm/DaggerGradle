package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;
import static dagger.internal.codegen.ErrorMessages.INDENT;

final class DependencyRequestFormatter extends Formatter<DependencyRequest> {
  private final Types types;

  DependencyRequestFormatter(Types types) {
    this.types = types;
  }

  @Override public String format(final DependencyRequest request) {
    Element requestElement = request.requestElement();
    Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(requestElement);
    return requestElement.accept(new SimpleElementVisitor6<String, Optional<AnnotationMirror>>(){

      @Override public String visitExecutable(
          ExecutableElement method, Optional<AnnotationMirror> qualifier) {
        StringBuilder builder = new StringBuilder(INDENT);
        if (method.getParameters().isEmpty()) {
          appendEnclosingTypeAndMemberName(method, builder).append("()\n")
              .append(INDENT).append(INDENT).append("[component method with return type: ");
          if (qualifier.isPresent()) {
            builder.append(qualifier.get()).append(' ');
          }
          builder.append(method.getReturnType()).append(']');
        } else {
          VariableElement componentMethodParameter = getOnlyElement(method.getParameters());
          appendEnclosingTypeAndMemberName(method, builder).append("(");
          appendParameter(componentMethodParameter, componentMethodParameter.asType(), builder);
          builder.append(")\n");
          builder.append(INDENT).append(INDENT).append("[component injection method for type: ")
              .append(componentMethodParameter.asType())
              .append(']');
        }
        return builder.toString();
      }

      @Override public String visitVariable(
          VariableElement variable, Optional<AnnotationMirror> qualifier) {
        StringBuilder builder = new StringBuilder(INDENT);
        TypeMirror resolvedVariableType =
            MoreTypes.asMemberOf(types, request.enclosingType(), variable);
        if (variable.getKind().equals(ElementKind.PARAMETER)) {
          ExecutableElement methodOrConstructor =
              MoreElements.asExecutable(variable.getEnclosingElement());
          ExecutableType resolvedMethodOrConstructor = MoreTypes.asExecutable(
              types.asMemberOf(request.enclosingType(), methodOrConstructor));
          appendEnclosingTypeAndMemberName(methodOrConstructor, builder).append('(');
          List<? extends VariableElement> parameters = methodOrConstructor.getParameters();
          List<? extends TypeMirror> parameterTypes =
              resolvedMethodOrConstructor.getParameterTypes();
          checkState(parameters.size() == parameterTypes.size());
          for (int i = 0; i < parameters.size(); i++) {
            appendParameter(parameters.get(i), parameterTypes.get(i), builder);
            if (i != parameters.size() - 1) {
              builder.append(", ");
            }
          }
          builder.append(")\n").append(INDENT).append(INDENT).append("[parameter: ");
        } else {
          appendEnclosingTypeAndMemberName(variable, builder).append("\n")
              .append(INDENT).append(INDENT).append("[injected field of type: ");
        }
        if (qualifier.isPresent()) {
          builder.append(qualifier.get()).append(' ');
        }
        builder.append(resolvedVariableType)
            .append(' ')
            .append(variable.getSimpleName())
            .append(']');
        return builder.toString();
      }

      @Override
      public String visitType(TypeElement e, Optional<AnnotationMirror> p) {
        return "";
      }

      @Override protected String defaultAction(Element element, Optional<AnnotationMirror> ignore) {
        throw new IllegalStateException(
            "Invalid request " + element.getKind() +  " element " + element);
      }
    }, qualifier);
  }

  private StringBuilder appendParameter(VariableElement parameter, TypeMirror type,
      StringBuilder builder) {
    return builder.append(type).append(' ').append(parameter.getSimpleName());
  }

  private StringBuilder appendEnclosingTypeAndMemberName(Element member, StringBuilder builder) {
    TypeElement type = MoreElements.asType(member.getEnclosingElement());
    return builder.append(type.getQualifiedName())
        .append('.')
        .append(member.getSimpleName());
  }
}
