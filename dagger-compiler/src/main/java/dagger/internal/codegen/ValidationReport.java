package dagger.internal.codegen;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import static javax.tools.Diagnostic.Kind.ERROR;

@AutoValue
abstract class ValidationReport<T> {
  abstract T subject();
  abstract ImmutableSet<Item> items();

  boolean isClean() {
    for (Item item : items()) {
      switch (item.kind()) {
        case ERROR:
          return false;
        default:
          break;
      }
    }
    return true;
  }

  void printMessagesTo(Messager messager) {
    for (Item item : items()) {
      item.printMessageTo(messager);
    }
  }

  @AutoValue
  static abstract class Item implements PrintableErrorMessage {
    abstract String message();
    abstract Kind kind();
    abstract Element element();
    abstract Optional<AnnotationMirror> annotation();

    @Override
    public void printMessageTo(Messager messager) {
      if (annotation().isPresent()) {
        messager.printMessage(kind(), message(), element(), annotation().get());
      } else {
        messager.printMessage(kind(), message(), element());
      }
    }
  }

  static final class Builder<T> {
    static <T> Builder<T> about(T subject) {
      return new Builder<T>(subject);
    }

    private final T subject;
    private final ImmutableSet.Builder<Item> items = ImmutableSet.builder();

    private Builder(T subject) {
      this.subject = subject;
    }

    T getSubject() {
      return subject;
    }

    Builder<T> addItem(String message, Element element) {
      addItem(message, ERROR, element, Optional.<AnnotationMirror>absent());
      return this;
    }

    Builder<T> addItem(String message, Kind kind, Element element) {
      addItem(message, kind, element, Optional.<AnnotationMirror>absent());
      return this;
    }

    Builder<T> addItem(String message, Element element, AnnotationMirror annotation) {
      addItem(message, ERROR, element, Optional.of(annotation));
      return this;
    }

    Builder<T> addItem(String message, Kind kind, Element element, AnnotationMirror annotation) {
      addItem(message, kind, element, Optional.of(annotation));
      return this;
    }

    private Builder<T> addItem(String message, Kind kind, Element element,
        Optional<AnnotationMirror> annotation) {
      items.add(new AutoValue_ValidationReport_Item(message, kind, element, annotation));
      return this;
    }

    ValidationReport<T> build() {
      return new AutoValue_ValidationReport<T>(subject, items.build());
    }
  }
}
