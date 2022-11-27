package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import java.util.Set;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@AutoValue
abstract class SetType {

  protected abstract Equivalence.Wrapper<DeclaredType> wrappedDeclaredSetType();

  DeclaredType declaredSetType() {
    return wrappedDeclaredSetType().get();
  }

  boolean isRawType() {
    return declaredSetType().getTypeArguments().isEmpty();
  }

  TypeMirror elementType() {
    return declaredSetType().getTypeArguments().get(0);
  }

  boolean elementsAreTypeOf(Class<?> clazz) {
    return MoreTypes.isType(elementType()) && MoreTypes.isTypeOf(clazz, elementType());
  }

  TypeMirror unwrappedElementType(Class<?> wrappingClass) {
    checkArgument(
        wrappingClass.getTypeParameters().length == 1,
        "%s must have exactly one type parameter",
        wrappingClass);
    checkState(elementsAreTypeOf(wrappingClass));
    return MoreTypes.asDeclared(elementType()).getTypeArguments().get(0);
  }

  static boolean isSet(TypeMirror type) {
    return MoreTypes.isType(type) && MoreTypes.isTypeOf(Set.class, type);
  }

  static SetType from(TypeMirror type) {
    checkArgument(isSet(type), "%s must be a Set", type);
    return new AutoValue_SetType(MoreTypes.equivalence().wrap(MoreTypes.asDeclared(type)));
  }
}
