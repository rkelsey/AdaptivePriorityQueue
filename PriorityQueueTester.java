import java.util.concurrent.ThreadLocalRandom;

public class PriorityQueueTester implements Runnable {
    private static final int minGenerate = 0;
    private static final int maxGenerate = 10000 + 1;
    
    private int threadId;
    private double operationMix; //0-1, higher = more additions
    private int numOperationsPerThread;
    private IPriorityQueue queueObject;

    private PriorityQueueTester(int threadId, double operationMix, int numOperationsPerThread, IPriorityQueue queueObject) {
        this.threadId = threadId;
        this.operationMix = operationMix;
        this.numOperationsPerThread = numOperationsPerThread;
        this.queueObject = queueObject;
    }

    public static long RunTest(int numThreads, double operationMix, int numOperations, IPriorityQueue queueObject) {
        int numOperationsPerThread = numOperations / numThreads;
        Thread[] threads = new Thread[numThreads];

        for(int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(new PriorityQueueTester(i, operationMix, numOperationsPerThread, queueObject));
        }

        long start = System.currentTimeMillis();

        for(int i = 0; i < numThreads; i++) {
            threads[i].start();
        }
        
        for(int i = 0; i < numThreads; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {}
        }

        long end = System.currentTimeMillis();

        if(queueObject instanceof PaperPriorityQueue)
        	((PaperPriorityQueue)queueObject).stop();
        return end - start;
    }

    public void run() {
        long addSum = 0;
        long remSum = 0;
        int numAdded = 0;
        for(int i = 0; i < numOperationsPerThread; i++) {
            if(numAdded <= 0 || ThreadLocalRandom.current().nextDouble(1) < operationMix) {
                int v = ThreadLocalRandom.current().nextInt(minGenerate, maxGenerate);
                queueObject.add(v, threadId);
                addSum += v;
                numAdded++;
            } else {
                int v = queueObject.removeMin(threadId);
                remSum += v == Integer.MAX_VALUE ? 0 : v;
                numAdded--;
            }
        }
        //System.out.println("Thread " + threadId + " ended with addSum = " + addSum + ", remSum = " + remSum + ", diff = " + (addSum - remSum));
    }
}