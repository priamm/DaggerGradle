package dagger.internal.codegen;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementKindVisitor6;

final class InjectProcessingStep implements BasicAnnotationProcessor.ProcessingStep {
  private final Messager messager;
  private final InjectConstructorValidator constructorValidator;
  private final InjectFieldValidator fieldValidator;
  private final InjectMethodValidator methodValidator;
  private final ProvisionBinding.Factory provisionBindingFactory;
  private final MembersInjectionBinding.Factory membersInjectionBindingFactory;
  private final InjectBindingRegistry injectBindingRegistry;

  InjectProcessingStep(Messager messager,
      InjectConstructorValidator constructorValidator,
      InjectFieldValidator fieldValidator,
      InjectMethodValidator methodValidator,
      ProvisionBinding.Factory provisionBindingFactory,
      MembersInjectionBinding.Factory membersInjectionBindingFactory,
      InjectBindingRegistry factoryRegistrar) {
    this.messager = messager;
    this.constructorValidator = constructorValidator;
    this.fieldValidator = fieldValidator;
    this.methodValidator = methodValidator;
    this.provisionBindingFactory = provisionBindingFactory;
    this.membersInjectionBindingFactory = membersInjectionBindingFactory;
    this.injectBindingRegistry = factoryRegistrar;
  }

  @Override
  public Set<Class<? extends Annotation>> annotations() {
    return ImmutableSet.<Class<? extends Annotation>>of(Inject.class);
  }

  @Override
  public Set<? extends Element> process(SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation) {
    final ImmutableSet.Builder<ProvisionBinding> provisions = ImmutableSet.builder();
    final ImmutableSet.Builder<DeclaredType> membersInjectedTypes = ImmutableSet.builder();

    for (Element injectElement : elementsByAnnotation.get(Inject.class)) {
      injectElement.accept(
          new ElementKindVisitor6<Void, Void>() {
            @Override
            public Void visitExecutableAsConstructor(
                ExecutableElement constructorElement, Void v) {
              ValidationReport<ExecutableElement> report =
                  constructorValidator.validate(constructorElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                provisions.add(provisionBindingFactory.forInjectConstructor(constructorElement,
                    Optional.<TypeMirror>absent()));
              }

              return null;
            }

            @Override
            public Void visitVariableAsField(VariableElement fieldElement, Void p) {
              ValidationReport<VariableElement> report = fieldValidator.validate(fieldElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                membersInjectedTypes.add(
                    MoreTypes.asDeclared(fieldElement.getEnclosingElement().asType()));
              }

              return null;
            }

            @Override
            public Void visitExecutableAsMethod(ExecutableElement methodElement, Void p) {
              ValidationReport<ExecutableElement> report =
                  methodValidator.validate(methodElement);

              report.printMessagesTo(messager);

              if (report.isClean()) {
                membersInjectedTypes.add(
                    MoreTypes.asDeclared(methodElement.getEnclosingElement().asType()));
              }

              return null;
            }
          }, null);
    }

    for (DeclaredType injectedType : membersInjectedTypes.build()) {
      injectBindingRegistry.registerBinding(membersInjectionBindingFactory.forInjectedType(
          injectedType, Optional.<TypeMirror>absent()));
    }

    for (ProvisionBinding binding : provisions.build()) {
      injectBindingRegistry.registerBinding(binding);
    }
    return null;
  }
}
