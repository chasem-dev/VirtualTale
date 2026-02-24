package dev.chasem.hg.virtualtale.input;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.chasem.hg.virtualtale.emulator.EmulatorSession;
import dev.chasem.hg.virtualtale.emulator.EmulatorSessionManager;
import eu.rekawek.coffeegb.controller.Button;

import javax.annotation.Nonnull;

/**
 * ECS tick system that detects WASD movement deltas while the player
 * is in VirtualTale mode. When movement is detected, the player is
 * teleported back to their anchor position and the direction is
 * translated to a Game Boy D-pad button press.
 *
 * This works while the map is open because WASD movement still
 * generates position updates on the server.
 */
public class VirtualTaleInputSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final double MOVE_THRESHOLD = 0.1;

    private final EmulatorSessionManager sessionManager;

    public VirtualTaleInputSystem(@Nonnull EmulatorSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                Player.getComponentType(),
                VirtualTaleInputComponent.getComponentType()
        );
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (!ref.isValid()) return;

        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        VirtualTaleInputComponent input = archetypeChunk.getComponent(index, VirtualTaleInputComponent.getComponentType());
        if (player == null || input == null || !input.isPositionInitialized()) return;

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        EmulatorSession session = sessionManager.getSession(playerRef.getUuid());
        if (session == null || !session.isRunning()) return;

        double currentX = transform.getPosition().x;
        double currentZ = transform.getPosition().z;

        double deltaX = currentX - input.getAnchorX();
        double deltaZ = currentZ - input.getAnchorZ();

        if (Math.abs(deltaX) < MOVE_THRESHOLD && Math.abs(deltaZ) < MOVE_THRESHOLD) {
            return;
        }

        // Determine primary direction
        Button button;
        if (Math.abs(deltaX) > Math.abs(deltaZ)) {
            button = deltaX > 0 ? Button.RIGHT : Button.LEFT;
        } else {
            button = deltaZ > 0 ? Button.DOWN : Button.UP;
        }

        LOGGER.atInfo().log("[VT] WASD detected: deltaX=%.2f deltaZ=%.2f -> %s (player=%s, pos=%.1f,%.1f anchor=%.1f,%.1f)",
                deltaX, deltaZ, button, playerRef.getUuid(),
                currentX, currentZ, input.getAnchorX(), input.getAnchorZ());

        // Press and schedule release
        session.getGameboy().pressButton(button);

        // Teleport player back to anchor
        transform.getPosition().x = input.getAnchorX();
        transform.getPosition().y = input.getAnchorY();
        transform.getPosition().z = input.getAnchorZ();

        // Schedule button release after a short hold
        final Button pressedButton = button;
        Thread.ofVirtual().name("VT-Release-" + pressedButton).start(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            session.getGameboy().releaseButton(pressedButton);
        });
    }
}
