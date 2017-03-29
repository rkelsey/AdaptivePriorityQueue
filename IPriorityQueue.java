public interface IPriorityQueue {
    public int removeMin(int threadId);
    public boolean add(int inValue, int threadId);
}