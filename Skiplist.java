import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Skiplist {
	private AtomicInteger lockTimestamp, minValue, seqElementsToAdd, seqInsertions;
	private BucketNode headSeq, currSeq, headPar, tail;
	private AtomicReference<BucketNode> lastSeq;
	private ReadWriteLock lock;
	private final int MAX_HEIGHT;
	
	boolean bad = false;
	
	private static final int SEQUENTIAL_INSERTIONS_OVERLOAD = 1000;
	private static final int SEQUENTIAL_INSERTIONS_UNDERLOAD = 100;
	private static final int MIN_SEQUENTIAL_ELEMENTS_TO_ADD = 8;
	private static final int MAX_SEQUENTIAL_ELEMENTS_TO_ADD = 65536;
	
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
		//headSeq.counter.decrementAndGet();
		//headPar.counter.decrementAndGet();
		//tail.counter.decrementAndGet();
		for(int i = 0; i < h; i++) {
			headSeq.next[i] = new AtomicReference<BucketNode>(tail);
			headPar.next[i] = new AtomicReference<BucketNode>(tail);
		}
		lastSeq = new AtomicReference<BucketNode>(headPar);
	}
	
	public int removeSeq() {
		//System.out.println("removeSeq");
		if(minValue.get() == Integer.MAX_VALUE)
			return Integer.MAX_VALUE;
		
		if(currSeq == null)
			moveHead();
		
		if(currSeq == null)
			return Integer.MAX_VALUE;
		
		int key = currSeq.key;
		currSeq.counter.decrementAndGet();
		
		if(currSeq.counter.get() == 0) {
			if(currSeq != lastSeq.get()) {
				for(int i = 0; i < currSeq.topLevel; i++) {
					headSeq.next[i] = new AtomicReference<BucketNode>(currSeq.next[i].get());
				}
				currSeq = currSeq.next[0].get();
				minValue.set(currSeq.key);
				return key;
			}
			moveHead();
		}
		return key;
	}
	
	public void addSeq(int v) {
		seqInsertions.incrementAndGet();
		if(currSeq == null)
			currSeq = headSeq;
		
		BucketNode[] preds = new BucketNode[MAX_HEIGHT], succs = new BucketNode[MAX_HEIGHT];
		BucketNode node = find(headSeq, v, preds, succs, false);
		if(node != null) {
			node.counter.incrementAndGet();
			return;
		}
		
		node = new BucketNode(v, generateHeight());
		for(int i = 0; i < node.topLevel; i++) {
			preds[i].next[i] = new AtomicReference<BucketNode>(node);
			node.next[i] = new AtomicReference<BucketNode>(succs[i]);
		}
		
		if(v < minValue.get()) {
			minValue.set(v);
			currSeq = node;
		}
		
		if(lastSeq.get() == headPar || lastSeq.get().key < v) {
			lastSeq.set(node);
		}
	}
	
	private Tuple<BucketNode, Boolean> cleanFind(int v, BucketNode[] preds, BucketNode[] succs) {
		int t = lockTimestamp.get();
		BucketNode b = find(headPar, v, preds, succs, true);
		lock.readLock().lock();
		if(t < lockTimestamp.get()) {
			lock.readLock().unlock();
			return new Tuple<BucketNode, Boolean>(null, false);
		}
		return new Tuple<BucketNode, Boolean>(b, true);
	}
	
	public boolean addPar(int v) {
		if(v <= lastSeq.get().key)
			return false;
		
		boolean x, r;
		BucketNode b;
		BucketNode[] preds = new BucketNode[MAX_HEIGHT], succs = new BucketNode[MAX_HEIGHT];
		do {
			x = false;
			do {
				Tuple<BucketNode, Boolean> tuple = cleanFind(v, preds, succs);
				b = tuple.a;
				r = tuple.b;
			} while(!r);
			
			if(b != null) {
				b.counter.incrementAndGet();
				lock.readLock().unlock();
				return true;
			}
			
			b = new BucketNode(v, generateHeight());
			for(int i = 0; i < b.topLevel; i++)
				b.next[i] = new AtomicReference<BucketNode>(succs[i]);
			
			if(!preds[0].next[0].compareAndSet(succs[0], b)) {
				lock.readLock().unlock();
				x = true;
			}
		} while(x);
		
		int m = minValue.get();
		while(m > v && !minValue.compareAndSet(m, v))
			m = minValue.get();
		
		for(int i = 0; i < b.topLevel; i++) {
			if(preds[i].next[i].compareAndSet(succs[i], b)) {
				b.next[i].set(succs[i]);
				continue;
			}
			
			lock.readLock().unlock();
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
	
	public boolean moveHead() {
		int n = determineDynamically();
		lock.writeLock().lock();
		currSeq = null;
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
			if(pred.key > curr.key)
				bad = true;
		}
		
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
		
		lastSeq.set(pred);
		for(i = MAX_HEIGHT - 1; i >= 0; i--)
			headSeq.next[i] = new AtomicReference<BucketNode>(headPar.next[i].get());
		
		BucketNode[] preds = new BucketNode[MAX_HEIGHT], succs = new BucketNode[MAX_HEIGHT];
		find(headSeq, lastSeq.get().key + 1, preds, succs, false);
		for(i = MAX_HEIGHT - 1; i >= 0; i--) {
			preds[i].next[i] = new AtomicReference<BucketNode>(tail);
			headPar.next[i] = new AtomicReference<BucketNode>(succs[i]);
		}
		lockTimestamp.incrementAndGet();
		lock.writeLock().unlock();
		return true;
	}
	
	private int determineDynamically() {
		int tmp = seqElementsToAdd.get();
		int tmp2 = seqInsertions.get();
		
		if(tmp2 > SEQUENTIAL_INSERTIONS_OVERLOAD)
			seqElementsToAdd.compareAndSet(tmp, Math.max(tmp >> 1, MIN_SEQUENTIAL_ELEMENTS_TO_ADD));
		else if(tmp2 < SEQUENTIAL_INSERTIONS_UNDERLOAD)
			seqElementsToAdd.compareAndSet(tmp, Math.min(tmp << 1, MAX_SEQUENTIAL_ELEMENTS_TO_ADD));
		
		seqInsertions.set(0);
		return seqElementsToAdd.get();
	}
	
	public boolean chopHead() {
		System.out.println("CHOPPED");
		if(currSeq == null)
			return false;
		
		BucketNode[] preds = new BucketNode[MAX_HEIGHT], succs = new BucketNode[MAX_HEIGHT];
		find(headSeq, lastSeq.get().key + 1, preds, succs, false);
		find(headSeq, currSeq.key, new BucketNode[MAX_HEIGHT], succs, false);
		
		lock.writeLock().lock();
		
		for(int i = MAX_HEIGHT - 1; i >= 0; i--)
			preds[i].next[i] = new AtomicReference<BucketNode>(headPar.next[i].get());
		
		lastSeq.set(headPar);
		currSeq = null;
		
		for(int i = MAX_HEIGHT - 1; i >= 0; i--)
			if(succs[i] != tail)
				headPar.next[i] = new AtomicReference<BucketNode>(succs[i]);
		
		lockTimestamp.incrementAndGet();
		lock.writeLock().unlock();
		return true;
	}
	
	private BucketNode find(BucketNode head, int v, BucketNode[] preds, BucketNode[] succs, boolean par) {
		int h = MAX_HEIGHT - 1;
		while(h >= 0) {
			BucketNode next = head.next[h].get();
			if(v > next.key) {
				head = next;
			} else {
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
	
	private int generateHeight() {
		int h = 1;
		while(h < MAX_HEIGHT && ThreadLocalRandom.current().nextBoolean())
			h++;
		return h;
	}
	
	public int getMinValue() {
		return minValue.get();
	}
	
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