package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;

import static com.google.common.base.Preconditions.checkState;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.STATIC;

final class Util {

  public static TypeMirror getProvidedValueTypeOfMap(DeclaredType mapType) {
    checkState(MoreTypes.isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    return MoreTypes.asDeclared(mapType.getTypeArguments().get(1)).getTypeArguments().get(0);
  }

  public static TypeMirror getValueTypeOfMap(DeclaredType mapType) {
    checkState(MoreTypes.isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    List<? extends TypeMirror> mapArgs = mapType.getTypeArguments();
    return mapArgs.get(1);
  }

  public static DeclaredType getKeyTypeOfMap(DeclaredType mapType) {
    checkState(MoreTypes.isTypeOf(Map.class, mapType), "%s is not a Map.", mapType);
    List<? extends TypeMirror> mapArgs = mapType.getTypeArguments();
    return MoreTypes.asDeclared(mapArgs.get(0));
  }

  public static TypeElement getKeyTypeElement(AnnotationMirror mapKey, final Elements elements) {
    Map<? extends ExecutableElement, ? extends AnnotationValue> map = mapKey.getElementValues();
    AnnotationValueVisitor<TypeElement, Void> mapKeyVisitor =
        new SimpleAnnotationValueVisitor6<TypeElement, Void>() {
          @Override
          public TypeElement visitEnumConstant(VariableElement c, Void p) {
            return MoreElements.asType(c.getEnclosingElement()) ;
          }

          @Override
          public TypeElement visitString(String s, Void p) {
            return elements.getTypeElement(String.class.getCanonicalName());
          }

          @Override
          protected TypeElement defaultAction(Object o, Void v) {
            throw new IllegalStateException(
                "Non-supported key type for map binding " + o.getClass().getCanonicalName());
          }
        };
    TypeElement keyTypeElement =
        Iterables.getOnlyElement(map.entrySet()).getValue().accept(mapKeyVisitor, null);
    return keyTypeElement;
  }

  static <T> Optional<Equivalence.Wrapper<T>> wrapOptionalInEquivalence(
      Equivalence<T> equivalence, Optional<T> optional) {
    return optional.isPresent()
        ? Optional.of(equivalence.wrap(optional.get()))
        : Optional.<Equivalence.Wrapper<T>>absent();
  }

  static <T> Optional<T> unwrapOptionalEquivalence(
      Optional<Equivalence.Wrapper<T>> wrappedOptional) {
    return wrappedOptional.isPresent()
        ? Optional.of(wrappedOptional.get().get())
        : Optional.<T>absent();
  }

  private static boolean requiresEnclosingInstance(TypeElement typeElement) {
    switch (typeElement.getNestingKind()) {
      case TOP_LEVEL:
        return false;
      case MEMBER:
        return !typeElement.getModifiers().contains(STATIC);
      case ANONYMOUS:
      case LOCAL:
        return true;
      default:
        throw new AssertionError("TypeElement cannot have nesting kind: "
            + typeElement.getNestingKind());
    }
  }

  static boolean componentCanMakeNewInstances(TypeElement typeElement) {
    switch (typeElement.getKind()) {
      case CLASS:
        break;
      case ENUM:
      case ANNOTATION_TYPE:
      case INTERFACE:
        return false;
      default:
        throw new AssertionError("TypeElement cannot have kind: " + typeElement.getKind());
    }

    if (typeElement.getModifiers().contains(ABSTRACT)) {
      return false;
    }

    if (requiresEnclosingInstance(typeElement)) {
      return false;
    }

    for (Element enclosed : typeElement.getEnclosedElements()) {
      if (enclosed.getKind().equals(CONSTRUCTOR)
          && ((ExecutableElement) enclosed).getParameters().isEmpty()) {
        return true;
      }
    }

    return false;
  }

  private Util() {}
}
