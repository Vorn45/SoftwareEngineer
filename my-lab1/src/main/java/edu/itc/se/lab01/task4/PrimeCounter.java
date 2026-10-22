package edu.itc.se.lab01.task4;

import java.util.concurrent.Callable;

public record PrimeCounter(long from, long to) implements Callable<Long> {
    @Override
    public Long call() {
        return Main.countPrimes(from, to);
    }
}
