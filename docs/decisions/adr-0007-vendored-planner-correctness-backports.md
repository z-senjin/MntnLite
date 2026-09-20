# ADR 0007: Vendored Planner Correctness Backports

- Status: Accepted (2026-08-27)

## Context
Microbot pins the non-UI planner core from `Skretzo/shortest-path` so upstream drift stays mechanical and
reviewable. Review of the pinned revision found correctness defects in empty league snapshots, expanded
transport origins, duplicate item quantities, multi-word skill requirements, and merged transport region
overrides. The same defects remain on the current upstream `master`, so advancing the pin cannot resolve them.

Two corrections fall inside files already modified for the Microbot adapter. The remaining three expand the
vendored patch surface from six to nine upstream-modified files. Leaving the defects in place would make the
vendored core knowingly unsafe to activate in the later runtime-integration PR.

## Decision
Carry the five corrections as explicit, focused backports and list them in `ADAPTER_PATCHES.md`. Increase the
reviewed budget to nine modified upstream files while retaining the single adapter-added-file limit. Keep the
upstream revision pin unchanged because it still accurately identifies the source revision being patched.

When equivalent fixes land in `Skretzo/shortest-path`, advance the pin and remove each redundant backport.

## Consequences
- The vendored-core drift checker must recognize a maximum of nine modified upstream files.
- Reviewers can distinguish runtime adapter hooks from temporary correctness backports.
- Future patch-surface growth still requires an ADR amendment rather than a digest-only baseline update.
