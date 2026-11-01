package com.bankops.seed;

import java.util.List;
import java.util.Random;

/** Sabit tohumlu rastgelelik: aynı tohum her çalıştırmada aynı demo verisini üretir. */
final class Dice {

    record Weighted<T>(T value, double weight) {
    }

    static <T> Weighted<T> w(T value, double weight) {
        return new Weighted<>(value, weight);
    }

    private final Random random;

    Dice(long seed) {
        this.random = new Random(seed);
    }

    Random random() {
        return random;
    }

    double uniform(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    /** Her iki uç dahil tamsayı. */
    int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    double gaussian() {
        return random.nextGaussian();
    }

    /** Alt ve üst sınır arasında logaritmik dağılım: küçük tutarlar büyüklerden daha sık çıkar. */
    double logUniform(double min, double max) {
        return Math.exp(uniform(Math.log(min), Math.log(max)));
    }

    <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }

    <T> T weighted(List<Weighted<T>> options) {
        double total = options.stream().mapToDouble(Weighted::weight).sum();
        double roll = random.nextDouble() * total;
        for (Weighted<T> option : options) {
            roll -= option.weight();
            if (roll < 0) {
                return option.value();
            }
        }
        return options.getLast().value();
    }
}
