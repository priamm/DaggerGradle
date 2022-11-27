package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import java.util.Map;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

@AutoValue
abstract class MapType {

  protected abstract Equivalence.Wrapper<DeclaredType> wrappedDeclaredMapType();

  DeclaredType declaredMapType() {
    return wrappedDeclaredMapType().get();
  }

  boolean isRawType() {
    return declaredMapType().getTypeArguments().isEmpty();
  }

  TypeMirror keyType() {
    checkState(!isRawType());
    return declaredMapType().getTypeArguments().get(0);
  }

  TypeMirror valueType() {
    checkState(!isRawType());
    return declaredMapType().getTypeArguments().get(1);
  }

  boolean valuesAreTypeOf(Class<?> clazz) {
    return MoreTypes.isType(valueType()) && MoreTypes.isTypeOf(clazz, valueType());
  }

  TypeMirror unwrappedValueType(Class<?> wrappingClass) {
    checkArgument(
        wrappingClass.getTypeParameters().length == 1,
        "%s must have exactly one type parameter",
        wrappingClass);
    checkState(valuesAreTypeOf(wrappingClass));
    return MoreTypes.asDeclared(valueType()).getTypeArguments().get(0);
  }

  static boolean isMap(TypeMirror type) {
    return MoreTypes.isType(type) && MoreTypes.isTypeOf(Map.class, type);
  }

  static MapType from(TypeMirror type) {
    checkArgument(isMap(type), "%s is not a Map", type);
    return new AutoValue_MapType(MoreTypes.equivalence().wrap(MoreTypes.asDeclared(type)));
  }
}
