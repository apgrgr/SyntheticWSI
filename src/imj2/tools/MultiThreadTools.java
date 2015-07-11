package imj2.tools;

import static multij.tools.Tools.unchecked;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import multij.tools.IllegalInstantiationException;
import multij.tools.SystemProperties;

/**
 * @author codistmonk (creation 2013-08-15)
 */
public final class MultiThreadTools {
	
	private MultiThreadTools() {
		throw new IllegalInstantiationException();
	}
	
	public static final int WORKER_COUNT = SystemProperties.getAvailableProcessorCount();
	
	private static final Map<Thread, Integer> workerIds = new HashMap<Thread, Integer>();
	
	private static ExecutorService executor;
	
	private static long lastAccess;
	
	static final synchronized long getLastAccess() {
		return lastAccess;
	}
	
	public static final synchronized ExecutorService getExecutor() {
		if (executor == null) {
			executor = Executors.newFixedThreadPool(WORKER_COUNT);
			
			final Timer terminator = new Timer();
			final long period = 2_000L;
			
			terminator.scheduleAtFixedRate(new TimerTask() {
				
				@Override
				public final void run() {
					if (period <= (System.currentTimeMillis() - getLastAccess())) {
						shutdownExecutor();
						terminator.cancel();
					}
				}
				
			}, period, period);
		}
		
		lastAccess = System.currentTimeMillis();
		
		return executor;
	}
	
	public static final synchronized void shutdownExecutor() {
		if (executor != null) {
			executor.shutdown();
			executor = null;
		}
	}
	
	public static final void wait(final Iterable<? extends Future<?>> tasks) {
		try {
			for (final Future<?> task : tasks) {
				task.get();
			}
		} catch (final Exception exception) {
			throw unchecked(exception);
		}
	}
	
	public static final int getWorkerId() {
		return getOrCreateId(workerIds, Thread.currentThread());
	}
	
	public static final <K> int getOrCreateId(final Map<K, Integer> ids, final K key) {
		synchronized (ids) {
			Integer result = ids.get(key);
			
			if (result == null) {
				result = ids.size();
				ids.put(key, result);
			}
			
			return result;
		}
	}
	
}
