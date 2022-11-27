package dagger.internal.codegen;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.Diagnostic.Kind.WARNING;

@AutoValue
abstract class ValidationReport<T extends Element> {
  abstract T subject();
  abstract ImmutableSet<Item> items();
  abstract ImmutableSet<ValidationReport<?>> subreports();

  boolean isClean() {
    for (Item item : items()) {
      switch (item.kind()) {
        case ERROR:
          return false;
        default:
          break;
      }
    }
    for (ValidationReport<?> subreport : subreports()) {
      if (!subreport.isClean()) {
        return false;
      }
    }
    return true;
  }

  void printMessagesTo(Messager messager) {
    for (Item item : items()) {
      if (isEnclosedIn(subject(), item.element())) {
        if (item.annotation().isPresent()) {
          messager.printMessage(
              item.kind(), item.message(), item.element(), item.annotation().get());
        } else {
          messager.printMessage(item.kind(), item.message(), item.element());
        }
      } else {
        String message = String.format("[%s] %s", elementString(item.element()), item.message());
        if (item.annotation().isPresent()) {
          messager.printMessage(item.kind(), message, subject(), item.annotation().get());
        } else {
          messager.printMessage(item.kind(), message, subject());
        }
      }
    }
    for (ValidationReport<?> subreport : subreports()) {
      subreport.printMessagesTo(messager);
    }
  }

  private static String elementString(Element element) {
    return element.accept(
        new SimpleElementVisitor6<String, Void>() {
          @Override
          protected String defaultAction(Element e, Void p) {
            return e.toString();
          }

          @Override
          public String visitExecutable(ExecutableElement e, Void p) {
            return e.getEnclosingElement().accept(this, null) + '.' + e.toString();
          }
        },
        null);
  }

  private static boolean isEnclosedIn(Element parent, Element child) {
    Element current = child;
    while (current != null) {
      if (current.equals(parent)) {
        return true;
      }
      current = current.getEnclosingElement();
    }
    return false;
  }

  @AutoValue
  static abstract class Item {
    abstract String message();
    abstract Kind kind();
    abstract Element element();
    abstract Optional<AnnotationMirror> annotation();
  }

  static <T extends Element> Builder<T> about(T subject) {
    return new Builder<T>(subject);
  }

  static final class Builder<T extends Element> {
    private final T subject;
    private final ImmutableSet.Builder<Item> items = ImmutableSet.builder();
    private final ImmutableSet.Builder<ValidationReport<?>> subreports = ImmutableSet.builder();

    private Builder(T subject) {
      this.subject = subject;
    }

    T getSubject() {
      return subject;
    }

    Builder<T> addItems(Iterable<Item> newItems) {
      items.addAll(newItems);
      return this;
    }

    Builder<T> addError(String message) {
      addItem(message, ERROR, subject, Optional.<AnnotationMirror>absent());
      return this;
    }

    Builder<T> addError(String message, Element element) {
      addItem(message, ERROR, element, Optional.<AnnotationMirror>absent());
      return this;
    }

    Builder<T> addError(String message, Element element, AnnotationMirror annotation) {
      addItem(message, ERROR, element, Optional.of(annotation));
      return this;
    }

    Builder<T> addWarning(String message) {
      addItem(message, WARNING, subject, Optional.<AnnotationMirror>absent());
      return this;
    }

    Builder<T> addWarning(String message, Element element) {
      addItem(message, WARNING, element, Optional.<AnnotationMirror>absent());
      return this;
    }

    Builder<T> addWarning(String message, Element element, AnnotationMirror annotation) {
      addItem(message, WARNING, element, Optional.of(annotation));
      return this;
    }

    Builder<T> addNote(String message) {
      addItem(message, NOTE, subject, Optional.<AnnotationMirror>absent());
      return this;
    }

    Builder<T> addNote(String message, Element element) {
      addItem(message, NOTE, element, Optional.<AnnotationMirror>absent());
      return this;
    }

    Builder<T> addNote(String message, Element element, AnnotationMirror annotation) {
      addItem(message, NOTE, element, Optional.of(annotation));
      return this;
    }

    Builder<T> addItem(String message, Kind kind, Element element) {
      addItem(message, kind, element, Optional.<AnnotationMirror>absent());
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

    Builder<T> addSubreport(ValidationReport<?> subreport) {
      subreports.add(subreport);
      return this;
    }

    ValidationReport<T> build() {
      return new AutoValue_ValidationReport<T>(subject, items.build(), subreports.build());
    }
  }
}
