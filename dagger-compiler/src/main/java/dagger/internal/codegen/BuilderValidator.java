package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

class BuilderValidator {
  private final Elements elements;
  private final Types types;

  BuilderValidator(Elements elements, Types types) {
    this.elements = elements;
    this.types = types;
  }

  public ValidationReport<TypeElement> validate(TypeElement subject) {
    ValidationReport.Builder<TypeElement> builder = ValidationReport.about(subject);

    ComponentDescriptor.Kind componentKind =
        ComponentDescriptor.Kind.forAnnotatedBuilderElement(subject).get();

    Element componentElement = subject.getEnclosingElement();
    ErrorMessages.ComponentBuilderMessages msgs = ErrorMessages.builderMsgsFor(componentKind);
    Class<? extends Annotation> componentAnnotation = componentKind.annotationType();
    Class<? extends Annotation> builderAnnotation = componentKind.builderAnnotationType();
    checkArgument(subject.getAnnotation(builderAnnotation) != null);

    if (!isAnnotationPresent(componentElement, componentAnnotation)) {
      builder.addError(msgs.mustBeInComponent(), subject);
    }

    switch (subject.getKind()) {
      case CLASS:
        List<? extends Element> allElements = subject.getEnclosedElements();
        List<ExecutableElement> cxtors = ElementFilter.constructorsIn(allElements);
        if (cxtors.size() != 1 || getOnlyElement(cxtors).getParameters().size() != 0) {
          builder.addError(msgs.cxtorOnlyOneAndNoArgs(), subject);
        }
        break;
      case INTERFACE:
        break;
      default:
        builder.addError(msgs.mustBeClassOrInterface(), subject);
        return builder.build();
    }

    if (!subject.getTypeParameters().isEmpty()) {
      builder.addError(msgs.generics(), subject);
    }

    Set<Modifier> modifiers = subject.getModifiers();
    if (modifiers.contains(PRIVATE)) {
      builder.addError(msgs.isPrivate(), subject);
    }
    if (!modifiers.contains(STATIC)) {
      builder.addError(msgs.mustBeStatic(), subject);
    }

    if (!modifiers.contains(ABSTRACT)) {
      builder.addError(msgs.mustBeAbstract(), subject);
    }

    ExecutableElement buildMethod = null;
    Multimap<Equivalence.Wrapper<TypeMirror>, ExecutableElement> methodsPerParam =
        LinkedHashMultimap.create();
    for (ExecutableElement method : Util.getUnimplementedMethods(elements, subject)) {
      ExecutableType resolvedMethodType =
          MoreTypes.asExecutable(types.asMemberOf(MoreTypes.asDeclared(subject.asType()), method));
      TypeMirror returnType = resolvedMethodType.getReturnType();
      if (method.getParameters().size() == 0) {
        if (types.isSameType(returnType, componentElement.asType())) {
          if (buildMethod != null) {
            error(builder, method, msgs.twoBuildMethods(), msgs.inheritedTwoBuildMethods(),
                buildMethod);
          }
        } else {
          error(builder, method, msgs.buildMustReturnComponentType(),
              msgs.inheritedBuildMustReturnComponentType());
        }
        buildMethod = method;
      } else if (method.getParameters().size() > 1) {
        error(builder, method, msgs.methodsMustTakeOneArg(), msgs.inheritedMethodsMustTakeOneArg());
      } else if (returnType.getKind() != TypeKind.VOID
          && !types.isSubtype(subject.asType(), returnType)) {
        error(builder, method, msgs.methodsMustReturnVoidOrBuilder(),
            msgs.inheritedMethodsMustReturnVoidOrBuilder());
      } else {
        methodsPerParam.put(
            MoreTypes.equivalence().<TypeMirror>wrap(
                Iterables.getOnlyElement(resolvedMethodType.getParameterTypes())),
            method);
      }

      if (!method.getTypeParameters().isEmpty()) {
        error(builder, method, msgs.methodsMayNotHaveTypeParameters(),
            msgs.inheritedMethodsMayNotHaveTypeParameters());
      }
    }

    if (buildMethod == null) {
      builder.addError(msgs.missingBuildMethod(), subject);
    }

    for (Map.Entry<Equivalence.Wrapper<TypeMirror>, Collection<ExecutableElement>> entry :
        methodsPerParam.asMap().entrySet()) {
      if (entry.getValue().size() > 1) {
        TypeMirror type = entry.getKey().get();
        builder.addError(String.format(msgs.manyMethodsForType(), type, entry.getValue()), subject);
      }
    }

    return builder.build();
  }

  private void error(
      ValidationReport.Builder<TypeElement> builder,
      ExecutableElement method,
      String enclosedError,
      String inheritedError,
      Object... extraArgs) {
    if (method.getEnclosingElement().equals(builder.getSubject())) {
      builder.addError(String.format(enclosedError, extraArgs), method);
    } else {
      Object[] newArgs = new Object[extraArgs.length + 1];
      newArgs[0] = method;
      System.arraycopy(extraArgs, 0, newArgs, 1, extraArgs.length);
      builder.addError(String.format(inheritedError, newArgs), builder.getSubject());
    }
  }
}
