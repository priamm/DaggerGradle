package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import static com.google.common.base.Preconditions.checkState;
import static dagger.internal.codegen.ErrorMessages.stripCommonTypePrefixes;

final class MethodSignatureFormatter extends Formatter<ExecutableElement> {
  private final Types types;

  MethodSignatureFormatter(Types types) {
    this.types = types;
  }

  @Override public String format(ExecutableElement method) {
    return format(method, Optional.<DeclaredType>absent());
  }

  public String format(ExecutableElement method, Optional<DeclaredType> container) {
    StringBuilder builder = new StringBuilder();
    TypeElement type = MoreElements.asType(method.getEnclosingElement());
    ExecutableType executableType = MoreTypes.asExecutable(method.asType());
    if (container.isPresent()) {
      executableType = MoreTypes.asExecutable(types.asMemberOf(container.get(), method));
      type = MoreElements.asType(container.get().asElement());
    }

    List<? extends AnnotationMirror> annotations = method.getAnnotationMirrors();
    if (!annotations.isEmpty()) {
      Iterator<? extends AnnotationMirror> annotationIterator = annotations.iterator();
      for (int i = 0; annotationIterator.hasNext(); i++) {
        if (i > 0) {
          builder.append(' ');
        }
        builder.append(ErrorMessages.format(annotationIterator.next()));
      }
      builder.append(' ');
    }
    builder.append(nameOfType(executableType.getReturnType()));
    builder.append(' ');
    builder.append(type.getQualifiedName());
    builder.append('.');
    builder.append(method.getSimpleName());
    builder.append('(');
    checkState(method.getParameters().size() == executableType.getParameterTypes().size());
    Iterator<? extends VariableElement> parameters = method.getParameters().iterator();
    Iterator<? extends TypeMirror> parameterTypes = executableType.getParameterTypes().iterator();
    for (int i = 0; parameters.hasNext(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      appendParameter(builder, parameters.next(), parameterTypes.next());
    }
    builder.append(')');
    return builder.toString();
  }

  private static void appendParameter(StringBuilder builder, VariableElement parameter,
      TypeMirror type) {
    Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(parameter);
    if (qualifier.isPresent()) {
      builder.append(ErrorMessages.format(qualifier.get())).append(' ');
    }
    builder.append(nameOfType(type));
  }

  private static String nameOfType(TypeMirror type) {
    if (type.getKind().isPrimitive()) {
      return MoreTypes.asPrimitiveType(type).toString();
    }
    return stripCommonTypePrefixes(MoreTypes.asDeclared(type).toString());
  }
}
