package dagger.internal.codegen;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import dagger.MembersInjector;
import dagger.internal.MapProviderFactory;
import dagger.producers.internal.MapOfProducerProducer;
import java.util.Set;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static dagger.internal.codegen.Accessibility.isTypeAccessibleFrom;
import static dagger.internal.codegen.CodeBlocks.makeParametersCodeBlock;
import static dagger.internal.codegen.CodeBlocks.toCodeBlocks;
import static dagger.internal.codegen.TypeNames.MAP_OF_PRODUCER_PRODUCER;
import static dagger.internal.codegen.TypeNames.MAP_PROVIDER_FACTORY;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTOR;
import static dagger.internal.codegen.TypeNames.MEMBERS_INJECTORS;
import static dagger.internal.codegen.TypeNames.SET;

abstract class MemberSelect {

  static MemberSelect localField(ClassName owningClass, String fieldName) {
    return new LocalField(owningClass, fieldName);
  }

  private static final class LocalField extends MemberSelect {
    final String fieldName;

    LocalField(ClassName owningClass, String fieldName) {
      super(owningClass, false);
      this.fieldName = checkNotNull(fieldName);
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? CodeBlocks.format("$L", fieldName)
          : CodeBlocks.format("$T.this.$L", owningClass(), fieldName);
    }
  }

  static MemberSelect staticMethod(ClassName owningClass, CodeBlock methodInvocationCodeBlock) {
    return new StaticMethod(owningClass, methodInvocationCodeBlock);
  }

  private static final class StaticMethod extends MemberSelect {
    final CodeBlock methodCodeBlock;

    StaticMethod(ClassName owningClass, CodeBlock methodCodeBlock) {
      super(owningClass, true);
      this.methodCodeBlock = checkNotNull(methodCodeBlock);
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      return owningClass().equals(usingClass)
          ? methodCodeBlock
          : CodeBlocks.format("$T.$L", owningClass(), methodCodeBlock);
    }
  }

  static MemberSelect noOpMembersInjector(TypeMirror type) {
    return new ParameterizedStaticMethod(
        MEMBERS_INJECTORS,
        ImmutableList.of(type),
        CodeBlocks.format("noOp()"),
        MEMBERS_INJECTOR);
  }

  static MemberSelect emptyFrameworkMapFactory(
      ClassName frameworkMapFactoryClass, TypeMirror keyType, TypeMirror unwrappedValueType) {
    checkArgument(
        frameworkMapFactoryClass.equals(MAP_PROVIDER_FACTORY)
            || frameworkMapFactoryClass.equals(MAP_OF_PRODUCER_PRODUCER),
        "frameworkMapFactoryClass must be MapProviderFactory or MapOfProducerProducer: %s",
        frameworkMapFactoryClass);
    return new ParameterizedStaticMethod(
        frameworkMapFactoryClass,
        ImmutableList.of(keyType, unwrappedValueType),
        CodeBlocks.format("empty()"),
        frameworkMapFactoryClass);
  }

  static MemberSelect emptySetProvider(ClassName setFactoryType, SetType setType) {
    return new ParameterizedStaticMethod(
        setFactoryType,
        ImmutableList.of(setType.elementType()),
        CodeBlocks.format("create()"),
        SET);
  }

  private static final class ParameterizedStaticMethod extends MemberSelect {
    final ImmutableList<TypeMirror> typeParameters;
    final CodeBlock methodCodeBlock;
    final ClassName rawReturnType;

    ParameterizedStaticMethod(
        ClassName owningClass,
        ImmutableList<TypeMirror> typeParameters,
        CodeBlock methodCodeBlock,
        ClassName rawReturnType) {
      super(owningClass, true);
      this.typeParameters = typeParameters;
      this.methodCodeBlock = methodCodeBlock;
      this.rawReturnType = rawReturnType;
    }

    @Override
    CodeBlock getExpressionFor(ClassName usingClass) {
      boolean accessible = true;
      for (TypeMirror typeParameter : typeParameters) {
        accessible &= isTypeAccessibleFrom(typeParameter, usingClass.packageName());
      }

      if (accessible) {
        return CodeBlocks.format(
            "$T.<$L>$L",
            owningClass(),
            makeParametersCodeBlock(toCodeBlocks(typeParameters)),
            methodCodeBlock);
      } else {
        return CodeBlocks.format("(($T) $T.$L)", rawReturnType, owningClass(), methodCodeBlock);
      }
    }
  }

  private final ClassName owningClass;
  private final boolean staticMember;

  MemberSelect(ClassName owningClass, boolean staticMemeber) {
    this.owningClass = owningClass;
    this.staticMember = staticMemeber;
  }

  ClassName owningClass() {
    return owningClass;
  }

  boolean staticMember() {
    return staticMember;
  }

  abstract CodeBlock getExpressionFor(ClassName usingClass);
}
