package dagger.internal.codegen;

import com.google.auto.common.MoreElements;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;

import static javax.lang.model.element.Modifier.PUBLIC;

abstract class Binding {
  static Optional<String> bindingPackageFor(Iterable<? extends Binding> bindings) {
    ImmutableSet.Builder<String> bindingPackagesBuilder = ImmutableSet.builder();
    for (Binding binding : bindings) {
      bindingPackagesBuilder.addAll(binding.bindingPackage().asSet());
    }
    ImmutableSet<String> bindingPackages = bindingPackagesBuilder.build();
    switch (bindingPackages.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.of(bindingPackages.iterator().next());
      default:
        throw new IllegalArgumentException();
    }
  }

  protected abstract Key key();

  abstract Element bindingElement();

  TypeElement bindingTypeElement() {
    return bindingElement().accept(new SimpleElementVisitor6<TypeElement, Void>() {
      @Override
      protected TypeElement defaultAction(Element e, Void p) {
        return MoreElements.asType(bindingElement().getEnclosingElement());
      }

      @Override
      public TypeElement visitType(TypeElement e, Void p) {
        return e;
      }
    }, null);
  }

  abstract ImmutableSet<DependencyRequest> dependencies();

  abstract ImmutableSet<DependencyRequest> implicitDependencies();

  abstract Optional<String> bindingPackage();

  protected static Optional<String> findBindingPackage(Key bindingKey) {
    Set<String> packages = nonPublicPackageUse(bindingKey.type());
    switch (packages.size()) {
      case 0:
        return Optional.absent();
      case 1:
        return Optional.of(packages.iterator().next());
      default:
        throw new IllegalStateException();
    }
  }

  private static Set<String> nonPublicPackageUse(TypeMirror typeMirror) {
    ImmutableSet.Builder<String> packages = ImmutableSet.builder();
    typeMirror.accept(new SimpleTypeVisitor6<Void, ImmutableSet.Builder<String>>() {
      @Override
      public Void visitArray(ArrayType t, ImmutableSet.Builder<String> p) {
        return t.getComponentType().accept(this, p);
      }

      @Override
      public Void visitDeclared(DeclaredType t, ImmutableSet.Builder<String> p) {
        for (TypeMirror typeArgument : t.getTypeArguments()) {
          typeArgument.accept(this, p);
        }
        TypeElement typeElement = MoreElements.asType(t.asElement());
        if (!typeElement.getModifiers().contains(PUBLIC)) {
          PackageElement elementPackage = MoreElements.getPackage(typeElement);
          Name qualifiedName = elementPackage.getQualifiedName();
          p.add(qualifiedName.toString());
        }
        typeElement.getEnclosingElement().asType().accept(this, p);
        return null;
      }

      @Override
      public Void visitWildcard(WildcardType t, ImmutableSet.Builder<String> p) {
        if (t.getExtendsBound() != null) {
          t.getExtendsBound().accept(this, p);
        }
        if (t.getSuperBound() != null) {
          t.getSuperBound().accept(this, p);
        }
        return null;
      }
    }, packages);
    return packages.build();
  }

  abstract boolean hasNonDefaultTypeParameters();

  static boolean hasNonDefaultTypeParameters(TypeElement element, TypeMirror type, Types types) {
    if (element.getTypeParameters().isEmpty()) {
      return false;
    }
    
    List<TypeMirror> defaultTypes = Lists.newArrayList();
    for (TypeParameterElement parameter : element.getTypeParameters()) {
      defaultTypes.add(parameter.asType());
    }
    
    List<TypeMirror> actualTypes =
        type.accept(new SimpleTypeVisitor6<List<TypeMirror>, Void>() {
          @Override
          protected List<TypeMirror> defaultAction(TypeMirror e, Void p) {
            return ImmutableList.of();
          }

          @Override
          public List<TypeMirror> visitDeclared(DeclaredType t, Void p) {
            return ImmutableList.copyOf(t.getTypeArguments());
          }
        }, null);

    if (defaultTypes.size() != actualTypes.size()) {
      return true;
    }

    for (int i = 0; i < defaultTypes.size(); i++) {
      if (!types.isSameType(defaultTypes.get(i), actualTypes.get(i))) {
        return true;
      }
    }
    return false;
  }
}
