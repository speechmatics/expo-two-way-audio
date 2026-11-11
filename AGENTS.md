# AGENTS.md

Operational notes for anyone, human or automated, about to change this
repository. This is not a second charter. The charter is
[INTENT.md](./INTENT.md).

## Load before working

1. **[INTENT.md](./INTENT.md) wins on intent.** Read it first. If code or a
   document contradicts a rule in INTENT.md, the rule stands and the code is
   the bug. Do not work around a rule; open a pull request that amends
   INTENT.md if the rule is wrong.
2. [docs/ROADMAP.md](./docs/ROADMAP.md) says what gets built in what order
   and what each step waits on.
3. [README.md](./README.md) is the public API reference and migration guide.
4. [CHANGELOG.md](./CHANGELOG.md) is what each released version contains.

## What this is

An Expo native module (Swift in `ios/`, Kotlin in `android/`, a thin
TypeScript layer in `src/`) for echo-cancelled microphone capture, low-latency
PCM playback and barge-in. Published to npm as
`@equationalapplications/expo-two-way-audio`. It is a maintained fork of
`@speechmatics/expo-two-way-audio`; keep the Speechmatics credit and never
imply they endorse this fork.

## Commands

```sh
npm ci                # install exactly what package-lock.json says
npm run typecheck     # tsc, no emit
npm run build         # tsc into build/
npm run format        # Biome check (read-only)
npm run format:fix    # Biome check and write fixes
npm pack --dry-run    # confirm the published file list
```

Continuous integration on pushes and pull requests to `main` runs `npm ci`,
format, typecheck, build and `npm pack --dry-run`, in that order. Make all of
them pass locally before opening a pull request.

## The native gotcha

Continuous integration cannot run the Swift or Kotlin code. It only checks the
TypeScript. Simulators and emulators do not do echo cancellation, noise
suppression or iOS microphone modes, so they cannot prove a native change
either.

The discipline:

- Any change under `ios/` or `android/` is tested on at least one physical
  device per platform touched, and the pull request lists which devices.
- A native fix without a device report is described as untested, in the pull
  request and in any public claim. Never write "fixed" for behaviour nobody
  has heard on a phone.
- Device commands are in the README under "Notes".

## Release recipe

Maintainers only. Contributors do not push tags.

1. Open a pull request that bumps `version` in `package.json` and adds the
   matching `## <version> (<date>)` section to `CHANGELOG.md`.
2. Merge it to `main`.
3. Push a signed tag `v<version>` on that merge commit. The tag must match
   `package.json` exactly; the publish workflow fails otherwise.

The publish workflow then runs typecheck, publishes to npm through trusted
publishing with provenance (no npm token is stored in this repository), skips
if that version is already on npm, and creates the GitHub release from the
CHANGELOG section. Never run `npm publish` by hand.

## Conventions

- Commit messages follow Conventional Commits (`fix:`, `feat:`, `docs:`,
  `chore:`).
- Formatting is Biome, configured in `biome.json`. Single quotes, no
  semicolons; let `npm run format:fix` decide.
- Public documentation is plain language, expands every acronym on first use,
  and contains no private host names, repository paths or code names.
- INTENT.md carries no status, version numbers or progress. Status goes in
  docs/ROADMAP.md as dated past-tense facts; shipped changes go in
  CHANGELOG.md.

## Where decisions live

- The reason for a change: the GitHub issue and pull request that made it.
- The order of work and what has shipped under it: docs/ROADMAP.md.
- What a released version contains: CHANGELOG.md.
- The rules themselves: INTENT.md, changed only by pull request.
