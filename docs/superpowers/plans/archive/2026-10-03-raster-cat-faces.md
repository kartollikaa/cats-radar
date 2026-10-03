# Raster cat faces implementation plan

**Goal:** Use the approved Rounded Ink raster faces throughout the coat UI.

**Architecture:** Replace the shared CatFace renderer's vector artwork with local transparent raster resources. Keep coat identities, Material containers, selection state and callbacks. Retain the legacy paths used by launcher and notification icons.

**Spec:** `docs/features/coat.md` and the owner's approved B prototype in this conversation.

**Assumptions:** Expressions belong to each coat's artwork, not encounter data. All eleven existing coats are included; no new coat or database field is introduced. Launcher and notification artwork stay separate.

**Current checkpoint:** The owner approved a softer ginger-and-white face revised from the original B reference. The full eleven-face family now follows it, including a brighter mouth on the solid black face for small-screen legibility. UI and app unit suites and both debug builds passed. Light and dark picker screenshots at 411 dp and an encounter screenshot at its real card sizes were inspected; ears and expressions remain legible. Independent review found and verified the black-mouth contrast fix. The full `check` reached unrelated launcher-resource lint failures; the owner requested no further check run before commit and push. No APK has been installed.

## Task 1: Assets and shared rendering

- [x] Generate each coat with its documented markings, using the approved B reference.
- [x] Package transparent raster resources with equal canvas dimensions.
- [x] Replace shared face painting while preserving theme contrast and caller sizing.
- [x] Check actual raster alpha against coat container boundaries; replace obsolete vector-only coverage.
- [x] Update the coat feature document to describe raster faces and fixed expressions.

## Task 2: Verification

- [x] Verify asset dimensions, alpha, distinct content, and all coat mappings.
- [x] Run existing coat, selector and encounter checks; build both debug distributions.
- [x] Inspect rendered faces in light and dark themes, at selector and encounter sizes.
- [x] Obtain an independent review and fix actionable findings.

## Review focus

White and black face contrast; differentiation of solid versus white-muzzled coats; calico white coverage; no clipped ears in Material containers; consistent rendering across selection and encounters.
