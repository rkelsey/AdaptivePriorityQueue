import java.util.concurrent.PriorityBlockingQueue;

/**
 * A priority queue that uses Java's PriorityBlockingQueue as the backing data structure.
 * @author Ryan Kelsey and Lee Berman
 *
 */
public class ConcurrentPriorityQueue implements IPriorityQueue {
    PriorityBlockingQueue<Integer> pq;
    
    public ConcurrentPriorityQueue() {
        pq = new PriorityBlockingQueue<Integer>();
    }
    /* (non-Javadoc)
     * @see IPriorityQueue#removeMin(int)
     */
    public int removeMin(int threadId) {
        try {
            return pq.take();
        } catch (InterruptedException e) {
            return -Integer.MIN_VALUE;
        }
    }
    /* (non-Javadoc)
     * @see IPriorityQueue#add(int, int)
     */
    public boolean add(int inValue, int threadId) {
        return pq.add(inValue);
    }
}