import java.util.concurrent.ThreadLocalRandom;

/**
 * A tester for a priority queue given some test parameters
 * @author Ryan Kelsey and Lee Berman
 *
 */
public class PriorityQueueTester implements Runnable {
    private static final int MIN_GENERATE = 0;
    private static final int MAX_GENERATE = 10000 + 1;
    private static final boolean DEBUG = false;
    
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

    /**
     * Runs a test on the given priority queue
     * @param numThreads The number of threads to use
     * @param operationMix The operation mix to use
     * @param numOperations The number of operations
     * @param queueObject The queue to test with
     * @return The time taken for the test, in milliseconds
     */
    public static long runTest(int numThreads, double operationMix, int numOperations, IPriorityQueue queueObject) {
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

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
        long addSum = 0;
        long remSum = 0;
        int numAdded = 0;
        for(int i = 0; i < numOperationsPerThread; i++) {
            if(numAdded <= 0 || ThreadLocalRandom.current().nextDouble(1) < operationMix) {
                int v = ThreadLocalRandom.current().nextInt(MIN_GENERATE, MAX_GENERATE);
                queueObject.add(v, threadId);
                addSum += v;
                numAdded++;
            } else {
                int v = queueObject.removeMin(threadId);
                remSum += v == Integer.MAX_VALUE ? 0 : v;
                numAdded--;
            }
        }
        
        if(DEBUG)
        	System.out.println("Thread " + threadId + " ended with addSum = " + addSum + ", remSum = " + remSum + ", diff = " + (addSum - remSum));
    }
}