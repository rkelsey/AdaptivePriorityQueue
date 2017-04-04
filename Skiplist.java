import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A Skiplist that allows for sequential adds and removes on the first end and parallel adds on the second end
 * @author Ryan Kelsey and Lee Berman
 *
 */
public class Skiplist {
	private AtomicInteger lockTimestamp, minValue, seqElementsToAdd, seqInsertions;
	private BucketNode headSeq, currSeq, headPar, tail;
	private AtomicReference<BucketNode> lastSeq;
	private ReadWriteLock lock;
	private final int MAX_HEIGHT;
	
	private static final int SEQUENTIAL_INSERTIONS_OVERLOAD = 1000;
	private static final int SEQUENTIAL_INSERTIONS_UNDERLOAD = 100;
	private static final int MIN_SEQUENTIAL_ELEMENTS_TO_ADD = 8;
	private static final int MAX_SEQUENTIAL_ELEMENTS_TO_ADD = 65536;
	
	/**
	 * Constructs the Skiplist
	 * @param h The height of the skiplist
	 */
	public Skiplist(int h) {
		MAX_HEIGHT = h;
		lock = new ReentrantReadWriteLock();
		minValue = new AtomicInteger(Integer.MAX_VALUE);
		lockTimestamp = new AtomicInteger(0);
		seqElementsToAdd = new AtomicInteger(MIN_SEQUENTIAL_ELEMENTS_TO_ADD);
		seqInsertions = new AtomicInteger(0);
		headSeq = new BucketNode(Integer.MIN_VALUE, h);
		headPar = new BucketNode(Integer.MAX_VALUE, h);
		tail = new BucketNode(Integer.MAX_VALUE, h);
		for(int i = 0; i < h; i++) {
			headSeq.next[i] = new AtomicReference<BucketNode>(tail);
			headPar.next[i] = new AtomicReference<BucketNode>(tail);
		}
		lastSeq = new AtomicReference<BucketNode>(headPar);
	}
	
