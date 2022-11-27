package dagger.internal.codegen;

import com.google.common.base.Function;
import java.util.Iterator;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor6;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_CAMEL;

enum KeyVariableNamer implements Function<Key, String> {
  INSTANCE;

  @Override
  public String apply(Key key) {
    StringBuilder builder = new StringBuilder();

    if (key.qualifier().isPresent()) {
      builder.append(key.qualifier().get().getAnnotationType().asElement().getSimpleName());
    }

    key.type().accept(new SimpleTypeVisitor6<Void, StringBuilder>() {
      @Override
      public Void visitDeclared(DeclaredType t, StringBuilder builder) {
        builder.append(t.asElement().getSimpleName());
        Iterator<? extends TypeMirror> argumentIterator = t.getTypeArguments().iterator();
        if (argumentIterator.hasNext()) {
          builder.append("Of");
          TypeMirror first = argumentIterator.next();
          first.accept(this, builder);
          while (argumentIterator.hasNext()) {
            builder.append("And");
            argumentIterator.next().accept(this, builder);
          }
        }
        return null;
      }
    }, builder);

    return UPPER_CAMEL.to(LOWER_CAMEL, builder.toString());
  }
}
