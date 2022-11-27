package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreElements;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor6;

final class InjectProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
  private final InjectBindingRegistry injectBindingRegistry;

  InjectProcessingStep(InjectBindingRegistry factoryRegistrar) {
    this.injectBindingRegistry = factoryRegistrar;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(Inject.class);
  }

  @Override
  public Set<Element> process(
      SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    ImmutableSet.Builder<Element> rejectedElements = ImmutableSet.builder();

    for (Element injectElement : elementsByAnnotation.get(Inject.class)) {
      try {
        injectElement.accept(
            new ElementKindVisitor6<Void, Void>() {
              @Override
              public Void visitExecutableAsConstructor(
                  ExecutableElement constructorElement, Void v) {
                injectBindingRegistry.tryRegisterConstructor(constructorElement);
                return null;
              }

              @Override
              public Void visitVariableAsField(VariableElement fieldElement, Void p) {
                injectBindingRegistry.tryRegisterMembersInjectedType(
                    MoreElements.asType(fieldElement.getEnclosingElement()));
                return null;
              }

              @Override
              public Void visitExecutableAsMethod(ExecutableElement methodElement, Void p) {
                injectBindingRegistry.tryRegisterMembersInjectedType(
                    MoreElements.asType(methodElement.getEnclosingElement()));
                return null;
              }
            },
            null);
      } catch (TypeNotPresentException e) {
        rejectedElements.add(injectElement);
      }
    }

    return rejectedElements.build();
  }
}
