package shortestpath;

import java.util.function.BiConsumer;
import javax.swing.JComponent;

/**
 * The wiring ceremony every config-bound panel control shares. A control
 * writes through {@link shortestpath.settings.Settings#write}, which
 * delivers same-key listeners <em>synchronously</em> inside the write —
 * so a config listener that mirrors stored state back into the control
 * (a programmatic {@code setSelected}/{@code setValue}/{@code setSelectedItem})
 * re-fires the control's own listener and would write the mirrored value
 * straight back to config. The {@link SyncGuard} is the only thing
 * suppressing that echo, and the ordering around it is the contract:
 *
 * <ul>
 *   <li>the guard exists <b>before</b> the control's listener is
 *   installed — a guard created after registration could miss the first
 *   echo;</li>
 *   <li>the listener's write goes through {@link #guardedWrite} so an
 *   event raised while a mirror is in flight is skipped, not persisted;</li>
 *   <li>the mirror itself runs inside {@link #resync}: guard raised
 *   first, released in a {@code finally} so a throwing mirror never
 *   leaves it stuck;</li>
 *   <li>any post-resync hook runs <b>after</b> the release — it observes
 *   the mirror's effects as an outside caller would.</li>
 * </ul>
 *
 * <p>The helper owns only this plumbing. Control construction, what the
 * write persists, and what the mirror re-applies stay call-site detail —
 * an envelope that needs side-effects the plumbing cannot express stays
 * hand-rolled rather than growing parameters here.</p>
 */
final class PanelRows
{
	private PanelRows()
	{
	}

	/**
	 * The write-echo guard shared by a control's listener and its config
	 * resync. Opaque to call sites — {@link #guardedWrite} reads it and
	 * {@link #resync} raises/releases it. Sites whose several controls
	 * mirror one stored value share one instance across the whole group,
	 * so a resync on any member suppresses every member's echo; sites
	 * with one control per guard mint a fresh one through
	 * {@link #controlRow}.
	 */
	static final class SyncGuard
	{
		private boolean held;
	}

	/**
	 * Runs {@code write} unless {@code guard} is held — the listener-side
	 * half of the echo contract: a control event raised while a resync
	 * mirrors stored state into the control is the echo, not a user edit.
	 */
	static void guardedWrite(SyncGuard guard, Runnable write)
	{
		if (!guard.held)
		{
			write.run();
		}
	}

	/**
	 * Runs {@code reapply} under the held guard: raised before the mirror
	 * runs (so the echoes it triggers read as sync, not edits) and
	 * released in a {@code finally} so a throwing mirror never wedges the
	 * guard. {@code postResync} — nullable — runs after the release, so
	 * it sees the control in its mirrored state as any outside observer
	 * would.
	 */
	static void resync(SyncGuard guard, Runnable reapply, Runnable postResync)
	{
		guard.held = true;
		try
		{
			reapply.run();
		}
		finally
		{
			guard.held = false;
		}
		if (postResync != null)
		{
			postResync.run();
		}
	}

	/**
	 * Registers {@code keyName}'s config listener as a guarded resync
	 * through the panel's shared registration seam — the panel retains
	 * the unlisten handle so one teardown drops the whole listener graph.
	 */
	static void listen(ShortestPathPanel panel, String keyName, SyncGuard guard,
		Runnable reapply, Runnable postResync)
	{
		panel.registerConfigListener(keyName, () -> resync(guard, reapply, postResync));
	}

	/**
	 * The full ceremony over a fresh guard: mint the guard, install the
	 * control's listener, register the resync — in that order, so the
	 * guard always predates the listener that consults it.
	 *
	 * @param installListener attaches the control's own listener. Each
	 *     control type takes a different listener (Action/Change/Item),
	 *     so the site installs whichever fits and routes its write
	 *     through {@link #guardedWrite}.
	 * @param reapply mirrors the stored value into the control; runs
	 *     under the held guard.
	 * @param postResync nullable hook run after the guard releases.
	 */
	static <C extends JComponent> C controlRow(ShortestPathPanel panel, String keyName, C control,
		BiConsumer<C, SyncGuard> installListener, Runnable reapply, Runnable postResync)
	{
		return controlRow(panel, keyName, control, new SyncGuard(), installListener, reapply, postResync);
	}

	/**
	 * The same ceremony over a caller-supplied guard — the shape sites
	 * take when several controls mirror one stored value and must share
	 * the guard across the group rather than each minting their own.
	 */
	static <C extends JComponent> C controlRow(ShortestPathPanel panel, String keyName, C control,
		SyncGuard guard, BiConsumer<C, SyncGuard> installListener,
		Runnable reapply, Runnable postResync)
	{
		installListener.accept(control, guard);
		listen(panel, keyName, guard, reapply, postResync);
		return control;
	}
}
