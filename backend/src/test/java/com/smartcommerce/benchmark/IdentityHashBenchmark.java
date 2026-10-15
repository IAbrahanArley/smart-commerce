package com.smartcommerce.benchmark;

import com.smartcommerce.identity.IdentityPasswordEncoder;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import tools.jackson.databind.json.JsonMapper;

/** Opt-in local experiment; never a timing assertion or part of the automated test suite. */
public final class IdentityHashBenchmark {
  private static final int WARMUP = 20;
  private static final int SAMPLES = 200;
  private static final AtomicLong BLACKHOLE = new AtomicLong();

  private IdentityHashBenchmark() {}

  public static void main(String[] arguments) throws Exception {
    if (arguments.length != 1) {
      throw new IllegalArgumentException("Provide a local report output path");
    }
    var encoder = new IdentityPasswordEncoder();
    var startedAt = Instant.now();
    var results = new ArrayList<Map<String, Object>>();
    var cases =
        List.of(
            new InputCase("ascii_15", "SC-Bench-2026!x"),
            new InputCase("unicode_nfc", "Cafe\u0301 e come\u0301rcio - benchmark 2026!"),
            new InputCase("supplementary_128", "\uD83D\uDD12".repeat(128)));
    for (var input : cases) {
      // Synthetic inputs/hashes stay inside the process and are never serialized or printed.
      String reference = encoder.encode(input.password());
      for (String operation : List.of("encode", "matches", "matches_negative")) {
        Runnable work =
            switch (operation) {
              case "encode" -> () -> BLACKHOLE.addAndGet(encoder.encode(input.password()).length());
              case "matches" ->
                  () -> {
                    if (!encoder.matches(input.password(), reference)) {
                      throw new IllegalStateException("Positive benchmark verification failed");
                    }
                    BLACKHOLE.incrementAndGet();
                  };
              default ->
                  () -> {
                    if (encoder.matches(input.password() + "x", reference)) {
                      throw new IllegalStateException("Negative benchmark verification failed");
                    }
                    BLACKHOLE.incrementAndGet();
                  };
            };
        // The negative input must stay within the current validation limits to exercise the KDF.
        if (operation.equals("matches_negative") && input.normalizedCodePoints() == 128) {
          String negative = "\uD83D\uDD10" + input.password().substring(2);
          work =
              () -> {
                if (encoder.matches(negative, reference)) {
                  throw new IllegalStateException("Negative benchmark verification failed");
                }
                BLACKHOLE.incrementAndGet();
              };
        }
        for (int concurrency : new int[] {1, 2, 4, 8}) {
          for (int count = 0; count < WARMUP; count++) {
            work.run();
          }
          var result = measure(input, operation, concurrency, work);
          results.add(result);
          System.out.printf(
              "%s %s concurrency=%d p50=%.2fms p95=%.2fms throughput=%.2f/s%n",
              input.name(),
              operation,
              concurrency,
              result.get("p50Millis"),
              result.get("p95Millis"),
              result.get("operationsPerSecond"));
        }
      }
    }
    var runtime = ManagementFactory.getRuntimeMXBean();
    var os = operatingSystem();
    var report = new LinkedHashMap<String, Object>();
    report.put("startedAtUtc", startedAt.toString());
    report.put("finishedAtUtc", Instant.now().toString());
    report.put("javaVersion", System.getProperty("java.version"));
    report.put("javaVendor", System.getProperty("java.vendor"));
    report.put("vmName", System.getProperty("java.vm.name"));
    report.put("vmVersion", runtime.getVmVersion());
    report.put("os", System.getProperty("os.name"));
    report.put("architecture", System.getProperty("os.arch"));
    report.put("availableProcessors", Runtime.getRuntime().availableProcessors());
    report.put("physicalMemoryBytes", os.getTotalMemorySize());
    report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
    report.put(
        "cryptoProvider",
        javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").getProvider().getName());
    report.put("encoderId", IdentityPasswordEncoder.CURRENT_ID);
    report.put("iterations", IdentityPasswordEncoder.MIN_ITERATIONS);
    report.put("saltBytes", 16);
    report.put("derivedKeyBits", 256);
    report.put("warmupPerSeries", WARMUP);
    report.put("samplesPerSeries", SAMPLES);
    report.put("heapSamplingMillis", 50);
    report.put(
        "latencyDefinition",
        "Service latency excludes executor queue; throughput includes scheduling. No endpoint or network is measured.");
    report.put(
        "scope",
        "Local candidate experiment, not a production capacity or SLA guarantee; no security parameters were reduced.");
    report.put("measurements", results);
    Path destination = Path.of(arguments[0]);
    if (destination.toAbsolutePath().getParent() != null) {
      Files.createDirectories(destination.toAbsolutePath().getParent());
    }
    Files.writeString(
        destination,
        JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
    System.out.println("Benchmark complete; local report contains metadata and timings only.");
  }

  private static Map<String, Object> measure(
      InputCase input, String operation, int concurrency, Runnable work) throws Exception {
    long[] durations = new long[SAMPLES];
    var os = operatingSystem();
    var memory = ManagementFactory.getMemoryMXBean();
    var peakHeap = new AtomicLong(memory.getHeapMemoryUsage().getUsed());
    var sampling = new AtomicBoolean(true);
    Thread sampler =
        Thread.ofPlatform()
            .daemon()
            .start(
                () -> {
                  while (sampling.get()) {
                    peakHeap.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
                    try {
                      Thread.sleep(50);
                    } catch (InterruptedException exception) {
                      Thread.currentThread().interrupt();
                      return;
                    }
                  }
                });
    var start = new CountDownLatch(1);
    long cpuBefore = os.getProcessCpuTime();
    long gcCountBefore = gcCount();
    long gcMillisBefore = gcMillis();
    long begin;
    long elapsed;
    try (var executor = Executors.newFixedThreadPool(concurrency)) {
      var futures = new ArrayList<java.util.concurrent.Future<?>>();
      for (int index = 0; index < SAMPLES; index++) {
        final int slot = index;
        futures.add(
            executor.submit(
                () -> {
                  try {
                    start.await();
                  } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Benchmark interrupted");
                  }
                  long operationBegin = System.nanoTime();
                  work.run();
                  durations[slot] = System.nanoTime() - operationBegin;
                }));
      }
      begin = System.nanoTime();
      start.countDown();
      for (var future : futures) {
        future.get();
      }
      elapsed = System.nanoTime() - begin;
    } finally {
      sampling.set(false);
      sampler.interrupt();
      sampler.join();
    }
    long processCpu = os.getProcessCpuTime() - cpuBefore;
    Arrays.sort(durations);
    var result = new LinkedHashMap<String, Object>();
    result.put("case", input.name());
    result.put("rawUtf16Units", input.password().length());
    result.put("normalizedCodePoints", input.normalizedCodePoints());
    result.put("operation", operation);
    result.put("concurrency", concurrency);
    result.put("samples", SAMPLES);
    result.put("p50Millis", percentile(durations, 50));
    result.put("p95Millis", percentile(durations, 95));
    result.put("p99Millis", percentile(durations, 99));
    result.put("maxMillis", durations[durations.length - 1] / 1_000_000.0);
    result.put("wallMillis", elapsed / 1_000_000.0);
    result.put("operationsPerSecond", SAMPLES * 1_000_000_000.0 / elapsed);
    result.put("processCpuMillis", processCpu / 1_000_000.0);
    result.put("processCpuPercentOneCore", processCpu * 100.0 / elapsed);
    result.put(
        "processCpuPercentAllAvailable",
        processCpu * 100.0 / elapsed / Runtime.getRuntime().availableProcessors());
    result.put("sampledPeakHeapBytes", peakHeap.get());
    result.put("committedVirtualMemoryBytesAtEnd", os.getCommittedVirtualMemorySize());
    result.put("gcCollections", gcCount() - gcCountBefore);
    result.put("gcMillis", gcMillis() - gcMillisBefore);
    return result;
  }

  private static double percentile(long[] ordered, int percentile) {
    int index = Math.max(0, (int) Math.ceil(percentile * ordered.length / 100.0) - 1);
    return ordered[index] / 1_000_000.0;
  }

  private static OperatingSystemMXBean operatingSystem() {
    return (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
  }

  private static long gcCount() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0, bean.getCollectionCount()))
        .sum();
  }

  private static long gcMillis() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0, bean.getCollectionTime()))
        .sum();
  }

  private record InputCase(String name, String password) {
    int normalizedCodePoints() {
      String normalized = Normalizer.normalize(password, Normalizer.Form.NFC);
      return normalized.codePointCount(0, normalized.length());
    }
  }
}
