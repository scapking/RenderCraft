package dev.scapking.rendcraft.input;

/**
 * Input mode the user is in. Controls how keyboard/mouse events are routed
 * between Minecraft and a captured external window.
 *
 * <p>The matrix is intentionally small — a wider state space is hard to
 * reason about and tends to ship with edge cases. Transitions between
 * restricted modes go through {@link InputModeController#transitionFrom}
 * so a state machine enforces the legal moves.
 */
public enum InputMode {
    /** Default: keystrokes go to Minecraft, no captured window has focus. */
    FREE,

    /** Minecraft forwards the cursor to a captured window but keeps keyboard. */
    NAVIGATION_CAPTURE,

    /** A captured window has both keyboard and pointer focus. */
    FULL_CAPTURE,

    /** Player is moving windows around in a template; clicks are reserved. */
    LAYOUT_ADJUSTMENT
}
