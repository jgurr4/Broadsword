package com.jmgurr.broadsword.model;

/** Enemy types the generator may place. Ghosts are Cemetery-only, never tiered. */
public enum EnemyKind {
    GRUNT,
    OCTOROCK,
    /** Cemetery-only; ethereal, immune to everything but the Flute. */
    GHOST
}
