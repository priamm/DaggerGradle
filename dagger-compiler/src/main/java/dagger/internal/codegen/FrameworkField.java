package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.ParameterizedTypeName;
import dagger.internal.codegen.writer.TypeName;
import dagger.internal.codegen.writer.TypeNames;
import javax.lang.model.type.TypeMirror;

@AutoValue
abstract class FrameworkField {

  static FrameworkField createWithTypeFromKey(
      Class<?> frameworkClass, BindingKey bindingKey, String name) {
    String suffix = frameworkClass.getSimpleName();
    ParameterizedTypeName frameworkType = ParameterizedTypeName.create(
        ClassName.fromClass(frameworkClass),
        TypeNames.forTypeMirror(bindingKey.key().type()));
    return new AutoValue_FrameworkField(frameworkClass, frameworkType, bindingKey,
        name.endsWith(suffix) ? name : name + suffix);
  }

  static FrameworkField createForMapBindingContribution(
      Class<?> frameworkClass, BindingKey bindingKey, String name) {
    TypeMirror mapValueType =
        MoreTypes.asDeclared(bindingKey.key().type()).getTypeArguments().get(1);
    return new AutoValue_FrameworkField(frameworkClass,
        TypeNames.forTypeMirror(mapValueType),
        bindingKey,
        name);
  }

  abstract Class<?> frameworkClass();
  abstract TypeName frameworkType();
  abstract BindingKey bindingKey();
  abstract String name();
}
