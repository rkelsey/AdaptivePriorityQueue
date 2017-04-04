public class Main {
    private static final int numOperations = 500_000;
    private static final int numTests = 1;
    public static void main(String[] args) throws InterruptedException {
	double[] opMixes = {0.25, 0.4, 0.5, 0.6, 0.75};
	for(int threads = 1; threads <= 8; threads *= 2)
		for(double opMix : opMixes)
			TestSuite(threads, opMix);
    }
    public static void TestSuite(int numThreads, double opMix) throws InterruptedException{
        System.out.print(numThreads + "," + opMix);
        System.out.println("\nSequential: ");
        for(int i = 0; i < numTests; i++ ) {
            System.out.print((i == 0 ? "" : ",") + PriorityQueueTester.RunTest(numThreads, opMix, numOperations, new SequentialPriorityQueue()));
        }
        System.out.println("\nConcurrent: ");
        for(int i = 0; i < numTests; i++ ) {
            System.out.print((i == 0 ? "" : ",") + PriorityQueueTester.RunTest(numThreads, opMix, numOperations, new ConcurrentPriorityQueue()));
        }
        
        System.out.println("\nPaper-23h: ");
        for(int i = 0; i < numTests; i++ ) {
            System.out.print((i == 0 ? "" : ",") + PriorityQueueTester.RunTest(numThreads, opMix, numOperations, new PaperPriorityQueue(23)));
        }
	System.out.println();
    }
}