# Contributing

Thanks for helping improve `@equationalapplications/expo-two-way-audio`.

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
