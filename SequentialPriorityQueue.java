import java.util.PriorityQueue;

/**
 * A priority queue that uses Java's PriorityQueue as the backing data structure inside synchronized blocks.
 * @author Ryan Kelsey and Lee Berman
 *
 */
public class SequentialPriorityQueue implements IPriorityQueue {
    private PriorityQueue<Integer> pq;
    
    public SequentialPriorityQueue() {
        pq = new PriorityQueue<Integer>();
    }
    
    /* (non-Javadoc)
     * @see IPriorityQueue#removeMin(int)
     */
    public int removeMin(int threadId) {
        while(true) {
            synchronized(pq) {
                Integer res = pq.poll();
                if(res != null) {
                    return res;
                }
            }
        }
    }
    /* (non-Javadoc)
     * @see IPriorityQueue#add(int, int)
     */
    public boolean add(int inValue, int threadId) {
        synchronized(pq) {
            return pq.add(inValue);
        }
    }
}