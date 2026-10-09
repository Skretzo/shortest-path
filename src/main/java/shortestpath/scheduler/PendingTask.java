package shortestpath.scheduler;

public class PendingTask
{
	private final int tick;
	private final Runnable task;

	PendingTask(int tick, Runnable task)
	{
		this.tick = tick;
		this.task = task;
	}

	boolean check(int tick)
	{
		return tick >= this.tick;
	}

	void run()
	{
		task.run();
	}
}
