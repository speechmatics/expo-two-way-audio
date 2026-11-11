# Contributing

Thanks for helping improve `@equationalapplications/expo-two-way-audio`.

## Before you start

- [INTENT.md](./INTENT.md) says what this package is and the rules it keeps. Read it first; if
  something in the code contradicts it, the code is the bug.
- [docs/ROADMAP.md](./docs/ROADMAP.md) says what gets built in what order and what each step
  waits on. If you want to work on a step, comment on the matching issue so we don't double up.

## Reporting bugs and requesting features

Open an issue at https://github.com/equationalapplications/expo-two-way-audio/issues. For bugs,
include your Expo SDK version, platform (iOS/Android, device or simulator), and a minimal
reproduction if you can.

## Pull requests

1. Fork the repo and create a branch from `main`.
2. `npm ci`, then make your change.
3. Run `npm run typecheck`, `npm run build` and `npm run format`.
4. Native changes (`ios/`, `android/`) can't be checked in CI. Please say in the PR which
   devices you tested on. Echo cancellation and microphone modes don't work on simulators.
5. Use [Conventional Commits](https://www.conventionalcommits.org/) for commit messages.

Formatting uses [Biome](https://biomejs.dev/): `npm run format:fix`.

## Dependency updates

Dependabot opens dependency and workflow-action updates weekly, grouped into one PR per
ecosystem per cycle. First-party `@equationalapplications/*` packages are deliberately not
in its config: we control those releases and bump them on our own schedule, with no
cooldown. Dependabot's 14-day cooldown (`.github/dependabot.yml`) keeps most too-new
versions out of the other PRs, but it is policy alignment, not enforcement.

The enforced gate is CI: `scripts/check-release-age.mjs` (run by the Dependency release age
workflow) age-checks every package version in a PR's `package-lock.json` delta against the
registry and fails if any was published less than 14 days ago. First-party packages are
exempt. Regardless of CI, before approving a dependency PR also check the target version's
release date (`npm view <pkg>@<version> time.modified`), including transitive versions
dragged in through the lockfile. If a version was released less than 14 days ago, **do not
merge**: apply the `cooldown-blocked` label and wait for it to mature, then remove the
label and merge. If the age can't be determined, treat it as blocked.

## Releases

Maintainers release; contributors don't need to do anything here, and please don't push tags.

1. A pull request bumps `version` in `package.json` and adds a `## <version> (<date>)` section
   to `CHANGELOG.md`. Conventional Commit messages make that section easy to write.
2. After it merges, a maintainer pushes a signed tag `v<version>` that matches `package.json`.
3. The publish workflow typechecks, publishes to npm through trusted publishing with provenance
   (no npm token is stored in this repo), and creates the GitHub release from the CHANGELOG
   section. Nothing is published by hand.
