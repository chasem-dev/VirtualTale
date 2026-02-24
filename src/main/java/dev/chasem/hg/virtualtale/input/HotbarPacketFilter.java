package dev.chasem.hg.virtualtale.input;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.chasem.hg.virtualtale.emulator.EmulatorButton;
import dev.chasem.hg.virtualtale.emulator.EmulatorSession;
import dev.chasem.hg.virtualtale.emulator.EmulatorSessionManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Packet filter that intercepts hotbar key presses (1-8) and maps them to
 * emulator buttons for players with active VirtualTale sessions.
 *
 * Mapping:
 *   Key 1 (slot 0) -> UP       Key 5 (slot 4) -> A
 *   Key 2 (slot 1) -> DOWN     Key 6 (slot 5) -> B
 *   Key 3 (slot 2) -> LEFT     Key 7 (slot 6) -> START
 *   Key 4 (slot 3) -> RIGHT    Key 8 (slot 7) -> SELECT
 *
 * All SyncInteractionChains and SetActiveSlot packets are blocked for active
 * sessions to prevent hotbar switching interference. After each button press,
 * the active slot is forced back to slot 8 (key 9) so repeated presses of
 * the same key generate new SwapFrom events.
 *
 * Input is debounced per-player per-button: a button press is ignored if
 * the same button was pressed within the hold window. The button is held
 * for the configured duration before being released.
 */
public class HotbarPacketFilter implements PlayerPacketFilter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Hotbar section ID used by Hytale's inventory system. */
    private static final int HOTBAR_SECTION_ID = -1;

    /** Neutral slot index (key 9, 0-indexed = 8) that we reset to after each press. */
    private static final int NEUTRAL_SLOT = 8;

    private final EmulatorSessionManager sessionManager;
    private final long buttonHoldMs;

    /**
     * Per-player, per-button pending release future. While a release is pending,
     * duplicate presses for that button are ignored (debounce).
     */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<EmulatorButton, ScheduledFuture<?>>> pendingReleases =
            new ConcurrentHashMap<>();

    private final ScheduledExecutorService releaseScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "VT-ButtonRelease");
        t.setDaemon(true);
        return t;
    });

    public HotbarPacketFilter(@Nonnull EmulatorSessionManager sessionManager, long buttonHoldMs) {
        this.sessionManager = sessionManager;
        this.buttonHoldMs = buttonHoldMs;
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        // Only intercept interaction and hotbar packets
        if (!(packet instanceof SyncInteractionChains) && !(packet instanceof SetActiveSlot)) {
            return false;
        }

        EmulatorSession session = sessionManager.getSession(playerRef.getUuid());
        if (session == null || !session.isRunning()) {
            return false;
        }

        // Block ALL SetActiveSlot packets for active sessions (client-side slot changes)
        if (packet instanceof SetActiveSlot) {
            return true;
        }

        // Extract button presses from SwapFrom interactions, then block the entire packet
        SyncInteractionChains syncPacket = (SyncInteractionChains) packet;
        LOGGER.atInfo().log("[VT-DBG] SyncInteractionChains packet with %d chain(s) from %s",
                syncPacket.updates.length, playerRef.getUuid());

        for (int i = 0; i < syncPacket.updates.length; i++) {
            SyncInteractionChain chain = syncPacket.updates[i];
            LOGGER.atInfo().log("[VT-DBG]   chain[%d]: type=%s initial=%b state=%s activeSlot=%d targetSlot=%s",
                    i, chain.interactionType, chain.initial, chain.state,
                    chain.activeHotbarSlot,
                    chain.data != null ? String.valueOf(chain.data.targetSlot) : "null");

            if (chain.interactionType == InteractionType.SwapFrom
                    && chain.data != null
                    && chain.initial) {

                int targetSlot = chain.data.targetSlot;
                EmulatorButton button = mapSlotToButton(targetSlot);

                LOGGER.atInfo().log("[VT-DBG]   -> SwapFrom match: slot=%d button=%s", targetSlot, button);

                if (button != null) {
                    handleButtonPress(playerRef, session, button);
                }
            }
        }

        // Block the packet regardless — no interaction processing for VT sessions
        return true;
    }

    /**
     * Handles a button press with debouncing. If the button already has a
     * pending release (still held), the press is ignored. Otherwise, the
     * button is pressed and a release is scheduled after the hold duration.
     * The active hotbar slot is then forced back to the neutral slot (9).
     */
    private void handleButtonPress(@Nonnull PlayerRef playerRef, @Nonnull EmulatorSession session, @Nonnull EmulatorButton button) {
        UUID playerId = playerRef.getUuid();
        ConcurrentHashMap<EmulatorButton, ScheduledFuture<?>> playerReleases =
                pendingReleases.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());

        ScheduledFuture<?> existingRelease = playerReleases.get(button);
        if (existingRelease != null && !existingRelease.isDone()) {
            // Button still held from previous press — ignore duplicate
            LOGGER.atInfo().log("[VT-DBG] DEBOUNCED %s for %s (still held)", button, playerId);
            return;
        }

        // Press the button
        LOGGER.atInfo().log("[VT-DBG] >>> PRESS %s -> emulator for %s", button, playerId);
        session.getBackend().pressButton(button);

        // Schedule release
        ScheduledFuture<?> releaseFuture = releaseScheduler.schedule(() -> {
            LOGGER.atInfo().log("[VT-DBG] <<< RELEASE %s -> emulator for %s", button, playerId);
            session.getBackend().releaseButton(button);
            playerReleases.remove(button);
        }, buttonHoldMs, TimeUnit.MILLISECONDS);
        playerReleases.put(button, releaseFuture);

        // Force hotbar back to neutral slot so the same key can be pressed again
        try {
            playerRef.getPacketHandler().write(new SetActiveSlot(HOTBAR_SECTION_ID, NEUTRAL_SLOT));
        } catch (Exception e) {
            LOGGER.atWarning().log("[VT] Failed to reset hotbar for %s: %s", playerId, e.getMessage());
        }
    }

    /**
     * Cleans up debounce state for a player (call when session stops).
     */
    public void clearPlayer(@Nonnull UUID playerId) {
        ConcurrentHashMap<EmulatorButton, ScheduledFuture<?>> playerReleases = pendingReleases.remove(playerId);
        if (playerReleases != null) {
            playerReleases.values().forEach(f -> f.cancel(false));
        }
    }

    /**
     * Shuts down the release scheduler. Call during plugin shutdown.
     */
    public void shutdown() {
        releaseScheduler.shutdown();
        pendingReleases.clear();
    }

    /**
     * Maps a hotbar slot (0-indexed) to an emulator button.
     * Slots 0-7 map to UP, DOWN, LEFT, RIGHT, A, B, START, SELECT.
     */
    @Nullable
    private static EmulatorButton mapSlotToButton(int slot) {
        return switch (slot) {
            case 0 -> EmulatorButton.UP;
            case 1 -> EmulatorButton.DOWN;
            case 2 -> EmulatorButton.LEFT;
            case 3 -> EmulatorButton.RIGHT;
            case 4 -> EmulatorButton.A;
            case 5 -> EmulatorButton.B;
            case 6 -> EmulatorButton.START;
            case 7 -> EmulatorButton.SELECT;
            default -> null;
        };
    }
}
