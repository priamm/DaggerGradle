package dagger.internal.codegen.writer;

import com.google.auto.common.MoreTypes;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public final class TypeVariableName implements TypeName {
  private final CharSequence name;
  private final Iterable<? extends TypeName> extendsBounds;

  TypeVariableName(CharSequence name, Iterable<? extends TypeName> extendsBounds) {
    this.name = name;
    this.extendsBounds = extendsBounds;
  }

  public CharSequence name() {
    return name;
  }

  @Override
  public Set<ClassName> referencedClasses() {
    ImmutableSet.Builder<ClassName> builder = new ImmutableSet.Builder<ClassName>();
    for (TypeName bound : extendsBounds) {
      builder.addAll(bound.referencedClasses());
    }
    return builder.build();
  }

  @Override
  public Appendable write(Appendable appendable, Context context) throws IOException {
    appendable.append(name);
    if (!Iterables.isEmpty(extendsBounds)) {
      appendable.append(" extends ");
      Iterator<? extends TypeName> iter = extendsBounds.iterator();
      iter.next().write(appendable, context);
      while (iter.hasNext()) {
        appendable.append(" & ");
        iter.next().write(appendable, context);  
      }
    }
    return appendable;
  }

  @Override
  public String toString() {
    return Writables.writeToString(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof TypeVariableName) {
      TypeVariableName that = (TypeVariableName) obj;
      return this.name.toString().equals(that.name.toString())
          && this.extendsBounds.equals(that.extendsBounds);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, extendsBounds);
  }

  static TypeVariableName named(CharSequence name) {
    return new TypeVariableName(name, ImmutableList.<TypeName>of());
  }
  
  public static TypeVariableName fromTypeVariable(TypeVariable variable) {
    return named(variable.asElement().getSimpleName());
  }

  public static TypeVariableName fromTypeParameterElement(TypeParameterElement element) {
    Iterable<? extends TypeName> bounds =
        FluentIterable.from(element.getBounds())
            .filter(new Predicate<TypeMirror>() {
              @Override public boolean apply(TypeMirror input) {
                return !MoreTypes.isType(input) || !MoreTypes.isTypeOf(Object.class, input);
              }
            })
            .transform(TypeNames.FOR_TYPE_MIRROR);
    return new TypeVariableName(element.getSimpleName(), bounds);
  }
}
