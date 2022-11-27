package dagger.internal.codegen;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import dagger.internal.codegen.writer.ClassName;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.tools.Diagnostic.Kind.ERROR;

final class SourceFileGenerationException extends Exception implements PrintableErrorMessage {
  private final ImmutableSet<ClassName> generatedClassNames;
  private final Optional<? extends Element> associatedElement;

  SourceFileGenerationException(Iterable<ClassName> generatedClassNames, Throwable cause,
      Optional<? extends Element> associatedElement) {
    super(createMessage(generatedClassNames, cause.getMessage()), cause);
    this.generatedClassNames = ImmutableSet.copyOf(generatedClassNames);
    this.associatedElement = checkNotNull(associatedElement);
  }

  SourceFileGenerationException(Iterable<ClassName> generatedClassNames, Throwable cause) {
    this(generatedClassNames, cause, Optional.<Element>absent());
  }

  SourceFileGenerationException(Iterable<ClassName> generatedClassNames, Throwable cause,
      Element associatedElement) {
    this(generatedClassNames, cause, Optional.of(associatedElement));
  }

  public ImmutableSet<ClassName> generatedClassNames() {
    return generatedClassNames;
  }

  public Optional<? extends Element> associatedElement() {
    return associatedElement;
  }

  private static String createMessage(Iterable<ClassName> generatedClassNames, String message) {
    return String.format("Could not generate %s: %s.",
        Iterables.isEmpty(generatedClassNames)
            ? "unknown files"
            : Iterables.toString(generatedClassNames),
        message);
  }

  @Override
  public void printMessageTo(Messager messager) {
    if (associatedElement.isPresent()) {
      messager.printMessage(ERROR, getMessage(), associatedElement.get());
    } else {
      messager.printMessage(ERROR, getMessage());
    }
  }
}
