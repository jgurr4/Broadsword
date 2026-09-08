package com.jmgurr.broadsword.model;

/** Enemy types the generator may place. Ghosts are Cemetery-only, never tiered. */
public enum EnemyKind {
    GRUNT,
    OCTOROCK,
    /** Cemetery-only; ethereal, immune to everything but the Flute. */
    GHOST,
    /** Boss (T11): stationary Hydra head, fires staggered Fireballs; 4 sword hits. */
    HYDRA_HEAD,
    /** Boss (T11): stationary invulnerable body; scenery until the heads fall. */
    HYDRA_BODY
}
