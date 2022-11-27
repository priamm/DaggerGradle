package dagger.internal.codegen;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import dagger.Provides;

final class AnnotationSpecs {

  static final AnnotationSpec SUPPRESS_WARNINGS_UNCHECKED = suppressWarnings("unchecked");
  static final AnnotationSpec SUPPRESS_WARNINGS_RAWTYPES = suppressWarnings("rawtypes");

  private static AnnotationSpec suppressWarnings(String value) {
    return AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", value).build();
  }

  static final AnnotationSpec PROVIDES_SET_VALUES =
      AnnotationSpec.builder(Provides.class)
          .addMember("type", "$T.SET_VALUES", ClassName.get(Provides.Type.class))
          .build();

  private AnnotationSpecs() {}
}
