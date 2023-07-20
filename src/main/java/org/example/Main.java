package org.example;
import EventStreamin.EventStreaming;

import java.io.IOException;
import java.nio.file.Path;

public class Main {
    private static final int NUMBER_OF_THREADS = 12;
    public static void main(String[] args) throws IOException {
        ThreadGroup threadGroup = new ThreadGroup("Workers");
        Thread[] threads = new Thread[NUMBER_OF_THREADS];
//        EventStreaming eventStreaming = new EventStreaming();
//        eventStreaming.path = Path.of("/Users/harsh.kumar/Desktop/file123.jfr");
//        Thread EventStreamingThread = new Thread(eventStreaming,"EventStreaming thread");
//        EventStreamingThread.start();
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(threadGroup, new Worker(), "Worker Thread " + (i+1));
            threads[i].setDaemon(true);
            threads[i].setDaemon(true);
            threads[i].start();
        }
        System.out.print("Press <enter> to quit!");
        System.out.flush();
        System.in.read();
    }
}