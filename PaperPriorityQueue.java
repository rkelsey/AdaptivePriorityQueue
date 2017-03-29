import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

public class PaperPriorityQueue implements IPriorityQueue {
	private Skiplist skiplist;
	private AtomicStampedReference<Integer>[] elim;
	private AtomicInteger uniqueStamp, addOps;
	private Server server;
	
	private final int ELIM_SIZE = 5;
	private final int MAX_ELIM_MIN = 5;
	private final int MAX_ELIM = 10;
	
	public final int EMPTY = Integer.MIN_VALUE;
	public final int REMREQ = Integer.MIN_VALUE + 1;
	public final int TAKEN = Integer.MIN_VALUE + 2;
	public final int INPROG = Integer.MIN_VALUE + 3;
	
	@SuppressWarnings("unchecked")
	public PaperPriorityQueue(int h) throws InterruptedException {
		skiplist = new Skiplist(h);
		elim = new AtomicStampedReference[ELIM_SIZE];
		for(int i = 0; i < ELIM_SIZE; i++)
			elim[i] = new AtomicStampedReference<Integer>(EMPTY, 0);
		uniqueStamp = new AtomicInteger(1);
		addOps = new AtomicInteger(0);
		
		server = new Server();
		server.start();
	}
	
	public int removeMin(int threadId) {
		addOps.set(0);
		int pos = threadId % ELIM_SIZE;
		int[] stampHolder = new int[1];
		while(true) {
			Integer value = elim[pos].get(stampHolder);
			int stamp = stampHolder[0];
			
			if(isValue(value) && stamp > 0 && value <= skiplist.getMinValue())
				if(elim[pos].compareAndSet(value, TAKEN, stamp, 0))
					return value;
			
			if(value == EMPTY) {
				if(elim[pos].compareAndSet(value, REMREQ, stamp, uniqueStamp())) {
					do {
						value = elim[pos].getReference();
					} while(value == REMREQ || value == INPROG);
					elim[pos] = new AtomicStampedReference<Integer>(EMPTY, 0);
					return value;
				}
			}
			pos = (pos + 1) % ELIM_SIZE;
		}
	}
	
	public boolean add(int inValue, int threadId) {
		addOps.incrementAndGet();
		if(!isValue(inValue))
			return false;
		
		int rep;
		if(inValue <= skiplist.getMinValue())
			rep = MAX_ELIM_MIN;
		else {
			if(skiplist.addPar(inValue))
				return true;
			rep = MAX_ELIM;
		}
		
		int pos = threadId % ELIM_SIZE;
		int[] stampHolder = new int[1];
		while(rep > 0) {
			Integer value = elim[pos].get(stampHolder);
			int stamp = stampHolder[0];
			if(value == REMREQ && inValue <= skiplist.getMinValue())
				if(elim[pos].compareAndSet(value, inValue, stamp, 0))
					return true;
			rep--;
			pos = (pos + 1) % ELIM_SIZE;
		}
		
		if(skiplist.addPar(inValue))
			return true;
		
		while(true) {
			Integer value = elim[pos].get(stampHolder);
			int stamp = stampHolder[0];
			if(value == REMREQ && inValue <= skiplist.getMinValue())
				if(elim[pos].compareAndSet(value, inValue, stamp, 0))
					return true;
			
			if(value == EMPTY) {
				if(elim[pos].compareAndSet(value, inValue, stamp, uniqueStamp())) {
					do {
						value = elim[pos].getReference();
					} while(value != TAKEN);
					elim[pos] = new AtomicStampedReference<Integer>(EMPTY, 0);
					return true;
				}
			}
			
			pos = (pos + 1) % ELIM_SIZE;
		}
	}
	
	private boolean isValue(int v) {
		return v > INPROG;
	}
	
	private int uniqueStamp() {
		return uniqueStamp.getAndIncrement();
	}
	
	public void stop() {
		server.run = false;
	}
	
	private class Server extends Thread {
		public volatile boolean run;
		
		public Server() {
			run = true;
		}
		
		@Override
		public void run() {
			int[] stampHolder = new int[1];
			while(run) {
				for(int i = 0; i < ELIM_SIZE; i++) {
					Integer value = elim[i].get(stampHolder);
					int stamp = stampHolder[0];
					if(value == REMREQ) {
						if(elim[i].compareAndSet(value, INPROG, stamp, 0)) {
							int min = skiplist.removeSeq();
							elim[i] = new AtomicStampedReference<Integer>(min, 0);
						}
					}
					
					if(isValue(value) && stamp > 0) {
						if(elim[i].compareAndSet(value, INPROG, stamp, 0)) {
							skiplist.addSeq(value);
							elim[i] = new AtomicStampedReference<Integer>(TAKEN, 0);
						}
					}
				}
			}
		}
	}
}