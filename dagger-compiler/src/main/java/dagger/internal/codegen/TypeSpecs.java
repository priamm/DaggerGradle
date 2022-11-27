package dagger.internal.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.TypeElement;

final class TypeSpecs {

  static void addSupertype(TypeSpec.Builder typeBuilder, TypeElement supertype) {
    switch (supertype.getKind()) {
      case CLASS:
        typeBuilder.superclass(ClassName.get(supertype));
        break;
      case INTERFACE:
        typeBuilder.addSuperinterface(ClassName.get(supertype));
        break;
      default:
        throw new AssertionError(supertype + " is neither a class nor an interface.");
    }
  }

  private TypeSpecs() {}
}
