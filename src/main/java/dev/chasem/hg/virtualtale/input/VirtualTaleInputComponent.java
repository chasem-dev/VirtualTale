package dev.chasem.hg.virtualtale.input;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * ECS Component for tracking VirtualTale input state on a player entity.
 * Stores the anchor position (where the player is teleported back to)
 * and currently held D-pad buttons.
 */
public class VirtualTaleInputComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, VirtualTaleInputComponent> COMPONENT_TYPE = new ComponentType<>();

    public static ComponentType<EntityStore, VirtualTaleInputComponent> getComponentType() {
        return COMPONENT_TYPE;
    }

    public static void setComponentType(ComponentType<EntityStore, VirtualTaleInputComponent> componentType) {
        COMPONENT_TYPE = componentType;
    }

    private double anchorX;
    private double anchorY;
    private double anchorZ;

    // Track the last known position for delta detection
    private double lastX;
    private double lastZ;
    private boolean positionInitialized;

    public VirtualTaleInputComponent() {
    }

    public void setAnchor(double x, double y, double z) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.lastX = x;
        this.lastZ = z;
        this.positionInitialized = true;
    }

    public double getAnchorX() { return anchorX; }
    public double getAnchorY() { return anchorY; }
    public double getAnchorZ() { return anchorZ; }

    public double getLastX() { return lastX; }
    public double getLastZ() { return lastZ; }

    public void setLastPosition(double x, double z) {
        this.lastX = x;
        this.lastZ = z;
    }

    public boolean isPositionInitialized() { return positionInitialized; }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        VirtualTaleInputComponent cloned = new VirtualTaleInputComponent();
        cloned.anchorX = this.anchorX;
        cloned.anchorY = this.anchorY;
        cloned.anchorZ = this.anchorZ;
        cloned.lastX = this.lastX;
        cloned.lastZ = this.lastZ;
        cloned.positionInitialized = this.positionInitialized;
        return cloned;
    }
}
