package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import dagger.Module;
import dagger.Multibindings;
import dagger.internal.codegen.BindingType.HasBindingType;
import dagger.internal.codegen.ContributionType.HasContributionType;
import dagger.internal.codegen.Key.HasKey;
import dagger.internal.codegen.SourceElement.HasSourceElement;
import dagger.producers.Producer;
import dagger.producers.ProducerModule;
import java.util.Map;
import java.util.Set;
import javax.inject.Provider;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.common.base.Preconditions.checkArgument;
import static javax.lang.model.element.ElementKind.INTERFACE;

@AutoValue
abstract class MultibindingDeclaration
    implements HasBindingType, HasKey, HasSourceElement, HasContributionType {

  @Override
  public abstract SourceElement sourceElement();

  @Override
  public abstract Key key();

  @Override
  public abstract ContributionType contributionType();

  @Override
  public abstract BindingType bindingType();

  static final class Factory {
    private final Elements elements;
    private final Types types;
    private final Key.Factory keyFactory;
    private final TypeElement objectElement;

    Factory(Elements elements, Types types, Key.Factory keyFactory) {
      this.elements = elements;
      this.types = types;
      this.keyFactory = keyFactory;
      this.objectElement = elements.getTypeElement(Object.class.getCanonicalName());
    }

    ImmutableSet<MultibindingDeclaration> forDeclaredInterface(TypeElement interfaceElement) {
      checkArgument(interfaceElement.getKind().equals(INTERFACE));
      checkArgument(isAnnotationPresent(interfaceElement, Multibindings.class));
      BindingType bindingType = bindingType(interfaceElement);
      DeclaredType interfaceType = MoreTypes.asDeclared(interfaceElement.asType());

      ImmutableSet.Builder<MultibindingDeclaration> declarations = ImmutableSet.builder();
      for (ExecutableElement method : getLocalAndInheritedMethods(interfaceElement, elements)) {
        if (!method.getEnclosingElement().equals(objectElement)) {
          ExecutableType methodType =
              MoreTypes.asExecutable(types.asMemberOf(interfaceType, method));
          declarations.add(forDeclaredMethod(bindingType, method, methodType, interfaceElement));
        }
      }
      return declarations.build();
    }

    private BindingType bindingType(TypeElement interfaceElement) {
      if (isAnnotationPresent(interfaceElement.getEnclosingElement(), Module.class)) {
        return BindingType.PROVISION;
      } else if (isAnnotationPresent(
          interfaceElement.getEnclosingElement(), ProducerModule.class)) {
        return BindingType.PRODUCTION;
      } else {
        throw new IllegalArgumentException(
            "Expected " + interfaceElement + " to be nested in a @Module or @ProducerModule");
      }
    }

    private MultibindingDeclaration forDeclaredMethod(
        BindingType bindingType,
        ExecutableElement method,
        ExecutableType methodType,
        TypeElement interfaceElement) {
      TypeMirror returnType = methodType.getReturnType();
      checkArgument(
          SetType.isSet(returnType) || MapType.isMap(returnType),
          "%s must return a set or map",
          method);
      return new AutoValue_MultibindingDeclaration(
          SourceElement.forElement(method, interfaceElement),
          keyFactory.forMultibindingsMethod(bindingType, methodType, method),
          contributionType(returnType),
          bindingType);
    }

    private ContributionType contributionType(TypeMirror returnType) {
      if (MapType.isMap(returnType)) {
        return ContributionType.MAP;
      } else if (SetType.isSet(returnType)) {
        return ContributionType.SET;
      } else {
        throw new IllegalArgumentException("Must be Map or Set: " + returnType);
      }
    }
  }
}
