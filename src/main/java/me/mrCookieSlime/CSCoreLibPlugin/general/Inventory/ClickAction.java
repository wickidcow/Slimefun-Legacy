package me.mrCookieSlime.CSCoreLibPlugin.general.Inventory;

/**
 * Immutable description of the mouse/keyboard modifiers used for a menu click.
 *
 * <p>This type remains part of the public menu-handler contract and is therefore
 * supported until a binary-compatible replacement API is introduced.
 */
public class ClickAction {

    private final boolean right;
    private final boolean shift;

    public ClickAction(boolean rightClicked, boolean shiftClicked) {
        this.right = rightClicked;
        this.shift = shiftClicked;
    }

    public boolean isRightClicked() {
        return right;
    }

    public boolean isShiftClicked() {
        return shift;
    }
}
