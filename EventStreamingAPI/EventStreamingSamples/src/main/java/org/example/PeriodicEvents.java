package org.example;

import jdk.jfr.*;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

// Calculates number of threads created and started every second.
// Use it by running this file with Flight Recording on As:
// java PeriodicEvents.java -XX:StartFlightRecording

// Analyse the peridic.jfr file in this directory using JDK mission control.
public class PeriodicEvents {
    private static final ThreadMXBean tBean =
            ManagementFactory.getThreadMXBean();

    @Name("com.spr.StartedThreadCount")
    @Label("Total number of started threads")
    @Period("1 s") // How often the event should be emitted (Like the CPU load event in other example)
    static class StartedThreadCount extends Event {
        long totalStartedThreadCount;
    }

    public static void main(String[] args) throws InterruptedException {

        Runnable hook = () -> { // Lambda expression (call back method) that creates and commits events.
            StartedThreadCount event = new StartedThreadCount();
            event.totalStartedThreadCount =
                    tBean.getTotalStartedThreadCount();
            event.commit();
        };

        FlightRecorder.addPeriodicEvent(StartedThreadCount.class, hook);// To add the event to the Recording
        // The second argument is a callback method that's represented by a lambda expression
        // that creates and commits the event: This type similar to events such as jdk.CPULoad event

        for (int i = 0; i < 4; i++) {
            Thread.sleep(1500);
            Thread t = new Thread();
            t.start();
        }
        FlightRecorder.removePeriodicEvent(hook);
        // In this we can do Recording.disable(Class name) of the event as well but this is better to avoid Memory leaks
    }
}