/**
 * A performance tester for priority queues
 * @author Ryan Kelsey and Lee Berman
 *
 */
public class PerformanceTester {
    private static final int NUM_OPERATIONS = 500_000;
    private static final int NUM_TESTS = 1;
    
    /**
     * The entry point of the program. It executes tests with different thread counts and operation mixes
     * @param args The command line arguments
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException {
	double[] opMixes = {0.25, 0.4, 0.5, 0.6, 0.75};
	for(int threads = 1; threads <= 8; threads *= 2)
		for(double opMix : opMixes)
			testSuite(threads, opMix);
    }
    
    /**
     * Runs the tests on ConcurrentPriorityQueue, SequentialPriorityQueue, and PaperPriorityQueue
     * @param numThreads The number of threads to test with
     * @param opMix The operation mix
     * @throws InterruptedException
     */
    public static void testSuite(int numThreads, double opMix) throws InterruptedException {
        System.out.println(numThreads + "," + opMix);
        System.out.println("Sequential: ");
        for(int i = 0; i < NUM_TESTS; i++) {
            System.out.print((i == 0 ? "" : ",") + PriorityQueueTester.runTest(numThreads, opMix, NUM_OPERATIONS, new SequentialPriorityQueue()));
        }
        System.out.println("\nConcurrent: ");
        for(int i = 0; i < NUM_TESTS; i++) {
            System.out.print((i == 0 ? "" : ",") + PriorityQueueTester.runTest(numThreads, opMix, NUM_OPERATIONS, new ConcurrentPriorityQueue()));
        }
        
        System.out.println("\nPaper-23h: ");
        for(int i = 0; i < NUM_TESTS; i++) {
            System.out.print((i == 0 ? "" : ",") + PriorityQueueTester.runTest(numThreads, opMix, NUM_OPERATIONS, new PaperPriorityQueue(23)));
        }
        System.out.println();
    }
}