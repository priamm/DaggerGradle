package dagger.internal.codegen;

import javax.annotation.processing.Messager;

interface PrintableErrorMessage {
  void printMessageTo(Messager messager);
}
