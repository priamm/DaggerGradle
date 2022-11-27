package dagger.internal.codegen;

interface Validator<T> {
  ValidationReport<T> validate(T subject);
}
