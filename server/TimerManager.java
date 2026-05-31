package server;

import model.GameRoom;

import java.util.List;
import java.util.concurrent.*;

/**
 * Drives the countdown timer for a single active question.
 *
 * Responsibilities:
 *   - Broadcast warning messages at configured thresholds (e.g. 10s, 5s, 3s left)
 *   - Fire a "time's up" callback when the full duration elapses
 *
 * A new TimerManager instance is created for each question.
 */
public class TimerManager {

    private final GameRoom        room;
    private final int             durationSeconds;
    private final List<Integer>   warningThresholds; // seconds remaining at which to warn
    private final Runnable        onTimeout;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();

    private volatile boolean cancelled = false;

    public TimerManager(GameRoom room, int durationSeconds,
                        List<Integer> warningThresholds, Runnable onTimeout) {
        this.room               = room;
        this.durationSeconds    = durationSeconds;
        this.warningThresholds  = warningThresholds;
        this.onTimeout          = onTimeout;
    }

    // -----------------------------------------------------------------------
    // Start
    // -----------------------------------------------------------------------

    public void start() {
        // Schedule a warning broadcast for each threshold
        for (int warnAt : warningThresholds) {
            if (warnAt >= durationSeconds) continue;          // skip nonsensical thresholds
            long delay = durationSeconds - warnAt;
            scheduler.schedule(() -> {
                if (!cancelled && room.isQuestionOpen()) {
                    room.broadcastAll("  \u23F1  " + warnAt + " seconds left!");
                }
            }, delay, TimeUnit.SECONDS);
        }

        // Schedule the "time's up" event
        scheduler.schedule(() -> {
            if (!cancelled) {
                onTimeout.run();
            }
        }, durationSeconds, TimeUnit.SECONDS);
    }

    // -----------------------------------------------------------------------
    // Cancel (call when question closes early — all players answered)
    // -----------------------------------------------------------------------

    public void cancel() {
        cancelled = true;
        scheduler.shutdownNow();
    }
}
