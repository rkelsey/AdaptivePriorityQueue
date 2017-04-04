import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * The priority queue presented in the paper by Calciu, Mendes, and Herlihy
 * @author Ryan Kelsey and Lee Berman
 *
 */
public class PaperPriorityQueue implements IPriorityQueue {
	private Skiplist skiplist;
	private AtomicStampedReference<Integer>[] elim;
	private AtomicInteger uniqueStamp;
	private Server server;
	
	private final int ELIM_SIZE = 5; //Size of the elimination array
	private final int MAX_ELIM_MIN = 2 * ELIM_SIZE; //Elimination attempts if added value is less than the skiplist minimum
	private final int MAX_ELIM = 6 * ELIM_SIZE; //Elimination attempts if added value is greater than the skiplist minimum
	
	//Reserved values
	public final int EMPTY = Integer.MIN_VALUE;
	public final int REMREQ = Integer.MIN_VALUE + 1;
	public final int TAKEN = Integer.MIN_VALUE + 2;
	public final int INPROG = Integer.MIN_VALUE + 3;
	
	/**
	 * Creates the priority queue and starts a Server
	 * @param h The height of the skiplist to be used
	 * @throws InterruptedException
	 */
	@SuppressWarnings("unchecked")
	public PaperPriorityQueue(int h) throws InterruptedException {
		skiplist = new Skiplist(h);
		elim = new AtomicStampedReference[ELIM_SIZE];
		for(int i = 0; i < ELIM_SIZE; i++)
			elim[i] = new AtomicStampedReference<Integer>(EMPTY, 0);
		uniqueStamp = new AtomicInteger(1);
		
		server = new Server();
		server.start();
	}
	
	/* (non-Javadoc)
	 * @see IPriorityQueue#removeMin(int)
	 */
	public int removeMin(int threadId) {
		int pos = threadId % ELIM_SIZE;
		int[] stampHolder = new int[1];
		while(true) {
			Integer value = elim[pos].get(stampHolder);
			int stamp = stampHolder[0];
			
			//If we discover a value smaller than the skiplist minimum, attempt to return it
			if(isValue(value) && stamp > 0 && value <= skiplist.getMinValue())
				if(elim[pos].compareAndSet(value, TAKEN, stamp, 0))
					return value;
			
			//If we discover an empty spot in the elimination array, attempt to make a remove request
			//and wait for the Server or add() to populate the slot
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
	
	/* (non-Javadoc)
	 * @see IPriorityQueue#add(int, int)
	 */
	public boolean add(int inValue, int threadId) {
		//Reserved values cannot be added to the priority queue
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
		
		//Attempt to eliminate with a remove request rep times
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
			
			//If a remove request is found, attempt to serve it inValue if it's small enough
			if(value == REMREQ && inValue <= skiplist.getMinValue())
				if(elim[pos].compareAndSet(value, inValue, stamp, 0))
					return true;
			
			//If an empty slot is found, attempt to post inValue and wait until the Server or remove() removes it
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
	
	/**
	 * Determines if a value is a reserved value or not.
	 * @param v The value to be checked
	 * @return false if the value is reserved
	 */
	private boolean isValue(int v) {
		return v > INPROG;
	}
	
	/**
	 * Atomically increments the unique stamp and returns the new value
	 * @return The value of the incremented stamp
	 */
	private int uniqueStamp() {
		return uniqueStamp.getAndIncrement();
	}
	
	/**
	 * Stops the Server
	 */
	public void stop() {
		server.run = false;
	}
	
	/**
	 * The Server thread that performs sequential operations on the priority queue
	 * @author Ryan Kelsey and Lee Berman
	 *
	 */
	private class Server extends Thread {
		protected volatile boolean run;
		
		/**
		 * Creates the Server and tells it to run
		 */
		public Server() {
			run = true;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			int[] stampHolder = new int[1];
			while(run) {
				for(int i = 0; i < ELIM_SIZE; i++) {
					Integer value = elim[i].get(stampHolder);
					int stamp = stampHolder[0];
					
					//If a remove request is found, attempt to fill it with the smallest value from the skiplist
					if(value == REMREQ) {
						if(elim[i].compareAndSet(value, INPROG, stamp, 0)) {
							int min = skiplist.removeSeq();
							elim[i] = new AtomicStampedReference<Integer>(min, 0);
						}
					}
					
					//If a value is found, attempt to add it to the skiplist
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