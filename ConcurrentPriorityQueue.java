import java.util.concurrent.PriorityBlockingQueue;

public class ConcurrentPriorityQueue implements IPriorityQueue {
    PriorityBlockingQueue<Integer> pq;
    
    public ConcurrentPriorityQueue() {
        pq = new PriorityBlockingQueue<Integer>();
    }
    public int removeMin(int threadId) {
        try {
            return pq.take();
        } catch (InterruptedException e) {
            return -Integer.MIN_VALUE;
        }
    }
    public boolean add(int inValue, int threadId) {
        return pq.add(inValue);
    }
}