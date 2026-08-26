#!/usr/bin/env node
// Updates altstore/classic.json with the latest
// release entry. Used by .github/workflows/release.yml on tag pushes; safe
// to run locally for manual maintenance.
//
// Usage:
//   node scripts/update-altstore-sources.mjs \
//     --tag v1.2.3 [--build-number 42] [--ipa-size 12345678]
//     [--ipa-name FlutLink-ios-unsigned.ipa] [--date 2026-08-22]
//     [--notes "What's new"] [--repo owner/name]

import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const files = [path.join(root, "altstore", "classic.json")];

function arg(name) {
  const i = process.argv.indexOf(`--${name}`);
  if (i !== -1 && i + 1 < process.argv.length && !process.argv[i + 1].startsWith("--")) {
    return process.argv[i + 1];
  }
  return undefined;
}

const tag = arg("tag");
if (!tag || !/^v?\d/.test(tag)) {
  console.error("error: --tag (e.g. v1.2.3) is required");
  process.exit(1);
}

const version = tag.replace(/^v/, "");
const repo = arg("repo") ?? process.env.GITHUB_REPOSITORY ?? "OseMine/FlutLink";
const ipaName = arg("ipa-name") ?? "FlutLink-ios-unsigned.ipa";
const downloadURL = `https://github.com/${repo}/releases/download/${tag}/${ipaName}`;
const date = arg("date") ?? new Date().toISOString().slice(0, 10);
const notes = arg("notes") ?? `Release ${version}.`;
const bundleId = arg("bundle-id") ?? "com.flutcloud.flutlink.ios";

for (const file of files) {
  const source = JSON.parse(readFileSync(file, "utf8"));
  const app = source.apps.find((a) => a.bundleIdentifier === bundleId);
  if (!app) {
    console.error(`error: no app with bundleIdentifier ${bundleId} in ${file}`);
    process.exit(1);
  }

  // Drop any stale entries for this version, then prepend the new one
  // (AltStore treats the FIRST version as the latest release).
  app.versions = app.versions.filter((v) => v.version !== version);

  const entry = { version, buildVersion: undefined, date, localizedDescription: notes, downloadURL };

  // Build number: explicit --build-number wins; otherwise increment the
  // highest existing one so it stays monotonic across releases.
  const previous = app.versions
    .map((v) => parseInt(v.buildVersion ?? v.version ?? "0", 10))
    .filter((n) => Number.isFinite(n));
  const nextBuild = previous.length ? Math.max(...previous) + 1 : 1;
  entry.buildVersion = String(arg("build-number") ?? nextBuild);

  const size = arg("ipa-size");
  if (size !== undefined) entry.size = parseInt(size, 10);
  else {
    const existing = app.versions[0];
    entry.size = Number.isFinite(existing?.size) ? existing.size : 0;
  }
  entry.minOSVersion = app.versions[0]?.minOSVersion ?? "15.0";

  app.versions.unshift(entry);
  writeFileSync(file, `${JSON.stringify(source, null, 2)}\n`);
  console.log(`${path.relative(root, file)}: added ${version} (${entry.buildVersion}) -> ${downloadURL}`);
}