	/**
	 * Removes the smallest value from the sequential part of the skiplist
	 * @return The removed value
	 */
	public int removeSeq() {
		//The skiplist is empty
		if(minValue.get() == Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		
		//The sequential part is empty; add elements from the parallel part
		if(currSeq == null)
			moveHead();
		
		//The parallel part was empty as well
		if(currSeq == null)
			return Integer.MAX_VALUE;
		
		int key = currSeq.key;
		currSeq.counter.decrementAndGet();
		
		//This was the last instance of the removed value. Physically remove the node.
		if(currSeq.counter.get() == 0) {
			if(currSeq != lastSeq.get()) {
				for(int i = 0; i < currSeq.topLevel; i++) {
					headSeq.next[i] = new AtomicReference<BucketNode>(currSeq.next[i].get());
				}
				currSeq = currSeq.next[0].get();
				minValue.set(currSeq.key);
				return key;
			}
			moveHead(); //If we just removed the last sequential element, add elements from the parallel part
		}
		return key;
	}
	
	/**
	 * Adds an element to the sequential part of the skiplist
	 * @param v The value to be added
	 */
	public void addSeq(int v) {
		seqInsertions.incrementAndGet();
		
		BucketNode[] preds = new BucketNode[MAX_HEIGHT], succs = new BucketNode[MAX_HEIGHT];
		BucketNode node = find(headSeq, v, preds, succs); //Fetches the point where the node should be inserted
		
		//The node already exists
		if(node != null) {
			node.counter.incrementAndGet();
			return;
		}
		
		node = new BucketNode(v, generateHeight());
		
		//Physically inserts the node
		for(int i = 0; i < node.topLevel; i++) {
			preds[i].next[i] = new AtomicReference<BucketNode>(node);
			node.next[i] = new AtomicReference<BucketNode>(succs[i]);
		}
		
		//Updates the minimum value
		if(v < minValue.get()) {
			minValue.set(v);
			currSeq = node;
		}
		
		//Updates the physical end of the list, if the current node is larger than the current last node
		if(lastSeq.get() == headPar || lastSeq.get().key < v) {
			lastSeq.set(node);
		}
	}
	
	/**
	 * Performs a clean find as described in the paper. If a node can be found without being
	 * interrupted by moveHead() or chopHead(), the node is returned.
	 * @param v The value to be inserted
	 * @param preds An array to store the values before the node to be inserted
	 * @param succs An array to store the values after the node to be inserted
	 * @return (null, false) if the clean find fails or (node, true) if the clean find succeeds.
	 */
	private Tuple<BucketNode, Boolean> cleanFind(int v, BucketNode[] preds, BucketNode[] succs) {
		int t = lockTimestamp.get();
		BucketNode b = find(headPar, v, preds, succs);
		lock.readLock().lock();
		if(t < lockTimestamp.get()) {
			lock.readLock().unlock();
			return new Tuple<BucketNode, Boolean>(null, false);
		}
		return new Tuple<BucketNode, Boolean>(b, true);
	}
	
	/**
	 * Adds a node to the parallel part of the skiplist
	 * @param v The value to be inserted
	 * @return false if unsuccessful, true if successful
	 */
	public boolean addPar(int v) {
		if(v <= lastSeq.get().key)
			return false;
		
		boolean x, r;
		BucketNode b;
		BucketNode[] preds = new BucketNode[MAX_HEIGHT], succs = new BucketNode[MAX_HEIGHT];
		do {
			x = false;
			//Continuously clean-finds until it succeeds
			do {
				Tuple<BucketNode, Boolean> tuple = cleanFind(v, preds, succs);
				b = tuple.a;
				r = tuple.b;
			} while(!r);
			
			//A node is found
			if(b != null) {
				b.counter.incrementAndGet();
				lock.readLock().unlock();
				return true;
			}
			
			b = new BucketNode(v, generateHeight());
			
			//Updates the node's links
			for(int i = 0; i < b.topLevel; i++)
				b.next[i] = new AtomicReference<BucketNode>(succs[i]);
			
			//Linearization point 1. Attempts to update the previous link to point to the new node.
			if(!preds[0].next[0].compareAndSet(succs[0], b)) {
				lock.readLock().unlock();
				x = true;
			}
		} while(x);
		
		int m = minValue.get();
		
		//Linearization point 2. Attempts to update the minimum value.
		while(m > v && !minValue.compareAndSet(m, v))
			m = minValue.get();
		
		//Updates the links that the new node points to and updates the previous links to point to
		//the new node.
		for(int i = 0; i < b.topLevel; i++) {
			if(preds[i].next[i].compareAndSet(succs[i], b)) {
				b.next[i].set(succs[i]);
				continue;
			}
			
			lock.readLock().unlock();
			
			//A new node has gotten in the way. Do a new clean find and start again.
			do {
				Tuple<BucketNode, Boolean> tuple = cleanFind(v, preds, succs);
				b = tuple.a;
				r = tuple.b;
			} while(!r);
			
			if(b == null) {
				lock.readLock().unlock();
				return true;
			}
		}
		lock.readLock().unlock();
		return true;
	}
	
	/**
	 * Moves elements from the parallel part to the sequential part
	 * @return true if successful
	 */
	public boolean moveHead() {
		int n = determineDynamically();
		lock.writeLock().lock();
		currSeq = null;
		
		//Finds the first n nodes in the parallel part
		BucketNode pred = headPar;
		BucketNode curr = headPar.next[0].get();
		int i = 0;
		while(i < n && curr != tail) {
			i += curr.counter.get();
			if(currSeq == null) {
				currSeq = curr;
				minValue.set(curr.key);
			}
			pred = curr;
			curr = curr.next[0].get();
		}
		
		//The parallel part is empty
		if(i == 0) {
			for(i = MAX_HEIGHT - 1; i >= 0; i--) {
				headPar.next[i].set(tail);
				headSeq.next[i].set(tail);
			}
			lastSeq.set(headPar);
			minValue.set(Integer.MAX_VALUE);
			lockTimestamp.incrementAndGet();
			lock.writeLock().unlock();
			return false;
		}
		
		//Update the sequential staring point to the parallel starting point
		lastSeq.set(pred);
		for(i = MAX_HEIGHT - 1; i >= 0; i--)
			headSeq.next[i] = new AtomicReference<BucketNode>(headPar.next[i].get());
		
		//Update the new sequential ending nodes to point to the dummy tail and update
		//the parallel head to point to point to where the sequential ending ends.
		BucketNode[] preds = new BucketNode[MAX_HEIGHT], succs = new BucketNode[MAX_HEIGHT];
		find(headSeq, lastSeq.get().key + 1, preds, succs);
		find(headSeq, lastSeq.get().key, new BucketNode[MAX_HEIGHT], succs);
		for(i = MAX_HEIGHT - 1; i >= 0; i--) {
			preds[i].next[i] = new AtomicReference<BucketNode>(tail);
			headPar.next[i] = new AtomicReference<BucketNode>(succs[i]);
		}
		lockTimestamp.incrementAndGet();
		lock.writeLock().unlock();
		return true;
	}
	
	/**
	 * Finds how many elements should be added to the sequential part of the list
	 * @return The number of elements to be added to the sequential part of the list
	 */
	private int determineDynamically() {
		int tmp = seqElementsToAdd.get();
		int tmp2 = seqInsertions.get();
		
		//If there were a lot of sequential insertions, halve the number of elements added.
		//If there were few sequential insertions, double the number of elements added.
		if(tmp2 > SEQUENTIAL_INSERTIONS_OVERLOAD)
			seqElementsToAdd.compareAndSet(tmp, Math.max(tmp >> 1, MIN_SEQUENTIAL_ELEMENTS_TO_ADD));
		else if(tmp2 < SEQUENTIAL_INSERTIONS_UNDERLOAD)
			seqElementsToAdd.compareAndSet(tmp, Math.min(tmp << 1, MAX_SEQUENTIAL_ELEMENTS_TO_ADD));
		
		seqInsertions.set(0);
		return seqElementsToAdd.get();
	}
	
	/**
	 * Moves the sequential part to the parallel part
	 * @return true if successful
	 */
	public boolean chopHead() {
		//The sequential part is empty
		if(currSeq == null)
			return false;
		
		//Gets the predecessors of the end of the sequential list and the successors of the beginning of the sequential list
		BucketNode[] preds = new BucketNode[MAX_HEIGHT], succs = new BucketNode[MAX_HEIGHT];
		find(headSeq, lastSeq.get().key + 1, preds, succs);
		find(headSeq, currSeq.key, new BucketNode[MAX_HEIGHT], succs);
		
		lock.writeLock().lock();
		
		//Update the end of the sequential list to point to the beginning of the parallel list
		for(int i = MAX_HEIGHT - 1; i >= 0; i--)
			preds[i].next[i] = new AtomicReference<BucketNode>(headPar.next[i].get());
		
		lastSeq.set(headPar);
		currSeq = null;
		
		//Update the beginning of the parallel list to point to the beginning of the sequential list
		for(int i = MAX_HEIGHT - 1; i >= 0; i--)
			if(succs[i] != tail)
				headPar.next[i] = new AtomicReference<BucketNode>(succs[i]);
		
		lockTimestamp.incrementAndGet();
		lock.writeLock().unlock();
		return true;
	}
	
	/**
	 * Finds where the specified value resides or would reside in the skiplist
	 * @param head The head of the skiplist
	 * @param v The value to be inserted
	 * @param preds An array to store the values before the node to be inserted
	 * @param succs An array to store the values after the node to be inserted
	 * @return The node if it exists, otherwise null
	 */
	private BucketNode find(BucketNode head, int v, BucketNode[] preds, BucketNode[] succs) {
		int h = MAX_HEIGHT - 1;
		while(h >= 0) {
			BucketNode next = head.next[h].get(); //The link to the next node
			
			//If the node to be inserted is larger, move right. If not, move down
			if(v > next.key) {
				head = next;
			} else {
				//Update the predecessors and successors to the current node and its successor
				preds[h] = head;
				succs[h] = next;
				if(v == next.key)
					succs[h] = next.next[h].get();
				h--;
			}
		}
		
		if(v == head.next[0].get().key)
			return head.next[0].get();
		return null;
	}
	
	/**
	 * Generates a random height between 1 and the maximum height specified
	 * @return The generated height
	 */
	private int generateHeight() {
		int h = 1;
		while(h < MAX_HEIGHT && ThreadLocalRandom.current().nextBoolean())
			h++;
		return h;
	}
	
	/**
	 * Fetches the minimum skiplist value
	 * @return The minimum skiplist value
	 */
	public int getMinValue() {
		return minValue.get();
	}
	
	/**
	 * A node to store skiplist buckets
	 * @author Ryan Kelsey and Lee Berman
	 *
	 */
	private class BucketNode {
		public int key, topLevel;
		public AtomicInteger counter;
		public AtomicReference<BucketNode>[] next;
		
		@SuppressWarnings("unchecked")
		public BucketNode(int key, int topLevel) {
			this.key = key;
			this.topLevel = topLevel;
			this.counter = new AtomicInteger(1);
			this.next = new AtomicReference[MAX_HEIGHT];
		}
		
		@Override
		public String toString() {
			return String.format("BucketNode %d %d %d", key, topLevel, counter.get());
		}
	}
}