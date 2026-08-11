package jp.yuya.dev.developerdungeon.app.javalearning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

    @Test
    void advancedLibraryKeepsEachReservationBoundToItsAssignedCopy() throws Exception {
        JavaProblem problem = new JavaProblemCatalog(new ObjectMapper())
                .findBySlug("library-advanced").orElseThrow();
        Path classes = compile(problem, output.resolve("library-advanced-runtime"));
        String packageName = "jp.yuya.dev.developerdungeon.javaproblems.library.advanced";
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})) {
            Class<?> serviceType = loader.loadClass(packageName + ".LibraryService");
            Class<?> titleType = loader.loadClass(packageName + ".BookTitle");
            Class<?> copyType = loader.loadClass(packageName + ".BookCopy");
            Class<?> memberType = loader.loadClass(packageName + ".Member");
            Class<?> policyType = loader.loadClass(packageName + ".LoanPolicy");
            Object policy = instantiate(loader.loadClass(packageName + ".StandardPolicy"));
            Object service = instantiate(serviceType, new Class<?>[]{Clock.class}, Clock.systemUTC());

            invoke(serviceType, service, "addTitle", new Class<?>[]{titleType},
                    instantiate(titleType, new Class<?>[]{String.class, String.class}, "ISBN-1", "設計入門"));
            invoke(serviceType, service, "addCopy", new Class<?>[]{copyType},
                    instantiate(copyType, new Class<?>[]{String.class, String.class}, "C1", "ISBN-1"));
            invoke(serviceType, service, "addCopy", new Class<?>[]{copyType},
                    instantiate(copyType, new Class<?>[]{String.class, String.class}, "C2", "ISBN-1"));
            for (String memberId : List.of("BORROWER", "M1", "M2")) {
                invoke(serviceType, service, "register", new Class<?>[]{memberType},
                        instantiate(memberType, new Class<?>[]{String.class, policyType}, memberId, policy));
            }

            invoke(serviceType, service, "lend", new Class<?>[]{String.class, String.class}, "BORROWER", "C1");
            invoke(serviceType, service, "reserve", new Class<?>[]{String.class, String.class}, "M1", "ISBN-1");
            invoke(serviceType, service, "reserve", new Class<?>[]{String.class, String.class}, "M2", "ISBN-1");
            invoke(serviceType, service, "returnCopy", new Class<?>[]{String.class, String.class}, "BORROWER", "C1");

            assertThatThrownBy(() -> invoke(serviceType, service, "lend",
                    new Class<?>[]{String.class, String.class}, "M1", "C2"))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            invoke(serviceType, service, "lend", new Class<?>[]{String.class, String.class}, "M1", "C1");
            invoke(serviceType, service, "lend", new Class<?>[]{String.class, String.class}, "M2", "C2");
        }
    }

    @Test
    void advancedVendingReturnsCashChangeAndRejectsInvalidSlots() throws Exception {
        JavaProblem problem = new JavaProblemCatalog(new ObjectMapper())
                .findBySlug("vending-machine-advanced").orElseThrow();
        Path classes = compile(problem, output.resolve("vending-advanced-runtime"));
        String packageName = "jp.yuya.dev.developerdungeon.javaproblems.vending.machine.advanced";
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})) {
            Class<?> machineType = loader.loadClass(packageName + ".VendingMachine");
            Class<?> productType = loader.loadClass(packageName + ".Product");
            Class<?> paymentType = loader.loadClass(packageName + ".PaymentMethod");
            Object machine = instantiate(machineType, new Class<?>[]{Clock.class}, Clock.systemUTC());
            Object product = instantiate(productType, new Class<?>[]{String.class, String.class}, "P1", "水");
            invoke(machineType, machine, "addSlot",
                    new Class<?>[]{String.class, productType, int.class, int.class}, "A1", product, 300, 1);
            Object cash = instantiate(loader.loadClass(packageName + ".CashPayment"),
                    new Class<?>[]{int.class}, 500);
            Object outcome = invoke(machineType, machine, "sell",
                    new Class<?>[]{String.class, paymentType}, "A1", cash);
            assertThat(invoke(outcome.getClass(), outcome, "returnedYen", new Class<?>[0])).isEqualTo(200);

            assertThatThrownBy(() -> invoke(machineType, machine, "addSlot",
                    new Class<?>[]{String.class, productType, int.class, int.class}, "B1", product, -1, 1))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void advancedCartLeavesInventoryUntouchedWhenDiscountCalculationFails() throws Exception {
        JavaProblem problem = new JavaProblemCatalog(new ObjectMapper())
                .findBySlug("shopping-cart-advanced").orElseThrow();
        Path classes = compile(problem, output.resolve("cart-advanced-policy-runtime"));
        String packageName = "jp.yuya.dev.developerdungeon.javaproblems.shopping.cart.advanced";
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})) {
            Class<?> cartType = loader.loadClass(packageName + ".ShoppingCart");
            Class<?> inventoryType = loader.loadClass(packageName + ".Inventory");
            Class<?> itemType = loader.loadClass(packageName + ".CartItem");
            Class<?> moneyType = loader.loadClass(packageName + ".Money");
            Class<?> policyType = loader.loadClass(packageName + ".DiscountPolicy");
            Object cart = instantiate(cartType);
            Object inventory = instantiate(inventoryType);
            invoke(inventoryType, inventory, "stock", new Class<?>[]{String.class, int.class}, "P1", 1);
            Object money = instantiate(moneyType, new Class<?>[]{int.class}, 100);
            invoke(cartType, cart, "add", new Class<?>[]{itemType},
                    instantiate(itemType, new Class<?>[]{String.class, String.class, moneyType, int.class},
                            "P1", "商品", money, 1));
            Object checkout = instantiate(loader.loadClass(packageName + ".CheckoutService"),
                    new Class<?>[]{inventoryType}, inventory);
            Method checkoutMethod = accessibleMethod(checkout.getClass(), "checkout", cartType, policyType);
            Object failingPolicy = Proxy.newProxyInstance(loader, new Class<?>[]{policyType},
                    (proxy, method, arguments) -> { throw new IllegalStateException("割引計算失敗"); });

            assertThatThrownBy(() -> checkoutMethod.invoke(checkout, cart, failingPolicy))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
            Field inventoryField = inventoryType.getDeclaredField("available");
            inventoryField.setAccessible(true);
            assertThat(((Map<?, ?>) inventoryField.get(inventory)).get("P1")).isEqualTo(1);

            Object noDiscount = instantiate(loader.loadClass(packageName + ".NoDiscount"));
            assertThat(checkoutMethod.invoke(checkout, cart, noDiscount)).isNotNull();
            assertThat(((Map<?, ?>) inventoryField.get(inventory)).get("P1")).isEqualTo(0);
        }
    }

    @Test
    void intermediateVendingChecksSalesOverflowBeforeChangingStockOrBalance() throws Exception {
        JavaProblem problem = new JavaProblemCatalog(new ObjectMapper())
                .findBySlug("vending-machine-intermediate").orElseThrow();
        Path classes = compile(problem, output.resolve("vending-intermediate-runtime"));
        String packageName = "jp.yuya.dev.developerdungeon.javaproblems.vending.machine.intermediate";
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()})) {
            Class<?> machineType = loader.loadClass(packageName + ".VendingMachine");
            Class<?> productType = loader.loadClass(packageName + ".Product");
            Object machine = instantiate(machineType);
            Object product = instantiate(productType, new Class<?>[]{String.class, String.class}, "P1", "水");
            invoke(machineType, machine, "addSlot",
                    new Class<?>[]{String.class, productType, int.class, int.class}, "A1", product, 1, 1);
            invoke(machineType, machine, "insert", new Class<?>[]{int.class}, 1);
            Field sales = machineType.getDeclaredField("salesYen");
            sales.setAccessible(true);
            sales.setInt(machine, Integer.MAX_VALUE);

            assertThatThrownBy(() -> invoke(machineType, machine, "purchase",
                    new Class<?>[]{String.class}, "A1"))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(ArithmeticException.class);
            Field slots = machineType.getDeclaredField("slots");
            slots.setAccessible(true);
            Object slot = ((Map<?, ?>) slots.get(machine)).get("A1");
            assertThat(invoke(slot.getClass(), slot, "stock", new Class<?>[0])).isEqualTo(1);
            Field balance = machineType.getDeclaredField("balanceYen");
            balance.setAccessible(true);
            assertThat(balance.getInt(machine)).isEqualTo(1);
        }
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
            Class<?> discountType = advanced ? loader.loadClass(packageName + ".DiscountPolicy") : null;
            Object noDiscount = advanced ? instantiate(loader.loadClass(packageName + ".NoDiscount")) : null;
            Method checkoutMethod = advanced
                    ? accessibleMethod(checkoutType, "checkout", cartType, discountType)
                    : accessibleMethod(checkoutType, "checkout", cartType);
            assertThatThrownBy(() -> {
                if (advanced) checkoutMethod.invoke(checkout, cart, noDiscount);
                else checkoutMethod.invoke(checkout, cart);
            }).isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(IllegalStateException.class);

            Field inventoryField = inventoryType.getDeclaredField(advanced ? "available" : "quantities");
            inventoryField.setAccessible(true);
            assertThat(((Map<?, ?>) inventoryField.get(inventory)).get("P1")).isEqualTo(5);
            assertThat((List<?>) invoke(cartType, cart, "items", new Class<?>[0])).hasSize(advanced ? 2 : 1);
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
