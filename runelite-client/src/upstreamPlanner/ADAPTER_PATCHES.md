# Microbot adapter patches

The production shadow adapter requires a deliberately small delta from the pinned core:

- retain the exact selected `Transport` on each `PathStep` (the same patch used by the comparison harness);
- allow an immutable edge override for Microbot's pinned live-collision snapshot;
- allow an immutable walking-cost policy for dangerous-tile penalties;
- expose the transport-availability builder to the package-external adapter;
- replace the upstream plugin class with a resource/config compatibility anchor only.

The following correctness backports were identified while reviewing the pin and are kept explicit until they
land upstream:

- accept an empty league-region test snapshot without calling `EnumSet.copyOf` on an empty collection;
- materialize blocked-neighbour transport origins at the expanded neighbour tile;
- aggregate duplicate item quantities across inventory, equipment, bank, and rune-pouch snapshots;
- preserve multi-word special skill names when parsing requirements; and
- preserve a region override when merging permutation transports.

None of these hooks owns execution, reads Microbot globals, or changes queue ordering when its supplied
policy returns the default value.

The reviewed patch budget is nine modified upstream files and one adapter-added file. The offline vendored-core
checker rejects growth beyond that budget. Increasing either limit requires an ADR amendment that explains why
the hook cannot remain outside the core or be contributed upstream; changing only the digest is not sufficient
review for a larger long-lived fork.
