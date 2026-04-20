package shortestpath;

public class PendingTask {
	private final int tick;
	private final Runnable task;

	PendingTask(int tick, Runnable task) {
		this.tick = tick;
		this.task = task;
	}

	public boolean check(int tick) {
		return tick >= this.tick;
	}

	public void run() {
		task.run();
	}
}
