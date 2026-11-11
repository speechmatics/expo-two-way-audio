#!/usr/bin/env node
import { execFileSync } from 'node:child_process'
// Supply-chain policy: registry package versions must have been published at
// least MIN_DAYS days ago (default 14), so a poisoned upstream release has
// time to be caught and unpublished before we install it.
//
// Modes:
//   --base <git-ref>  PR check: age-check only the package-lock.json DELTA vs
//                     <ref> (entries that are new or changed version). A PR
//                     whose delta is empty passes with no cooldown.
//   (no --base)       Whole-lockfile audit of every registry package — the
//                     manual `workflow_dispatch` sweep.
//
// Usage: node scripts/check-release-age.mjs [--base origin/main] [--days 14] [--lockfile package-lock.json]
// Uses only Node built-ins so it can run before `npm ci`.
// To regenerate a compliant lockfile: npm install --before=<ISO date MIN_DAYS ago>
import fs from 'node:fs'

const args = process.argv.slice(2)
const arg = (name, fallback) => {
  const i = args.indexOf(name)
  return i >= 0 ? args[i + 1] : fallback
}
const minDays = Number(arg('--days', process.env.MIN_RELEASE_AGE_DAYS || 14))
if (!Number.isFinite(minDays) || minDays <= 0) {
  console.error(`Invalid minimum release age: ${minDays}`)
  process.exit(2)
}
const lockfile = arg('--lockfile', 'package-lock.json')
const baseFlag = args.indexOf('--base')
const baseRef = baseFlag >= 0 ? args[baseFlag + 1] : undefined
if (baseFlag >= 0 && !baseRef) {
  console.error('--base requires a git ref (e.g. --base origin/main)')
  process.exit(2)
}
const cutoff = new Date(Date.now() - minDays * 24 * 60 * 60 * 1000)
const REGISTRY = 'https://registry.npmjs.org/'

// path -> meta for every packages entry, including link:/file:/bundled ones
// (the delta comparison must see them or a changed non-registry entry would
// look new every run).
function metaMap(lock) {
  const metas = new Map()
  for (const [path, meta] of Object.entries(lock.packages ?? {})) {
    if (path && meta && typeof meta === 'object') metas.set(path, meta)
  }
  return metas
}

// Registry-checkable subset of the given paths: name -> Set(version). The
// root entry and link:/file:/bundled/non-registry entries have no own
// tarball to age-check and are reported as skipped instead of crashing.
// First-party @equationalapplications/* packages are exempt from the
// 14-day gate per house policy (their releases are ours; the wait would
// only delay our own fixes).
function wantedFrom(metas, paths) {
  const wanted = new Map()
  const skipped = []
  const exempt = []
  for (const path of paths) {
    const meta = metas.get(path)
    if (!meta || meta.link || !meta.version || meta.inBundle) {
      skipped.push(path)
      continue
    }
    const name = meta.name ?? path.slice(path.lastIndexOf('node_modules/') + 'node_modules/'.length)
    if (name.startsWith('@equationalapplications/')) {
      exempt.push(`${name}@${meta.version}`)
      continue
    }
    if (meta.resolved && !meta.resolved.startsWith(REGISTRY)) {
      skipped.push(path)
      continue
    }
    if (!wanted.has(name)) wanted.set(name, new Set())
    wanted.get(name).add(meta.version)
  }
  return { wanted, skipped, exempt }
}

const lock = JSON.parse(fs.readFileSync(lockfile, 'utf8'))
const currentMetas = metaMap(lock)
let wanted
let inherited = 0
const skipped = []
const exempt = []
let scope
if (baseRef) {
  let baseRaw
  try {
    const repoRoot = execFileSync('git', ['rev-parse', '--show-toplevel'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim()
    baseRaw = execFileSync('git', ['show', `${baseRef}:${lockfile}`], {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    })
  } catch (err) {
    console.error(
      `Could not read ${lockfile} from ${baseRef}: ${String(err.stderr ?? err.message).trim()}`,
    )
    process.exit(2)
  }
  const baseMetas = metaMap(JSON.parse(baseRaw))
  const changedPaths = []
  for (const [path, meta] of currentMetas) {
    const before = baseMetas.get(path)
    if (before && (meta.version ?? '') === (before.version ?? '')) inherited++
    else changedPaths.push(path)
  }
  if (changedPaths.length === 0) {
    console.log(`No dependency changes vs ${baseRef}; nothing to age-check.`)
    console.log(
      `${inherited} packages inherited unchanged from ${baseRef} are not re-audited in delta mode (workflow_dispatch runs the whole-lockfile audit).`,
    )
    process.exit(0)
  }
  const delta = wantedFrom(currentMetas, changedPaths)
  wanted = delta.wanted
  skipped.push(...delta.skipped)
  exempt.push(...delta.exempt)
  scope = `delta vs ${baseRef}`
} else {
  const all = wantedFrom(currentMetas, [...currentMetas.keys()])
  wanted = all.wanted
  exempt.push(...all.exempt)
  scope = 'whole lockfile'
}

const violations = []
const failures = []
const names = [...wanted.keys()]
let next = 0
async function worker() {
  while (next < names.length) {
    const name = names[next++]
    let doc
    for (let attempt = 1; attempt <= 3 && !doc; attempt++) {
      try {
        const res = await fetch(REGISTRY + name.replace(/\//g, '%2f'), {
          signal: AbortSignal.timeout(30_000),
        })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        doc = await res.json()
      } catch (err) {
        if (attempt === 3) failures.push(`${name}: ${err.message}`)
      }
    }
    if (!doc) continue
    for (const version of wanted.get(name)) {
      const published = doc.time?.[version]
      if (!published) failures.push(`${name}@${version}: no publish time in registry`)
      else if (new Date(published) > cutoff)
        violations.push(`${name}@${version} published ${published.slice(0, 10)}`)
    }
  }
}
await Promise.all(Array.from({ length: 16 }, worker))

if (baseRef) {
  const versionCount = [...wanted.values()].reduce((n, s) => n + s.size, 0)
  console.log(
    `Checked ${versionCount} changed package version(s) across ${names.length} packages (${scope}) against a ${minDays}-day minimum release age (cutoff ${cutoff.toISOString().slice(0, 10)}).`,
  )
  if (skipped.length)
    console.log(
      `Not age-checkable, skipped (link:/file:/bundled/non-registry entries): ${[...new Set(skipped)].sort().join(', ')}`,
    )
  if (exempt.length)
    console.log(
      `First-party @equationalapplications/* releases exempt from the gate: ${[...new Set(exempt)].sort().join(', ')}`,
    )
  console.log(
    `${inherited} packages inherited unchanged from ${baseRef} are not re-audited in delta mode (workflow_dispatch runs the whole-lockfile audit).`,
  )
} else {
  console.log(
    `Checked ${names.length} packages against a ${minDays}-day minimum release age (cutoff ${cutoff.toISOString().slice(0, 10)}).`,
  )
}
if (failures.length) console.error(`Could not verify:\n  ${failures.sort().join('\n  ')}`)
if (violations.length) {
  console.error(`Too new for policy:\n  ${violations.sort().join('\n  ')}`)
  if (baseRef) {
    console.error(
      `Resolve by waiting until these publish dates are ${minDays} days old (or pinning releases published before ${cutoff.toISOString().slice(0, 10)}).`,
    )
  } else {
    console.error(
      `Regenerate with: rm -rf node_modules package-lock.json && npm install --before=${cutoff.toISOString()}`,
    )
  }
}
process.exit(violations.length || failures.length ? 1 : 0)
