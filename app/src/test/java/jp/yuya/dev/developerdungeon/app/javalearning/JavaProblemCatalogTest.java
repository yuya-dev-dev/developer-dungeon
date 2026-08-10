package jp.yuya.dev.developerdungeon.app.javalearning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import jp.yuya.dev.developerdungeon.app.javalearning.content.JavaProblemCatalog;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaDifficulty;
import jp.yuya.dev.developerdungeon.app.javalearning.domain.JavaProblem;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class JavaProblemCatalogTest {
    @TempDir Path output;

    @Test
    void loadsTheFixedThreeByThreeCatalogAndBeginnerScaffolds() {
        JavaProblemCatalog catalog = new JavaProblemCatalog(new ObjectMapper());

        assertThat(catalog.all()).hasSize(9);
        assertThat(catalog.all()).extracting(JavaProblem::order).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(catalog.all()).filteredOn(problem -> problem.difficulty() == JavaDifficulty.BEGINNER)
                .allSatisfy(problem -> assertThat(problem.beginnerScaffold()).isNotNull());
        assertThat(catalog.all()).filteredOn(problem -> problem.difficulty() != JavaDifficulty.BEGINNER)
                .allSatisfy(problem -> assertThat(problem.beginnerScaffold()).isNull());
        assertThat(catalog.findBySlug("library-beginner")).isPresent();
        assertThat(catalog.findBySlug("../../application.properties")).isEmpty();
    }

    @Test
    void everyReferenceSolutionCompilesOnJava25WithoutExternalLibraries() throws IOException {
        JavaProblemCatalog catalog = new JavaProblemCatalog(new ObjectMapper());
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK is required to validate reference code").isNotNull();

        for (JavaProblem problem : catalog.all()) {
            compile(problem, output.resolve(problem.slug()));
        }
    }

    @Test
    void cartReferenceSolutionsRejectDuplicateRowsWithoutChangingInventoryOrCart() throws Exception {
        JavaProblemCatalog catalog = new JavaProblemCatalog(new ObjectMapper());
        assertDuplicateRowsAreAtomic(catalog.findBySlug("shopping-cart-intermediate").orElseThrow(), false);
        assertDuplicateRowsAreAtomic(catalog.findBySlug("shopping-cart-advanced").orElseThrow(), true);
    }

    private void assertDuplicateRowsAreAtomic(JavaProblem problem, boolean advanced) throws Exception {
        Path classes = compile(problem, output.resolve(problem.slug() + "-runtime"));
        String packageName = "jp.yuya.dev.developerdungeon.javaproblems." + problem.slug().replace('-', '.');
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})) {
            Class<?> cartType = loader.loadClass(packageName + ".ShoppingCart");
            Class<?> inventoryType = loader.loadClass(packageName + ".Inventory");
            Object cart = instantiate(cartType);
            Object inventory = instantiate(inventoryType);
            invoke(inventoryType, inventory, "stock", new Class<?>[]{String.class, int.class}, "P1", 5);

            if (advanced) {
                Class<?> moneyType = loader.loadClass(packageName + ".Money");
                Class<?> itemType = loader.loadClass(packageName + ".CartItem");
                Object money = instantiate(moneyType, new Class<?>[]{int.class}, 100);
                Method add = accessibleMethod(cartType, "add", itemType);
                add.invoke(cart, instantiate(itemType,
                        new Class<?>[]{String.class, String.class, moneyType, int.class}, "P1", "商品", money, 3));
                add.invoke(cart, instantiate(itemType,
                        new Class<?>[]{String.class, String.class, moneyType, int.class}, "P1", "商品", money, 3));
            } else {
                Class<?> productType = loader.loadClass(packageName + ".Product");
                Object product = instantiate(productType,
                        new Class<?>[]{String.class, String.class, int.class}, "P1", "商品", 100);
                Method add = accessibleMethod(cartType, "add", productType, int.class);
                add.invoke(cart, product, 3);
                add.invoke(cart, product, 3);
            }

            Class<?> checkoutType = loader.loadClass(packageName + ".CheckoutService");
            Object checkout = advanced
                    ? instantiate(checkoutType, new Class<?>[]{inventoryType}, inventory)
                    : instantiate(checkoutType, new Class<?>[]{inventoryType, Clock.class}, inventory, Clock.systemUTC());
            Method checkoutMethod = advanced
                    ? accessibleMethod(checkoutType, "checkout", cartType, List.class)
                    : accessibleMethod(checkoutType, "checkout", cartType);
            assertThatThrownBy(() -> {
                if (advanced) checkoutMethod.invoke(checkout, cart, List.of());
                else checkoutMethod.invoke(checkout, cart);
            }).isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(IllegalStateException.class);

            Field inventoryField = inventoryType.getDeclaredField(advanced ? "available" : "quantities");
            inventoryField.setAccessible(true);
            assertThat(((Map<?, ?>) inventoryField.get(inventory)).get("P1")).isEqualTo(5);
            assertThat((List<?>) invoke(cartType, cart, "items", new Class<?>[0])).hasSize(2);
        }
    }

    private Path compile(JavaProblem problem, Path destination) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("A JDK is required to validate reference code").isNotNull();
        List<JavaFileObject> sources = problem.referenceSources().stream()
                .map(source -> new SourceFile(problem.slug(), source.fileName(), source.source()))
                .map(JavaFileObject.class::cast)
                .toList();
        Path problemOutput = java.nio.file.Files.createDirectories(destination);
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                null, null, java.nio.charset.StandardCharsets.UTF_8)) {
            manager.setLocationFromPaths(javax.tools.StandardLocation.CLASS_OUTPUT, List.of(problemOutput));
            boolean compiled = compiler.getTask(null, manager, null,
                    List.of("--release", "25", "-proc:none", "-classpath", problemOutput.toString()),
                    null, sources).call();
            assertThat(compiled).as(problem.slug() + " reference code").isTrue();
        }
        return problemOutput;
    }

    private static Object instantiate(Class<?> type, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static Object instantiate(Class<?> type) throws Exception {
        return instantiate(type, new Class<?>[0]);
    }

    private static Object invoke(Class<?> type, Object target, String name,
                                 Class<?>[] parameterTypes, Object... arguments) throws Exception {
        return accessibleMethod(type, name, parameterTypes).invoke(target, arguments);
    }

    private static Method accessibleMethod(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static final class SourceFile extends SimpleJavaFileObject {
        private final String source;
        private SourceFile(String slug, String fileName, String source) {
            super(URI.create("string:///" + slug + "/" + fileName), Kind.SOURCE);
            this.source = source;
        }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
    }
}
