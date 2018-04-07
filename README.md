# value-processor
Helper for creating annotation processors that create/read value objects.

## Download

### Gradle

```groovy
dependencies {
  compile 'me.tatarka.value:value-processor:0.2'
}
```

### Maven

```xml
<dependency>
  <groupId>me.tatarka.value</groupId>
  <artifactId>value-processor</artifactId>
  <version>0.2</version>
</dependency>
```

## Usage

First you create a `ValueCreator` with the processing environment.

```java
public class MyProcessor extends AbstractProcessor {

    private ValueCreator valueCreator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        valueCreator = new ValueCreator(processingEnv);
    }
```

Then create a `Value` from an `Element`. There are various methods based on what you have annotated. For example, the below creates it from the target class.

```java
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
               try {
                   Value value = valueCreator.fromClass((TypeElement) element);
               } catch (ElementException e) {
                   e.printMessage(messenger);
               }
            }
        }
        return false;
    }
```

You can then iterate through the properties to generate your code.

```java
// all properties
for (Property<?> property : value.getProperties()) {
    ...
}
// of a specific kind
for (Property.Getter getter : value.getProperties().getGetters()) {
    ...
}
```

## Supported Classes
Note: Classes are expected to be treated as immutable. This meas you can only create instances with properties or read properties on and instance.

### Fields
```java
public class Foo {
    public String bar;
}
```
### Getters
```java
public class Foo {
    public String getBar() { ... }
}
```
### Constructor Params
```java
public class Foo {
    public Foo(String bar) { ... }
}
```
### Factory Method Params
```java
public static Foo createFoo(String bar) { ... }
```
### Builder
```java
public class FooBuilder {
    public FooBuilder setBar(String bar) { ... }
    public Foo build() { ... }
}
```
### Builder Constructor Params
```java
public class FooBuilder {
    public FooBuilder(String bar) { ... }
    public Foo build() { ... }
}
```
### Builder Factory Params
```java
public static FooBuilder builder(String bar) { ... }
```
### Kotlin Data Classes
```kotlin
data class Foo(val bar: String)
```
