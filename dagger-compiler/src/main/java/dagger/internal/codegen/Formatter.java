package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import static dagger.internal.codegen.ErrorMessages.INDENT;

abstract class Formatter<T> implements Function<T, String> {

  public abstract String format(T object);

  @SuppressWarnings("javadoc")
  @Deprecated
  @Override final public String apply(T object) {
    return format(object);
  }

  public void formatIndentedList(
      StringBuilder builder, Iterable<? extends T> items, int indentLevel) {
    formatIndentedList(builder, indentLevel, items, ImmutableList.<T>of());
  }

  public void formatIndentedList(
      StringBuilder builder, Iterable<? extends T> items, int indentLevel, int limit) {
    formatIndentedList(
        builder, indentLevel, Iterables.limit(items, limit), Iterables.skip(items, limit));
  }

  private void formatIndentedList(
      StringBuilder builder,
      int indentLevel,
      Iterable<? extends T> firstItems,
      Iterable<? extends T> restOfItems) {
    for (T item : firstItems) {
      builder.append('\n');
      appendIndent(builder, indentLevel);
      builder.append(format(item));
    }
    int numberOfOtherItems = Iterables.size(restOfItems);
    if (numberOfOtherItems > 0) {
      builder.append('\n');
      appendIndent(builder, indentLevel);
      builder.append("and ").append(numberOfOtherItems).append(" other");
    }
    if (numberOfOtherItems > 1) {
      builder.append('s');
    }
  }

  private void appendIndent(StringBuilder builder, int indentLevel) {
    for (int i = 0; i < indentLevel; i++) {
      builder.append(INDENT);
    }
  }
}
