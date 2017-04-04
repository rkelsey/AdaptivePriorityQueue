/**
 * A generic priority queue interface
 * @author Ryan Kelsey and Lee Berman
 *
 */
public interface IPriorityQueue {
    /**
     * Removes the minimum value from the priority queue
     * @param threadId The ID of the thread
     * @return The minimum value from the priority queue
     */
    public int removeMin(int threadId);
    
    
    /**
     * Adds a value to the priority queue
     * @param inValue The value to be added
     * @param threadId The ID of the thread
     * @return true if successful
     */
    public boolean add(int inValue, int threadId);
}