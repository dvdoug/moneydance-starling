# Marketplace submission

The bar is “a stranger can install this from an official directory, enter their own Starling PAT, and trust it with a data file.”

## Moneydance (Infinite Kind)

### What “listed” means

- Appears under **Extensions → Manage Extensions**.
- Also downloadable from https://infinitekind.com/extensions as a `.mxt`.
- Every extension is **audited and signed by The Infinite Kind**. Local DevKit signatures are for development. Users who force-load unsigned MXTs are not the marketplace audience.

### What Infinite Kind expects (from their developer docs and existing listings)

- One self-contained `.mxt` (jar/zip). No “also install this JRE” or sidecar scripts.
- Valid `meta_info.dict`: `id`, `module_name`, `module_desc`, `vendor`, `module_build`, `minbuild`.
- `id` stable forever (`starling`).
- Kotlin/Java FeatureModule, not a Jython-only script, for a new official extension.
- Does not brick data files. Import path must be idempotent.
- Logging goes to Help → Show Console, not a surprise window.
- Reasonable `minbuild` so old Moneydance copies get a clear “update Moneydance” failure instead of a `NoClassDefFoundError`.

### How to get signed / listed

There is no fully self-serve developer console. Path:

1. Build and dogfood a stable `.mxt`.
2. Post in [Extension Development](https://infinitekind.tenderapp.com/discussions/moneydance-development) and/or contact Infinite Kind.
3. Send the unsigned (or locally signed) package plus source. They counter-sign.
4. Listing copy should match `meta_info.dict` `module_desc`: unofficial Starling import via personal access token. Vendor **Doug Wright**. Always include the unaffiliated disclaimer.

Useful links:

- Developer kit: https://infinitekind.com/developer
- Core API: https://infinitekind.com/dev/apidoc/index.html
- Open-source reference extensions: https://github.com/TheInfiniteKind/moneydance_open
- Forum: https://infinitekind.tenderapp.com/discussions/moneydance-development

### Reviewer hot buttons for *this* extension

- Where is the PAT stored, and is it in the git repo / MXT? It must not be.
- Can a sync run twice without duplicating transactions?
- Does unload leak listeners if the user switches data files?
- Network on the EDT (will freeze the UI) — do not.

There is no Starling “destination catalog” to list in. Users create a PAT in Starling’s Developer Portal.

## Shared polish checklist before submission

- [x] Settings UI for the PAT; Add token checks scopes
- [x] In-app help: [docs/user](user/README.md) setup guide (GitHub)
- [x] Account mapping + From date (rolls forward after success)
- [x] Idempotent import (FITID skip; second import of the same window adds no extra blue dots)
- [x] Errors a non-developer can act on (status bar + window + console)
- [x] Extension icon (placeholder; replace before listing)
- [x] Marketplace blurb (`module_desc`) / LICENSE
- [x] No secrets in the repo, the MXT, or logs
- [ ] Tested on current Moneydance Windows (in progress) and at least one of macOS/Linux before Infinite Kind review
- [x] `module_desc` written for the Manage Extensions list, not for engineers
