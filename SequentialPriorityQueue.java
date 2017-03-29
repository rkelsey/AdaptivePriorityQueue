import java.util.PriorityQueue;

public class SequentialPriorityQueue implements IPriorityQueue {
    private PriorityQueue<Integer> pq;
    
    public SequentialPriorityQueue() {
        pq = new PriorityQueue<Integer>();
    }
    
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
    public boolean add(int inValue, int threadId) {
        synchronized(pq) {
            return pq.add(inValue);
        }
    }
}