package me.tatarka.valueprocessor

import com.google.common.truth.Expect
import com.google.testing.compile.Compiler.javac
import com.google.testing.compile.JavaFileObjects.forSourceString
import org.junit.Rule
import org.junit.Test
import javax.lang.model.element.ExecutableElement
import javax.lang.model.util.ElementFilter

class ValueTest {
    @get:Rule
    val expect = Expect.create()

    @Test
    fun `creates value from value class`() {
        javac()
            .withProcessors(ElementExtractor { env, element ->
                val value = ValueCreator(env).fromConstructor(ElementFilter.constructorsIn(element.enclosedElements)[0])
                expect.that(value.element.qualifiedName.toString()).isEqualTo("test.Test")
            }).compile(
            forSourceString(
                "test.Test",
                """
                    package test;
                    import me.tatarka.valueprocessor.Target;
                    @Target public class Test {}
                """
            )
        )
    }

    @Test
    fun `creates value from factory`() {
        javac()
            .withProcessors(ElementExtractor { env, element ->
                val value = ValueCreator(env).fromFactory(element as ExecutableElement)
                expect.that(value.element.qualifiedName.toString()).isEqualTo("test.Test")
            }).compile(
            forSourceString(
                "test.Test",
                """
                    package test;
                    import me.tatarka.valueprocessor.Target;
                    public class Test {
                        @Target
                        public static Test create() {
                            return new Test();
                        }
                    }
                """
            )
        )
    }

    @Test
    fun `creates value from builder constructor`() {
        javac()
            .withProcessors(ElementExtractor { env, element ->
                val value =
                    ValueCreator(env).fromBuilderConstructor(ElementFilter.constructorsIn(element.enclosedElements)[0])
                expect.that(value.element.qualifiedName.toString()).isEqualTo("test.Test")
            }).compile(
            forSourceString(
                "test.Test",
                """
                    package test;
                    import me.tatarka.valueprocessor.Target;
                    public class Test {
                        @Target
                        public static class Builder {
                            public Test build() {
                                return new Test();
                            }
                        }
                    }
                """
            )
        )
    }

    @Test
    fun `creates value from builder factory`() {
        javac()
            .withProcessors(ElementExtractor { env, element ->
                val value =
                    ValueCreator(env).fromBuilderFactory(element as ExecutableElement)
                expect.that(value.element.qualifiedName.toString()).isEqualTo("test.Test")
            }).compile(
            forSourceString(
                "test.Test",
                """
                    package test;
                    import me.tatarka.valueprocessor.Target;
                    public class Test {
                        @Target
                        public static Builder builder() {
                            return new Builder();
                        }

                        public static class Builder {
                            public Test build() {
                                return new Test();
                            }
                        }
                    }
                """
            )
        )
    }

    @Test
    fun `creates property from public field`() {
        javac()
            .withProcessors(ElementExtractor { env, element ->
                val value =
                    ValueCreator(env).fromConstructor(ElementFilter.constructorsIn(element.enclosedElements)[0])
                expect.that(value.properties[0].name).isEqualTo("arg")
            }).compile(
            forSourceString(
                "test.Test",
                """
                    package test;
                    import me.tatarka.valueprocessor.Target;
                    @Target public class Test {
                        public final String arg;

                        public Test(String arg) {
                            this.arg = arg;
                        }
                    }
                """
            )
        )
    }

    @Test
    fun `creates property from getter`() {
        javac()
            .withProcessors(ElementExtractor { env, element ->
                val value =
                    ValueCreator(env).fromConstructor(ElementFilter.constructorsIn(element.enclosedElements)[0])
                expect.that(value.properties[0].name).isEqualTo("arg")
            }).compile(
            forSourceString(
                "test.Test",
                """
                    package test;
                    import me.tatarka.valueprocessor.Target;
                    @Target public class Test {
                        private final String arg;

                        public Test(String arg) {
                            this.arg = arg;
                        }

                        public String getArg() {
                            return arg;
                        }
                    }
                """
            )
        )
    }

    @Test
    fun `gets props from parent class`() {
        javac()
            .withProcessors(ElementExtractor { env, element ->
                val value =
                    ValueCreator(env).fromConstructor(ElementFilter.constructorsIn(element.enclosedElements)[0])
                expect.that(value.properties[0].name).isEqualTo("arg1")
                expect.that(value.properties[1].name).isEqualTo("arg2")
                expect.that(value.properties.getters).hasSize(2)
                expect.that(value.properties.params).hasSize(2)
            }).compile(
                forSourceString(
                    "test.Test",
                    """
                    package test;
                    import me.tatarka.valueprocessor.Target;
                    @Target public class Test extends Parent {
                        private final String arg2;

                        public Test(String arg1, String arg2) {
                            super(arg1);
                            this.arg2 = arg2;
                        }

                        public String arg2() {
                            return arg2;
                        }

                        @Override
                        public String getArg1() {
                            super.getArg1();
                        }
                    }

                    class Parent {
                        private final String arg1;

                        public String getArg1() {
                            return arg1;
                        }
                    }
                """
                )
            )
    }
}