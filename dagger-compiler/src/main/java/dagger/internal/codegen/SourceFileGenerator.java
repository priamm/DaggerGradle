package dagger.internal.codegen;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.TypeWriter;
import java.io.IOException;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;

import static com.google.common.base.Preconditions.checkNotNull;

abstract class SourceFileGenerator<T> {
  private final Filer filer;

  SourceFileGenerator(Filer filer) {
    this.filer = checkNotNull(filer);
  }

  final void generate(T input) throws SourceFileGenerationException {
    ClassName generatedTypeName = nameGeneratedType(input);
    ImmutableSet<Element> originatingElements = ImmutableSet.copyOf(getOriginatingElements(input));
    try {
      ImmutableSet<JavaWriter> writers = write(generatedTypeName, input);
      for (JavaWriter javaWriter : writers) {
        try {
          javaWriter.file(filer, originatingElements);
        } catch (IOException e) {
          throw new SourceFileGenerationException(getNamesForWriters(javaWriter.getTypeWriters()),
              e, getElementForErrorReporting(input));
        }
      }
    } catch (Exception e) {
      Throwables.propagateIfPossible(e, SourceFileGenerationException.class);
      throw new SourceFileGenerationException(ImmutableList.<ClassName>of(), e,
          getElementForErrorReporting(input));
    }
  }

  private static Iterable<ClassName> getNamesForWriters(Iterable<TypeWriter> typeWriters) {
    return Iterables.transform(typeWriters, new Function<TypeWriter, ClassName>() {
      @Override public ClassName apply(TypeWriter input) {
        return input.name();
      }
    });
  }

  abstract ClassName nameGeneratedType(T input);

  abstract Iterable<? extends Element> getOriginatingElements(T input);

  abstract Optional<? extends Element> getElementForErrorReporting(T input);

  abstract ImmutableSet<JavaWriter> write(ClassName generatedTypeName, T input);
}
