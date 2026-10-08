package shortestpath.spirittree;

import java.util.HashSet;
import java.util.Set;

/**
 * Parsed contents of one spirit tree travel menu: every patch row the
 * menu showed ({@link #listed}) and the subset usable right now
 * ({@link #available} — a greyed row means planted but not usable).
 */
final class SpiritTreeMenuSnapshot
{
	final Set<String> listed = new HashSet<>();
	final Set<String> available = new HashSet<>();
}
