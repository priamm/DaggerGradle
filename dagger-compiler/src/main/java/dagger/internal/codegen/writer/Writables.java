package dagger.internal.codegen.writer;

import dagger.internal.codegen.writer.Writable.Context;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

final class Writables {

  static void join(String delimiter, Iterable<? extends Writable> writables,
      String prefix, String suffix,
      Appendable appendable, Context context) throws IOException {
    Iterator<? extends Writable> iter = writables.iterator();
    if (iter.hasNext()) {
      appendable.append(prefix);
      iter.next().write(appendable, context);
      while (iter.hasNext()) {
        appendable.append(delimiter);
        iter.next().write(appendable, context);
      }
      appendable.append(suffix);
    }
  }

  static void join(String delimiter, Iterable<? extends Writable> writables,
      Appendable appendable, Context context) throws IOException {
    join(delimiter, writables, "", "", appendable, context);
  }

  static Writable toStringWritable(final Object object) {
    return new Writable() {
      @Override
      public Appendable write(Appendable appendable, Context context) throws IOException {
        return appendable.append(object.toString());
      }
    };
  }

  private static final Context DEFAULT_CONTEXT = new Context() {
    @Override
    public String sourceReferenceForClassName(ClassName className) {
      return className.canonicalName();
    }

    @Override
    public Context createSubcontext(Set<ClassName> newTypes) {
      throw new UnsupportedOperationException();
    }
  };

  static String writeToString(Writable writable) {
    StringBuilder builder = new StringBuilder();
    try {
      writable.write(builder, DEFAULT_CONTEXT);
    } catch (IOException e) {
      throw new AssertionError("StringBuilder doesn't throw IOException" + e);
    }
    return builder.toString();
  }

  private Writables() {
  }
}
