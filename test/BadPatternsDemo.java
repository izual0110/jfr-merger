import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

public class BadPatternsDemo {

    // Volatile "black hole" so that results are not eliminated by JIT/GC
    public static volatile Object SINK;

    enum Color {
        RED, GREEN, BLUE, YELLOW, BLACK
    }

    public static void main(String[] args) throws Exception {
        int iters = args.length > 0 ? Integer.parseInt(args[0]) : 10_000;
        System.out.println("warmup...");
        exec(500_000); // warmup
        Thread.sleep(10_000);
        for (int i = 0; i < 1; i++) {
            System.out.println("Running bad patterns with iterations[" + i + "] = " + iters);
            exec(iters);
            Thread.sleep(5_000);
        }
    }

    private static void exec(int iters) throws Exception{
        badEnumValues(iters);
        Thread.sleep(1_000);
        badStringSplit(iters);
        Thread.sleep(1_000);
        badStringFormat(iters);
        Thread.sleep(1_000);
        badSimpleDateFormat(iters);
        Thread.sleep(1_000);
        badAutoboxing(iters);
        Thread.sleep(1_000);
        badSingletonList(iters);
        Thread.sleep(1_000);
        badOptional(iters);
        Thread.sleep(1_000);
        badLambdaAllocations(iters);
        Thread.sleep(1_000);
        badRandomCtor(iters);
        Thread.sleep(1_000);
        badToArrayCtor(iters);
        Thread.sleep(1_000);
        badKeysetContains(iters);
        Thread.sleep(1_000);
        badStreamsInLoop(iters);
        Thread.sleep(1_000);
        badCurrentTimeMillis(iters);
        Thread.sleep(1_000);
        badBigDecimalCtor(iters);
        Thread.sleep(1_000);
        badToCharArray(iters);

        System.out.println("Done. SINK=" + (SINK == null ? "null" : SINK.hashCode()));
    }

    // 1) Enum.values() → each call clones the array (Enum.clone visible in stack)
    static void badEnumValues(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            // BAD: MyEnum.values() in a hot loop → new array allocation every time
            for (Color c : Color.values()) {
                sum += c.ordinal();
            }
        }
        SINK = sum;
    }

    // 2) String.split(regex) → compiles Pattern + allocates array
    static void badStringSplit(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            // BAD: regex split inside a loop
            String[] parts = "a,b,c,d".split(",");
            total += parts.length;
        }
        SINK = total;
    }

    // 3) String.format → heavy Formatter and lots of temporary objects
    static void badStringFormat(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            // BAD: formatting in a tight loop
            String s = String.format("num=%d time=%s", i, Instant.now().toEpochMilli());
            total += s.length();
        }
        SINK = total;
    }

    // 4) new SimpleDateFormat(...) often and/or formatting in loop
    static void badSimpleDateFormat(int n) {
        long total = 0;
        for (int i = 0; i < n; i++) {
            // BAD: creating SDF in loop; also not thread-safe
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            String s = sdf.format(new Date(i * 1000L));
            total += s.length();
        }
        SINK = total;
    }

    // 5) Autoboxing: millions of Integer.valueOf/Long.valueOf
    static void badAutoboxing(int n) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // BAD: autoboxing int -> Integer in a hot loop
            list.add(i); // Integer.valueOf(i)
            if (list.size() > 32) list.clear();
        }
        SINK = list.size();
    }

    // 6) Collections.singletonList in loop → many small objects
    static void badSingletonList(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            // BAD: each iteration creates a new wrapper
            List<String> one = Collections.singletonList("x");
            total += one.size();
        }
        SINK = total;
    }

    // 7) Optional.of(...) in tight loop → unnecessary allocations
    static void badOptional(int n) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            // BAD: frequent creation of Optional
            Optional<String> opt = Optional.of("x" + i);
            if (opt.isPresent()) total++;
        }
        SINK = total;
    }

    // 8) Lambdas with captured variables in loop: new objects created each time
    static void badLambdaAllocations(int n) {
        long total = 0;
        for (int i = 0; i < n; i++) {
            var fi = i;
            // BAD: capturing i ⇒ lambda not static, creates new object
            Function<Integer, Integer> f = x -> x + fi;
            total += f.apply(1);
        }
        SINK = total;
    }

    // 9) new Random() on each call → new object + poor parallel scalability
    static void badRandomCtor(int n) {
        long total = 0;
        for (int i = 0; i < n; i++) {
            // BAD: creating Random on each iteration
            Random r = new Random();
            total += r.nextInt(10);
        }
        SINK = total;
    }

    // 10) toArray(new T[0]) in loop → allocations; (just repeated here for test)
    static void badToArrayCtor(int n) {
        List<String> list = Arrays.asList("a", "b", "c");
        int total = 0;
        for (int i = 0; i < n; i++) {
            // BAD: constructor array, especially new T[0] old style
            String[] arr = list.toArray(new String[0]);
            total += arr.length;
        }
        SINK = total;
    }

    // 11) map.keySet().contains(x) instead of map.containsKey(x)
    static void badKeysetContains(int n) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < 128; i++) map.put("k" + i, i);

        int hit = 0;
        for (int i = 0; i < n; i++) {
            String key = "k" + (i & 127);
            // BAD: via keySet().contains
            if (map.keySet().contains(key)) hit++;
        }
        SINK = hit;
    }

    // 12) Stream API on short collections in hot loop
    static void badStreamsInLoop(int n) {
        int[] tiny = {1, 2, 3, 4};
        long sum = 0;
        for (int i = 0; i < n; i++) {
            // BAD: unnecessary stream pipeline objects
            sum += IntStream.of(tiny).map(x -> x + 1).sum();
        }
        SINK = sum;
    }

    // 13) System.currentTimeMillis() in tight loop
    static void badCurrentTimeMillis(int n) {
        long acc = 0;
        for (int i = 0; i < n; i++) {
            // BAD: native call in loop
            acc += System.currentTimeMillis() & 1;
        }
        SINK = acc;
    }

    // 14) new BigDecimal(double) → costly and imprecise compared to valueOf
    static void badBigDecimalCtor(int n) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < n; i++) {
            // BAD: constructor from double
            BigDecimal x = new BigDecimal(0.1d);
            sum = sum.add(x);
        }
        SINK = sum;
    }

    // 15) String.toCharArray() → allocates a new array each call
    static void badToCharArray(int n) {
        int acc = 0;
        for (int i = 0; i < n; i++) {
            // BAD: unnecessary char[] allocation
            char[] cs = "abcdef".toCharArray();
            acc += cs[(i & 7) % cs.length];
        }
        SINK = acc;
    }
}
