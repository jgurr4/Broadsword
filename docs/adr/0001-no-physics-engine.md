# No physics engine

Movement in Broadsword is tile-based: positions are tile coordinates and motion is discrete tile stepping. We considered dyn4j and a from-scratch physics engine; neither is needed because there is nothing to simulate — smooth on-screen motion is render-side interpolation between tiles, not physical state.

**Consequences:** positions stay discrete and deterministic, which the puzzle/map-reading design depends on; "smooth movement" is an animation concern, so no physics library appears in the dependency tree. If a future version needs real dynamics (rolling boulders, knockback), that's a new subsystem, not a rewrite of the movement layer.
